/*
 * (C) Copyright 2015-2016 Opencell SAS (http://opencellsoft.com/) and contributors.
 * (C) Copyright 2009-2014 Manaty SARL (http://manaty.net/) and contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * This program is not suitable for any direct or indirect application in MILITARY industry
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.meveocrm.admin.action.reporting;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.jboss.seam.international.status.builder.BundleKey;
import org.meveo.admin.action.BaseBean;
import org.meveo.admin.exception.BusinessException;
import org.meveo.admin.exception.RejectedImportException;
import org.meveo.commons.utils.CsvBuilder;
import org.meveo.commons.utils.CsvReader;
import org.meveo.commons.utils.StringUtils;
import org.meveo.model.dwh.MeasurableQuantity;
import org.meveo.model.dwh.MeasurementPeriodEnum;
import org.meveo.model.notification.StrategyImportTypeEnum;
import org.meveo.model.shared.DateUtils;
import org.meveo.service.base.local.IPersistenceService;
import org.meveocrm.services.dwh.MeasurableQuantityService;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Wassim Drira
 * @lastModifiedVersion 5.0
 *
 */
@Named
@ViewScoped
public class MeasurableQuantityBean extends BaseBean<MeasurableQuantity> {

    private static final long serialVersionUID = -1644247310944456827L;
    
    private static Logger log = LoggerFactory.getLogger(MeasurableQuantityBean.class);

    @Inject
    MeasurableQuantityService measurableQuantityService;

    CsvReader csvReader = null;
    CsvBuilder csv = null;
    private UploadedFile file;
    private String existingEntitiesCsvFile = null;

    private StrategyImportTypeEnum strategyImportType;

    private static final int CODE = 0;
    private static final int DIMENSION_1 = 1;
    private static final int DIMENSION_2 = 2;
    private static final int DIMENSION_3 = 3;
    private static final int DIMENSION_4 = 4;
    private static final int SQL_QUERY = 5;
    private static final int CYPHER_QUERY = 6;
    private static final int MEASUREMENT_PERIOD = 7;
    private static final int LAST_MEASURE_DATE = 8;
    private static final int EDITABLE = 9;

    public MeasurableQuantityBean() {
        super(MeasurableQuantity.class);
    }

    @Override
    protected IPersistenceService<MeasurableQuantity> getPersistenceService() {
        return measurableQuantityService;
    }

    @Override
    protected String getListViewName() {
        return "measurableQuantities";
    }

    public void exportToFile() {

        CsvBuilder csv = new CsvBuilder();

        csv.appendValue("Code");
        csv.appendValue("Dimension 1");
        csv.appendValue("Dimension 2");
        csv.appendValue("Dimension 3");
        csv.appendValue("Dimension 4");
        csv.appendValue("SQL Query");
        csv.appendValue("Cypher Query");
        csv.appendValue("Measurement period");
        csv.appendValue("Last measure date");
        csv.appendValue("Editable");
        csv.startNewLine();
        for (MeasurableQuantity measurableQuantity : (!filters.isEmpty() && filters.size() > 0) ? getLazyDataModel() : measurableQuantityService.list()) {
            csv.appendValue(measurableQuantity.getCode());
            csv.appendValue(measurableQuantity.getDimension1());
            csv.appendValue(measurableQuantity.getDimension2());
            csv.appendValue(measurableQuantity.getDimension3());
            csv.appendValue(measurableQuantity.getDimension4());
            csv.appendValue(measurableQuantity.getSqlQuery());
            csv.appendValue(measurableQuantity.getCypherQuery());
            csv.appendValue(measurableQuantity.getMeasurementPeriod() != null ? measurableQuantity.getMeasurementPeriod() + "" : null);
            csv.appendValue(DateUtils.formatDateWithPattern(measurableQuantity.getLastMeasureDate(), "dd/MM/yyyy"));
            csv.appendValue(measurableQuantity.isEditable() + "");
            csv.startNewLine();
        }
        InputStream inputStream = new ByteArrayInputStream(csv.toString().getBytes());
        csv.download(inputStream, "MeasurableQuantity.csv");
    }

