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

package org.meveo.persistence;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.meveo.persistence.scheduler.EntityRef;

public class PersistenceActionResult {

    private Set<EntityRef> persistedEntities;
    private String baseEntityUuid;
    private Map<String, Object> persistedValues;

    public PersistenceActionResult(Set<EntityRef> persistedEntities, String baseEntityUuid) {
        this.persistedEntities = persistedEntities;
        this.baseEntityUuid = baseEntityUuid;
    }

    public PersistenceActionResult(String baseEntityUuid) {
        this.baseEntityUuid = baseEntityUuid;
        persistedEntities = Collections.EMPTY_SET;
    }

    public Set<EntityRef> getPersistedEntities() {
        return persistedEntities;
    }

    public String getBaseEntityUuid() {
        return baseEntityUuid;
    }

	/**
	 * @return the {@link #persistedValues}
	 */
	public Map<String, Object> getPersistedValues() {
		return persistedValues;
	}

	/**
	 * @param persistedValues the persistedValues to set
	 */
	public void setPersistedValues(Map<String, Object> persistedValues) {
		this.persistedValues = persistedValues;
	}
    
}
