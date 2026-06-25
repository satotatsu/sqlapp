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

import static com.sqlapp.data.schemas.StaxWriterUtils.writeColumnSimple;
import static com.sqlapp.util.CommonUtils.first;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.object.ColumnListProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.SeparatedStringBuilder;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * チェック制約抽象クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractColumnConstraint<T extends AbstractColumnConstraint<T>> extends Constraint
		implements ColumnListProperty<T> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6581080845961436973L;
	/** テーブルのカラム */
	protected final ColumnList columns = new ColumnList();

	/**
	 * コンストラクタ
	 */
	protected AbstractColumnConstraint() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param constraintName 制約名
	 * @param columns        テーブルのカラム
	 */
	protected AbstractColumnConstraint(final String constraintName, final Column... columns) {
		super(constraintName);
		this.columns.addAll(columns);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param constraintName 制約名
	 * @param columns        テーブルのカラム
	 */
	protected AbstractColumnConstraint(final String constraintName, final List<Column> columns) {
		super(constraintName);
		this.columns.addAll(columns);
	}

	/**
	 * テーブルの取得
	 * 
	 */
	public Table getTable() {
		if (this.getParent() != null && this.getParent().getParent() != null) {
			return this.getParent().getParent();
		}
		if (isEmpty(columns)) {
			return null;
		}
		Column column = first(columns);
		if (column == null || column.getParent() == null) {
			return null;
		}
		return column.getParent().getParent();
	}

	@Override
	public ColumnList getColumns() {
		if (isEmpty(columns)) {
			return columns;
		}
		Column column = first(columns);
		if (column.getParent() == null) {
			setParentColumn(columns);
		}
		return columns;
	}

	/**
	 * テーブルから正式なカラムを設定します
	 * 
	 * @param columns
	 */
	protected void setParentColumn(List<Column> columns) {
		if (isEmpty(columns)) {
			return;
		}
		Column column = first(columns);
		Table table = column.getTable();
		if (this.getParent() != null) {
			table = this.getParent().getTable();
		}
		if (table == null) {
			return;
		}
		for (int i = 0; i < columns.size(); i++) {
			column = columns.get(i);
			TableCollection tables = table.getParent();
			if (tables == null) {
				continue;
			}
			if (column.getTableName() != null) {
				table = tables.get(column.getTableName());
				if (table == null) {
					continue;
				}
			}
			Column getColumn = table.getColumns().get(column.getName());
			if (getColumn == null) {
				continue;
			}
			if (column == getColumn) {
				continue;
			}
			columns.set(i, getColumn);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.Constraint#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof AbstractColumnConstraint)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		@SuppressWarnings("unchecked")
		T val = (T) obj;
		if (!equals(SchemaObjectProperties.COLUMN_LIST, val, equalsHandler,
				EqualsUtils.getEqualsSupplier(eqColumnName(this.getColumns(), val.getColumns())))) {
			return false;
		}
		return true;
	}

	protected boolean eqColumnName(List<Column> columns1, List<Column> columns2) {
		if (columns1 == null) {
			if (columns2 != null) {
				return false;
			} else {
				return true;
			}
		} else {
			if (columns2 == null) {
				return false;
			}
		}
		if (columns1.size() != columns2.size()) {
			return false;
		}
		for (int i = 0; i < columns1.size(); i++) {
			if (!CommonUtils.eq(columns1.get(i).getName(), columns2.get(i).getName())) {
				return false;
			}
		}
		return true;
	}

	public boolean containsColumn(Column column) {
		for (Column col : this.columns) {
			if (col == column) {
				return true;
			}
			if (CommonUtils.eq(col.getName(), column.getName())) {
				return true;
			}
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.Constraint#getTableName()
	 */
	@Override
	public String getTableName() {
		if (this.getTable() != null) {
			return getTable().getName();
		}
		return super.getTableName();
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void cloneProperties(Constraint clone) {
		super.cloneProperties(clone);
		List<Column> columns = CommonUtils.list();
		for (Column column : this.getColumns()) {
			columns.add(column.clone());
		}
		((T) clone).getColumns().set(columns);
	}

	protected void writeColumns(String name, StaxWriter stax, List<Column> columns) throws XMLStreamException {
		if (!isEmpty(columns)) {
			stax.newLine();
			stax.indent();
			stax.writeStartElement(name);
			stax.addIndentLevel(1);
			writeColumnSimple(stax, columns);
			stax.addIndentLevel(-1);
			stax.newLine();
			stax.indent();
			stax.writeEndElement();
		}
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax) throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		writeColumns(SchemaObjectProperties.COLUMN_LIST.getLabel(), stax, this.getColumns());
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaObjectProperties.COLUMN_LIST, columnNamesToString(this.getColumns()));
		super.toStringDetail(builder);
	}

	/**
	 * カラム名を文字列に変換します
	 * 
	 * @param columns
	 */
	protected static String columnNamesToString(List<Column> columns) {
		if (columns == null || columns.size() == 0) {
			return "";
		}
		SeparatedStringBuilder builder = new SeparatedStringBuilder(",");
		builder.setStart("(").setEnd(")");
		builder.addNames(columns);
		return builder.toString();
	}

	/**
	 * カラム名の配列からテーブル名を取得します
	 * 
	 * @param columns
	 */
	protected static String getTableName(List<Column> columns) {
		if (columns == null || columns.size() == 0) {
			return null;
		}
		return first(columns).getTableName();
	}

	/**
	 * カラム名の配列からスキーマ名を取得します
	 * 
	 * @param columns
	 */
	protected static String getSchemaName(List<Column> columns) {
		if (columns == null || columns.size() == 0) {
			return null;
		}
		return first(columns).getSchemaName();
	}

}
