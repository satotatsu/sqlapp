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
import com.sqlapp.data.schemas.TableSpace;
import com.sqlapp.data.schemas.properties.IndexTableSpaceNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Table Space Name IF
 * 
 * @author satoh
 * 
 */
public interface IndexTableSpaceProperty<T extends DbCommonObject<?>> extends IndexTableSpaceNameProperty<T>{

	default TableSpace getIndexTableSpace(){
		TableSpace obj= SimpleBeanUtils.getField(this, SchemaProperties.INDEX_TABLE_SPACE_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setIndexTableSpace(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setIndexTableSpace(TableSpace value){
		if (this instanceof DbCommonObject){
			value=SchemaUtils.getTableSpaceFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.INDEX_TABLE_SPACE_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}

	@Override
	default String getIndexTableSpaceName(){
		return getIndexTableSpace()==null?null:getIndexTableSpace().getName();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	default T setIndexTableSpaceName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setIndexTableSpace(null);
		} else{
			if (this.getIndexTableSpace()==null||!CommonUtils.eq(this.getIndexTableSpaceName(), name)){
				TableSpace tableSpace=new TableSpace(name);
				this.setIndexTableSpace(tableSpace);
			}
		}
		return (T)this;
	}
	
}
