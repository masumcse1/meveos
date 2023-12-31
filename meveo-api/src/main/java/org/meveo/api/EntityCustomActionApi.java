package org.meveo.api;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.Stateless;
import javax.inject.Inject;

import org.meveo.admin.exception.BusinessException;
import org.meveo.admin.exception.ElementNotFoundException;
import org.meveo.admin.exception.InvalidPermissionException;
import org.meveo.admin.exception.InvalidScriptException;
import org.meveo.api.dto.CustomFieldTemplateDto;
import org.meveo.api.dto.EntityCustomActionDto;
import org.meveo.api.dto.ScriptInstanceErrorDto;
import org.meveo.api.dto.module.MeveoModuleItemDto;
import org.meveo.api.exception.BusinessApiException;
import org.meveo.api.exception.EntityAlreadyExistsException;
import org.meveo.api.exception.EntityDoesNotExistsException;
import org.meveo.api.exception.InvalidParameterException;
import org.meveo.api.exception.MeveoApiException;
import org.meveo.api.exception.MissingParameterException;
import org.meveo.commons.utils.ReflectionUtils;
import org.meveo.commons.utils.StringUtils;
import org.meveo.elresolver.ELException;
import org.meveo.model.CustomFieldEntity;
import org.meveo.model.IEntity;
import org.meveo.model.crm.custom.EntityCustomAction;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.scripts.ScriptInstance;
import org.meveo.model.scripts.ScriptInstanceError;
import org.meveo.model.storage.Repository;
import org.meveo.service.base.MeveoValueExpressionWrapper;
import org.meveo.service.base.local.IPersistenceService;
import org.meveo.service.custom.EntityCustomActionService;
import org.meveo.service.script.Script;
import org.meveo.service.script.ScriptInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Edward P. Legaspi | edward.legaspi@manaty.net
 * @version 6.12
 **/
@Stateless
public class EntityCustomActionApi extends BaseCrudApi<EntityCustomAction, EntityCustomActionDto> {

	private static Logger log = LoggerFactory.getLogger(EntityCustomActionApi.class);
	
    /**
	 * Instantiates a new EntityCustomActionApi
	 *
	 * @param jpaClass
	 * @param dtoClass
	 */
	public EntityCustomActionApi() {
		super(EntityCustomAction.class, EntityCustomActionDto.class);
	}

	@Inject
    private ScriptInstanceService scriptInstanceService;

    @Inject
    private EntityCustomActionService entityCustomActionService;
    
    @Inject
    private Repository repository;

    public List<ScriptInstanceErrorDto> create(EntityCustomActionDto actionDto, String appliesTo)
            throws MissingParameterException, EntityAlreadyExistsException, MeveoApiException, BusinessException {

        checkDtoAndSetAppliesTo(actionDto, appliesTo, false);

        if (entityCustomActionService.findByCodeAndAppliesTo(actionDto.getCode(), actionDto.getAppliesTo()) != null) {
            throw new EntityAlreadyExistsException(EntityCustomAction.class, actionDto.getCode() + "/" + actionDto.getAppliesTo());
        }

        EntityCustomAction action = fromDto(actionDto);

        entityCustomActionService.create(action);

        List<ScriptInstanceErrorDto> result = new ArrayList<ScriptInstanceErrorDto>();
        ScriptInstance scriptInstance = action.getScript();
        if (scriptInstance.isError() != null && scriptInstance.isError().booleanValue()) {
            for (ScriptInstanceError error : scriptInstance.getScriptErrors()) {
                ScriptInstanceErrorDto errorDto = new ScriptInstanceErrorDto(error);
                result.add(errorDto);
            }
        }
        return result;
    }
    
    @Override
    public EntityCustomAction fromDto(EntityCustomActionDto dto, EntityCustomAction action) throws MeveoApiException, BusinessException {
    	if (action == null) {
    		action = new EntityCustomAction();
    	}
    	entityCustomActionFromDTO(dto, action);
    	return action;
    }

    public List<ScriptInstanceErrorDto> update(EntityCustomActionDto actionDto, String appliesTo)
            throws MissingParameterException, EntityDoesNotExistsException, MeveoApiException, BusinessException {

        checkDtoAndSetAppliesTo(actionDto, appliesTo, true);

        EntityCustomAction action = entityCustomActionService.findByCodeAndAppliesTo(actionDto.getCode(), actionDto.getAppliesTo());
        if (action == null) {
            throw new EntityDoesNotExistsException(EntityCustomAction.class, actionDto.getCode() + "/" + actionDto.getAppliesTo());
        }

        entityCustomActionFromDTO(actionDto, action);

        action = entityCustomActionService.update(action);

        List<ScriptInstanceErrorDto> result = new ArrayList<ScriptInstanceErrorDto>();
        ScriptInstance scriptInstance = action.getScript();
        if (scriptInstance.isError() != null && scriptInstance.isError().booleanValue()) {
            for (ScriptInstanceError error : scriptInstance.getScriptErrors()) {
                ScriptInstanceErrorDto errorDto = new ScriptInstanceErrorDto(error);
                result.add(errorDto);
            }
        }
        return result;
    }

