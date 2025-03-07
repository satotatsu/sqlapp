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
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.properties.TableNameProperty;
import com.sqlapp.data.schemas.properties.TableSchemaNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Table IF
 * 
 * @author satoh
 * 
 */
public interface TableProperty<T extends DbCommonObject<?>> extends TableNameProperty<T>,TableSchemaNameProperty<T>{
	
	default Table getTable(){
		Table obj= SimpleBeanUtils.getField(this, SchemaProperties.TABLE_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setTable(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setTable(Table value){
		if (this instanceof DbCommonObject){
			value=SchemaUtils.getTableFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.TABLE_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}

	@Override
	default String getTableSchemaName(){
		return getTable()==null?null:getTable().getSchemaName();
	}

	@Override
	default String getTableName(){
		return getTable()==null?null:getTable().getName();
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setTableName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setTable(null);
		} else{
			if (this.getTable()==null||!CommonUtils.eq(this.getTableName(), name)){
				Table obj=new Table(name);
				this.setTable(obj);
			}
		}
		return (T)this;
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setTableSchemaName(String name) {
		if (this.getTable()==null||!CommonUtils.eq(this.getTableSchemaName(), name)){
			Table obj=new Table();
			obj.setSchemaName(name);
			this.setTable(obj);
		}
		return (T)this;
	}

}
