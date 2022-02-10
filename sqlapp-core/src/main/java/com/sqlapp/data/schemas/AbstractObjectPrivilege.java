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
import static com.sqlapp.util.CommonUtils.compare;
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.isEmpty;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.ObjectNameProperty;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.data.schemas.properties.SpecificNameProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

abstract class AbstractObjectPrivilege<T extends AbstractObjectPrivilege<T>>
		extends AbstractPrivilege<T> implements SchemaNameProperty<T>,SpecificNameProperty<T>, ObjectNameProperty<T> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	/** スキーマ名 */
	private String schemaName;
	/** オブジェクト名 */
	private String objectName;
	/** オブジェクト特化名 */
	private String specificName;

	/**
	 * @return the specificName
	 */
	public String getSpecificName() {
		return specificName;
	}

	/**
	 * @param specificName
	 *            the specificName to set
	 */
	public T setSpecificName(String specificName) {
		this.specificName = specificName;
		return instance();
	}

	@Override
	public String getSchemaName() {
		return schemaName;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T setSchemaName(String schemaName) {
		this.schemaName = schemaName;
		return (T) this;
	}

	public String getObjectName() {
		return objectName;
	}

	@SuppressWarnings("unchecked")
	public T setObjectName(String objectName) {
		this.objectName = objectName;
		return (T) this;
	}

	protected void writeAttributeXml(StaxWriter stax) throws XMLStreamException {
		stax.writeAttribute(SchemaProperties.SCHEMA_NAME.getLabel(), this.getSchemaName());
		stax.writeAttribute(getObjectNameLabel(), this.getObjectName());
		if (!isEmpty(this.getSpecificName())) {
			if (!eq(this.getObjectName(), this.getSpecificName())) {
				stax.writeAttribute(SchemaProperties.SPECIFIC_NAME.getLabel(), this.getSpecificName());
			}
		}
	}

	protected abstract void writeValueXml(StaxWriter stax)
			throws XMLStreamException;

	/**
	 * toString()で子クラスで追加したプロパティの設定
	 * 
	 * @param builder
	 */
	@Override
	protected void toString(ToStringBuilder builder) {
		super.toString(builder);
		builder.add(SchemaProperties.SCHEMA_NAME.getLabel(), this.getSchemaName());
		builder.add(getObjectNameLabel(), this.getObjectName());
		if (!isEmpty(this.getSpecificName())) {
			if (!eq(this.getObjectName(), this.getSpecificName())) {
				builder.add(SchemaProperties.SPECIFIC_NAME.getLabel(), this.getSpecificName());
			}
		}
	}

	protected abstract String getObjectNameLabel();

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof AbstractObjectPrivilege)) {
			return false;
		}
		T val = cast(obj);
		if (!equals(SchemaProperties.SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.OBJECT_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SPECIFIC_NAME, val, equalsHandler)) {
			return false;
		}
		return true;
	}

	/**
	 * like用のハンドラー
	 */
	private final static EqualsHandler LIKE_EQUALS_HANDLER = new IncludeFilterEqualsHandler(
			SchemaProperties.SCHEMA_NAME.getLabel(), SchemaProperties.OBJECT_NAME.getLabel(), SchemaProperties.SPECIFIC_NAME.getLabel());

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
		if (!equals(obj, LIKE_EQUALS_HANDLER)) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractPrivilege#compareTo(com.sqlapp.data.schemas
	 * .AbstractPrivilege)
	 */
	@Override
	public int compareTo(T o) {
		int ret = super.compareTo(o);
		if (ret != 0) {
			return ret;
		}
		ret = compare(this.getSchemaName(), o.getSchemaName());
		if (ret != 0) {
			return ret;
		}
		ret = compare(this.getObjectName(), o.getObjectName());
		if (ret != 0) {
			return ret;
		}
		ret = compare(this.getSpecificName(), o.getSpecificName());
		if (ret != 0) {
			return ret;
		}
		return ret;
	}
}
