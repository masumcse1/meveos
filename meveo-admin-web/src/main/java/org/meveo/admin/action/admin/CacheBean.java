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
package org.meveo.admin.action.admin;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.infinispan.Cache;
import org.infinispan.commons.api.BasicCache;
import org.jboss.seam.international.status.Messages;
import org.jboss.seam.international.status.builder.BundleKey;
import org.meveo.cache.CustomFieldsCacheContainerProvider;
import org.meveo.cache.JobCacheContainerProvider;
import org.meveo.cache.NotificationCacheContainerProvider;
import org.meveo.cache.UserMessageCacheContainerProvider;
import org.meveo.commons.utils.StringUtils;
import org.meveo.model.BusinessEntity;
import org.meveo.model.IEntity;
import org.meveo.service.script.ScriptInstanceService;
import org.meveo.util.view.LazyDataModelWSize;
import org.omnifaces.cdi.Param;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
@ViewScoped
public class CacheBean implements Serializable {

    private static final long serialVersionUID = -8072659867697109888L;
    
    @Inject
    private NotificationCacheContainerProvider notificationCacheContainerProvider;

    @Inject
    private CustomFieldsCacheContainerProvider customFieldsCacheContainerProvider;

    @Inject
    private JobCacheContainerProvider jobCacheContainerProvider;
    
    @Inject
    private ScriptInstanceService scriptInstanceService;

    @Inject
    private UserMessageCacheContainerProvider userMessageCacheContainerProvider;

    @Inject
    protected Messages messages;

    /**
     * Request parameter. Name of a cache to show details of
     */
    @Inject
    @Param
    private String cacheName;

    /**
     * Selected cache to display details of - retrieved from cacheName request parameter
     */
    @SuppressWarnings("rawtypes")
    private BasicCache selectedCache;

    /**
     * In case that cache contains a list of values, this contains an Entry selected from a cache values.\
     * 
     * In case that cache contains a map of values, this contains an Entry selected from a selectedCacheMapItem values.
     */
    @SuppressWarnings("rawtypes")
    private Entry selectedCacheItem;

    /**
     * In case that cache contains a map of values, this contains an Entry selected from a cache values.
     */
    @SuppressWarnings("rawtypes")
    private Entry selectedCacheMapItem;

    /**
     * Datamodel for lazy dataloading in cached contents.
     */
    @SuppressWarnings("rawtypes")
    protected LazyDataModel cacheContents;

    /**
     * Datamodel for lazy dataloading in cached item map contents.
     */
    @SuppressWarnings("rawtypes")
    protected LazyDataModel cacheMapContents;

    /**
     * Datamodel for lazy dataloading in cached item or item in a cached map contents.
     */
    @SuppressWarnings("rawtypes")
    protected LazyDataModel cacheItemContents;

    @SuppressWarnings("rawtypes")
    public void preRenderView() {

        if (cacheName != null) {
            Map<String, Cache> caches = new HashMap<>();
            caches.putAll(notificationCacheContainerProvider.getCaches());
            caches.putAll(customFieldsCacheContainerProvider.getCaches());
            caches.putAll(jobCacheContainerProvider.getCaches());
            caches.putAll(userMessageCacheContainerProvider.getCaches());
            selectedCache = caches.get(cacheName);
        }
    }

    /**
     * Get a summary of cached information
     * 
     * @return A list of a map containing cache information with the following cache keys: name, count
     */
    @SuppressWarnings("rawtypes")
    public List<Map<String, String>> getSummaryOfCaches() {
        List<Map<String, String>> cacheSummary = new ArrayList<Map<String, String>>();

        Map<String, Cache> caches = new HashMap<>();
        caches.putAll(notificationCacheContainerProvider.getCaches());
        caches.putAll(customFieldsCacheContainerProvider.getCaches());
        caches.putAll(jobCacheContainerProvider.getCaches());
        caches.putAll(userMessageCacheContainerProvider.getCaches());
        caches = new TreeMap<String, Cache>(caches);

        for (Entry<String, Cache> cache : caches.entrySet()) {
            Map<String, String> cacheInfo = new HashMap<String, String>();
            cacheInfo.put("name", cache.getKey());
            cacheInfo.put("count", Integer.toString(cache.getValue().size()));
            cacheSummary.add(cacheInfo);
        }
        
		Map<String, String> cacheInfo = new HashMap<String, String>();
		cacheInfo.put("name", "meveo-scripts");
		cacheInfo.put("count", Integer.toString(scriptInstanceService.getScriptCache().size()));
		cacheSummary.add(cacheInfo);
        
        return cacheSummary;
    }

