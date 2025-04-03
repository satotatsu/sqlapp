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

package com.sqlapp.data.schemas.properties;

import java.util.Optional;

import com.sqlapp.data.db.datatype.util.TypeInformation;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.complex.DialectGetter;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * DBの型プロパティ
 * 
 * @author tatsuo satoh
 * 
 * @param <T>
 */
public interface DataTypeNameProperty<T> {

	/**
	 * DB固有型名を取得します
	 * 
	 */
	default String getDataTypeName() {
		return SimpleBeanUtils.getField(this, SchemaProperties.DATA_TYPE_NAME.getLabel());
	}

	/**
	 * DB固有型名を設定します
	 * 
	 * @param dataTypeName
	 */
	@SuppressWarnings("unchecked")
	default T setDataTypeName(String dataTypeName) {
		if (dataTypeName != null) {
			String text = SchemaUtils.normalizeDataType(dataTypeName);
			String own = SchemaUtils.getDataTypeNameInternal(this);
			if (this instanceof DialectGetter) {
				DialectGetter dialectGetter = (DialectGetter) this;
				Dialect dialect = dialectGetter.getDialect();
				if (dialect != null && this instanceof DataTypeProperties) {
					DataTypeProperties<?> dataTypeProperties = (DataTypeProperties<?>) this;
					if (CommonUtils.eq(own, text) && dataTypeProperties.getDataType() != null) {
						return (T) this;
					}
					if (this instanceof DataTypeLengthProperties) {
						DataTypeLengthProperties<?> obj = (DataTypeLengthProperties<?>) this;
						Optional<TypeInformation> type = dialect.matchDbType(text, obj.getLength(), obj.getScale());
						type.get().set(obj);
					} else if (this instanceof DataTypeProperties) {
						DataTypeProperties<?> obj = (DataTypeProperties<?>) this;
						Optional<TypeInformation> type = dialect.matchDbType(text, null, null);
						type.get().set(obj);
					}
				}
			} else {
				SchemaProperties.DATA_TYPE_NAME.setValue(this, text);
			}
		} else {
			SchemaProperties.DATA_TYPE_NAME.setValue(this, dataTypeName);
		}
		return (T) this;
	}
}
