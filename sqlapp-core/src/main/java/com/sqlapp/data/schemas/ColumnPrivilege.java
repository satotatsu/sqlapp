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

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.compare;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.ColumnNameProperty;
import com.sqlapp.data.schemas.properties.TableNameProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * カラム権限
 * 
 * @author satoh
 * 
 */
public final class ColumnPrivilege extends
		AbstractObjectPrivilege<ColumnPrivilege> implements
		HasParent<ColumnPrivilegeCollection>, TableNameProperty<ColumnPrivilege>, ColumnNameProperty<ColumnPrivilege> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	/** カラム名 */
	private String columnName;

	@Override
	protected Supplier<ColumnPrivilege> newInstance(){
		return ()->new ColumnPrivilege();
	}

	@Override
	public String getColumnName() {
		return columnName;
	}

	@Override
	public ColumnPrivilege setColumnName(String columnName) {
		this.columnName = columnName;
		return this;
	}

	@Override
	public String getTableName() {
		return this.getObjectName();
	}

	@Override
	public ColumnPrivilege setTableName(String tableName) {
		this.setObjectName(tableName);
		return this;
	}

	@Override
	public ColumnPrivilege clone() {
		ColumnPrivilege clone = new ColumnPrivilege();
		cloneProperties(clone);
		return clone;
	}

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
		if (!(obj instanceof ColumnPrivilege)) {
			return false;
		}
		ColumnPrivilege val = cast(obj);
		if (!equals(SchemaProperties.COLUMN_NAME, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void writeAttributeXml(StaxWriter stax) throws XMLStreamException {
		super.writeAttributeXml(stax);
		stax.writeAttribute(SchemaProperties.COLUMN_NAME.getLabel(), this.getColumnName());
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
		builder.add(SchemaProperties.COLUMN_NAME, this.getColumnName());
	}

	@Override
	protected String getObjectNameLabel() {
		return "tableName";
	}

	@Override
	public ColumnPrivilegeCollection getParent() {
		return (ColumnPrivilegeCollection) super.getParent();
	}

	/**
	 * like用のハンドラー
	 */
	private final static EqualsHandler LIKE_EQUALS_HANDLER = new IncludeFilterEqualsHandler(
			SchemaProperties.COLUMN_NAME.getLabel());

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

	@Override
	protected AbstractBaseDbObjectXmlReaderHandler<ColumnPrivilege> getDbObjectXmlReaderHandler() {
		return new AbstractBaseDbObjectXmlReaderHandler<ColumnPrivilege>(this.newInstance()) {
			@Override
			protected void initializeSetValue() {
				super.initializeSetValue();
				setAlias(SchemaProperties.OBJECT_NAME.getLabel(), "tableName");
			}
		};
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractPrivilege#compareTo(com.sqlapp.data.schemas
	 * .AbstractPrivilege)
	 */
	@Override
	public int compareTo(ColumnPrivilege o) {
		int ret = super.compareTo(o);
		if (ret != 0) {
			return ret;
		}
		ret = compare(this.getColumnName(), o.getColumnName());
		return ret;
	}
}
