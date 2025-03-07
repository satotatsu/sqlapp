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

import com.sqlapp.data.schemas.properties.HierachyProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * RoutinePrivilege
 * 
 * @author satoh
 * 
 */
public final class RoutinePrivilege extends
		AbstractObjectPrivilege<RoutinePrivilege> implements
		HasParent<RoutinePrivilegeCollection>
	,HierachyProperty<RoutinePrivilege>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * 今後作成されるサブオブジェクトも含めてすべて権限を与える
	 */
	private boolean hierachy = (Boolean)SchemaProperties.HIERACHY.getDefaultValue();
	@Override
	protected Supplier<RoutinePrivilege> newInstance(){
		return ()->new RoutinePrivilege();
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof RoutinePrivilege)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		RoutinePrivilege val = (RoutinePrivilege) obj;
		if (!equals(SchemaProperties.HIERACHY, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	public boolean isHierachy() {
		return hierachy;
	}

	public RoutinePrivilege setHierachy(boolean hierachy) {
		this.hierachy = hierachy;
		return this;
	}

	@Override
	protected void writeAttributeXml(StaxWriter stax) throws XMLStreamException {
		super.writeAttributeXml(stax);
		if (isHierachy()) {
			stax.writeAttribute(SchemaProperties.HIERACHY.getLabel(), this.isHierachy());
		}
	}

	@Override
	protected void writeValueXml(StaxWriter stax) throws XMLStreamException {
	}

	/**
	 * toString()で子クラスで追加したプロパティの設定
	 * 
	 * @param builder
	 */
	@Override
	protected void toString(ToStringBuilder builder) {
		super.toString(builder);
		builder.add(SchemaProperties.HIERACHY, this.isHierachy());
	}


	@Override
	protected String getObjectNameLabel() {
		return "objectName";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractDbObject#getParent()
	 */
	@Override
	public RoutinePrivilegeCollection getParent() {
		return (RoutinePrivilegeCollection) super.getParent();
	}
}
