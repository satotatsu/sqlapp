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

import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Collation IF
 * @author satoh
 *
 */
public interface CollationProperty<T> {

	default String getCollation(){
		if (this instanceof DataTypeProperty){
			DataTypeProperty<?> prop=(DataTypeProperty<?>)this;
			if (prop.getDataType()!=null&&!prop.getDataType().isCharacter()){
				return null;
			}
		}
		String value= SimpleBeanUtils.getField(this, SchemaProperties.COLLATION.getLabel());
		if (value==null){
			if (this instanceof DbCommonObject){
				DbCommonObject<?> dbObj=(DbCommonObject<?>)this;
				return SchemaUtils.getParentCollation(dbObj);
			}
		}
		return value;
	}

	T setCollation(String value);
}
