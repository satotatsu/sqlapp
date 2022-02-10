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
 * 指定したプロパティを比較にしするEqualsHandler
 * 
 * @author tatsuo satoh
 * 
 */
public class IncludeFilterEqualsHandler extends EqualsHandler {

	private Set<String> includeProperties;

	/**
	 * 名称を比較するハンドラー
	 */
	public static final IncludeFilterEqualsHandler EQUALS_NAME_HANDLER = new IncludeFilterEqualsHandler(
			SchemaProperties.NAME.getLabel(), SchemaProperties.SPECIFIC_NAME.getLabel());

	/**
	 * コンストラクタ
	 * 
	 * @param names
	 *            比較対象から除きたいプロパティ名
	 */
	public IncludeFilterEqualsHandler(String... names) {
		this.includeProperties = CommonUtils.set(names);
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
		if (includeProperties.contains(propertyName)) {
			return super.valueEquals(propertyName, object1, object2, value1, value2, p);
		}
		return true;
	}

	@Override
	public IncludeFilterEqualsHandler clone(){
		IncludeFilterEqualsHandler clone=(IncludeFilterEqualsHandler)super.clone();
		if (this.includeProperties!=null){
			clone.includeProperties=CommonUtils.set(this.includeProperties);
		}
		return clone;
	}
}
