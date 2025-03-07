/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.schemas;

import java.util.Map;
import java.util.function.BooleanSupplier;

import com.sqlapp.util.CommonUtils;

/**
 * 全てのプロパティをマップで取得するためのハンドラー
 * 
 * @author tatsuo satoh
 * 
 */
class GetPropertyMapEqualsHandler extends EqualsHandler {

	private final DbObject<?> dbObject;

	private final Map<String, Object> result = CommonUtils.linkedMap();

	GetPropertyMapEqualsHandler(DbObject<?> dbObject) {
		this.dbObject = dbObject;
	}

	@Override
	protected boolean referenceEquals(Object object1, Object object2) {
		return false;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.EqualsHandler#equals(java.lang.String, java.lang.Object, java.lang.Object, java.lang.Object, java.lang.Object, java.util.function.BooleanSupplier)
	 */
	@Override
	protected boolean valueEquals(String propertyName, Object object1, Object object2,
			Object value1, Object value2, BooleanSupplier p) {
		if (dbObject == object1) {
			result.put(propertyName, value1);
		}
		return true;
	}

	@Override
	protected boolean equalsResult(Object object1, Object object2) {
		return false;
	}

	/**
	 * @return the result
	 */
	public Map<String, Object> getResult() {
		return result;
	}
	
	@Override
	public GetPropertyMapEqualsHandler clone(){
		return (GetPropertyMapEqualsHandler)super.clone();
	}
}
