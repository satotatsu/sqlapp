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
import com.sqlapp.data.schemas.properties.JoinFunctionNameProperty;
import com.sqlapp.data.schemas.properties.JoinFunctionSchemaNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * JoinFunction IF
 * 
 * @author satoh
 * 
 */
public interface JoinFunctionProperty<T extends DbCommonObject<?>> extends JoinFunctionNameProperty<T>,JoinFunctionSchemaNameProperty<T>{
	
	default Function getJoinFunction(){
		Function obj= SimpleBeanUtils.getField(this, SchemaProperties.RESTRICT_FUNCTION_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setJoinFunction(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setJoinFunction(Function value){
		if (this instanceof DbCommonObject){
			OperatorArgument leftArgument= SimpleBeanUtils.getField(this, "leftArgument");
			OperatorArgument rightArgument= SimpleBeanUtils.getField(this, "rightArgument");
			value=SchemaUtils.getFunctionFromParent(value, leftArgument, rightArgument, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.RESTRICT_FUNCTION_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}


	@Override
	default String getJoinFunctionSchemaName(){
		return getJoinFunction()==null?null:getJoinFunction().getSchemaName();
	}

	@Override
	default String getJoinFunctionName(){
		return getJoinFunction()==null?null:getJoinFunction().getName();
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setJoinFunctionName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setJoinFunction(null);
		} else{
			if (this.getJoinFunction()==null||!CommonUtils.eq(this.getJoinFunctionName(), name)){
				String[] args=name.split("\\.");
				Function obj=new Function(CommonUtils.last(args));
				if (args.length>1){
					obj.setSchemaName(args[args.length-2]);
				}
				this.setJoinFunction(obj);
			}
		}
		return (T)this;
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setJoinFunctionSchemaName(String schemaName) {
		if (this.getJoinFunction()==null||!CommonUtils.eq(this.getJoinFunctionSchemaName(), schemaName)){
			Function obj=new Function();
			obj.setSchemaName(schemaName);
			this.setJoinFunction(obj);
		}
		return (T)this;
	}

}
