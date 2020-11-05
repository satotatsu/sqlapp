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
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.TableSpace;
import com.sqlapp.data.schemas.properties.TemporaryTableSpaceNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Table Space Name IF
 * 
 * @author satoh
 * 
 */
public interface TemporaryTableSpaceProperty<T extends DbCommonObject<?>> extends TemporaryTableSpaceNameProperty<T>{

	default TableSpace getTemporaryTableSpace(){
		TableSpace obj= SimpleBeanUtils.getField(this, SchemaProperties.TEMPORARY_TABLE_SPACE_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setTemporaryTableSpace(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setTemporaryTableSpace(TableSpace value){
		if (this instanceof DbCommonObject){
			value=SchemaUtils.getTableSpaceFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.TEMPORARY_TABLE_SPACE_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}

	@Override
	default String getTemporaryTableSpaceName(){
		return getTemporaryTableSpace()==null?null:getTemporaryTableSpace().getName();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	default T setTemporaryTableSpaceName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setTemporaryTableSpace(null);
		} else{
			if (this.getTemporaryTableSpace()==null||!CommonUtils.eq(this.getTemporaryTableSpaceName(), name)){
				TableSpace tableSpace=new TableSpace(name);
				this.setTemporaryTableSpace(tableSpace);
			}
		}
		return (T)this;
	}
	
}
