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
import com.sqlapp.data.schemas.PartitionFunction;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.PartitionFunctionNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Partition Function Name IF
 * 
 * @author satoh
 * 
 */
public interface PartitionFunctionProperty<T extends DbCommonObject<?>> extends PartitionFunctionNameProperty<T>{

	default PartitionFunction getPartitionFunction(){
		PartitionFunction obj= SimpleBeanUtils.getField(this, SchemaProperties.PARTITION_FUNCTION_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setPartitionFunction(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setPartitionFunction(PartitionFunction value){
		if (this instanceof DbCommonObject){
			value=SchemaUtils.getPartitionFunctionFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.PARTITION_FUNCTION_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}

	@Override
	default String getPartitionFunctionName(){
		return getPartitionFunction()==null?null:getPartitionFunction().getName();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	default T setPartitionFunctionName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setPartitionFunction(null);
		} else{
			if (this.getPartitionFunction()==null||!CommonUtils.eq(this.getPartitionFunctionName(), name)){
				PartitionFunction obj=new PartitionFunction(name);
				this.setPartitionFunction(obj);
			}
		}
		return (T)this;
	}
	
}
