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
import com.sqlapp.data.schemas.User;
import com.sqlapp.data.schemas.properties.OwnerNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Owner Name IF
 * 
 * @author satoh
 * 
 */
public interface OwnerProperty<T extends DbCommonObject<?>> extends OwnerNameProperty<T>{

	default User getOwner(){
		User obj= SimpleBeanUtils.getField(this, SchemaProperties.OWNER_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setOwner(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setOwner(User value){
		if (this instanceof DbCommonObject){
			value=SchemaUtils.getUserFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.OWNER_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}

	@Override
	default String getOwnerName(){
		return getOwner()==null?null:getOwner().getName();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	default T setOwnerName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setOwner(null);
		} else{
			if (this.getOwner()==null||!CommonUtils.eq(this.getOwnerName(), name)){
				User obj=new User(name);
				this.setOwner(obj);
			}
		}
		return (T)this;
	}
	
}
