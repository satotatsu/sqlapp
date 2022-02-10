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

package com.sqlapp.data.schemas.properties.complex;

import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Schema Name IF
 * 
 * @author satoh
 * 
 */
public interface SchemaProperty<T extends DbCommonObject<?>> extends SchemaNameProperty<T>{

	default Schema getSchema(){
		Schema obj= SimpleBeanUtils.getField(this, SchemaProperties.SCHEMA_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setSchema(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setSchema(Schema value){
		if (this instanceof DbCommonObject){
			value=SchemaUtils.getSchemaFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.SCHEMA_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}

	@Override
	default String getSchemaName(){
		return getSchema()==null?null:getSchema().getName();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	default T setSchemaName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setSchema(null);
		} else{
			if (this.getSchema()==null||!CommonUtils.eq(this.getSchemaName(), name)){
				Schema obj=new Schema(name);
				this.setSchema(obj);
			}
		}
		return (T)this;
	}
	
}