    public void handleFileUpload(FileUploadEvent event) {
        try {
            file = event.getFile();
            log.debug("File uploaded " + file.getFileName());
            upload();
            messages.info(new BundleKey("messages", "import.csv.successful"));
        } catch (Exception e) {
            log.error("Failed to handle uploaded file {}", event.getFile().getFileName(), e);
            messages.error(new BundleKey("messages", "import.csv.failed"), e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private void upload() throws IOException, BusinessException {
        if (file == null) {
            return;
        }
        csvReader = new CsvReader(file.getInputstream(), ';', Charset.forName("ISO-8859-1"));
        csvReader.readHeaders();

        String existingEntitiesCSV = paramBeanFactory.getInstance().getProperty("existingEntities.csv.dir", "existingEntitiesCSV");
        File dir = new File(paramBeanFactory.getChrootDir() + File.separator + existingEntitiesCSV);
        dir.mkdirs();
        existingEntitiesCsvFile = dir.getAbsolutePath() + File.separator + "MeasurableQuantitys_" + new SimpleDateFormat("ddMMyyyyHHmmSS").format(new Date()) + ".csv";
        csv = new CsvBuilder();
        boolean isEntityAlreadyExist = false;
        while (csvReader.readRecord()) {
            String[] values = csvReader.getValues();
            MeasurableQuantity existingEntity = measurableQuantityService.findByCode(values[CODE]);
            if (existingEntity != null) {
                checkSelectedStrategy(values, existingEntity, isEntityAlreadyExist);
                isEntityAlreadyExist = true;
            } else {

                MeasurableQuantity measurableQuantity = new MeasurableQuantity();
                measurableQuantity.setCode(values[CODE]);
                measurableQuantity.setDimension1(values[DIMENSION_1]);
                measurableQuantity.setDimension2(values[DIMENSION_2]);
                measurableQuantity.setDimension3(values[DIMENSION_3]);
                measurableQuantity.setDimension4(values[DIMENSION_4]);
                measurableQuantity.setCypherQuery(values[CYPHER_QUERY]);
                measurableQuantity.setSqlQuery(values[SQL_QUERY]);
                if (!StringUtils.isBlank(values[MEASUREMENT_PERIOD])) {
                    measurableQuantity.setMeasurementPeriod(MeasurementPeriodEnum.valueOf(values[MEASUREMENT_PERIOD]));
                }
                measurableQuantity.setLastMeasureDate(DateUtils.parseDateWithPattern((values[LAST_MEASURE_DATE]), "dd/MM/yyyy"));
                measurableQuantity.setEditable(Boolean.parseBoolean(values[EDITABLE]));
                measurableQuantityService.create(measurableQuantity);
            }
        }
        if (isEntityAlreadyExist && strategyImportType.equals(StrategyImportTypeEnum.REJECT_EXISTING_RECORDS)) {
            csv.writeFile(csv.toString().getBytes(), existingEntitiesCsvFile);
        }
    }

    public void checkSelectedStrategy(String[] values, MeasurableQuantity existingEntity, boolean isEntityAlreadyExist) throws BusinessException {
        if (strategyImportType.equals(StrategyImportTypeEnum.UPDATED)) {

            existingEntity.setDimension1(values[DIMENSION_1]);
            existingEntity.setDimension2(values[DIMENSION_2]);
            existingEntity.setDimension3(values[DIMENSION_3]);
            existingEntity.setDimension4(values[DIMENSION_4]);
            existingEntity.setCypherQuery(values[CYPHER_QUERY]);
            existingEntity.setSqlQuery(values[SQL_QUERY]);
            if (!StringUtils.isBlank(values[MEASUREMENT_PERIOD])) {
                existingEntity.setMeasurementPeriod(MeasurementPeriodEnum.valueOf(values[MEASUREMENT_PERIOD]));
            }
            existingEntity.setLastMeasureDate(DateUtils.parseDateWithPattern((values[LAST_MEASURE_DATE]), "dd/MM/yyyy"));
            existingEntity.setEditable(Boolean.parseBoolean(values[EDITABLE]));
            measurableQuantityService.update(existingEntity);
        } else if (strategyImportType.equals(StrategyImportTypeEnum.REJECTE_IMPORT)) {
            throw new RejectedImportException("notification.rejectImport");
        } else if (strategyImportType.equals(StrategyImportTypeEnum.REJECT_EXISTING_RECORDS)) {
            if (!isEntityAlreadyExist) {
                csv.appendValue("Code");
                csv.appendValue("Dimension 1");
                csv.appendValue("Dimension 2");
                csv.appendValue("Dimension 3");
                csv.appendValue("Dimension 4");
                csv.appendValue("SQL Query");
                csv.appendValue("CYPHER Query");
                csv.appendValue("Measurement period");
                csv.appendValue("Last measure date");
                csv.appendValue("Editable");
            }
            csv.startNewLine();
            csv.appendValue(values[CODE]);
            csv.appendValue(values[DIMENSION_1]);
            csv.appendValue(values[DIMENSION_2]);
            csv.appendValue(values[DIMENSION_3]);
            csv.appendValue(values[DIMENSION_4]);
            csv.appendValue(values[SQL_QUERY]);
            csv.appendValue(values[CYPHER_QUERY]);
            csv.appendValue(values[MEASUREMENT_PERIOD]);
            csv.appendValue(values[LAST_MEASURE_DATE]);
            csv.appendValue(values[EDITABLE]);
        }
    }

    public StrategyImportTypeEnum getStrategyImportType() {
        return strategyImportType;
    }

    public void setStrategyImportType(StrategyImportTypeEnum strategyImportType) {
        this.strategyImportType = strategyImportType;
    }
}
