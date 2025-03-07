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

import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * characterSetNameを設定するインタフェース
 * 
 * @author satoh
 * 
 */
public interface CharacterSetProperty<T> {

	default String getCharacterSet(){
		if (this instanceof DataTypeProperty){
			DataTypeProperty<?> prop=(DataTypeProperty<?>)this;
			if (prop.getDataType()!=null&&!prop.getDataType().isCharacter()){
				return null;
			}
		}
		String value= SimpleBeanUtils.getField(this, SchemaProperties.CHARACTER_SET.getLabel());
		if (value==null){
			if (this instanceof DbCommonObject){
				DbCommonObject<?> dbObj=(DbCommonObject<?>)this;
				return SchemaUtils.getParentCharacterSet(dbObj);
			}
		}
		return value;
	}

	T setCharacterSet(String value);
}
