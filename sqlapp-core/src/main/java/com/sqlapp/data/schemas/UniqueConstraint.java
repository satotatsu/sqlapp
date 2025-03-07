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
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.IndexTypeProperty;
import com.sqlapp.data.schemas.properties.PrimaryKeyProperty;
import com.sqlapp.data.schemas.properties.complex.IndexProperty;
import com.sqlapp.data.schemas.properties.object.ReferenceColumnsProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SeparatedStringBuilder;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * ユニーク制約
 * 
 * @author SATOH
 * 
 */
public final class UniqueConstraint extends Constraint implements PrimaryKeyProperty<UniqueConstraint>
	, IndexProperty<UniqueConstraint>
	, ReferenceColumnsProperty<UniqueConstraint>
	, IndexTypeProperty<UniqueConstraint>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	@SuppressWarnings("unused")
	private final Index index = new Index();
	/** プライマリキー */
	private boolean primaryKey = (Boolean)SchemaProperties.PRIMARY_KEY.getDefaultValue();
	/** プライマリキー制約名 */
	protected static final String PRIMARY_KEY_CONSTRAINT = SchemaProperties.PRIMARY_KEY.getLabel()
			+ "Constraint";

	/**
	 * コンストラクタ
	 */
	public UniqueConstraint() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 *            制約名
	 */
	public UniqueConstraint(final String name) {
		super(name);
	}

	@Override
	protected Supplier<Constraint> newInstance(){
		return ()->new UniqueConstraint();
	}
	
	/**
	 * コンストラクタ
	 * 
	 * @param constraintName
	 *            制約名
	 * @param column
	 *            制約のあるカラム
	 */
	public UniqueConstraint(final String constraintName, final Column column) {
		super(constraintName);
		this.primaryKey = false;
		this.getColumns().add(new ReferenceColumn(column));
	}

	/**
	 * コンストラクタ
	 * 
	 * @param constraintName
	 *            制約名
	 * @param primaryKey
	 *            プライマリーキー
	 */
	public UniqueConstraint(final String constraintName, final boolean primaryKey) {
		super(constraintName);
		this.primaryKey = primaryKey;
	}

	/**
	 * コンストラクタ
	 * 
	 * @param constraintName
	 *            制約名
	 * @param primaryKey
	 *            プライマリーキー
	 * @param column
	 *            制約のあるカラム
	 */
	public UniqueConstraint(final String constraintName, final boolean primaryKey,
			final Column column) {
		super(constraintName);
		this.primaryKey = primaryKey;
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
	public UniqueConstraint(final String constraintName, final Column... columns) {
		this(constraintName, false, columns);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param constraintName
	 *            制約名
	 * @param primaryKey
	 *            プライマリーキー
	 * @param columns
	 *            制約のあるカラム
	 */
	public UniqueConstraint(final String constraintName, final boolean primaryKey,
			final Column... columns) {
		super(constraintName);
		this.primaryKey = primaryKey;
		for (final Column column : columns) {
			this.getColumns().add(new ReferenceColumn(column));
		}
	}

	/**
	 * コンストラクタ
	 * 
	 * @param constraintName
	 *            制約名
	 * @param primaryKey
	 *            プライマリーキー
	 * @param columns
	 *            制約のあるカラム
	 */
	public UniqueConstraint(final String constraintName, final boolean primaryKey,
			final ReferenceColumn... columns) {
		super(constraintName);
		this.primaryKey = primaryKey;
		for (final ReferenceColumn column : columns) {
			this.getColumns().add(column);
		}
	}

	@Override
	public boolean isPrimaryKey() {
		return primaryKey;
	}

	@Override
	public UniqueConstraint setPrimaryKey(final boolean primaryKey) {
		this.primaryKey = primaryKey;
		if (primaryKey) {
			for (final ReferenceColumn column : this.getColumns()) {
				if (column.getColumn() != null) {
					column.getColumn().setNotNull(true);
				}
			}
		}
		if (this.getParent() != null) {
			final UniqueConstraint uk=this.getParent().getPrimaryKeyConstraint();
			if (uk!=null&&uk!=this){
				uk.primaryKey=false;
			}
			this.getParent().sort();
		}
		return this;
	}

	/**
	 * インデックスカラムの追加
	 * 
	 * @param columnName
	 */
	public UniqueConstraint addColumn(final String columnName) {
		if (!this.getColumns().contains(columnName)) {
			this.getColumns().add(columnName);
		}
		return this;
	}

	@Override
	protected void toStringDetail(final ToStringBuilder builder) {
		if (this.getColumns().size() > 0) {
			builder.add(SchemaObjectProperties.REFERENCE_COLUMNS, getColumnsString());
		}
		builder.add(SchemaProperties.PRIMARY_KEY, this.isPrimaryKey());
		builder.add(SchemaProperties.INDEX_TYPE, this.getIndexType());
		super.toStringDetail(builder);
	}

	@Override
	public ReferenceColumnCollection getColumns() {
		if (this.getIndex()!=null) {
			return this.getIndex().getColumns();
		}
		return null;
	}

	protected void setColumns(final ReferenceColumnCollection columns) {
		if (columns != null) {
			if (this.getParent() != null) {
				final ConstraintCollection cc = this.getParent();
				columns.setTable(cc.getTable());
			}
		}
		this.getIndex().setColumns(columns);
	}

	private String getColumnsString() {
		final SeparatedStringBuilder sep = new SeparatedStringBuilder(", ");
		sep.setStart("(").setEnd(")");
		for (final ReferenceColumn column : getColumns()) {
			sep.add(column.getName());
		}
		return sep.toString();
	}

	@Override
	protected void writeXmlOptionalAttributes(final StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		if (this.isPrimaryKey()) {
			stax.writeAttribute(SchemaProperties.PRIMARY_KEY.getLabel(), this.isPrimaryKey());
		}
		if (this.getIndexName() != null
				&& !CommonUtils.eq(this.getName(), getIndexName())) {
			stax.writeAttribute(SchemaProperties.INDEX_NAME.getLabel(), this.getIndexName());
		}
		stax.writeAttribute(SchemaProperties.INDEX_TYPE.getLabel(), this.getIndexType());
	}

	@Override
	protected void writeXmlOptionalValues(final StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		if (!isEmpty(getColumns())) {
			getColumns().writeXml(stax);
		}
	}

	protected void writeXmlAsPrimary(final StaxWriter stax) throws XMLStreamException {
		if (!this.primaryKey) {
			return;
		}
		stax.newLine();
		stax.indent();
		stax.writeStartElement(PRIMARY_KEY_CONSTRAINT);
		writeName(stax);
		stax.writeAttribute(SchemaProperties.DEFERRABILITY.getLabel(), this.getDeferrability());
		stax.writeAttribute(SchemaProperties.INDEX_TYPE.getLabel(), this.getIndexType());
		writeCommonAttribute(stax);
		stax.addIndentLevel(1);
		if (!isEmpty(getColumns())) {
			getColumns().writeXml(stax);
		}
		writeCommonValue(stax);
		stax.addIndentLevel(-1);
		stax.newLine();
		stax.indent();
		stax.writeEndElement();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.Constraint#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj, final EqualsHandler equalsHandler) {
		if (!(obj instanceof UniqueConstraint)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		final UniqueConstraint val = (UniqueConstraint) obj;
		if (!equals(SchemaProperties.PRIMARY_KEY, val, equalsHandler)) {
			return false;
		}
		if (this.getParent() == null && val.getParent() == null) {
			if (!equals(SchemaProperties.TABLE_NAME,val, equalsHandler)) {
				return false;
			}
		}
		if (!equals(SchemaObjectProperties.REFERENCE_COLUMNS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.INDEX_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!this.isPrimaryKey()) {
			if (!equals(SchemaProperties.NAME, val, equalsHandler)) {
				return false;
			}
		}
		return equalsHandler.equalsResult(this, obj);
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
			final ConstraintCollection constraintc = cast(getParent());
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
	public UniqueConstraint setIndexType(final IndexType indexType) {
		this.getIndex().setIndexType(indexType);
		return this;
	}

	@Override
	public boolean like(final Object obj) {
		if (!(obj instanceof UniqueConstraint)){
			return false;
		}
		final UniqueConstraint con=(UniqueConstraint)obj;
		if (this.isPrimaryKey()&&con.isPrimaryKey()){
			return true;
		}
		if (!CommonUtils.eq(this.getName(), con.getName())){
			if (this.getParent()!=null&&con.getParent()!=null){
				if (this.getParent().contains(con.getName())||con.getParent().contains(this.getName())){
					return false;
				}
			}
		}
		if (!eq(this.getColumnsString(), con.getColumnsString())) {
			return false;
		}
		return true;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#toStringSimple()
	 */
	@Override
	public String toStringSimple() {
		final ToStringBuilder builder = new ToStringBuilder(this.getSimpleName());
		if (this.getParent()==null){
			builder.add(SchemaProperties.CATALOG_NAME.getLabel(), this.getCatalogName());
			builder.add(SchemaProperties.SCHEMA_NAME.getLabel(), this.getSchemaName());
		}
		builder.add(SchemaProperties.NAME.getLabel(), this.getName());
		builder.add(SchemaObjectProperties.REFERENCE_COLUMNS, this.getColumnsString());
		return builder.toString();
	}
	
	@Override
	protected UniqueConstraint instance() {
		return this;
	}
	
	@Override
	public UniqueConstraint setEnable(final boolean bool){
		super.setEnable(bool);
		return instance();
	}
	
	@Override
	public UniqueConstraint setDeferrability(final Deferrability deferrability) {
		super.setDeferrability(deferrability);
		return instance();
	}
	
	@Override
	public UniqueConstraint setDeferrability(final String deferrability) {
		super.setDeferrability(deferrability);
		return instance();
	}
}
