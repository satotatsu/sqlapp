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
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.properties.IndexNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Index IF
 * 
 * @author satoh
 * 
 */
public interface IndexProperty<T extends DbCommonObject<?>> extends IndexNameProperty<T>{

	default Index getIndex(){
		Index obj= SimpleBeanUtils.getField(this, SchemaProperties.INDEX_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setIndex(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setIndex(Index value){
		if (this instanceof DbObject){
			value=SchemaUtils.getIndexFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.INDEX_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}

	@Override
	default String getIndexName(){
		return getIndex()==null?null:getIndex().getName();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	default T setIndexName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setIndex(null);
		} else{
			if (this.getIndex()==null||!CommonUtils.eq(this.getIndexName(), name)){
				Index obj=new Index(name);
				this.setIndex(obj);
			}
		}
		return (T)this;
	}
	
}
