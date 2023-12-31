/*
 * (C) Copyright 2018-2019 Webdrone SAS (https://www.webdrone.fr/) and contributors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. This program is
 * not suitable for any direct or indirect application in MILITARY industry See the GNU Affero
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package org.meveo.admin.job;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.meveo.admin.async.SubListCreator;
import org.meveo.admin.exception.BusinessException;
import org.meveo.commons.utils.FileUtils;
import org.meveo.commons.utils.ParamBean;
import org.meveo.commons.utils.ParamBeanFactory;
import org.meveo.model.crm.CustomFieldTemplate;
import org.meveo.model.crm.custom.CustomFieldTypeEnum;
import org.meveo.model.customEntities.CustomEntityTemplate;
import org.meveo.model.jobs.JobCategoryEnum;
import org.meveo.model.jobs.JobExecutionResultImpl;
import org.meveo.model.jobs.JobInstance;
import org.meveo.model.persistence.sql.SQLStorageConfiguration;
import org.meveo.security.MeveoUser;
import org.meveo.security.keycloak.CurrentUserProvider;
import org.meveo.service.custom.CustomEntityTemplateService;
import org.meveo.service.custom.CustomTableService;
import org.meveo.service.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Import data to custom tables
 * 
 * @author Andrius Karpavicius
 * @author Edward P. Legaspi | czetsuya@gmail.com
 * @version 6.6.0
 */
@Stateless
public class CustomTableImportJob extends Job {
	
	private static Logger log = LoggerFactory.getLogger(CustomTableImportJob.class);

    @Inject
    private ParamBeanFactory paramBeanFactory;

    @Inject
    private CustomTableService customTableService;

    @Inject
    private CurrentUserProvider currentUserProvider;

    @Inject
    private CustomEntityTemplateService customEntityTemplateService;