    /**
     * Refresh cache
     * 
     * @param cacheName Cache name
     */
    public void refresh(String cacheName) {
        if (StringUtils.isBlank(cacheName)) {
            cacheName = null;
        }
        notificationCacheContainerProvider.refreshCache(cacheName);
        customFieldsCacheContainerProvider.refreshCache(cacheName);
        jobCacheContainerProvider.refreshCache(cacheName);
        userMessageCacheContainerProvider.refreshCache(cacheName);
        messages.info(new BundleKey("messages", "cache.refreshInitiated"));
    }

    public void refreshCaches() {
        notificationCacheContainerProvider.refreshCache(null);
        customFieldsCacheContainerProvider.refreshCache(null);
        jobCacheContainerProvider.refreshCache(null);
        userMessageCacheContainerProvider.refreshCache(null);
        messages.info(new BundleKey("messages", "cache.refreshInitiated"));
    }

    @SuppressWarnings("rawtypes")
    public LazyDataModel getCacheContents() {
        return getCacheContents(null, false);
    }

    @SuppressWarnings("rawtypes")
    public LazyDataModel getCacheContents(Map<String, Object> inputFilters, boolean forceReload) {
        if (cacheContents == null || forceReload) {

            // final Map<String, Object> filters = inputFilters;

            cacheContents = new LazyDataModelWSize() {

                private static final long serialVersionUID = -5796910936316457321L;

                @SuppressWarnings("unchecked")
                @Override
                public List load(int first, int pageSize, String sortField, SortOrder sortOrder, Map filters) {
                    setRowCount(selectedCache.size());

                    if (getRowCount() > 0) {
                        int toNr = first + pageSize;
                        return new LinkedList(selectedCache.entrySet()).subList(first, getRowCount() <= toNr ? getRowCount() : toNr);

                    } else {
                        return new ArrayList();
                    }
                }
            };
        }
        return cacheContents;
    }
    
    @SuppressWarnings("rawtypes")
    public LazyDataModel getScriptCacheContents() {
        return getScriptCacheContents(null, false);
    }
    
	@SuppressWarnings("rawtypes")
	public LazyDataModel getScriptCacheContents(Map<String, Object> inputFilters, boolean forceReload) {
		if (cacheContents == null || forceReload) {

			// final Map<String, Object> filters = inputFilters;

			cacheContents = new LazyDataModelWSize() {

				private static final long serialVersionUID = -5796910936316457321L;

				@SuppressWarnings("unchecked")
				@Override
				public List load(int first, int pageSize, String sortField, SortOrder sortOrder, Map filters) {
					setRowCount(scriptInstanceService.getScriptCache().size());

					if (getRowCount() > 0) {
						int toNr = first + pageSize;
						return new LinkedList(scriptInstanceService.getScriptCache().entrySet()).subList(first, getRowCount() <= toNr ? getRowCount() : toNr);

					} else {
						return new ArrayList();
					}
				}
			};
		}
		return cacheContents;
	}

    @SuppressWarnings("rawtypes")
    public LazyDataModel getCacheMapContents() {
        return getCacheMapContents(null, false);
    }

    @SuppressWarnings("rawtypes")
    public LazyDataModel getCacheMapContents(Map<String, Object> inputFilters, boolean forceReload) {
        if (cacheMapContents == null || forceReload) {

            // final Map<String, Object> filters = inputFilters;

            cacheMapContents = new LazyDataModelWSize() {

                private static final long serialVersionUID = -5796910936316457321L;

                @SuppressWarnings("unchecked")
                @Override
                public List load(int first, int pageSize, String sortField, SortOrder sortOrder, Map filters) {
                    Map selectedMap = (Map) selectedCacheMapItem.getValue();
                    setRowCount(selectedMap.size());

                    if (getRowCount() > 0) {
                        int toNr = first + pageSize;
                        return new LinkedList(selectedMap.entrySet()).subList(first, getRowCount() <= toNr ? getRowCount() : toNr);

                    } else {
                        return new ArrayList();
                    }
                }
            };
        }
        return cacheMapContents;
    }

