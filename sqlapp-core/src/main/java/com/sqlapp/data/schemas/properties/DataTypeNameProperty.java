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

package com.sqlapp.data.schemas.properties;

import static com.sqlapp.util.CommonUtils.trim;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.complex.DialectGetter;
import com.sqlapp.exceptions.FieldNotFoundException;
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
	default String getDataTypeName(){
		return SimpleBeanUtils.getField(this, SchemaProperties.DATA_TYPE_NAME.getLabel());
	}
	
	/**
	 * DB固有型名を設定します
	 * 
	 * @param dataTypeName
	 */
	@SuppressWarnings("unchecked")
	default T setDataTypeName(String dataTypeName){
		if (dataTypeName != null) {
			String text = trim(dataTypeName);
			String own=SchemaUtils.getDataTypeNameInternal(this);
			boolean bool=SchemaUtils.setDataTypeNameInternal(text, this);
			if (!bool){
				throw new FieldNotFoundException(SchemaProperties.DATA_TYPE_NAME.getLabel(), this);
			}
			if (this instanceof DialectGetter){
				DialectGetter dialectGetter=(DialectGetter)this;
				Dialect dialect=dialectGetter.getDialect();
				if (dialect != null&&this instanceof DataTypeProperties) {
					DataTypeProperties<?> dataTypeProperties=(DataTypeProperties<?>)this;
					if (CommonUtils.eq(own, text)&&dataTypeProperties.getDataType()!=null) {
						return (T)this;
					}
					if (this instanceof DataTypeLengthProperties){
						DataTypeLengthProperties<?> obj=(DataTypeLengthProperties<?>)this;
						dialect.setDbType(text, obj.getLength(),
								obj.getScale(), obj);
					}else if (this instanceof  DataTypeProperties) {
						DataTypeProperties<?> obj=(DataTypeProperties<?>)this;
						dialect.setDbType(text, obj);
					}
					if (dataTypeProperties.getDataType()!=null){
						if (!dataTypeProperties.getDataType().isOther()&&!dataTypeProperties.getDataType().isDomain()&&!dataTypeProperties.getDataType().isType()){
							if (dialect.matchDataTypeName(dataTypeProperties.getDataType(), dataTypeProperties.getDataTypeName())){
								bool=SchemaUtils.setDataTypeNameInternal(null, this);
								if (!bool){
									throw new FieldNotFoundException(SchemaProperties.DATA_TYPE_NAME.getLabel(), this);
								}
							}
						}
					}
				} else{
					bool=SchemaUtils.setDataTypeNameInternal(CommonUtils.toUpperCase(text), this);
					if (!bool){
						throw new FieldNotFoundException(SchemaProperties.DATA_TYPE_NAME.getLabel(), this);
					}
				}
			}
		} else {
			boolean bool=SchemaUtils.setDataTypeNameInternal(null, this);
			if (!bool){
				throw new FieldNotFoundException(SchemaProperties.DATA_TYPE_NAME.getLabel(), this);
			}
		}
		return (T)this;
	}
	
}
