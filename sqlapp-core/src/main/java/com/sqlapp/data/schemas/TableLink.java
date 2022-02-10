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
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.TableNameProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * テーブルリンク
 * 
 * @author satoh
 * 
 */
public final class TableLink extends AbstractObjectLink<TableLink> implements
		HasParent<TableLinkCollection>, TableNameProperty<TableLink> {
	/** serialVersionUID */
	private static final long serialVersionUID = -2088739233867759868L;
	/** リンク先のテーブル名 */
	private String tableName = null;

	/**
	 * コンストラクタ
	 */
	protected TableLink() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public TableLink(String name) {
		super(name);
	}

	@Override
	protected Supplier<TableLink> newInstance(){
		return ()->new TableLink();
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof TableLink)) {
			return false;
		}
		TableLink val = cast(obj);
		if (!equals(SchemaProperties.TABLE_NAME, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toString(ToStringBuilder builder) {
		if (!isEmpty(tableName)) {
			builder.add(SchemaProperties.TABLE_NAME, this.getTableName());
		}
	}

	public String getTableName() {
		return tableName;
	}

	public TableLink setTableName(String tableName) {
		this.tableName = tableName;
		return this;
	}

	/**
	 * XML書き込みでオプション属性を書き込みます
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.TABLE_NAME.getLabel(), this.getTableName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public TableLinkCollection getParent() {
		return (TableLinkCollection) super.getParent();
	}

}
