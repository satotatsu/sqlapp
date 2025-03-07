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
import com.sqlapp.data.schemas.Operator;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.NegationOperatorNameProperty;
import com.sqlapp.data.schemas.properties.NegationOperatorSchemaNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Table IF
 * 
 * @author satoh
 * 
 */
public interface NegationOperatorProperty<T extends DbCommonObject<?>> extends NegationOperatorNameProperty<T>,NegationOperatorSchemaNameProperty<T>{

	default Operator getNegationOperator(){
		Operator obj= SimpleBeanUtils.getField(this, SchemaProperties.COMMUTATIVE_OPERATOR_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setNegationOperator(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setNegationOperator(Operator value){
		if (this instanceof DbCommonObject){
			value=SchemaUtils.getOperatorFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.COMMUTATIVE_OPERATOR_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}

	@Override
	default String getNegationOperatorSchemaName(){
		return getNegationOperator()==null?null:getNegationOperator().getSchemaName();
	}

	@Override
	default String getNegationOperatorName(){
		return getNegationOperator()==null?null:getNegationOperator().getName();
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setNegationOperatorName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setNegationOperator(null);
		} else{
			if (this.getNegationOperator()==null||!CommonUtils.eq(this.getNegationOperatorName(), name)){
				Operator obj=new Operator(name);
				this.setNegationOperator(obj);
			}
		}
		return (T)this;
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setNegationOperatorSchemaName(String name) {
		if (this.getNegationOperator()==null||!CommonUtils.eq(this.getNegationOperatorSchemaName(), name)){
			Operator obj=new Operator();
			obj.setSchemaName(name);
			this.setNegationOperator(obj);
		}
		return (T)this;
	}

}
