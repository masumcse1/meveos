/**
 * 
 */
package org.meveo.service.admin.impl;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.meveo.admin.exception.BusinessException;
import org.meveo.model.BusinessEntity;
import org.meveo.model.module.MeveoModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeveoModuleHelper {

	@Inject
	private MeveoModuleService moduleService;
	
	private static Logger log = LoggerFactory.getLogger(MeveoModuleHelper.class);

	@SuppressWarnings("unchecked")
	public <T extends BusinessEntity> List<T> getEntities(MeveoModule module, Class<T> entityClass) {
		return module.getModuleItems()
				.stream()
				.filter(item -> item.getItemClass().equals(entityClass.getName()))
				.map(item -> {
					try {
						moduleService.loadModuleItem(item);
						if (item.getItemEntity() == null) log.error("Module item not loaded {}", item);
						return (T) item.getItemEntity();
					} catch (BusinessException e) {
						log.error("Failed to load module item", e);
						return null;
					}
				}).collect(Collectors.toList());
	}
}
