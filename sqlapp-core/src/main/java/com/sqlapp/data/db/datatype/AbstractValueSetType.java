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
package com.sqlapp.data.db.datatype;

import static com.sqlapp.util.CommonUtils.trim;

import java.util.regex.Matcher;

import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;
import com.sqlapp.data.schemas.properties.ValuesProperty;
import com.sqlapp.util.CommonUtils;

public abstract class AbstractValueSetType<T extends DbDataType<T>> extends
		DbDataType<T> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -388123344808557284L;

	/**
	 * 初期化
	 * 
	 * @param typeName
	 */
	protected void initialize(String dataTypeName) {
		this.setCreateFormat(getCreateValueSetFormat(dataTypeName + "(", ")"));
		this.addFormats(dataTypeName);
		addValueSetFormat(dataTypeName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.datatype.AbstractDbDataType#parseAndSet(java.util.
	 * regex.Matcher, com.sqlapp.schemas.DataTypeSetProperties)
	 */
	@Override
	protected void parseAndSet(Matcher matcher,
			DataTypeLengthProperties<?> column) {
		if (!CommonUtils.eq(column.getDataTypeName(), this.getTypeName())) {
			SchemaUtils.setDataTypeNameInternal(this.getTypeName(), column);
		}
		if (matcher.groupCount() > 0) {
			if (column instanceof ValuesProperty) {
				ValuesProperty<?> vp = (ValuesProperty<?>) column;
				vp.getValues().clear();
				String str = matcher.group(1);
				String[] vals = str.split(",");
				for (String val : vals) {
					vp.getValues().add(trim(val));
				}
			}
		}
	}

	/**
	 * 値セットフォーマットのフォーマットの追加
	 * 
	 * @param dataTypeName
	 */
	@SuppressWarnings("unchecked")
	public T addValueSetFormat(String dataTypeName) {
		this.addFormats(dataTypeName + "\\s*\\(\\s*(.+)\\s*\\)\\s*");
		return (T) (this);
	}
}
