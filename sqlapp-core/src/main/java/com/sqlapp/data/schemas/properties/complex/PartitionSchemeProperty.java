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
import com.sqlapp.data.schemas.PartitionScheme;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.PartitionSchemeNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Partition Scheme Name IF
 * 
 * @author satoh
 * 
 */
public interface PartitionSchemeProperty<T extends DbCommonObject<?>> extends PartitionSchemeNameProperty<T>{

	default PartitionScheme getPartitionScheme(){
		PartitionScheme obj= SimpleBeanUtils.getField(this, SchemaProperties.PARTITION_SCHEME_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setPartitionScheme(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setPartitionScheme(PartitionScheme value){
		if (this instanceof DbCommonObject){
			value=SchemaUtils.getPartitionSchemeFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.PARTITION_SCHEME_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}

	@Override
	default String getPartitionSchemeName(){
		return getPartitionScheme()==null?null:getPartitionScheme().getName();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	default T setPartitionSchemeName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setPartitionScheme(null);
		} else{
			if (this.getPartitionScheme()==null||!CommonUtils.eq(this.getPartitionSchemeName(), name)){
				PartitionScheme obj=new PartitionScheme(name);
				this.setPartitionScheme(obj);
			}
		}
		return (T)this;
	}
	
}
