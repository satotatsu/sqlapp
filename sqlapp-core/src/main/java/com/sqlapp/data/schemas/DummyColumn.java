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

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * DummyColumn
 * 
 */
final class DummyColumn extends AbstractNamedObject<DummyColumn> implements
		HasParent<DummyColumnCollection>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7183150115593442021L;

	protected DummyColumn() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param columnName
	 */
	protected DummyColumn(String columnName) {
		super(columnName);
	}
	
	@Override
	protected Supplier<DummyColumn> newInstance(){
		return ()->new DummyColumn();
	}
	
	@Override
	protected String getSimpleName() {
		return "column";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractColumn#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof DummyColumn)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	protected DummyColumn setColumns(DummyColumnCollection columns) {
		this.setParent(columns);
		return this;
	}


	/**
	 * カラムの属するテーブルの取得
	 * 
	 */
	public Table getTable() {
		return this.getAncestor(Table.class);
	}

	@Override
	public DummyColumnCollection getParent(){
		return (DummyColumnCollection)super.getParent();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#toStringSimple()
	 */
	@Override
	public String toStringSimple() {
		ToStringBuilder builder = new ToStringBuilder(this.getSimpleName());
		if (this.getParent()==null){
			builder.add(SchemaProperties.CATALOG_NAME, this.getCatalogName());
		}
		builder.add(SchemaProperties.NAME.getLabel(), this.getName());
		return builder.toString();
	}

	/**
	 * @return カタログ名を取得します
	 */
	@Override
	public String getCatalogName() {
		Table table = this.getTable();
		if (table != null) {
			return table.getCatalogName();
		}
		return super.getCatalogName();
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
	protected DummyColumnXmlReaderHandler getDbObjectXmlReaderHandler() {
		return new DummyColumnXmlReaderHandler();
	}

	protected Column toColumn(){
		Column column=new Column();
		SchemaUtils.copySchemaProperties(this, column);
		return column;
	}
	
	public boolean isForeignKey(){
		if(this.getTable()==null){
			return false;
		}
		return false;
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
		if (equals(obj, IncludeFilterEqualsHandler.EQUALS_NAME_HANDLER)) {
			return true;
		} else {
			if (!equals(obj,
					ExcludeFilterEqualsHandler.EQUALS_WITHOUT_NAME_HANDLER)) {
				return false;
			}
			DummyColumn column = (DummyColumn) obj;
			if (this.getOrdinal() != column.getOrdinal()) {
				return false;
			}
			if (this.getParent() == null || column.getParent() == null) {
				return true;
			}
			// 他に同じ名前のがある場合はそちらを優先
			DummyColumn eqName = column.getParent().get(this.getName());
			if (eqName != null) {
				return false;
			}
			eqName = this.getParent().get(column.getName());
			if (eqName != null) {
				return false;
			}
			return true;
		}
	}
	
	@Override
	public DummyColumn setName(String name) {
		final String origianlName=this.getName();
		if(CommonUtils.eq(name, origianlName)){
			return instance();
		}
		super.setName(name);
		return instance();
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		
	}
}