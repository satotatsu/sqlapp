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

package com.sqlapp.data.schemas;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.AdminProperty;
import com.sqlapp.util.StaxWriter;

/**
 * ROLE権限に対応したオブジェクト
 * 
 * @author satoh
 * 
 */
public final class RolePrivilege extends AbstractPrivilege<RolePrivilege>
		implements AdminProperty<RolePrivilege>,
		HasParent<RolePrivilegeCollection> {
	/** serialVersionUID */
	private static final long serialVersionUID = 1L;
	/** admin */
	private boolean admin = false;

	public RolePrivilege() {
	}
	
	@Override
	protected Supplier<RolePrivilege> newInstance(){
		return ()->new RolePrivilege();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof RolePrivilege)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		RolePrivilege val = (RolePrivilege) obj;
		if (!equals(SchemaProperties.ADMIN, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void writeAttributeXml(StaxWriter stax) throws XMLStreamException {
		stax.writeAttribute(SchemaProperties.ADMIN.getLabel(), this.isAdmin());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractDbObject#getParent()
	 */
	@Override
	public RolePrivilegeCollection getParent() {
		return (RolePrivilegeCollection) super.getParent();
	}

	/**
	 * @return the admin
	 */
	@Override
	public boolean isAdmin() {
		return admin;
	}

	/**
	 * @param admin
	 *            the admin to set
	 */
	@Override
	public RolePrivilege setAdmin(boolean admin) {
		this.admin = admin;
		return this;
	}

}
