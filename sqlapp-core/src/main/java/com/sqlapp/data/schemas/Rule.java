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

import com.sqlapp.data.schemas.properties.TableNameProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;
import com.sqlapp.util.xml.AbstractSetValue;
import com.sqlapp.util.xml.StaxElementHandler;

/**
 * DB Rule(Postgres専用?)
 * 
 * @author satoh
 * 
 */
public final class Rule extends AbstractSchemaObject<Rule> implements
		HasParent<RuleCollection>, TableNameProperty<Rule> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4010444838360519766L;
	/**
	 * ルールを設定するテーブル、ビュー名
	 */
	private Table table = null;

	/**
	 * コンストラクタ
	 */
	public Rule() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public Rule(String name) {
		super(name);
	}
	
	@Override
	protected Supplier<Rule> newInstance(){
		return ()->new Rule();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedDdlObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Rule)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		Rule val = cast(obj);
		if (!equals(SchemaProperties.TABLE_NAME, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.TABLE_NAME, this.getTableName());
	}

	public Table getTable() {
		if (this.getParent() != null) {
			return this.table;
		}
		this.table = getTableFromParent(table);
		return table;
	}

	/**
	 * @return the tableName
	 */
	@Override
	public String getTableName() {
		if (this.table == null) {
			return null;
		}
		return getTable().getName();
	}

	/**
	 * @param tableName
	 *            the tableName to set
	 */
	@Override
	public Rule setTableName(String tableName) {
		if (tableName == null) {
			this.table = null;
			return this;
		}
		Table table = new Table(tableName);
		table.setSchemaName(this.getSchemaName());
		this.table = getTableFromParent(table);
		return this;
	}

	/**
	 * @param table
	 *            the table to set
	 */
	public void setTable(Table table) {
		if (table != null) {
			table.setSchemaName(this.getSchemaName());
		}
		this.table = getTableFromParent(table);
	}

	protected Table getTableFromParent(Table table) {
		if (table == null) {
			return table;
		}
		if (this.getParent() == null) {
			return table;
		}
		Schema schema = this.getParent().getSchema();
		if (schema == null) {
			return table;
		}
		schema.getTables().get(table.getName());
		Table getTable = schema.getTables().get(table.getName());
		if (getTable != null) {
			return getTable;
		}
		return getTable;
	}

	@Override
	public Rule setSchemaName(String schemaName) {
		super.setSchemaName(schemaName);
		if (this.table != null) {
			this.table.setSchemaName(schemaName);
		}
		return this;
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		stax.writeAttribute(SchemaProperties.TABLE_NAME.getLabel(), this.getTableName());
		super.writeXmlOptionalAttributes(stax);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public RuleCollection getParent() {
		return (RuleCollection) super.getParent();
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<Rule> getDbObjectXmlReaderHandler() {
		return new AbstractNamedObjectXmlReaderHandler<Rule>(this.newInstance()) {
			@Override
			protected void initializeSetValue() {
				super.initializeSetValue();
				StaxElementHandler handler = new TableXmlReaderHandler();
				register(handler.getLocalName(), new AbstractSetValue<Rule, Object>() {
					@Override
					public void setValue(Rule target, String name, Object value)
							throws XMLStreamException {
						if (value instanceof String) {
							target.setTableName((String) value);
						} else if (value instanceof Table) {
							target.setTable((Table) value);
						}
					}
				});
				registerChild(handler);
			}
		};
	}

}
