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

package com.sqlapp.data.schemas;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.GrantableProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * ロールに対応したオブジェクト
 * 
 * @author satoh
 * 
 */
public final class Role extends Principal<Role> implements
		HasParent<RoleCollection>, GrantableProperty<Role> {
	/** serialVersionUID */
	private static final long serialVersionUID = 1L;

	private boolean grantable = (Boolean)SchemaProperties.GRANTABLE.getDefaultValue();

	public Role() {
	}

	
	@Override
	protected Supplier<Role> newInstance(){
		return ()->new Role();
	}

	
	/**
	 * @return the grantable
	 */
	@Override
	public boolean isGrantable() {
		return grantable;
	}

	/**
	 * @param grantable
	 *            the grantable to set
	 */
	@Override
	public Role setGrantable(boolean grantable) {
		this.grantable = grantable;
		return this;
	}

	public Role(String roleName) {
		super(roleName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Role)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		Role val = (Role) obj;
		if (!equals(SchemaProperties.GRANTABLE, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.GRANTABLE.getLabel(), this.isGrantable());
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.GRANTABLE, this.isGrantable());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#getParent()
	 */
	@Override
	public RoleCollection getParent() {
		return (RoleCollection) super.getParent();
	}

}