    @SuppressWarnings("rawtypes")
    public LazyDataModel getCacheItemContents() {
        return getCacheItemContents(null, false);
    }

    @SuppressWarnings("rawtypes")
    public LazyDataModel getCacheItemContents(Map<String, Object> inputFilters, boolean forceReload) {
        if (cacheItemContents == null || forceReload) {

            // final Map<String, Object> filters = inputFilters;

            cacheItemContents = new LazyDataModelWSize() {

                private static final long serialVersionUID = -5796910936316457322L;

                @Override
                public List load(int first, int pageSize, String sortField, SortOrder sortOrder, Map filters) {
                    List valueList = (List) selectedCacheItem.getValue();
                    setRowCount(valueList.size());

                    if (getRowCount() > 0) {
                        int toNr = first + pageSize;
                        return valueList.subList(first, getRowCount() <= toNr ? getRowCount() : toNr);

                    } else {
                        return new ArrayList();
                    }
                }
            };
        }
        return cacheItemContents;
    }

	public String getCacheName() {
		return selectedCache == null ? cacheName : selectedCache.getName();
	}

    public Object getSelectedCacheItem() {
        return selectedCacheItem;
    }

    @SuppressWarnings("rawtypes")
    public void setSelectedCacheItem(Object selectedCacheItem) {
        this.selectedCacheItem = (Entry) selectedCacheItem;
    }

    @SuppressWarnings("rawtypes")
    public void setSelectedCacheMapItem(Object selectedCacheMapItem) {
        this.selectedCacheMapItem = (Entry) selectedCacheMapItem;
        this.selectedCacheItem = null;
    }

    public Object getSelectedCacheMapItem() {
        return selectedCacheMapItem;
    }

    /**
     * Extract values of cached object to show in a list. In case of list of items, show only the first 10 items, in case of mapped items - only first 2 entries.
     * 
     * @param item Item to convert to string
     * @param returnToStringForSimpleObjects true/false
     * @return A string representation of an item. Preferred way is code (id) or id or a value. For lists, separate items by a comma, for maps: key:[items..]
     */
    @SuppressWarnings("rawtypes")
    public String getShortRepresentationOfCachedValue(Object item, boolean returnToStringForSimpleObjects) {

        if (item instanceof List) {
            StringBuilder builder = new StringBuilder();
            List listObject = (List) item;
            for (int i = 0; i < 10 && i < listObject.size(); i++) {
                builder.append(builder.length() == 0 ? "" : ", ");
                Object listItem = listObject.get(i);
                builder.append(getShortRepresentationOfCachedValue(listItem, false));
            }

            if (listObject.size() > 10) {
                builder.append(", ...");
            }

            return builder.toString();

        } else if (item instanceof Map) {
            StringBuilder builder = new StringBuilder();
            Map mapObject = (Map) item;
            int i = 0;
            for (Object mapEntry : mapObject.entrySet()) {
                builder.append(builder.length() == 0 ? "" : ", ");
                Object key = ((Entry) mapEntry).getKey();
                Object value = ((Entry) mapEntry).getValue();
                if (i > 2) {
                    break;
                }
                builder.append(String.format("%s: [%s]", key, getShortRepresentationOfCachedValue(value, false)));
                i++;
            }
            if (mapObject.size() > 2) {
                builder.append(", ...");
            }
            return builder.toString();

        } else if (returnToStringForSimpleObjects) {
            return item.toString();

        } else if (item instanceof BusinessEntity) {
            return String.format("%s (%s)", ((BusinessEntity) item).getCode(), ((BusinessEntity) item).getId());

        } else if (item instanceof IEntity) {
            return ((IEntity) item).getId().toString();

        } else if (item instanceof Long) {
            return item.toString();

        } else {

            Object code = null;
            try {
                code = MethodUtils.invokeExactMethod(item, "getCode");
            } catch (Exception e) {
                // Method does not exist - so just ignore
            }
            Object id = null;
            try {
                id = MethodUtils.invokeExactMethod(item, "getId");
            } catch (Exception e) {
                // Method does not exist - so just ignore
            }

            if (code != null && id != null) {
                return String.format("%s (%s)", code, id);
            } else if (code != null) {
                return code.toString();
            } else if (id != null) {
                return id.toString();
            } else {
                return item.toString();
            }
        }

    }

}