    @EJB
    private CustomTableImportJob customTableImportJobAsync;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    @TransactionAttribute(TransactionAttributeType.NEVER)
    protected void execute(JobExecutionResultImpl result, JobInstance jobInstance, Map<String, Object> params) throws BusinessException {
        try {
            Long nbRuns;
            Long waitingMillis;
            String sqlConnectionCode = null;
            try {
                nbRuns = (Long) this.getParamOrCFValue(jobInstance, "nbRuns");
                waitingMillis = (Long) this.getParamOrCFValue(jobInstance, "waitingMillis");
                sqlConnectionCode = (String) this.getParamOrCFValue(jobInstance, "sqlConnectionCode");
                
                if (nbRuns == -1) {
                    nbRuns = (long) Runtime.getRuntime().availableProcessors();
                }
                
            } catch (Exception e) {
                nbRuns = 1L;
                waitingMillis = 0L;
                log.warn("Cant get customFields for " + jobInstance.getJobTemplate(), e);
            }

            ParamBean parambean = paramBeanFactory.getInstance();
            String customTableDir = parambean.getChrootDir(currentUser.getProviderCode()) + File.separator + "imports" + File.separator + "customTables" + File.separator;

            MeveoUser lastCurrentUser = currentUser.unProxy();

            // Process files that should overrite data

            File inputDir = new File(customTableDir + "input");

            if (!inputDir.exists()) {
                inputDir.mkdirs();
            }

            List<File> files = FileUtils.listFiles(inputDir, "csv", null);
            if (files != null && !files.isEmpty()) {

                result.setNbItemsToProcess(files.size());

                List<Future<String>> futures = new ArrayList<>();
                SubListCreator subListCreator = new SubListCreator(files, nbRuns.intValue());

                while (subListCreator.isHasNext()) {
                    futures.add(customTableImportJobAsync.launchAndForget(sqlConnectionCode, customTableDir, (List<File>) subListCreator.getNextWorkSet(), result, jobInstance.getParametres(),
                        lastCurrentUser));
                    if (subListCreator.isHasNext()) {
                        try {
                            Thread.sleep(waitingMillis.longValue());
                        } catch (InterruptedException e) {
                            log.error("", e);
                        }
                    }
                }

                // Wait for all async methods to finish
                for (Future<String> future : futures) {
                    try {
                        future.get();

                    } catch (InterruptedException e) {
                        // It was cancelled from outside - no interest

                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause();
                        result.registerError(cause.getMessage());
                        log.error("Failed to execute async method", cause);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to run custom table data import", e);
            result.registerError(e.getMessage());
        }
    }

    /**
     * Import a set of file
     * 
     * @param customTableDir Custom table import file directory
     * @param files Files to import
     * @param result Job execution result
     * @param parameter Execution parameters
     * @param lastCurrentUser Current user
     * @return Future execution object
     * @throws BusinessException Business exception
     */
    @Asynchronous
    @TransactionAttribute(TransactionAttributeType.NEVER)
    public Future<String> launchAndForget(String sqlConnectionCode, String customTableDir, List<File> files, JobExecutionResultImpl result, String parameter, MeveoUser lastCurrentUser)
            throws BusinessException {

        currentUserProvider.reestablishAuthentication(lastCurrentUser);

        String outputDir = customTableDir + "output";
        String rejectDir = customTableDir + "reject";

        File f = new File(outputDir);
        if (!f.exists()) {
            f.mkdirs();
        }
        f = new File(rejectDir);
        if (!f.exists()) {
            f.mkdirs();
        }

        for (File file : files) {
            if (!jobExecutionService.isJobRunningOnThis(result.getJobInstance())) {
                break;
            }

            log.info("Importing data into custom table from file {}", file.getAbsolutePath());
            String filename = file.getName();

            boolean appendImportedData = filename.contains(CustomTableService.FILE_APPEND);

            List<CustomEntityTemplate> cets = customEntityTemplateService.listCustomTableTemplates();
            CustomEntityTemplate customTable = null;

            for (CustomEntityTemplate cet : cets) {
                if (filename.startsWith(SQLStorageConfiguration.getCetDbTablename(cet.getCode()))) {
                    customTable = cet;
                    break;
                }
            }

            try {
                if (customTable == null) {
                    throw new BusinessException("No Custom table matched by name " + filename);
                }

                customTableService.importData(sqlConnectionCode, customTable, file, appendImportedData);
                result.registerSucces();
                FileUtils.moveFile(outputDir, file, filename);

            } catch (Exception e) {
                log.error("Failed to import data from file {} into custom table", filename, e);
                result.registerError(filename, e.getMessage());
                FileUtils.moveFile(rejectDir, file, filename);
            }
        }

        return new AsyncResult<String>("OK");
    }

    @Override
    public JobCategoryEnum getJobCategory() {
        return JobCategoryEnum.UTILS;
    }

    @Override
    public Map<String, CustomFieldTemplate> getCustomFields() {
        Map<String, CustomFieldTemplate> result = new HashMap<String, CustomFieldTemplate>();

        CustomFieldTemplate nbRuns = new CustomFieldTemplate();
        nbRuns.setCode("nbRuns");
        nbRuns.setAppliesTo("JOB_CustomTableImportJob");
        nbRuns.setActive(true);
        nbRuns.setDescription(resourceMessages.getString("jobExecution.nbRuns"));
        nbRuns.setFieldType(CustomFieldTypeEnum.LONG);
        nbRuns.setDefaultValue("1");
        nbRuns.setValueRequired(false);
        result.put("nbRuns", nbRuns);

        CustomFieldTemplate waitingMillis = new CustomFieldTemplate();
        waitingMillis.setCode("waitingMillis");
        waitingMillis.setAppliesTo("JOB_CustomTableImportJob");
        waitingMillis.setActive(true);
        waitingMillis.setDescription(resourceMessages.getString("jobExecution.waitingMillis"));
        waitingMillis.setFieldType(CustomFieldTypeEnum.LONG);
        waitingMillis.setDefaultValue("0");
        waitingMillis.setValueRequired(false);
        result.put("waitingMillis", waitingMillis);

        return result;
    }
}