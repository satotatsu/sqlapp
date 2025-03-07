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
import com.sqlapp.data.schemas.properties.LobTableSpaceNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Table Space Name IF
 * 
 * @author satoh
 * 
 */
public interface LobTableSpaceProperty<T extends DbCommonObject<?>> extends LobTableSpaceNameProperty<T>{

	default TableSpace getLobTableSpace(){
		TableSpace obj= SimpleBeanUtils.getField(this, SchemaProperties.LOB_TABLE_SPACE_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setLobTableSpace(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setLobTableSpace(TableSpace value){
		if (this instanceof DbCommonObject){
			value=SchemaUtils.getTableSpaceFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.LOB_TABLE_SPACE_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}

	@Override
	default String getLobTableSpaceName(){
		return getLobTableSpace()==null?null:getLobTableSpace().getName();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	default T setLobTableSpaceName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setLobTableSpace(null);
		} else{
			if (this.getLobTableSpace()==null||!CommonUtils.eq(this.getLobTableSpaceName(), name)){
				TableSpace tableSpace=new TableSpace(name);
				this.setLobTableSpace(tableSpace);
			}
		}
		return (T)this;
	}
	
}
