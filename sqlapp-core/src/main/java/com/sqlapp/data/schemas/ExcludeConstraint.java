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
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.eqIgnoreCase;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.IndexTypeProperty;
import com.sqlapp.data.schemas.properties.complex.IndexProperty;
import com.sqlapp.data.schemas.properties.object.ReferenceColumnsProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * ユニーク制約
 * 
 * @author SATOH
 * 
 */
public final class ExcludeConstraint extends Constraint implements 
	IndexTypeProperty<ExcludeConstraint>
	,IndexProperty<ExcludeConstraint>
	,ReferenceColumnsProperty<ExcludeConstraint>
	{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	private Index index = new Index();

	/**
	 * コンストラクタ
	 */
	public ExcludeConstraint() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 *            制約名
	 */
	public ExcludeConstraint(String name) {
		super(name);
	}

	
	@Override
	protected Supplier<Constraint> newInstance(){
		return ()->new ExcludeConstraint();
	}

	/**
	 * コンストラクタ
	 * 
	 * @param constraintName
	 *            制約名
	 * @param column
	 *            制約のあるカラム
	 */
	public ExcludeConstraint(String constraintName, Column column) {
		super(constraintName);
		this.getColumns().add(new ReferenceColumn(column));
	}

	/**
	 * コンストラクタ
	 * 
	 * @param constraintName
	 *            制約名
	 * @param columns
	 *            制約のあるカラム
	 */
	public ExcludeConstraint(String constraintName, Column... columns) {
		super(constraintName);
		for (Column column : columns) {
			this.getColumns().add(new ReferenceColumn(column));
		}
	}

	/**
	 * カラムを追加します
	 * 
	 * @param columnName
	 */
	public ExcludeConstraint addColumn(final String columnName) {
		if (!this.getColumns().contains(columnName)) {
			this.getColumns().add(columnName);
		}
		return this;
	}

	/**
	 * カラムを追加します
	 * 
	 * @param columnName カラム名
	 * @param with 排他制約
	 */
	public ExcludeConstraint addColumn(final String columnName, String with) {
		if (!this.getColumns().contains(columnName)) {
			this.getColumns().add(columnName);
		}
		this.getColumns().get(columnName).setWith(with);
		return this;
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		if (this.getColumns().size() > 0) {
			builder.add(SchemaObjectProperties.REFERENCE_COLUMNS, getColumns().toStringSimple());
		}
		builder.add(SchemaProperties.INDEX_TYPE, this.getIndexType());
		super.toStringDetail(builder);
	}

	@Override
	public ReferenceColumnCollection getColumns() {
		return index.getColumns();
	}

	protected ExcludeConstraint setColumns(ReferenceColumnCollection columns) {
		if (columns != null) {
			if (this.getParent() != null) {
				ConstraintCollection cc = this.getParent();
				columns.setTable(cc.getTable());
			}
		}
		this.index.setColumns(columns);
		return instance();
	}

	/**
	 * カラム名の一致を判定します
	 * 
	 * @param columns
	 * @param targetColumns
	 */
	public static boolean eqColumnNames(
			final ReferenceColumnCollection columns,
			final ReferenceColumnCollection targetColumns) {
		if (columns.size() != targetColumns.size()) {
			return false;
		}
		int size = columns.size();
		for (int i = 0; i < size; i++) {
			if (!eqIgnoreCase(columns.get(i).getName(), targetColumns.get(i)
					.getName())) {
				return false;
			}
		}
		return true;
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		if (this.getIndexName() != null
				&& !CommonUtils.eqIgnoreCase(this.getName(), getIndexName())) {
			stax.writeAttribute(SchemaProperties.INDEX_NAME.getLabel(), this.getIndexName());
		}
		stax.writeAttribute(SchemaProperties.INDEX_TYPE.getLabel(), this.getIndexType());
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		if (!isEmpty(getColumns())) {
			getColumns().writeXml(stax);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.Constraint#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof ExcludeConstraint)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		ExcludeConstraint val = (ExcludeConstraint) obj;
		if (!equals(SchemaObjectProperties.REFERENCE_COLUMNS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.INDEX_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.INDEX_NAME, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.Constraint#clone()
	 */
	@Override
	public ExcludeConstraint clone() {
		ExcludeConstraint clone = new ExcludeConstraint(this.getName());
		this.cloneProperties(clone);
		return clone;
	}

	/**
	 * プロパティのコピー
	 * 
	 * @param clone
	 */
	protected void cloneProperties(ExcludeConstraint clone) {
		super.cloneProperties(clone);
	}


	public Table getTable(){
		if (this.getParent() != null) {
			return this.getParent().getTable();
		}
		return null;
	}
	
	@Override
	public String getTableName() {
		if (this.getParent() != null) {
			ConstraintCollection constraintc = cast(getParent());
			if (constraintc.getTable() != null) {
				return constraintc.getTable().getName();
			}
		}
		return super.getTableName();
	}

	@Override
	public IndexType getIndexType() {
		if (this.getIndex() != null) {
			return this.getIndex().getIndexType();
		}
		return null;
	}

	@Override
	public ExcludeConstraint setIndexType(IndexType indexType) {
		this.getIndex().setIndexType(indexType);
		return this;
	}

	@Override
	public boolean like(Object obj) {
		if (!(obj instanceof ExcludeConstraint)){
			return false;
		}
		ExcludeConstraint con=(ExcludeConstraint)obj;
		if (!CommonUtils.eq(this.getName(), con.getName())){
			if (this.getParent()!=null&&con.getParent()!=null){
				if (this.getParent().contains(con.getName())||con.getParent().contains(this.getName())){
					return false;
				}
			}
		}
		if (eq(this.getColumns(), con.getColumns())) {
			return true;
		}
		return false;
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
			builder.add(SchemaProperties.CATALOG_NAME.getLabel(), this.getCatalogName());
			builder.add(SchemaProperties.SCHEMA_NAME.getLabel(), this.getSchemaName());
		}
		builder.add(SchemaProperties.NAME.getLabel(), this.getName());
		builder.add(SchemaObjectProperties.REFERENCE_COLUMNS.getLabel(), this.getColumns().toStringSimple());
		return builder.toString();
	}
	
	@Override
	protected ExcludeConstraint instance() {
		return this;
	}
	
	@Override
	public ExcludeConstraint setEnable(boolean bool){
		super.setEnable(bool);
		return instance();
	}

	@Override
	public ExcludeConstraint setDeferrability(Deferrability deferrability) {
		super.setDeferrability(deferrability);
		return instance();
	}
	
	@Override
	public ExcludeConstraint setDeferrability(String deferrability) {
		super.setDeferrability(deferrability);
		return instance();
	}
}
