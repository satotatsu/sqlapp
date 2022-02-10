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
import com.sqlapp.data.schemas.Operator;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.properties.OperatorNameProperty;
import com.sqlapp.data.schemas.properties.OperatorSchemaNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Operator IF
 * 
 * @author satoh
 * 
 */
public interface OperatorProperty<T extends DbCommonObject<?>> extends OperatorNameProperty<T>,OperatorSchemaNameProperty<T>{
	
	default Operator getOperator(){
		Operator obj= SimpleBeanUtils.getField(this, SchemaProperties.OPERATOR_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setOperator(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setOperator(Operator value){
		if (this instanceof DbCommonObject){
			value=SchemaUtils.getOperatorFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.OPERATOR_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}

	@Override
	default String getOperatorSchemaName(){
		return getOperator()==null?null:getOperator().getSchemaName();
	}

	@Override
	default String getOperatorName(){
		return getOperator()==null?null:getOperator().getName();
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setOperatorName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setOperator(null);
		} else{
			if (this.getOperator()==null||!CommonUtils.eq(this.getOperatorName(), name)){
				Operator obj=new Operator(name);
				this.setOperator(obj);
			}
		}
		return (T)this;
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setOperatorSchemaName(String name) {
		if (this.getOperator()==null||!CommonUtils.eq(this.getOperatorSchemaName(), name)){
			Operator obj=new Operator();
			obj.setSchemaName(name);
			this.setOperator(obj);
		}
		return (T)this;
	}

}
