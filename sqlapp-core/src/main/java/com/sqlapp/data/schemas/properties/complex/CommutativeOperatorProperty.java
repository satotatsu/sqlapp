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
import com.sqlapp.data.schemas.Operator;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.CommutativeOperatorNameProperty;
import com.sqlapp.data.schemas.properties.CommutativeOperatorSchemaNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Table IF
 * 
 * @author satoh
 * 
 */
public interface CommutativeOperatorProperty<T extends DbCommonObject<?>> extends CommutativeOperatorNameProperty<T>,CommutativeOperatorSchemaNameProperty<T>{

	default Operator getCommutativeOperator(){
		Operator obj= SimpleBeanUtils.getField(this, SchemaProperties.COMMUTATIVE_OPERATOR_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setCommutativeOperator(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setCommutativeOperator(Operator value){
		if (this instanceof DbCommonObject){
			value=SchemaUtils.getOperatorFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.COMMUTATIVE_OPERATOR_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}

	@Override
	default String getCommutativeOperatorSchemaName(){
		return getCommutativeOperator()==null?null:getCommutativeOperator().getSchemaName();
	}

	@Override
	default String getCommutativeOperatorName(){
		return getCommutativeOperator()==null?null:getCommutativeOperator().getName();
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setCommutativeOperatorName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setCommutativeOperator(null);
		} else{
			if (this.getCommutativeOperator()==null||!CommonUtils.eq(this.getCommutativeOperatorName(), name)){
				Operator obj=new Operator(name);
				this.setCommutativeOperator(obj);
			}
		}
		return (T)this;
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setCommutativeOperatorSchemaName(String name) {
		if (this.getCommutativeOperator()==null||!CommonUtils.eq(this.getCommutativeOperatorSchemaName(), name)){
			Operator obj=new Operator();
			obj.setSchemaName(name);
			this.setCommutativeOperator(obj);
		}
		return (T)this;
	}

}
