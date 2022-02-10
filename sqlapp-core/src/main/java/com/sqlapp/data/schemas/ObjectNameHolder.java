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
public class ObjectNameHolder {

	public ObjectNameHolder() {
	}

	public ObjectNameHolder(String name) {
		String[] splits = name.split("\\.");
		objectName = CommonUtils.last(splits);
	}

	public boolean match(String catalogName, String schemaName, String name) {
		return matchObjectName(name);
	}

	private boolean matchObjectName(String name) {
		if (objectNamePattern != null) {
			return objectNamePattern.matcher(name).matches();
		}
		return CommonUtils.eq(getObjectName(), name);
	}

	private String objectName = null;

	private Pattern objectNamePattern = null;

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
		builder.add("objectName", objectName);
		return builder.toString();
	}
}
