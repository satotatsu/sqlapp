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
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.OperatorArgument;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.RestrictFunctionNameProperty;
import com.sqlapp.data.schemas.properties.RestrictFunctionSchemaNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * RestrictFunction IF
 * 
 * @author satoh
 * 
 */
public interface RestrictFunctionProperty<T extends DbCommonObject<?>> extends RestrictFunctionNameProperty<T>,RestrictFunctionSchemaNameProperty<T>{
	
	default Function getRestrictFunction(){
		Function obj= SimpleBeanUtils.getField(this, SchemaProperties.RESTRICT_FUNCTION_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setRestrictFunction(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setRestrictFunction(Function value){
		if (this instanceof DbCommonObject){
			OperatorArgument leftArgument= SimpleBeanUtils.getField(this, "leftArgument");
			OperatorArgument rightArgument= SimpleBeanUtils.getField(this, "rightArgument");
			value=SchemaUtils.getFunctionFromParent(value, leftArgument, rightArgument, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.RESTRICT_FUNCTION_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}


	@Override
	default String getRestrictFunctionSchemaName(){
		return getRestrictFunction()==null?null:getRestrictFunction().getSchemaName();
	}

	@Override
	default String getRestrictFunctionName(){
		return getRestrictFunction()==null?null:getRestrictFunction().getName();
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setRestrictFunctionName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setRestrictFunction(null);
		} else{
			if (this.getRestrictFunction()==null||!CommonUtils.eq(this.getRestrictFunctionName(), name)){
				String[] args=name.split("\\.");
				Function obj=new Function(CommonUtils.last(args));
				if (args.length>1){
					obj.setSchemaName(args[args.length-2]);
				}
				this.setRestrictFunction(obj);
			}
		}
		return (T)this;
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setRestrictFunctionSchemaName(String schemaName) {
		if (this.getRestrictFunction()==null||!CommonUtils.eq(this.getRestrictFunctionSchemaName(), schemaName)){
			Function obj=new Function();
			obj.setSchemaName(schemaName);
			this.setRestrictFunction(obj);
		}
		return (T)this;
	}

}