    /**
     * Find entity custom action by its code and appliesTo attributes
     * 
     * @param actionCode Entity custom action code
     * @param appliesTo Applies to
     * @return DTO
     * @throws EntityDoesNotExistsException Entity custom action was not found
     * @throws MissingParameterException A parameter, necessary to find an entity custom action, was not provided
     */
    public EntityCustomActionDto find(String actionCode, String appliesTo) throws EntityDoesNotExistsException, MissingParameterException {

        if (StringUtils.isBlank(actionCode)) {
            missingParameters.add("actionCode");
        }
        if (StringUtils.isBlank(appliesTo)) {
            missingParameters.add("appliesTo");
        }
        handleMissingParameters();

        EntityCustomAction action = entityCustomActionService.findByCodeAndAppliesTo(actionCode, appliesTo);
        if (action == null) {
            throw new EntityDoesNotExistsException(EntityCustomAction.class, actionCode + "/" + appliesTo);
        }
        EntityCustomActionDto actionDto = new EntityCustomActionDto(action);

        return actionDto;
    }

    /**
     * Same as find method, only ignore EntityDoesNotExistException exception and return Null instead
     * 
     * @param actionCode Entity custom action code
     * @param appliesTo Applies to
     * @return DTO or Null if not found
     * @throws MissingParameterException A parameter, necessary to find an entity custom action, was not provided
     */
    public EntityCustomActionDto findIgnoreNotFound(String actionCode, String appliesTo) throws MissingParameterException {
        try {
            return find(actionCode, appliesTo);
        } catch (EntityDoesNotExistsException e) {
            return null;
        }
    }
    
    @Override
	public boolean exists(EntityCustomActionDto dto) {
		return entityCustomActionService.exists(dto.getCode(), dto.getAppliesTo());
	}

    public void remove(String actionCode, String appliesTo) throws EntityDoesNotExistsException, MissingParameterException, BusinessException {

        if (StringUtils.isBlank(actionCode)) {
            missingParameters.add("actionCode");
        }
        if (StringUtils.isBlank(appliesTo)) {
            missingParameters.add("appliesTo");
        }

        handleMissingParameters();

        EntityCustomAction scriptInstance = entityCustomActionService.findByCodeAndAppliesTo(actionCode, appliesTo);
        if (scriptInstance == null) {
            throw new EntityDoesNotExistsException(EntityCustomAction.class, actionCode);
        }
        entityCustomActionService.remove(scriptInstance);
    }

    public List<ScriptInstanceErrorDto> createOrUpdate(EntityCustomActionDto postData, String appliesTo)
            throws MissingParameterException, EntityAlreadyExistsException, EntityDoesNotExistsException, MeveoApiException, BusinessException {

        List<ScriptInstanceErrorDto> result = new ArrayList<ScriptInstanceErrorDto>();
        checkDtoAndSetAppliesTo(postData, appliesTo, true);

        EntityCustomAction scriptInstance = entityCustomActionService.findByCodeAndAppliesTo(postData.getCode(), postData.getAppliesTo());

        if (scriptInstance == null) {
            result = create(postData, appliesTo);
        } else {
            result = update(postData, appliesTo);
        }
        return result;
    }

    private void checkDtoAndSetAppliesTo(EntityCustomActionDto dto, String appliesTo, boolean isUpdate) throws MissingParameterException, BusinessApiException {

        if (StringUtils.isBlank(dto.getCode())) {
            missingParameters.add("code");
        }

        if (appliesTo != null) {
            dto.setAppliesTo(appliesTo);
        }

        if (StringUtils.isBlank(dto.getAppliesTo())) {
            missingParameters.add("appliesTo");
        }
        if (StringUtils.isBlank(dto.getScript())) {
            if (!isUpdate) {
                missingParameters.add("script");
            }
        }

        handleMissingParameters();
    }

