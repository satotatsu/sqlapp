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

import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * テーブルに相当するオブジェクト
 * 
 */
class DummyTable extends AbstractDbObject<DummyTable> implements SchemaNameProperty<DummyTable>
	, NameProperty<DummyTable>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7120013699239800425L;

	/** カラムのコレクション */
	private DummyColumnCollection columns = new DummyColumnCollection(this);
	
	private String schemaName=null;
	
	private String name=null;
	
	/**
	 * デフォルトコンストラクタ
	 */
	protected DummyTable() {
	}

	/**
	 * コンストラクタ
	 */
	protected DummyTable(String tableName) {
		setName(tableName);
	}
	
	@Override
	protected Supplier<DummyTable> newInstance(){
		return ()->new DummyTable();
	}

	@Override
	protected String getSimpleName() {
		return "table";
	}
	

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedDdlObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof DummyTable)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		DummyTable val = cast(obj);
		if (!equals(SchemaObjectProperties.COLUMNS, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/**
	 * @return the columns
	 */
	public DummyColumnCollection getColumns() {
		return columns;
	}

	/**
	 * @param columns the columns to set
	 */
	public DummyTable setColumns(DummyColumnCollection columns) {
		this.columns = columns;
		return instance();
	}

	/**
	 * @return the schemaName
	 */
	@Override
	public String getSchemaName() {
		return schemaName;
	}

	/**
	 * @param schemaName the schemaName to set
	 */
	@Override
	public DummyTable setSchemaName(String schemaName) {
		this.schemaName = schemaName;
		return instance();
	}

	/**
	 * @return the name
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	@Override
	public DummyTable setName(String name) {
		this.name = name;
		return instance();
	}

	@Override
	protected void cloneProperties(DummyTable clone) {
		super.cloneProperties(clone);
	}

	protected void setTables(TableCollection tableCollection) {
		this.setParent(tableCollection);
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
	}

	@Override
	protected DummyTableXmlReaderHandler getDbObjectXmlReaderHandler() {
		return new DummyTableXmlReaderHandler();
	}

	protected Table toTable(){
		Table table=new Table();
		SchemaUtils.copySchemaProperties(this, table);
		for(DummyColumn column:this.getColumns()){
			table.getColumns().add(column.toColumn());
		}
		return table;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#validate()
	 */
	@Override
	protected void validate() {
		super.validate();
	}

	@Override
	public int compareTo(DummyTable o) {
		return 0;
	}
	
	@Override
	public String toString(){
		ToStringBuilder builder=new ToStringBuilder();
		builder.add("schemaName", this.getSchemaName());
		builder.add("name", this.getName());
		return builder.toString();
	}
}
