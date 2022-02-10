/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.schemas;

import java.util.regex.Pattern;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;

/**
 * オブジェクト名ホルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class SchemaObjectNameHolder {

	public SchemaObjectNameHolder() {
	}

	public SchemaObjectNameHolder(String name) {
		String[] splits = name.split("\\.");
		if (splits.length == 1) {
			schemaName = null;
		} else if (splits.length == 2) {
			schemaName = CommonUtils.first(splits);
		} else {
			throw new IllegalArgumentException("name=" + name);
		}
		objectName = CommonUtils.last(splits);
	}

	public boolean match(String catalogName, String schemaName, String name) {
		if (!matchObjectName(name)) {
			return false;
		}
		if (matchSchemaName(schemaName)) {
			if (matchCatalogName(catalogName)) {
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	private boolean matchObjectName(String name) {
		if (objectNamePattern != null) {
			return objectNamePattern.matcher(name).matches();
		}
		return CommonUtils.eq(getObjectName(), name);
	}

	private boolean matchSchemaName(String name) {
		if (schemaNamePattern != null) {
			return schemaNamePattern.matcher(name).matches();
		}
		if (getSchemaName() == null || CommonUtils.eq(getSchemaName(), name)) {
			return true;
		}
		return false;
	}

	private boolean matchCatalogName(String name) {
		if (catalogNamePattern != null) {
			return catalogNamePattern.matcher(name).matches();
		}
		if (getCatalogName() == null || CommonUtils.eq(getCatalogName(), name)) {
			return true;
		}
		return false;
	}

	private String catalogName = null;
	private String schemaName = null;
	private String objectName = null;

	private Pattern catalogNamePattern = null;
	private Pattern schemaNamePattern = null;
	private Pattern objectNamePattern = null;

	/**
	 * @return the catalogName
	 */
	public String getCatalogName() {
		return catalogName;
	}

	/**
	 * @param catalogName
	 *            the catalogName to set
	 */
	public void setCatalogName(String catalogName) {
		if (catalogName != null) {
			if (catalogName.contains("*")) {
				catalogNamePattern = Pattern.compile(catalogName.replace("*",
						".*"));
			} else {
				catalogNamePattern = null;
			}
		} else {
			catalogNamePattern = null;
		}
		this.catalogName = catalogName;
	}

	/**
	 * @return the schemaName
	 */
	public String getSchemaName() {
		return schemaName;
	}

	/**
	 * @param schemaName
	 *            the schemaName to set
	 */
	public void setSchemaName(String schemaName) {
		if (schemaName != null) {
			if (schemaName.contains("*")) {
				schemaNamePattern = Pattern.compile(schemaName.replace("*",
						".*"));
			} else {
				schemaNamePattern = null;
			}
		} else {
			schemaNamePattern = null;
		}
		this.schemaName = schemaName;
	}

	/**
	 * @return the objectName
	 */
	public String getObjectName() {
		return objectName;
	}

	/**
	 * @param objectName
	 *            the objectName to set
	 */
	public void setObjectName(String objectName) {
		if (objectName != null) {
			if (objectName.contains("*")) {
				objectNamePattern = Pattern.compile(objectName.replace("*",
						".*"));
			} else {
				objectNamePattern = null;
			}
		} else {
			objectNamePattern = null;
		}
		this.objectName = objectName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		ToStringBuilder builder = new ToStringBuilder(this.getClass());
		builder.add("catalogName", catalogName);
		builder.add("schemaName", schemaName);
		builder.add("objectName", objectName);
		return builder.toString();
	}
}
