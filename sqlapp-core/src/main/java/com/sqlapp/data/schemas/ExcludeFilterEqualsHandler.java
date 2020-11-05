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

import java.util.Set;
import java.util.function.BooleanSupplier;

import com.sqlapp.util.CommonUtils;

/**
 * 指定したプロパティを比較対象から除いたEqualsHandler
 * 
 * @author tatsuo satoh
 * 
 */
public class ExcludeFilterEqualsHandler extends EqualsHandler {

	private Set<String> excludeProperties;

	/**
	 * 名称以外を比較するハンドラー
	 */
	public static final ExcludeFilterEqualsHandler EQUALS_WITHOUT_NAME_HANDLER = new ExcludeFilterEqualsHandler(
			SchemaProperties.NAME.getLabel(), SchemaProperties.SPECIFIC_NAME.getLabel(),
			SchemaProperties.CREATED_AT.getLabel(), SchemaProperties.LAST_ALTERED_AT.getLabel());

	/**
	 * コンストラクタ
	 * 
	 * @param names
	 *            比較対象から除きたいプロパティ名
	 */
	public ExcludeFilterEqualsHandler(String... names) {
		this.excludeProperties = CommonUtils.set(names);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.EqualsHandler#equals(java.lang.String,
	 * java.lang.Object, java.lang.Object, java.lang.Object, java.lang.Object)
	 */
	@Override
	public boolean valueEquals(String propertyName, Object object1, Object object2,
			Object value1, Object value2, BooleanSupplier p) {
		if (excludeProperties.contains(propertyName)) {
			return true;
		}
		return super.valueEquals(propertyName, object1, object2, value1, value2, p);
	}

	public void setExcludeProperties(String... names){
		this.excludeProperties = CommonUtils.set(names);
	}
	
	@Override
	public ExcludeFilterEqualsHandler clone(){
		ExcludeFilterEqualsHandler clone= (ExcludeFilterEqualsHandler)super.clone();
		if (this.excludeProperties!=null){
			clone.excludeProperties=CommonUtils.set(this.excludeProperties);
		}
		return clone;
	}
}
