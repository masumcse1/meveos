package org.meveo.admin.action.storage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.jboss.seam.international.status.builder.BundleKey;
import org.meveo.admin.action.BaseCrudBean;
import org.meveo.admin.exception.BusinessException;
import org.meveo.admin.listener.CommitMessageBean;
import org.meveo.admin.web.interceptor.ActionMethod;
import org.meveo.api.BaseCrudApi;
import org.meveo.api.dto.git.GitRepositoryDto;
import org.meveo.api.dto.module.ModuleDependencyDto;
import org.meveo.api.exception.MissingModuleException;
import org.meveo.api.git.GitRepositoryApi;
import org.meveo.api.module.MeveoModuleApi;
import org.meveo.commons.utils.ParamBean;
import org.meveo.elresolver.ELException;
import org.meveo.model.git.GitRepository;
import org.meveo.model.module.MeveoModule;
import org.meveo.model.storage.Repository;
import org.meveo.security.PasswordUtils;
import org.meveo.service.admin.impl.MeveoModuleService;
import org.meveo.service.base.local.IPersistenceService;
import org.meveo.service.git.GitClient;
import org.meveo.service.git.GitHelper;
import org.meveo.service.git.GitRepositoryService;
import org.meveo.util.view.MessagesHelper;
import org.primefaces.PrimeFaces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.DualListModel;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for managing git repository model.
 * 
 * @author Edward P. Legaspi | edward.legaspi@manaty.net
 * @version 6.9.0
 * @see GitRepository
 */
@Named
@ViewScoped
public class GitRepositoryBean extends BaseCrudBean<GitRepository, GitRepositoryDto> {

	private static final long serialVersionUID = 8661265102557481231L;

	@Inject
	private GitRepositoryService gitRepositoryService;

	@Inject
	private GitRepositoryApi gitRepositoryApi;
	
	@Inject
	private MeveoModuleApi moduleApi;
	
	@Inject
	private MeveoModuleService moduleService;

	private static Logger log = LoggerFactory.getLogger(GitRepositoryBean.class);

	@Inject
	private GitClient gitClient;
	
    @Inject
    private CommitMessageBean commitMessageBean;

	private String username;

	private String password;

	private String branch;
	
	protected DualListModel<String> repositories;
	
	protected DualListModel<String> pendingGitFiles = new DualListModel<String>();
	
	private List<ModuleDependencyDto> missingDependencies;
	
	private List<String> branchList = List.of();

	public GitRepositoryBean() {
		super(GitRepository.class);
	}
	
	@PostConstruct
	public void init() {
        repositories = new DualListModel<>(repositoryService.list().stream().map(Repository::getCode).collect(Collectors.toList()), new ArrayList<>());
	}

	@Override
	public GitRepository initEntity() {
		GitRepository entity = super.initEntity();
		
		if (entity != null && entity.getId() !=null && entity.getCode() != null) {
			try {
				branchList = gitClient.listRefs(entity);
				pendingGitFiles.setSource(gitClient.listPendingChanges(entity));
			} catch (BusinessException e) {
				log.warn("Failed to retrieve refs list", e);
			}
		}
		

		
		return entity;
	}
	
	/**
	 * @return the {@link #branchList}
	 */
	public List<String> getBranchList(String query) {
		return branchList.stream()
				.filter(ref -> ref.startsWith(query))
				.collect(Collectors.toList());
	}

	@ActionMethod
	public String saveOrUpdateGit() throws BusinessException, ELException {

		if (entity.getId() == null && entity.getRemoteOrigin() != null) {
			
			if(this.password.equals(entity.getDefaultRemotePassword())) {
				password = PasswordUtils.decrypt(entity.getSalt(), password);
			}
			
			try {
				gitRepositoryService.create(entity, false, username,password);
			} catch (Exception e) {
				return MessagesHelper.error(messages, e);
			}
		}

		String result = saveOrUpdate(false);

		if (result == null) {
			FacesContext.getCurrentInstance().validationFailed();
		}

		return result;
	}

