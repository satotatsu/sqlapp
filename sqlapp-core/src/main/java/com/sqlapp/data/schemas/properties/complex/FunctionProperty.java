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
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.OperatorArgument;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.FunctionNameProperty;
import com.sqlapp.data.schemas.properties.FunctionSchemaNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Function IF
 * 
 * @author satoh
 * 
 */
public interface FunctionProperty<T extends DbCommonObject<?>> extends FunctionNameProperty<T>,FunctionSchemaNameProperty<T>{
	
	default Function getFunction(){
		Function obj= SimpleBeanUtils.getField(this, SchemaProperties.FUNCTION_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setFunction(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setFunction(Function value){
		if (this instanceof DbCommonObject){
			OperatorArgument leftArgument= SimpleBeanUtils.getField(this, "leftArgument");
			OperatorArgument rightArgument= SimpleBeanUtils.getField(this, "rightArgument");
			value=SchemaUtils.getFunctionFromParent(value, leftArgument, rightArgument, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.FUNCTION_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}


	@Override
	default String getFunctionSchemaName(){
		return getFunction()==null?null:getFunction().getSchemaName();
	}

	@Override
	default String getFunctionName(){
		return getFunction()==null?null:getFunction().getName();
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setFunctionName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setFunction(null);
		} else{
			if (this.getFunction()==null||!CommonUtils.eq(this.getFunctionName(), name)){
				Function table=new Function(name);
				this.setFunction(table);
			}
		}
		return (T)this;
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setFunctionSchemaName(String name) {
		if (this.getFunction()==null||!CommonUtils.eq(this.getFunctionSchemaName(), name)){
			Function obj=new Function();
			obj.setSchemaName(name);
			this.setFunction(obj);
		}
		return (T)this;
	}

}
