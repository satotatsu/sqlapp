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

package com.sqlapp.data.schemas.properties.complex;

import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.data.schemas.properties.ImplementationTypeNameProperty;
import com.sqlapp.data.schemas.properties.ImplementationTypeSchemaNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Table IF
 * 
 * @author satoh
 * 
 */
public interface ImplementationTypeProperty<T extends DbCommonObject<?>> extends ImplementationTypeNameProperty<T>,ImplementationTypeSchemaNameProperty<T>{
	
	default Type getImplementationType(){
		Type obj= SimpleBeanUtils.getField(this, SchemaProperties.IMPLEMENTATION_TYPE_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setImplementationType(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setImplementationType(Type value){
		if (this instanceof DbCommonObject){
			value=SchemaUtils.getTypeFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.IMPLEMENTATION_TYPE_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}

	@Override
	default String getImplementationTypeSchemaName(){
		return getImplementationType()==null?null:getImplementationType().getSchemaName();
	}

	@Override
	default String getImplementationTypeName(){
		return getImplementationType()==null?null:getImplementationType().getName();
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setImplementationTypeName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setImplementationType(null);
		} else{
			if (this.getImplementationType()==null||!CommonUtils.eq(this.getImplementationTypeName(), name)){
				Type obj=new Type(name);
				this.setImplementationType(obj);
			}
		}
		return (T)this;
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setImplementationTypeSchemaName(String name) {
		if (this.getImplementationType()==null||!CommonUtils.eq(this.getImplementationTypeSchemaName(), name)){
			Type obj=new Type(name);
			obj.setSchemaName(name);
			this.setImplementationType(obj);
		}
		return (T)this;
	}

}
