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
import com.sqlapp.data.schemas.DbObjects;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.TableSpace;
import com.sqlapp.data.schemas.properties.TableSpaceNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Table Space Name IF
 * 
 * @author satoh
 * 
 */
public interface TableSpaceProperty<T extends DbCommonObject<?>> extends TableSpaceNameProperty<T>{

	default TableSpace getTableSpace(){
		TableSpace obj= SimpleBeanUtils.getField(this, DbObjects.TABLE_SPACE.getCamelCase());
		if (obj != null && obj.getParent() == null) {
			setTableSpace(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setTableSpace(TableSpace value){
		if (this instanceof DbCommonObject){
			value=SchemaUtils.getTableSpaceFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, DbObjects.TABLE_SPACE.getCamelCase(), value);
		return (T)this;
	}

	@Override
	default String getTableSpaceName(){
		return getTableSpace()==null?null:getTableSpace().getName();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	default T setTableSpaceName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setTableSpace(null);
		} else{
			if (this.getTableSpace()==null||!CommonUtils.eq(this.getTableSpaceName(), name)){
				TableSpace obj=new TableSpace(name);
				this.setTableSpace(obj);
			}
		}
		return (T)this;
	}
	
}