	public void pushRemote() {
		try {
			if(this.password.equals(entity.getDefaultRemotePassword())) {
				password = PasswordUtils.decrypt(entity.getSalt(), password);
			}
			gitClient.push(entity,username, password);
			initEntity(entity.getId());
			messages.info(new BundleKey("messages", "gitRepositories.push.successful"));
		} catch (Exception e) {
			log.error("Failed to push", e);
			messages.error(new BundleKey("messages", "gitRepositories.push.error"), e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
		}
	}
	
	public int countCommitsToPush() throws BusinessException {
		if (entity != null && entity.getId() != null) {
			return gitClient.countCommitsToPush(entity);
		}
		
		return 0;
	}
	
	public int countCommitsToPull() throws BusinessException {
		if (entity != null && entity.getId() != null) {
			return gitClient.countCommitsToPull(entity);
		}
		return 0;
	}
	
	public int countPendingChanges() throws BusinessException {
		if (entity != null && entity.getId() != null) {
			// return gitClient.countPendingChanges(entity);
			return this.pendingGitFiles.getTarget().size();
		}
		return 0;
	}
	
	public void commit() throws BusinessException {
		if (!pendingGitFiles.getTarget().isEmpty()) {
			gitClient.commit(entity, pendingGitFiles.getTarget(), commitMessageBean.getCommitMessage());
			List<String> emptyTarget = new ArrayList<String>();
			pendingGitFiles.setTarget(emptyTarget);
		}
		messages.info("Pending changes successfully commited");
	}
	
	public void discard() throws BusinessException {
		gitClient.discard(entity);
		pendingGitFiles = new DualListModel<String>();
		messages.info("Pending changes successfully discarded");
	}
	
	public void fetchRemote() {
		try {
			if(this.password.equals(entity.getDefaultRemotePassword())) {
				password = PasswordUtils.decrypt(entity.getSalt(), password);
			}
			gitClient.fetch(entity, username, password);
			initEntity(entity.getId());
			messages.info(new BundleKey("messages", "gitRepositories.pull.successful"));
		} catch (Exception e) {
			log.error("Failed to fetch", e);
			messages.error(new BundleKey("messages", "gitRepositories.pull.error"), e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
		}	
	}

	public void pullRemote() {
		try {
			if(this.password.equals(entity.getDefaultRemotePassword())) {
				password = PasswordUtils.decrypt(entity.getSalt(), password);
			}
			gitClient.pull(entity, username, password);
			initEntity(entity.getId());
			messages.info(new BundleKey("messages", "gitRepositories.pull.successful"));
		} catch (Exception e) {
			log.error("Failed to pull", e);
			messages.error(new BundleKey("messages", "gitRepositories.pull.error"), e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
		}
	}

	public String importZip(FileUploadEvent event) {
		UploadedFile file = event.getFile();
		String filename = file.getFileName();
		try {
			InputStream inputStream = file.getInputstream();
			GitRepositoryDto dto = gitRepositoryApi.toDto(entity);
			gitRepositoryApi.importZip(inputStream, dto, isEdit());
			messages.info(new BundleKey("messages", "importZip.successfull"), filename);
		} catch (Exception e) {
			messages.error(new BundleKey("messages", "importZip.error"), filename, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
		}
		return getEditViewName();
	}

	public StreamedContent exportZip() {
		String filename = entity.getCode();
		branch = branch != null ? branch : entity.getCurrentBranch();
		try {
			byte[] exportZip = gitRepositoryApi.exportZip(entity.getCode(), branch);
			InputStream is = new ByteArrayInputStream(exportZip);
			return new DefaultStreamedContent(is, "application/octet-stream", filename + "-" + branch + ".zip");
		} catch (Exception e) {
			messages.error(new BundleKey("messages", "exportZip.error"), e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
		}
		return null;
	}
	
	/**
	 * @return the {@link #repositories}
	 */
	public DualListModel<String> getRepositories() {
		return repositories;
	}
	
	public DualListModel<String> getPendingGitFiles() {
		return pendingGitFiles;
	}
	
	public void setPendingGitFiles(DualListModel<String> pendingGitFiles) {
		this.pendingGitFiles = pendingGitFiles;
	}
	
	public CommitMessageBean getCommitMessageBean() {
		return this.commitMessageBean;
	}
	
	public void setCommitMessageBean(CommitMessageBean commitMessageBean) {
		this.commitMessageBean = commitMessageBean;
	}
	
	@Override
	protected String getListViewName() {
		return "gitRepositories";
	}

	@Override
	public BaseCrudApi<GitRepository, GitRepositoryDto> getBaseCrudApi() {
		return gitRepositoryApi;
	}

	@Override
	protected IPersistenceService<GitRepository> getPersistenceService() {
		return gitRepositoryService;
	}

	public String getUsername() {
		if (username == null && entity.getDefaultRemoteUsername() != null) {
			username = entity.getDefaultRemoteUsername();
		}
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		if (entity.getId() != null && entity.getRemoteOrigin() != null) {
			password = entity.getDefaultRemotePassword();
		}
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getBranch() {
		return branch;
	}

	public void setBranch(String branch) {
		this.branch = branch;
	}
	
	public boolean isModuleRepository() {
		if(entity.getCode()!=null){
			File gitDir = GitHelper.getRepositoryDir(currentUser, entity);
			if (gitDir.exists() && gitDir.isDirectory()) {
			  return Arrays.stream(gitDir.list()).anyMatch(("module.json")::equals);
			}
		}
		return false;
	}
	
	public boolean isModuleInstalled() {
		MeveoModule module = moduleService.findByCode(entity.getCode());
		if(module != null) {
			return module.isInstalled();
		}
		return false;
	}
	
	public String installMissingDependencies() {
		try {
			
			if(this.password.equals(entity.getDefaultRemotePassword())) {
				password = PasswordUtils.decrypt(entity.getSalt(), password);
			}
			LinkedHashMap<GitRepository, ModuleDependencyDto> gitRepos = moduleApi.retrieveModuleDependencies(missingDependencies, username, password);
			
			this.missingDependencies = new ArrayList<>(gitRepos.values());
			
			for (GitRepository repo : gitRepos.keySet()) {
				ModuleDependencyDto dependency = gitRepos.get(repo);
				dependency.setInstalling(true);
				moduleApi.install(repositories.getTarget(), repo);
				dependency.setInstalled(true);
			}
			
			return install();
		} catch (BusinessException e) {
			MessagesHelper.error(messages, "Failed to install module or dependency", e);
			return "gitRepositoryDetail.xhtml?faces-redirect=true&objectId=" + entity.getId() + "&edit=true";
		}
		
	}
	
	public String install() {
		try {
			List<String> repos = repositories.getTarget();
			if (!repos.isEmpty()) {
				moduleApi.install(repos, entity);
				messages.info("Module successfully installed");
			} else {
				messages.error("At least one repository should be selected");
			}

		} catch (MissingModuleException e) {
			this.missingDependencies = e.getMissingModules();
			PrimeFaces.current().ajax().update("installDeps");
			PrimeFaces.current().executeScript("PF('installDialog').hide();");
			PrimeFaces.current().executeScript("PF('installDeps').show();");
			return null;
			
		} catch (Exception e) {
			MessagesHelper.error(messages, "Failed to install module", e);
		}
		
		return "gitRepositoryDetail.xhtml?faces-redirect=true&objectId=" + entity.getId() + "&edit=true";
	}
	
	public String reInstall() {
		try {
			List<String> repos = repositories.getTarget();
			if (!repos.isEmpty()) {
				moduleApi.reInstall(repos, entity);
				messages.info("Module successfully installed");
			} else {
				messages.error("At least one repository should be selected");
			}

		} catch (MissingModuleException e) {
			this.missingDependencies = e.getMissingModules();
			PrimeFaces.current().ajax().update("installDeps");
			PrimeFaces.current().executeScript("PF('installDialog').hide();");
			PrimeFaces.current().executeScript("PF('installDeps').show();");
			return null;
			
		} catch (Exception e) {
			MessagesHelper.error(messages, "Failed to install module", e);
		}
		
		return "gitRepositoryDetail.xhtml?faces-redirect=true&objectId=" + entity.getId() + "&edit=true";
	}
	
	public Long getModuleId() {
		MeveoModule module = moduleService.findByCode(entity.getCode());
		if(module != null) {
			return module.getId();
		}
		return null;
	}
	
	/**
	 * @param repositories the repositories to set
	 */
	public void setRepositories(DualListModel<String> repositories) {
		this.repositories = repositories;
	}
	
	/**
	 * @return the {@link #missingDependencies}
	 */
	public List<ModuleDependencyDto> getMissingDependencies() {
		return missingDependencies;
	}

	public String getGitCloneUrl() {
		try {
			ParamBean paramBean = ParamBean.getInstance();			
			String baseUrlConfig = paramBean.getProperty("meveo.admin.baseUrl", "http://localhost:8080/meveo");
			String webContext    = paramBean.getProperty("meveo.admin.webContext","meveo");
			URL baseUrl = new URL(baseUrlConfig);

			return baseUrl.getProtocol() + "://"
				+ currentUser.getUserName() 
				+ ":<password>@" 
				+ baseUrlConfig.replace(baseUrl.getProtocol() + "://","") 
				+ webContext
				+ "/git/" 
				+ this.entity.getCode();
		}
		catch(Exception  ex) {
			return ex.toString();
		}
	}
}