    /**
     * Convert EntityCustomActionDto to a EntityCustomAction instance.
     * 
     * @param dto EntityCustomActionDto object to convert
     * @param action EntityCustomAction to update with values from dto
     * @return A new or updated EntityCustomAction object
     * @throws MeveoApiException
     * @throws BusinessException
     */
    private void entityCustomActionFromDTO(EntityCustomActionDto dto, EntityCustomAction action) throws MeveoApiException, BusinessException {

        if (action.isTransient()) {
            action.setCode(dto.getCode());
            action.setAppliesTo(dto.getAppliesTo());
        }
        if (dto.getDescription() != null) {
            action.setDescription(dto.getDescription());
        }
        if (dto.getApplicableOnEl() != null) {
            action.setApplicableOnEl(dto.getApplicableOnEl());
        }
        if (dto.getLabel() != null) {
            action.setLabel(dto.getLabel());
        }
        if (dto.getGuiPosition() != null) {
            action.setGuiPosition(dto.getGuiPosition());
        }

        if (dto.getLabelsTranslated() != null) {
            action.setLabelI18n(convertMultiLanguageToMapOfValues(dto.getLabelsTranslated(), action.getLabelI18n()));
        }

        if (dto.getScript() != null) {
            // Extract script associated with an action
            ScriptInstance scriptInstance = scriptInstanceService.findByCode(dto.getScript());
            if (scriptInstance == null) {
                throw new EntityDoesNotExistsException(ScriptInstance.class, dto.getScript());
            }
            action.setScript(scriptInstance);
        }
        
		if (dto.getApplicableToEntityList() != null) {
			action.setApplicableToEntityList(dto.getApplicableToEntityList());
		}

		if (dto.getApplicableToEntityInstance() != null) {
			action.setApplicableToEntityInstance(dto.getApplicableToEntityInstance());
		}
		
		action.setScriptParameters(dto.getScriptParameters());
    }

    @SuppressWarnings("rawtypes")
    public String execute(String actionCode, String appliesTo, String entityCode)
            throws MeveoApiException, InvalidScriptException, ElementNotFoundException, InvalidPermissionException, BusinessException {
        EntityCustomAction action = entityCustomActionService.findByCodeAndAppliesTo(actionCode, appliesTo);
        if (action == null) {
            throw new EntityDoesNotExistsException(EntityCustomAction.class, actionCode + "/" + appliesTo);
        }

        Set<Class<?>> cfClasses = ReflectionUtils.getClassesAnnotatedWith(CustomFieldEntity.class, "org.meveo.model");
        Class entityClass = null;
        for (Class<?> clazz : cfClasses) {
            if (appliesTo.equals(clazz.getAnnotation(CustomFieldEntity.class).cftCodePrefix())) {
                entityClass = clazz;
                break;
            }
        }

        IEntity entity = (IEntity) entityCustomActionService.findByEntityClassAndCode(entityClass, entityCode);

        Map<String, Object> context = new HashMap<String, Object>();
		Map<Object, Object> elContext = new HashMap<>(context);
		elContext.put("entity", entity);
		
		action.getScriptParameters().forEach((key, value) -> {
			try {
				context.put(key, MeveoValueExpressionWrapper.evaluateExpression(value, elContext, Object.class));
			} catch (ELException e) {
				log.error("Failed to evaluate el for custom action", e);
			}
		});
        
        Map<String, Object> result = scriptInstanceService.execute(entity, repository, action.getScript().getCode(), context);

        if (result.containsKey(Script.RESULT_GUI_OUTCOME)) {
            return (String) result.get(Script.RESULT_GUI_OUTCOME);
        }

        return null;
    }

	public Collection<EntityCustomActionDto> readEcas(File directory) {
    	List<EntityCustomActionDto> ecaDtos = new ArrayList<>();
    	
    	for (File cetOrCrtDir : directory.listFiles()) {
    		if(!cetOrCrtDir.isDirectory()) {
    			continue;
    		}
    		
    		for (File cftFile : cetOrCrtDir.listFiles()) {
    			try {
					ecaDtos.add(JacksonUtil.read(cftFile, EntityCustomActionDto.class));
				} catch (IOException e) {
					log.error("Failed to read custom action file", e);
					return null;
				}
    		}
    	}
    	
    	return ecaDtos;
    }
    
	@Override
	public EntityCustomActionDto find(String code) throws EntityDoesNotExistsException, MissingParameterException, InvalidParameterException, MeveoApiException, org.meveo.exceptions.EntityDoesNotExistsException {
		throw new UnsupportedOperationException("Use find(code, appliesTo) instead");
	}

	@Override
	public EntityCustomAction createOrUpdate(EntityCustomActionDto dtoData) throws MeveoApiException, BusinessException {
		EntityCustomAction eca = this.entityCustomActionService.findByCodeAndAppliesTo(dtoData.getCode(), dtoData.getAppliesTo());
		if (eca == null) {
			this.create(dtoData, dtoData.getAppliesTo());
		} else {
			this.update(dtoData, dtoData.getAppliesTo());
		}
		return eca;
	}

	@Override
	public EntityCustomActionDto toDto(EntityCustomAction entity) throws MeveoApiException {
		return new EntityCustomActionDto(entity);
	}

	@Override
	public IPersistenceService<EntityCustomAction> getPersistenceService() {
		return this.entityCustomActionService;
	}

}