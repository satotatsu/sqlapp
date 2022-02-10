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
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.properties.MemberRoleNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * MemberRole IF
 * 
 * @author satoh
 * 
 */
public interface MemberRoleProperty<T extends DbCommonObject<?>> extends MemberRoleNameProperty<T>{

	default Role getMemberRole(){
		Role obj= SimpleBeanUtils.getField(this, SchemaProperties.MEMBER_ROLE_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setMemberRole(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setMemberRole(Role value){
		if (this instanceof DbCommonObject){
			value=SchemaUtils.getRoleFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.MEMBER_ROLE_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}

	@Override
	default String getMemberRoleName(){
		return getMemberRole()==null?null:getMemberRole().getName();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	default T setMemberRoleName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setMemberRole(null);
		} else{
			if (this.getMemberRole()==null||!CommonUtils.eq(this.getMemberRoleName(), name)){
				Role obj=new Role(name);
				this.setMemberRole(obj);
			}
		}
		return (T)this;
	}
	
}
