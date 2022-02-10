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

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.exceptions.FieldNotFoundException;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * DBの型プロパティ
 * 
 * @author tatsuo satoh
 * 
 * @param <T>
 */
public interface DataTypeProperty<T> {

	/**
	 * 型を取得します
	 * 
	 */
	default DataType getDataType(){
		return SimpleBeanUtils.getField(this, SchemaProperties.DATA_TYPE.getLabel());
	}

	/**
	 * 型を設定します
	 * 
	 * @param dataType
	 */
	@SuppressWarnings("unchecked")
	default T setDataType(DataType dataType){
		boolean bool=SimpleBeanUtils.setField(this, SchemaProperties.DATA_TYPE.getLabel(), dataType);
		if (!bool){
			throw new FieldNotFoundException(SchemaProperties.DATA_TYPE.getLabel(), this);
		}
		return (T)this;
	}
	
	default T setDataType(String dataType){
		if (dataType==null){
			return setDataType((DataType)null);
		}
		return setDataType(DataType.valueOf(dataType));
	}
	
}
