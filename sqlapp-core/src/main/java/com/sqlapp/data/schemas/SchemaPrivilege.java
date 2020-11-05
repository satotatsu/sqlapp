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

import static com.sqlapp.util.CommonUtils.cast;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * スキーマ権限に対応したオブジェクト
 * 
 * @author satoh
 * 
 */
public final class SchemaPrivilege extends AbstractPrivilege<SchemaPrivilege>
		implements SchemaNameProperty<SchemaPrivilege>,
		HasParent<SchemaPrivilegeCollection> {
	/** serialVersionUID */
	private static final long serialVersionUID = 1L;
	/** スキーマ名 */
	private String schemaName;

	@Override
	protected Supplier<SchemaPrivilege> newInstance(){
		return ()->new SchemaPrivilege();
	}
	
	@Override
	public String getSchemaName() {
		return schemaName;
	}

	@Override
	public SchemaPrivilege setSchemaName(String schemaName) {
		this.schemaName = schemaName;
		return this;
	}

	@Override
	protected void writeAttributeXml(StaxWriter stax) throws XMLStreamException {
		stax.writeAttribute(SchemaProperties.SCHEMA_NAME.getLabel(), this.getSchemaName());
	}

	/**
	 * toString()で子クラスで追加したプロパティの設定
	 * 
	 * @param builder
	 */
	@Override
	protected void toString(ToStringBuilder builder) {
		super.toString(builder);
		builder.add(SchemaProperties.SCHEMA_NAME, this.getSchemaName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof SchemaPrivilege)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		SchemaPrivilege val = cast(obj);
		if (!equals(SchemaProperties.SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractDbObject#like(com.sqlapp.data.schemas
	 * .AbstractDbObject)
	 */
	@Override
	public boolean like(Object obj) {
		if (!super.like(obj)) {
			return false;
		}
		if (!(obj instanceof SchemaPrivilege)) {
			return false;
		}
		SchemaPrivilege cst = (SchemaPrivilege) obj;
		if (!CommonUtils.eq(this.getSchemaName(), cst.getSchemaName())) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractDbObject#getParent()
	 */
	@Override
	public SchemaPrivilegeCollection getParent() {
		return (SchemaPrivilegeCollection) super.getParent();
	}

}
