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

package com.sqlapp.util;

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.LengthProperties;
import com.sqlapp.data.db.datatype.PrecisionProperties;
import com.sqlapp.data.db.datatype.ScaleProperties;
import com.sqlapp.data.db.dialect.DefaultDialectHolder;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.TableLockMode;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.AbstractNamedObject;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnCollection;
import com.sqlapp.data.schemas.DimensionLevel;
import com.sqlapp.data.schemas.EnumProperties;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.FunctionReturning;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.NamedArgumentCollection;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.ReferenceColumnCollection;
import com.sqlapp.data.schemas.Routine;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.properties.DataTypeProperties;
import com.sqlapp.data.schemas.properties.DataTypeSetProperties;
import com.sqlapp.jdbc.sql.ParameterDirection;

/**
 * SQL構築用抽象クラス
 * 
 * @author satoh
 * 
 */
public class AbstractSqlBuilder<T extends AbstractSqlBuilder<?>> implements Serializable, Cloneable {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 8626853738386921275L;

	private StringBuilder builder = new StringBuilder(256);
	/**
	 * インデント文字
	 */
	private String indentString = "\t";
	/**
	 * 改行時のインデントサイズ
	 */
	private int indentSize = 0;

	private Dialect dialect = DefaultDialectHolder.DefaultDialect;
	/**
	 * オブジェクト名のクォート
	 */
	private boolean quateObjectName = true;
	/**
	 * カラム名のクォート
	 */
	private boolean quateColumnName = true;
	/**
	 * スキーマ名の有
	 */
	private boolean withSchemaName = true;
	/**
	 * スペースの自動追加
	 */
	private boolean appendAutoSpace = true;

	/**
	 * @return the withSchemaName
	 */
	public boolean isWithSchemaName() {
		return withSchemaName;
	}

	/**
	 * @param withSchemaName the withSchemaName to set
	 */
	public T setWithSchemaName(final boolean withSchemaName) {
		this.withSchemaName = withSchemaName;
		return instance();
	}

	/**
	 * @return the appendAutoSpace
	 */
	public boolean isAppendAutoSpace() {
		return appendAutoSpace;
	}

	/**
	 * @param appendAutoSpace the appendAutoSpace to set
	 */
	public T setAppendAutoSpace(final boolean appendAutoSpace) {
		this.appendAutoSpace = appendAutoSpace;
		return instance();
	}

	@SuppressWarnings("unchecked")
	protected T instance() {
		return (T) this;
	}

	/**
	 * @return the quate
	 */
	public boolean isQuateObjectName() {
		return quateObjectName;
	}

	/**
	 * @param quateObjectName the quateObjectName to set
	 */
	public T setQuateObjectName(final boolean quateObjectName) {
		this.quateObjectName = quateObjectName;
		return instance();
	}

	/**
	 * @return the quateColumnName
	 */
	public boolean isQuateColumnName() {
		return quateColumnName;
	}

	/**
	 * @param quateColumnName the quateColumnName to set
	 */
	public T setQuateColumnName(final boolean quateColumnName) {
		this.quateColumnName = quateColumnName;
		return instance();
	}

	public AbstractSqlBuilder(final Dialect dialect) {
		this.dialect = dialect;
	}

	/**
	 * CREATE句を追加します
	 * 
	 */
	public T create() {
		appendElement("CREATE");
		return instance();
	}

	/**
	 * DECLARE句を追加します
	 * 
	 */
	public T declare() {
		appendElement("DECLARE");
		return instance();
	}

	/**
	 * LOCAL句を追加します
	 * 
	 */
	public T local() {
		appendElement("LOCAL");
		return instance();
	}

	/**
	 * TEMPORARY句を追加します
	 * 
	 */
	public T temporary() {
		appendElement("TEMPORARY");
		return instance();
	}

	/**
	 * PRESERVE句を追加します
	 * 
	 * @return this
	 */
	public T preserve() {
		appendElement("PRESERVE");
		return instance();
	}

	/**
	 * ALTER句を追加します
	 * 
	 * @return this
	 */
	public T alter() {
		appendElement("ALTER");
		return instance();
	}

	/**
	 * ALTER COLUMN句を追加します
	 * 
	 * @return this
	 */
	public T alterColumn() {
		appendElement("ALTER COLUMN");
		return instance();
	}

	/**
	 * RENAME句を追加します
	 * 
	 * @return this
	 */
	public T rename() {
		appendElement("RENAME");
		return instance();
	}

	/**
	 * CONNECT句を追加します
	 * 
	 * @return this
	 */
	public T connect() {
		appendElement("CONNECT");
		return instance();
	}

	/**
	 * TO句を追加します
	 * 
	 * @return this
	 */
	public T to() {
		appendElement("TO");
		return instance();
	}

	/**
	 * Add GENERATE_SERIES
	 * 
	 * @return this
	 */
	public T generateSeries() {
		appendElement("GENERATE_SERIES");
		return instance();
	}

	/**
	 * Add UNNEST
	 * 
	 * @return this
	 */
	public T unnest() {
		appendElement("UNNEST");
		return instance();
	}

	/**
	 * Add SEQUENCE_ARRAY
	 * 
	 * @return this
	 */
	public T sequenceArray() {
		appendElement("SEQUENCE_ARRAY");
		return instance();
	}

	/**
	 * 名称を追加します
	 * 
	 * @param object
	 * @return this
	 */
	public T name(final AbstractNamedObject<?> object) {
		return appendQuoteName(object.getName());
	}

	/**
	 * インデックス名を追加します
	 * 
	 * @param index
	 * @return this
	 */
	public T name(final Index index) {
		return name(index, this.isWithSchemaName());
	}

	/**
	 * インデックス名を追加します
	 * 
	 * @param index
	 * @param withSchemaName
	 */
	public T name(final Index index, final boolean withSchemaName) {
		if (withSchemaName) {
			if (!isEmpty(index.getTable().getSchemaName())) {
				appendQuoteName(index.getTable().getSchemaName());
				this._add('.');
			}
		}
		return appendQuoteName(index.getName());
	}

	/**
	 * 名称を追加します
	 * 
	 * @param abstractSchemaObject
	 */
	public T name(final AbstractSchemaObject<?> abstractSchemaObject) {
		return name(abstractSchemaObject, this.isWithSchemaName());
	}

	/**
	 * 名称を追加します
	 * 
	 * @param abstractSchemaObject
	 * @param withSchemaName
	 */
	public T name(final AbstractSchemaObject<?> abstractSchemaObject, final boolean withSchemaName) {
		if (withSchemaName) {
			if (!isEmpty(abstractSchemaObject.getSchemaName())) {
				appendQuoteName(abstractSchemaObject.getSchemaName());
				this._add('.');
			}
		}
		return appendQuoteName(abstractSchemaObject.getName());
	}

	/**
	 * 名称を追加します
	 * 
	 * @param routine
	 * @param withSchemaName
	 */
	public T specificName(final Routine<?> routine, final boolean withSchemaName) {
		if (withSchemaName) {
			if (!isEmpty(routine.getSchemaName())) {
				appendQuoteName(routine.getSchemaName());
				this._add('.');
			}
		}
		return appendQuoteName(routine.getSpecificName());
	}

	/**
	 * 名称を追加します
	 * 
	 * @param schema スキーマ
	 */
	public T name(final Schema schema) {
		if (isEmpty(schema)) {
			return instance();
		}
		return appendQuoteName(schema.getName());
	}

	/**
	 * カラム名称を追加します
	 * 
	 * @param column
	 */
	public T name(final Column column) {
		this.appendQuoteColumnName(column.getName());
		return instance();
	}

	/**
	 * カラム名称を追加します
	 * 
	 * @param prefix
	 * @param column
	 */
	public T name(final String prefix, final Column column) {
		this.appendQuoteColumnName(prefix, column.getName());
		return instance();
	}

	/**
	 * カラム名称を追加します
	 * 
	 * @param prefix
	 * @param column
	 */
	public T name(final String prefix, final ReferenceColumn column) {
		this.appendQuoteColumnName(prefix, column.getName());
		return instance();
	}

	/**
	 * カラム名称を追加します
	 * 
	 * @param column
	 */
	public T name(final DimensionLevel column) {
		this.appendQuoteColumnName(column.getName());
		return instance();
	}

	/**
	 * 名称を追加します
	 * 
	 * @param name
	 */
	public T name(final String name) {
		return appendQuoteName(name);
	}

	/**
	 * 名称を追加します
	 * 
	 * @param names
	 */
	public T names(final String... names) {
		boolean add = false;
		for (final String name : names) {
			if (name == null) {
				continue;
			}
			_add(".", add);
			appendQuoteName(name);
			add = true;
		}
		return instance();
	}

	/**
	 * カラム名を追加します
	 * 
	 * @param name
	 */
	public T columnName(final String name) {
		return appendQuoteColumnName(name);
	}

	/**
	 * カラム名を追加します
	 * 
	 * @param column
	 */
	public T columnName(final Column column, final boolean withTableName) {
		return columnName(column, withTableName, false);
	}

	/**
	 * カラム名を追加します
	 * 
	 * @param column
	 */
	public T columnName(final Column column, final boolean withTableName, final boolean withSchemaName) {
		final Table table = column.getTable();
		if (withSchemaName) {
			if (table != null && !CommonUtils.isEmpty(table.getSchemaName())) {
				appendQuoteName(table.getSchemaName());
				_add(".");
			}
		}
		if (withTableName) {
			if (table == null || CommonUtils.isEmpty(table.getName())) {
				return columnName(column.getName());
			}
			appendQuoteName(table.getName());
			_add(".");
			appendQuoteName(column.getName());
		} else {
			appendQuoteName(column.getName());
		}
		return instance();
	}

	/**
	 * カラム名称を追加します
	 * 
	 * @param columns
	 */
	public T names(final Column... columns) {
		return names(c -> true, columns);
	}

	/**
	 * カラム名称を追加します
	 * 
	 * @param columns
	 */
	public T names(final Predicate<Column> p, final Column... columns) {
		boolean first = true;
		for (int i = 0; i < columns.length; i++) {
			final Column column = columns[i];
			if (p.test(column)) {
				comma(!first).appendQuoteColumnName(column.getName());
				first = false;
			}
		}
		return instance();
	}

	/**
	 * カラム名称を追加します
	 * 
	 * @param columns
	 */
	public T names(final ReferenceColumn... columns) {
		return names(c -> true, columns);
	}

	/**
	 * カラム名称を追加します
	 * 
	 * @param columns
	 */
	public T names(final Predicate<ReferenceColumn> p, final ReferenceColumn... columns) {
		boolean first = true;
		for (int i = 0; i < columns.length; i++) {
			final ReferenceColumn column = columns[i];
			if (p.test(column)) {
				comma(!first).appendQuoteColumnName(column.getName());
				order(column.getOrder());
				first = false;
			}
		}
		return instance();
	}

	/**
	 * カラム名称を追加します
	 * 
	 * @param columns
	 */
	public T names(final ColumnCollection columns) {
		return names(c -> true, columns);
	}

	/**
	 * カラム名称を追加します
	 * 
	 * @param columns
	 */
	public T names(final Predicate<Column> p, final ColumnCollection columns) {
		boolean first = true;
		for (int i = 0; i < columns.size(); i++) {
			final Column column = columns.get(i);
			if (p.test(column)) {
				comma(!first).appendQuoteColumnName(column.getName());
				first = false;
			}
		}
		return instance();
	}

	/**
	 * カラム名称を追加します
	 * 
	 * @param columns
	 */
	public T names(final ReferenceColumnCollection columns) {
		return names(c -> true, columns);
	}

	/**
	 * カラム名称を追加します
	 * 
	 * @param columns
	 */
	public T names(final Predicate<ReferenceColumn> p, final ReferenceColumnCollection columns) {
		boolean first = true;
		for (int i = 0; i < columns.size(); i++) {
			final ReferenceColumn column = columns.get(i);
			comma(!first).appendQuoteColumnName(column.getName());
			order(column.getOrder());
			first = false;
		}
		return instance();
	}

	protected T order(final Order order) {
		if (order == Order.Desc) {
			space()._add(Order.Desc.toString().toUpperCase());
		}
		return instance();
	}

	/**
	 * 名称を追加します
	 * 
	 * @param name
	 */
	protected T appendQuoteName(final String name) {
		if (getDialect() != null && getDialect().needQuote(name)) {
			if (this.isQuateObjectName()) {
				appendElement(getDialect().quote(name));
			} else {
				appendElement(name);
			}
			return instance();
		}
		appendElement(name);
		return instance();
	}

	/**
	 * 名称を追加します
	 * 
	 * @param prefix prefix
	 * @param name   name
	 */
	public T appendQuoteName(String prefix, final String name) {
		if (prefix == null) {
			prefix = "";
		}
		if (getDialect() != null && getDialect().needQuote(name)) {
			if (this.isQuateObjectName()) {
				appendElement(prefix + getDialect().quote(name));
			} else {
				appendElement(prefix + name);
			}
			return instance();
		}
		appendElement(prefix + name);
		return instance();
	}

	/**
	 * カラム名を追加します
	 * 
	 * @param name
	 */
	protected T appendQuoteColumnName(final String name) {
		return appendQuoteColumnName("", name);
	}

	/**
	 * カラム名を追加します
	 * 
	 * @param prefix prefix
	 * @param name   name
	 */
	protected T appendQuoteColumnName(final String prefix, final String name) {
		final String _pre = prefix == null ? "" : prefix;
		if (getDialect() != null && getDialect().needQuote(name)) {
			if (this.isQuateColumnName()) {
				appendElement(_pre + getDialect().quote(name));
			} else {
				appendElement(_pre + name);
			}
			return instance();
		}
		appendElement(_pre + name);
		return instance();
	}

	/**
	 * コメントを追加します
	 * 
	 * @param comment
	 */
	public T comment(final String comment) {
		appendElement("/*").appendElement(comment).appendElement("*/");
		return instance();
	}

	/**
	 * データ型を追加します
	 * 
	 * @param column
	 */
	public T dataType(final Column column) {
		final DbDataType<?> dbDataType = this.getDialect().getDbDataTypes().getDbType(column.getDataType(),
				column.getLength());
		dbDataType.getColumCreateDefinition(column.getLength(), column.getScale());
		return instance();
	}

	/**
	 * DROP句を追加します
	 * 
	 */
	public T drop() {
		appendElement("DROP");
		return instance();
	}

	/**
	 * SELECT句を追加します
	 * 
	 */
	public T select() {
		appendElement("SELECT");
		return instance();
	}

	/**
	 * count句を追加します
	 * 
	 */
	public T count() {
		appendElement("COUNT");
		return instance();
	}

	/**
	 * count句を追加します
	 * 
	 */
	public T count(final String value) {
		if (!CommonUtils.isEmpty(value)) {
			this.count();
			this._add("(");
			this._add(value);
			this._add(")");
		}
		return instance();
	}

	/**
	 * count句を追加します
	 * 
	 */
	public T count(final String value, final boolean condition) {
		if (condition) {
			this.count(value);
		}
		return instance();
	}

	/**
	 * AS句を追加します
	 * 
	 */
	public T as() {
		appendElement("AS");
		return instance();
	}

	/**
	 * START句を追加します
	 * 
	 */
	public T start() {
		appendElement("START");
		return instance();
	}

	/**
	 * TRANSACTION句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T transaction() {
		appendElement("TRANSACTION");
		return instance();
	}

	/**
	 * AUTOCOMMIT句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T autocommit() {
		appendElement("AUTOCOMMIT");
		return instance();
	}

	/**
	 * GLOBAL句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T global() {
		appendElement("GLOBAL");
		return instance();
	}

	/**
	 * WITH句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T with() {
		appendElement("WITH");
		return instance();
	}

	/**
	 * WITHOUT句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T without() {
		appendElement("WITHOUT");
		return instance();
	}

	/**
	 * INCREMENT句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T increment() {
		appendElement("INCREMENT");
		return instance();
	}

	/**
	 * BY句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T by() {
		appendElement("BY");
		return instance();
	}

	/**
	 * TARGET句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T target() {
		appendElement("TARGET");
		return instance();
	}

	/**
	 * SOURCE句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T source() {
		appendElement("SOURCE");
		return instance();
	}

	/**
	 * MAXVALUE句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T maxvalue() {
		appendElement("MAXVALUE");
		return instance();
	}

	/**
	 * MINVALUE句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T minvalue() {
		appendElement("MINVALUE");
		return instance();
	}

	/**
	 * CYCLE句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T cycle() {
		appendElement("CYCLE");
		return instance();
	}

	/**
	 * CACHE句を追加します
	 * 
	 */
	public T cache() {
		appendElement("CACHE");
		return instance();
	}

	/**
	 * ORDER句を追加します
	 * 
	 */
	public T order() {
		appendElement("ORDER");
		return instance();
	}

	/**
	 * NO ORDER句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T noOrder() {
		appendElement("NO ORDER");
		return instance();
	}

	/**
	 * MAX句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T max() {
		appendElement("MAX");
		return instance();
	}

	/**
	 * VALUES句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T values() {
		appendElement("VALUES");
		return instance();
	}

	/**
	 * VALUE句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T value() {
		appendElement("VALUE");
		return instance();
	}

	/**
	 * CASE句を追加します
	 * 
	 * @return this
	 */
	public T case_() {
		appendElement("CASE");
		return instance();
	}

	/**
	 * FOR句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T for_() {
		appendElement("FOR");
		return instance();
	}

	/**
	 * RETURN句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T return_() {
		appendElement("RETURN");
		return instance();
	}

	/**
	 * PUBLIC句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T public_() {
		appendElement("PUBLIC");
		return instance();
	}

	/**
	 * NULL句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T null_() {
		appendElement("NULL");
		return instance();
	}

	/**
	 * NEW句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T new_() {
		appendElement("NEW");
		return instance();
	}

	/**
	 * DEFAULT句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T default_() {
		appendElement("DEFAULT");
		return instance();
	}

	/**
	 * DO句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T do_() {
		appendElement("DO");
		return instance();
	}

	/**
	 * PACKAGE句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T package_() {
		appendElement("PACKAGE");
		return instance();
	}

	/**
	 * CHECK句を追加します
	 * 
	 */
	public T check() {
		appendElement("CHECK");
		return instance();
	}

	/**
	 * CALL句を追加します
	 * 
	 */
	public T call() {
		appendElement("CALL");
		return instance();
	}

	/**
	 * SCHEMA句を追加します
	 * 
	 */
	public T schema() {
		appendElement("SCHEMA");
		return instance();
	}

	/**
	 * UNIQUE句を追加します
	 * 
	 */
	public T unique() {
		appendElement("UNIQUE");
		return instance();
	}

	/**
	 * REPLICATION句を追加します
	 * 
	 */
	public T replication() {
		appendElement("REPLICATION");
		return instance();
	}

	/**
	 * 条件に従って、UNIQUE句を追加します
	 * 
	 * @param condition 出力条件
	 */
	public T unique(final boolean condition) {
		if (condition) {
			return unique();
		}
		return instance();
	}

	/**
	 * INTO句を追加します
	 * 
	 */
	public T into() {
		appendElement("INTO");
		return instance();
	}

	/**
	 * INTO句を追加します
	 * 
	 */
	public T from() {
		appendElement("FROM");
		return instance();
	}

	/**
	 * AT句を追加します
	 * 
	 */
	public T at() {
		appendElement("AT");
		return instance();
	}

	/**
	 * AND句を追加します
	 * 
	 */
	public T and() {
		appendElement("AND");
		return instance();
	}

	/**
	 * COMMIT句を追加します
	 * 
	 */
	public T commit() {
		appendElement("COMMIT");
		return instance();
	}

	/**
	 * LOCK句を追加します
	 * 
	 */
	public T lock() {
		appendElement("LOCK");
		return instance();
	}

	/**
	 * 条件がtrueの場合にAND句を追加します
	 * 
	 */
	public T and(final boolean condition) {
		if (condition) {
			appendElement("AND");
		}
		return instance();
	}

	/**
	 * INSERT句を追加します
	 * 
	 */
	public T insert() {
		appendElement("INSERT");
		return instance();
	}

	/**
	 * UPDATE句を追加します
	 * 
	 */
	public T update() {
		appendElement("UPDATE");
		return instance();
	}

	/**
	 * DELETE句を追加します
	 * 
	 */
	public T delete() {
		appendElement("DELETE");
		return instance();
	}

	/**
	 * MERGE句を追加します
	 * 
	 */
	public T merge() {
		appendElement("MERGE");
		return instance();
	}

	/**
	 * add SPLIT statement
	 * 
	 */
	public T split() {
		appendElement("SPLIT");
		return instance();
	}

	/**
	 * REPLACE句を追加します
	 * 
	 */
	public T replace() {
		appendElement("REPLACE");
		return instance();
	}

	/**
	 * TABLE句を追加します
	 * 
	 */
	public T table() {
		appendElement("TABLE");
		return instance();
	}

	/**
	 * VIEW句を追加します
	 * 
	 */
	public T view() {
		appendElement("VIEW");
		return instance();
	}

	/**
	 * MATERIALIZED句を追加します
	 * 
	 */
	public T materialized() {
		appendElement("MATERIALIZED");
		return instance();
	}

	/**
	 * LOG句を追加します
	 * 
	 */
	public T log() {
		appendElement("LOG");
		return instance();
	}

	/**
	 * INTERVAL句を追加します
	 * 
	 */
	public T interval() {
		appendElement("INTERVAL");
		return instance();
	}

	/**
	 * SPECIFIC句を追加します
	 * 
	 */
	public T specific() {
		appendElement("SPECIFIC");
		return instance();
	}

	/**
	 * SYNONYM句を追加します
	 * 
	 */
	public T synonym() {
		appendElement("SYNONYM");
		return instance();
	}

	/**
	 * INDEX句を追加します
	 * 
	 */
	public T index() {
		appendElement("INDEX");
		return instance();
	}

	/**
	 * TRIGGER句を追加します
	 * 
	 */
	public T trigger() {
		appendElement("TRIGGER");
		return instance();
	}

	/**
	 * FULLTEXT句を追加します
	 * 
	 */
	public T fulltext() {
		appendElement("FULLTEXT");
		return instance();
	}

	/**
	 * SPATIAL句を追加します
	 * 
	 */
	public T spatial() {
		appendElement("SPATIAL");
		return instance();
	}

	/**
	 * FETCH句を追加します
	 * 
	 * @return this
	 */
	public T fetch() {
		appendElement("FETCH");
		return instance();
	}

	/**
	 * FIRST句を追加します
	 * 
	 * @return this
	 */
	public T first() {
		appendElement("FIRST");
		return instance();
	}

	/**
	 * FINAL句を追加します
	 * 
	 * @return this
	 */
	public T final_() {
		appendElement("FINAL");
		return instance();
	}

	/**
	 * TRUNCATE句を追加します
	 * 
	 * @return this
	 */
	public T truncate() {
		appendElement("TRUNCATE");
		return instance();
	}

	/**
	 * ANALYZE句を追加します
	 * 
	 * @return this
	 */
	public T analyze() {
		appendElement("ANALYZE");
		return instance();
	}

	/**
	 * CASCADE句を追加します
	 * 
	 */
	public T cascade() {
		appendElement("CASCADE");
		return instance();
	}

	/**
	 * KEY句を追加します
	 * 
	 */
	public T key() {
		appendElement("KEY");
		return instance();
	}

	/**
	 * ADD句を追加します
	 * 
	 */
	public T add() {
		appendElement("ADD");
		return instance();
	}

	/**
	 * MODIFY句を追加します
	 * 
	 */
	public T modify() {
		appendElement("MODIFY");
		return instance();
	}

	/**
	 * CONVERT句を追加します
	 * 
	 */
	public T convert() {
		appendElement("CONVERT");
		return instance();
	}

	/**
	 * ON句を追加します
	 * 
	 */
	public T on() {
		appendElement("ON");
		return instance();
	}

	/**
	 * ONLY句を追加します
	 * 
	 */
	public T only() {
		appendElement("ONLY");
		return instance();
	}

	/**
	 * OR句を追加します
	 * 
	 */
	public T or() {
		appendElement("OR");
		return instance();
	}

	/**
	 * OR句を追加します
	 * 
	 * @param bool 出力条件
	 */
	public T or(boolean bool) {
		if (bool) {
			return or();
		}
		return instance();
	}

	/**
	 * OFF句を追加します
	 * 
	 */
	public T off() {
		appendElement("OFF");
		return instance();
	}

	/**
	 * MATCH句を追加します
	 * 
	 */
	public T match() {
		appendElement("MATCH");
		return instance();
	}

	/**
	 * PRIMARY KEY句を追加します
	 * 
	 */
	public T primaryKey() {
		appendElement("PRIMARY KEY");
		return instance();
	}

	/**
	 * FOREIGN KEY句を追加します
	 * 
	 */
	public T foreignKey() {
		appendElement("FOREIGN KEY");
		return instance();
	}

	/**
	 * REFERENCES句を追加します
	 * 
	 */
	public T references() {
		appendElement("REFERENCES");
		return instance();
	}

	/**
	 * WHERE句を追加します
	 * 
	 */
	public T where() {
		appendElement("WHERE");
		return instance();
	}

	/**
	 * IS句を追加します
	 * 
	 */
	public T is() {
		appendElement("IS");
		return instance();
	}

	/**
	 * ORDER BY句を追加します
	 * 
	 */
	public T orderBy() {
		appendElement("ORDER BY");
		return instance();
	}

	/**
	 * GROUP BY句を追加します
	 * 
	 */
	public T groupBy() {
		appendElement("GROUP BY");
		return instance();
	}

	/**
	 * ADD ENABLE
	 * 
	 */
	public T enable() {
		return enable(true);
	}

	/**
	 * ADD ENABLE
	 * 
	 */
	public T enable(final boolean condition) {
		if (condition) {
			appendElement("ENABLE");
		}
		return instance();
	}

	/**
	 * ADD DISABLE
	 * 
	 */
	public T disable() {
		return disable(true);
	}

	/**
	 * ADD DISABLE
	 * 
	 */
	public T disable(final boolean condition) {
		if (condition) {
			appendElement("DISABLE");
		}
		return instance();
	}

	/**
	 * CONSTRAINTS句を追加します
	 * 
	 */
	public T constraints() {
		appendElement("CONSTRAINTS");
		return instance();
	}

	/**
	 * COLUMN句を追加します
	 * 
	 */
	public T column() {
		return column(true);
	}

	/**
	 * 条件がtrueの場合にCOLUMN句を追加します
	 * 
	 * @param bool 条件
	 */
	public T column(final boolean bool) {
		if (!bool) {
			return instance();
		}
		appendElement("COLUMN");
		return instance();
	}

	/**
	 * CONSTRAINT句を追加します
	 * 
	 */
	public T constraint() {
		appendElement("CONSTRAINT");
		return instance();
	}

	/**
	 * IF EXISTS句を追加します
	 * 
	 */
	public T ifExists() {
		return ifExists(true);
	}

	/**
	 * IF EXISTS句を追加します
	 * 
	 */
	public T ifExists(final boolean bool) {
		if (bool) {
			appendElement("IF EXISTS");
		}
		return instance();
	}

	/**
	 * CHANGE句を追加します
	 * 
	 * @return this
	 */
	public T change() {
		appendElement("CHANGE");
		return instance();
	}

	/**
	 * OPTIMIZE句を追加します
	 * 
	 * @return this
	 */
	public T optimize() {
		appendElement("OPTIMIZE");
		return instance();
	}

	/**
	 * OPTION句を追加します
	 * 
	 * @return this
	 */
	public T option() {
		appendElement("OPTION");
		return instance();
	}

	/**
	 * MAXRECURSION句を追加します
	 * 
	 * @return this
	 */
	public T maxrecursion() {
		appendElement("MAXRECURSION");
		return instance();
	}

	/**
	 * RECURSIVE句を追加します
	 * 
	 * @return this
	 */
	public T recursive() {
		appendElement("RECURSIVE");
		return instance();
	}

	/**
	 * PARTITION句を追加します
	 * 
	 * @return this
	 */
	public T partition() {
		return partition(true);
	}

	/**
	 * PARTITION句を追加します
	 * 
	 * @return this
	 */
	public T partition(final boolean bool) {
		if (bool) {
			appendElement("PARTITION");
		}
		return instance();
	}

	/**
	 * OFFSET句を追加します
	 * 
	 * @return this
	 */
	public T offset() {
		appendElement("OFFSET");
		return instance();
	}

	/**
	 * PARTITION BY句を追加します
	 * 
	 * @return this
	 */
	public T partitionBy() {
		appendElement("PARTITION BY");
		return instance();
	}

	/**
	 * SUBPARTITION BY句を追加します
	 * 
	 * @return this
	 */
	public T subpartitionBy() {
		appendElement("SUBPARTITION BY");
		return instance();
	}

	/**
	 * Add SECURITY key word
	 * 
	 * @return this
	 */
	public T security() {
		appendElement("SECURITY");
		return instance();
	}

	/**
	 * Add INVOKER key word
	 * 
	 * @return this
	 */
	public T invoker() {
		appendElement("INVOKER");
		return instance();
	}

	/**
	 * Add DEFINER key word
	 * 
	 * @return this
	 */
	public T definer() {
		appendElement("DEFINER");
		return instance();
	}

	/**
	 * PARTITIONS句を追加します
	 * 
	 * @return this
	 */
	public T partitions() {
		appendElement("PARTITIONS");
		return instance();
	}

	/**
	 * PARTITIONING句を追加します
	 * 
	 * @return this
	 */
	public T partitioning() {
		appendElement("PARTITIONING");
		return instance();
	}

	/**
	 * SUBPARTITION句を追加します
	 * 
	 * @return this
	 */
	public T subpartition() {
		return subpartition(true);
	}

	/**
	 * SUBPARTITION句を追加します
	 * 
	 * @return this
	 */
	public T subpartition(final boolean bool) {
		if (bool) {
			appendElement("SUBPARTITION");
		}
		return instance();
	}

	/**
	 * SUBPARTITIONS句を追加します
	 * 
	 * @return this
	 */
	public T subpartitions() {
		appendElement("SUBPARTITIONS");
		return instance();
	}

	/**
	 * REFRESH句を追加します
	 * 
	 * @return this
	 */
	public T refresh() {
		appendElement("REFRESH");
		return instance();
	}

	/**
	 * REMOVE句を追加します
	 * 
	 * @return this
	 */
	public T remove() {
		appendElement("REMOVE");
		return instance();
	}

	/**
	 * IF NOT EXISTS句を追加します。
	 * 
	 * @return this
	 */
	public T ifNotExists() {
		appendElement("IF NOT EXISTS");
		return instance();
	}

	/**
	 * 条件を満たす場合に、IF NOT EXISTS句を追加します。
	 * 
	 * @param condition
	 * @return this
	 */
	public T ifNotExists(final boolean condition) {
		if (!condition) {
			return instance();
		}
		appendElement("IF NOT EXISTS");
		return instance();
	}

	/**
	 * 条件を満たす場合に、CONCURRENTLY句を追加します。
	 * 
	 * @param condition
	 * @return this
	 */
	public T concurrently(final boolean condition) {
		if (!condition) {
			return instance();
		}
		appendElement("CONCURRENTLY");
		return instance();
	}

	/**
	 * NOT句を追加します
	 * 
	 */
	public T not() {
		appendElement("NOT");
		return instance();
	}

	/**
	 * NO句を追加します
	 * 
	 * @return this
	 */
	public T no() {
		appendElement("NO");
		return instance();
	}

	/**
	 * NEXT句を追加します
	 * 
	 * @return this
	 */
	public T next() {
		appendElement("NEXT");
		return instance();
	}

	/**
	 * NEXTVAL句を追加します
	 * 
	 * @return this
	 */
	public T nextval() {
		appendElement("NEXTVAL");
		return instance();
	}

	/**
	 * SQL句を追加します
	 * 
	 * @return this
	 */
	public T sql() {
		appendElement("SQL");
		return instance();
	}

	/**
	 * LANGUAGE句を追加します
	 * 
	 * @return this
	 */
	public T language() {
		appendElement("LANGUAGE");
		return instance();
	}

	/**
	 * LEFT句を追加します
	 * 
	 * @return this
	 */
	public T left() {
		appendElement("LEFT");
		return instance();
	}

	/**
	 * OUTER句を追加します
	 * 
	 */
	public T outer() {
		appendElement("OUTER");
		return instance();
	}

	/**
	 * RIGHT句を追加します
	 * 
	 */
	public T right() {
		appendElement("RIGHT");
		return instance();
	}

	/**
	 * EXTERNAL句を追加します
	 * 
	 */
	public T external() {
		appendElement("EXTERNAL");
		return instance();
	}

	/**
	 * NAME句を追加します
	 * 
	 */
	public T name() {
		appendElement("NAME");
		return instance();
	}

	/**
	 * NOT NULLを追加します
	 * 
	 */
	public T notNull() {
		return notNull(true);
	}

	/**
	 * NOT NULLを追加します
	 * 
	 */
	public T notNull(final boolean bool) {
		if (bool) {
			appendElement("NOT NULL");
		}
		return instance();
	}

	/**
	 * ENDを追加します
	 * 
	 */
	public T end(final boolean bool) {
		if (bool) {
			appendElement("END");
		}
		return instance();
	}

	/**
	 * BEGINを追加します
	 * 
	 */
	public T begin(final boolean bool) {
		if (bool) {
			appendElement("BEGIN");
		}
		return instance();
	}

	/**
	 * BEGINを追加します
	 * 
	 */
	public T begin() {
		return begin(true);
	}

	/**
	 * ENDを追加します
	 * 
	 */
	public T end() {
		return end(true);
	}

	/**
	 * =句を追加します
	 * 
	 */
	public T eq() {
		appendElement("=");
		return instance();
	}

	/**
	 * COMPRESSED句を追加します
	 * 
	 */
	public T compressed() {
		appendElement("COMPRESSED");
		return instance();
	}

	/**
	 * COMPRESS句を追加します
	 * 
	 */
	public T compress() {
		appendElement("COMPRESS");
		return instance();
	}

	/**
	 * COMPRESSION句を追加します
	 * 
	 */
	public T compression() {
		appendElement("COMPRESSION");
		return instance();
	}

	/**
	 * YES句を追加します
	 * 
	 */
	public T yes() {
		appendElement("YES");
		return instance();
	}

	/**
	 * &gt;句を追加します
	 * 
	 */
	public T gt() {
		appendElement(">");
		return instance();
	}

	/**
	 * &gt;句を追加します
	 * 
	 */
	public T gte() {
		appendElement(">=");
		return instance();
	}

	/**
	 * &lt;=句を追加します
	 * 
	 */
	public T lt() {
		appendElement("<");
		return instance();
	}

	/**
	 * &lt;=句を追加します
	 * 
	 */
	public T lte() {
		appendElement("<=");
		return instance();
	}

	/**
	 * INHERITS句を追加します
	 * 
	 */
	public T inherits() {
		appendElement("INHERITS");
		return instance();
	}

	/**
	 * EXISTS句を追加します
	 * 
	 */
	public T exists() {
		appendElement("EXISTS");
		return instance();
	}

	/**
	 * SET句を追加します
	 * 
	 * @return this
	 */
	public T set() {
		appendElement("SET");
		return instance();
	}

	/**
	 * SET句を追加します
	 * 
	 * @param condition <code>true</code>の場合のみSET区を追加します。
	 * @return this
	 */
	public T set(final boolean condition) {
		if (condition) {
			set();
		}
		return instance();
	}

	/**
	 * SET NULL句を追加します
	 * 
	 * @return this
	 */
	public T setNull() {
		appendElement("SET NULL");
		return instance();
	}

	/**
	 * SET NOT NULL句を追加します
	 * 
	 * @return this
	 */
	public T setNotNull() {
		appendElement("SET NOT NULL");
		return instance();
	}

	/**
	 * SET DEFAULT句を追加します
	 * 
	 */
	public T setDefault() {
		appendElement("SET DEFAULT");
		return instance();
	}

	/**
	 * name=value形式の値を追加します
	 * 
	 */
	public T property(final String name, final String value) {
		if (value != null) {
			appendElement(name).eq()._add(value);
		}
		return instance();
	}

	/**
	 * name=value形式の値を追加します
	 * 
	 */
	public T property(final String name, final Object value) {
		return property(name, Converters.getDefault().convertString(value));
	}

	/**
	 * 条件がtrueの場合にname=value形式の値を追加します
	 * 
	 * @param condition
	 */
	public T property(final String name, final String value, final boolean condition) {
		if (condition) {
			return property(name, Converters.getDefault().convertString(value));
		}
		return instance();
	}

	/**
	 * 条件がtrueの場合にname=value形式の値を追加します
	 * 
	 * @param condition
	 */
	public T property(final String name, final Object value, final boolean condition) {
		if (condition) {
			return property(name, Converters.getDefault().convertString(value));
		}
		return instance();
	}

	/**
	 * DIMENSION句を追加します
	 * 
	 */
	public T dimension() {
		appendElement("DIMENSION");
		return instance();
	}

	/**
	 * DOMAIN句を追加します
	 * 
	 */
	public T domain() {
		appendElement("DOMAIN");
		return instance();
	}

	/**
	 * EVENT句を追加します
	 * 
	 */
	public T event() {
		appendElement("EVENT");
		return instance();
	}

	/**
	 * FUNCTION句を追加します
	 * 
	 */
	public T function() {
		appendElement("FUNCTION");
		return instance();
	}

	/**
	 * PROCEDURE句を追加します
	 * 
	 */
	public T procedure() {
		appendElement("PROCEDURE");
		return instance();
	}

	/**
	 * ROLE句を追加します
	 * 
	 */
	public T role() {
		appendElement("ROLE");
		return instance();
	}

	/**
	 * SEQUENCE句を追加します
	 * 
	 */
	public T sequence() {
		appendElement("SEQUENCE");
		return instance();
	}

	/**
	 * TYPE句を追加します
	 * 
	 */
	public T type() {
		appendElement("TYPE");
		return instance();
	}

	/**
	 * BODY句を追加します
	 * 
	 */
	public T body() {
		appendElement("BODY");
		return instance();
	}

	/**
	 * add INCLUSIVE
	 * 
	 */
	public T inclusive() {
		return inclusive(true);
	}

	/**
	 * add INCLUSIVE
	 * 
	 */
	public T inclusive(final boolean bool) {
		if (bool) {
			appendElement("INCLUSIVE");
		}
		return instance();
	}

	/**
	 * add ExCLUSIVE
	 * 
	 */
	public T exclusive() {
		return exclusive(true);
	}

	/**
	 * add ExCLUSIVE
	 * 
	 */
	public T exclusive(final boolean bool) {
		if (bool) {
			appendElement("ExCLUSIVE");
		}
		return instance();
	}

	/**
	 * WHEN句を追加します
	 * 
	 */
	public T when() {
		appendElement("WHEN");
		return instance();
	}

	/**
	 * MATCHED句を追加します
	 * 
	 */
	public T matched() {
		appendElement("MATCHED");
		return instance();
	}

	/**
	 * THEN句を追加します
	 * 
	 */
	public T then() {
		appendElement("THEN");
		return instance();
	}

	/**
	 * USER句を追加します
	 * 
	 */
	public T user() {
		appendElement("USER");
		return instance();
	}

	/**
	 * GENERATED句を追加します
	 * 
	 */
	public T generated() {
		appendElement("GENERATED");
		return instance();
	}

	/**
	 * CLUSTERED句を追加します
	 * 
	 */
	public T clustered() {
		appendElement("CLUSTERED");
		return instance();
	}

	/**
	 * ALWAYS句を追加します
	 * 
	 */
	public T always() {
		appendElement("ALWAYS");
		return instance();
	}

	/**
	 * IDENTITY句を追加します
	 * 
	 */
	public T identity() {
		appendElement("IDENTITY");
		return instance();
	}

	/**
	 * ID句を追加します
	 * 
	 */
	public T id() {
		appendElement("ID");
		return instance();
	}

	/**
	 * COLLATE句を追加します
	 * 
	 */
	public T collate() {
		appendElement("COLLATE");
		return instance();
	}

	/**
	 * COALESCE句を追加します
	 * 
	 * @return this
	 */
	public T coalesce() {
		appendElement("COALESCE");
		return instance();
	}

	/**
	 * COALESCE句を追加します
	 * 
	 */
	public T coalesce(final Runnable run) {
		coalesce();
		brackets(run);
		return instance();
	}

	/**
	 * CHARACTER SET句を追加します
	 * 
	 */
	public T characterSet() {
		appendElement("CHARACTER SET");
		return instance();
	}

	/**
	 * COLLATION句を追加します
	 * 
	 */
	public T collation() {
		appendElement("COLLATION");
		return instance();
	}

	/**
	 * FOR EACH句を追加します
	 * 
	 */
	public T forEach() {
		appendElement("FOR EACH");
		return instance();
	}

	/**
	 * ROW句を追加します
	 * 
	 */
	public T row() {
		return row(true);
	}

	/**
	 * 条件がtrueの場合にROW区を追加します
	 * 
	 * @param bool 条件
	 */
	public T row(final boolean bool) {
		if (!bool) {
			return instance();
		}
		appendElement("ROW");
		return instance();
	}

	/**
	 * USING句を追加します
	 * 
	 */
	public T using() {
		appendElement("USING");
		return instance();
	}

	/**
	 * ROWS句を追加します
	 * 
	 */
	public T rows() {
		appendElement("ROWS");
		return instance();
	}

	/**
	 * UNBOUNDED句を追加します
	 * 
	 */
	public T unbounded() {
		appendElement("UNBOUNDED");
		return instance();
	}

	/**
	 * PRECEDING句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T preceding() {
		appendElement("PRECEDING");
		return instance();
	}

	/**
	 * FOLLOWING句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T following() {
		appendElement("FOLLOWING");
		return instance();
	}

	/**
	 * CURRENT句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T current() {
		appendElement("CURRENT");
		return instance();
	}

	/**
	 * FROM Sysdummy句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T fromSysDummy() {
		if (this.getDialect().getSelectDummyTableName() != null) {
			appendElement("FROM " + this.getDialect().getSelectDummyTableName());
		}
		return instance();
	}

	/**
	 * trueを追加します
	 * 
	 * @return this
	 * 
	 */
	public T true_() {
		appendElement("1=1");
		return instance();
	}

	/**
	 * falseを追加します
	 * 
	 * @return this
	 * 
	 */
	public T false_() {
		appendElement("1=0");
		return instance();
	}

	/**
	 * 値と行を追加します
	 * 
	 * @return this
	 * 
	 */
	public T lineBreak(final char value) {
		_add(value);
		return lineBreak();
	}

	/**
	 * 値と行を追加します
	 * 
	 * @return this
	 * 
	 */
	public T lineBreak(final String value) {
		_add(value);
		return lineBreak();
	}

	/**
	 * カンマを追加します
	 * 
	 * @return this
	 * 
	 */
	public T comma() {
		_add(COMMA);
		if (this.isAppendAutoSpace()) {
			space();
		}
		return instance();
	}

	/**
	 * 引数の条件がtrueの場合のみカンマを追加します
	 * 
	 * @param condition
	 * @return this
	 */
	public T comma(final boolean condition) {
		if (condition) {
			comma();
		}
		return instance();
	}

	/**
	 * セミコロンを追加します
	 * 
	 * @return this
	 * 
	 */
	public T semicolon() {
		_add(';');
		return instance();
	}

	/**
	 * 引数の条件がtrueの場合のみセミコロンを追加します
	 * 
	 * @return this
	 * 
	 */
	public T semicolon(final boolean condition) {
		if (condition) {
			comma();
		}
		return instance();
	}

	private final Map<String, Boolean> conditionMap = new HashMap<String, Boolean>();

	private static final String COMMA = ",";

	private boolean firstElement = true;

	/**
	 * @return the firstElement
	 */
	public boolean isFirstElement() {
		return firstElement;
	}

	/**
	 * @param firstElement the firstElement to set
	 * @return this
	 */
	public T setFirstElement(final boolean firstElement) {
		this.firstElement = firstElement;
		return instance();
	}

	/**
	 * 条件を設定します
	 * 
	 * @param condition
	 * @return this
	 */
	public T setCondition(final String name, final boolean condition) {
		this.conditionMap.put(name, condition);
		return instance();
	}

	/**
	 * 条件を取得します
	 * 
	 * @return this
	 */
	public boolean getCondition(final String name) {
		final Boolean bool = conditionMap.get(name);
		if (bool == null) {
			return false;
		}
		return bool;
	}

	/**
	 * LESS THAN句を追加します
	 * 
	 * @return this
	 */
	public T lessThan() {
		appendElement("LESS THAN");
		return instance();
	}

	/**
	 * IN句を追加します
	 * 
	 * @return this
	 */
	public T in() {
		appendElement("IN");
		return instance();
	}

	/**
	 * UNION句を追加します
	 * 
	 * @return this
	 */
	public T union() {
		appendElement("UNION");
		return instance();
	}

	/**
	 * ALL句を追加します
	 * 
	 * @return this
	 */
	public T all() {
		appendElement("ALL");
		return instance();
	}

	/**
	 * 条件を満たすときにALL句を追加します
	 * 
	 * @param condition
	 * @return this
	 */
	public T all(final boolean condition) {
		if (condition) {
			return all();
		}
		return instance();
	}

	/**
	 * RETURNS句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T returns() {
		appendElement("RETURNS");
		return instance();
	}

	/**
	 * DETERMINISTIC句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T deterministic() {
		appendElement("DETERMINISTIC");
		return instance();
	}

	/**
	 * DYNAMIC句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T dynamic() {
		appendElement("DYNAMIC");
		return instance();
	}

	/**
	 * RESULT句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T result() {
		appendElement("RESULT");
		return instance();
	}

	/**
	 * ADD JOIN
	 * 
	 * @return this
	 * 
	 */
	public T join() {
		appendElement("JOIN");
		return instance();
	}

	/**
	 * SETS句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T sets() {
		appendElement("SETS");
		return instance();
	}

	/**
	 * TABLESPACE句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T tablespace() {
		appendElement("TABLESPACE");
		return instance();
	}

	/**
	 * COMMENT句を追加します
	 * 
	 * @return this
	 */
	public T comment() {
		appendElement("COMMENT");
		return instance();
	}

	/**
	 * OUTPUT句を追加します
	 * 
	 * @return this
	 */
	public T output() {
		appendElement("OUTPUT");
		return instance();
	}

	/**
	 * ENGINE句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T engine() {
		appendElement("ENGINE");
		return instance();
	}

	/**
	 * EXECUTE句を追加します
	 * 
	 * @return this
	 * 
	 */
	public T execute() {
		appendElement("EXECUTE");
		return instance();
	}

	/**
	 * スペースを追加します
	 * 
	 */
	public T space() {
		_add(' ');
		return instance();
	}

	/**
	 * ADD ASC
	 * 
	 */
	public T asc() {
		_add("ASC");
		return instance();
	}

	/**
	 * ADD DESC
	 * 
	 */
	public T desc() {
		_add("DESC");
		return instance();
	}

	/**
	 * 引数で与えた条件がtrueの場合にスペースを追加します
	 * 
	 * @param bool 条件
	 */
	public T space(final boolean bool) {
		if (bool) {
			space();
		}
		return instance();
	}

	/**
	 * 指定した長さのスペースを追加します
	 * 
	 * @param len 追加するスペースの数
	 */
	public T space(final int len) {
		for (int i = 0; i < len; i++) {
			space();
		}
		return instance();
	}

	/**
	 * 引数で与えた条件がtrueの場合に指定した長さのスペースを追加します
	 * 
	 * @param len  追加するスペースの数
	 * @param bool
	 */
	public T space(final int len, final boolean bool) {
		if (bool) {
			space(len);
		}
		return instance();
	}

	/**
	 * LIKEを追加します
	 * 
	 */
	public T like() {
		appendElement("LIKE");
		return instance();
	}

	/**
	 * 改行を追加します。
	 * 
	 */
	public T lineBreak() {
		return lineBreak(true);
	}

	/**
	 * 条件がtrueの場合に改行を追加します。
	 * 
	 * @param bool
	 */
	public T lineBreak(final boolean bool) {
		if (!bool) {
			return instance();
		}
		_add('\n');
		return indent();
	}

	/**
	 * 条件がtrueの場合に処理を実効します。
	 * 
	 * @param bool trueの場合にrunを実行する
	 * @param run  実行する内容
	 */
	public T _add(final boolean bool, Runnable run) {
		if (!bool) {
			return instance();
		}
		run.run();
		return instance();
	}

	protected T indent() {
		for (int i = 0; i < this.getIndentSize(); i++) {
			_add(indentString);
		}
		return instance();
	}

	/**
	 * 値の追加を行います
	 * 
	 * @param value
	 */
	public T _add(final char value) {
		builder.append(value);
		return instance();
	}

	/**
	 * 条件を満たす場合に値の追加を行います
	 * 
	 * @param value
	 * @param condition
	 */
	public T _add(final Object value, final boolean condition) {
		if (condition) {
			_add(Converters.getDefault().convertString(value));
		}
		return instance();
	}

	/**
	 * 条件を満たす場合にインデックスタイプの追加を行います
	 * 
	 * @param value
	 * @param condition
	 */
	public T _add(final IndexType value, final boolean condition) {
		if (condition) {
			if (IndexType.BTree != value) {
				_add(value.toString());
			}
		}
		return instance();
	}

	/**
	 * SQL CHAR値の追加を行います
	 * 
	 * @param value
	 */
	public T sqlChar(final String value) {
		if (value != null) {
			_add("'")._add(CommonUtils.unwrap(value, "'").replace("'", "''"))._add("'");
		}
		return instance();
	}

	/**
	 * SQL CHAR値の追加を行います
	 * 
	 * @param values
	 */
	public T sqlChar(final String... values) {
		if (isEmpty(values)) {
			return instance();
		}
		for (int i = 0; i < values.length; i++) {
			comma(i > 0).sqlChar(values[i]);
		}
		return instance();
	}

	/**
	 * SQL CHAR値の追加を行います
	 * 
	 * @param values
	 */
	public T sqlChar(final Collection<String> values) {
		if (isEmpty(values)) {
			return instance();
		}
		int i = 0;
		for (final String value : values) {
			comma(i > 0).sqlChar(value);
			i++;
		}
		return instance();
	}

	public T lockMode(final TableLockMode tableLockMode) {
		if (tableLockMode != null) {
			appendElement(tableLockMode.toString());
		}
		return instance();
	}

	public T mode() {
		appendElement("MODE");
		return instance();
	}

	/**
	 * 条件を満たす場合にSQL CHAR値の追加を行います
	 * 
	 * @param value     追加する値
	 * @param condition 条件
	 */
	public T sqlChar(final String value, final boolean condition) {
		if (condition) {
			sqlChar(value);
		}
		return instance();
	}

	/**
	 * SQL NCHAR文字列値の追加を行います
	 * 
	 * @param value 追加する値
	 */
	public T sqlNchar(final String value) {
		if (value != null) {
			_add("N'")._add(CommonUtils.unwrap(value, "'"))._add("'");
		}
		return instance();
	}

	/**
	 * SQL NCHAR値の追加を行います
	 * 
	 * @param values 追加する値
	 */
	public T sqlNchar(final String... values) {
		if (isEmpty(values)) {
			return instance();
		}
		for (int i = 0; i < values.length; i++) {
			comma(i > 0).sqlNchar(values[i]);
		}
		return instance();
	}

	/**
	 * 条件を満たす場合にSQL NCHAR文字列値の追加を行います
	 * 
	 * @param value     追加する値
	 * @param condition 条件
	 */
	public T sqlNchar(final String value, final boolean condition) {
		if (condition) {
			sqlNchar(value);
		}
		return instance();
	}

	/**
	 * SQL CHAR値の追加を行います
	 * 
	 * @param values 追加する値
	 */
	public T sqlNchar(final Collection<String> values) {
		if (isEmpty(values)) {
			return instance();
		}
		int i = 0;
		for (final String value : values) {
			comma(i > 0).sqlNchar(value);
			i++;
		}
		return instance();
	}

	/**
	 * 値の追加を行います
	 * 
	 * @param value 追加する値
	 */
	public T _add(final Object value) {
		if (value != null) {
			builder.append(value.toString());
		}
		return instance();
	}

	/**
	 * 値の追加を行います
	 * 
	 * @param value 追加する値
	 */
	public T plus(final char value) {
		return _add(value);
	}

	/**
	 * 値の追加を行います
	 * 
	 * @param value 追加する値
	 */
	public T plus(final Object value) {
		return _add(value);
	}

	/**
	 * 値の追加を行います
	 * 
	 * @param values 追加する値
	 */
	public T _add(final Collection<String> values) {
		return _add("\n", values);
	}

	/**
	 * 値の追加を行います
	 * 
	 * @param separator 値のセパレーター
	 * @param values    追加する値
	 */
	public T _add(final String separator, final Collection<String> values) {
		if (values != null) {
			final boolean needsIndent = "\n".equals(separator);
			boolean first = true;
			for (final Object obj : values) {
				if (!first) {
					_add(separator);
					if (needsIndent) {
						indent();
					}
				} else {
					first = false;
				}
				_add(obj);
			}
		}
		return instance();
	}

	/**
	 * 値の追加を行います
	 * 
	 * @param value 追加する値
	 */
	public T _add(final String value) {
		if (value != null) {
			builder.append(value);
		}
		return instance();
	}

	/**
	 * ()で囲って値の追加を行います
	 * 
	 * @param run
	 */
	public T brackets(final Runnable run) {
		return brackets("(", run, ")");
	}

	/**
	 * ()で囲って値の追加を行います
	 * 
	 * @param indent インデントの有無
	 * @param run
	 */
	public T brackets(final boolean indent, final Runnable run) {
		return brackets(indent, "(", run, ")");
	}

	/**
	 * start,endで囲って値の追加を行います
	 * 
	 * @param run
	 */
	public T brackets(final String start, final Runnable run, final String end) {
		return brackets(false, start, run, end);
	}

	/**
	 * start,endで囲って値の追加を行います
	 * 
	 * @param indent indent
	 * @param start  start bracket
	 * @param run
	 * @param end    end bracket
	 */
	public T brackets(final boolean indent, final String start, final Runnable run, final String end) {
		if (indent) {
			_add(start);
			indent(() -> {
				lineBreak();
				run.run();
			});
			lineBreak();
			_add(end);
			return instance();
		} else {
			_add(start);
			run.run();
			if (!endsWithSpace()) {
				space();
			}
			_add(end);
			return instance();
		}
	}

	private boolean endsWithSpace() {
		if (builder.length() == 0) {
			return false;
		}
		final char c = builder.charAt(builder.length() - 1);
		return c == ' ' || c == '\n' || c == '\t';
	}

	/**
	 * 値の追加を行います
	 * 
	 * @param open  open
	 * @param run   run
	 * @param close close
	 * 
	 */
	public T _add(final String open, final Runnable r, final String close) {
		_add(open);
		r.run();
		_add(close);
		return instance();
	}

	/**
	 * 値の追加を行います
	 * 
	 * @param open  open
	 * @param r     run
	 * @param close close
	 * 
	 */
	public T _add(final char open, final Runnable r, final char close) {
		_add(open);
		r.run();
		_add(close);
		return instance();
	}

	/**
	 * 値の追加を行います
	 * 
	 * @param value
	 */
	public T _add(final EnumProperties value) {
		if (value != null) {
			builder.append(value.getSqlValue());
		}
		return instance();
	}

	public T addTypeDefinition(final Column column) {
		typeDefinition(column);
		return instance();
	}

	/**
	 * カラム作成時の定義を追加します
	 * 
	 * @param column カラム
	 */
	public T definition(final Column column) {
		return definition(column, true);
	}

	/**
	 * カラム作成時の定義を追加します
	 * 
	 * @param column      カラム
	 * @param withRemarks
	 */
	public T definition(final Column column, final boolean withRemarks) {
		if (column.getDataType() == DataType.DOMAIN) {
			this._add(column.getDataTypeName());
		} else {
			typeDefinition(column);
			characterSetDefinition(column);
			collateDefinition(column);
		}
		if (!column.isIdentity()) {
			if (!CommonUtils.isEmpty(column.getDefaultValue())) {
				defaultDefinition(column);
			}
		}
		notNullDefinition(column);
		if (column.isIdentity()) {
			autoIncrement(column);
		}
		if (!CommonUtils.isEmpty(column.getOnUpdate())) {
			onUpdateDefinition(column);
		}
		if (!CommonUtils.isEmpty(column.getCheck())) {
			checkConstraintDefinition(column);
		}
		if (withRemarks) {
			if (!CommonUtils.isEmpty(column.getRemarks())) {
				comment(column);
			}
		}
		return instance();
	}

	protected void onUpdateDefinition(final Column column) {

	}

	/**
	 * カラムのデフォルト型定義を追加します
	 * 
	 * @param column
	 */
	protected T defaultDefinition(final Column column) {
		if (column.getDefaultValue() == null) {
			return instance();
		}
		default_();
		space();
		_add(column.getDefaultValue());
		return instance();
	}

	/**
	 * カラム変更時の定義を追加します
	 * 
	 * @param column カラム
	 */
	public T definitionForAlterColumn(final Column column) {
		if (column.getDataType() == DataType.DOMAIN) {
			this._add(column.getDataTypeName());
		} else {
			typeDefinition(column);
			characterSetDefinition(column);
			collateDefinition(column);
		}
		if (!column.isIdentity()) {
			if (!CommonUtils.isEmpty(column.getDefaultValue())) {
				defaultDefinitionForAlter(column);
			}
		}
		notNullDefinitionForAlter(column);
		if (column.isIdentity()) {
			autoIncrement(column);
		}
		if (column.getCheck() != null) {
			checkConstraintDefinition(column);
		}
		if (!CommonUtils.isEmpty(column.getRemarks())) {
			comment(column);
		}
		return instance();
	}

	/**
	 * 変更時のカラムのデフォルト型定義を追加します
	 * 
	 * @param column
	 */
	protected T defaultDefinitionForAlter(final Column column) {
		return defaultDefinition(column);
	}

	/**
	 * 変更時のカラムのNOT NULL定義を追加します
	 * 
	 * @param column
	 */
	protected T notNullDefinitionForAlter(final Column column) {
		return notNullDefinition(column);
	}

	/**
	 * カラムのNOT NULL定義を追加します
	 * 
	 * @param column
	 */
	protected T notNullDefinition(final Column column) {
		if (!column.isIdentity()) {
			if (column.isNotNull()) {
				space().notNull();
			}
		}
		return instance();
	}

	/**
	 * カラムの型の定義を追加します
	 * 
	 * @param column カラム
	 */
	protected T typeDefinition(final Column column) {
		DbDataType<?> dbDataType = null;
		if (column.getLength() != null) {
			dbDataType = this.getDialect().getDbDataTypes().getDbType(column.getDataType(), column.getLength());
		} else {
			dbDataType = this.getDialect().getDbDataTypes().getDbType(column.getDataType());
		}
		if (column.getDataType() == DataType.ENUM || column.getDataType() == DataType.SET) {
			_add(column.getDataType())._add("(").sqlChar(column.getValues())._add(")");
		} else if (column.getDataType() != DataType.OTHER) {
			Long len = null;
			if (dbDataType instanceof LengthProperties) {
				len = ((LengthProperties<?>) dbDataType).getLength(column.getLength());
			} else if (dbDataType instanceof PrecisionProperties) {
				len = ((PrecisionProperties<?>) dbDataType).getPrecision(column.getLength()).longValue();
			}
			Integer scale = column.getScale();
			if (dbDataType instanceof ScaleProperties) {
				scale = ((ScaleProperties<?>) dbDataType).getScale(scale);
			}
			if (dbDataType != null) {
				String def = dbDataType.getColumCreateDefinition(len, scale);
				if (dbDataType.getSupportCharacterSemantics().size() > 1 && column.getCharacterSemantics() != null) {
					def = def.replace(")", " " + column.getCharacterSemantics() + ")");
				}
				this._add(def);
			} else {
				this._add(column.getDataTypeName());
			}
		} else {
			this._add(column.getDataTypeName());
		}
		return instance();
	}

	/**
	 * カラムの型の定義を追加します
	 * 
	 */
	public T typeDefinition(final DataType type, final String dataTypeName, final Number maxlength,
			final Integer scale) {
		final Column column = new Column();
		column.setDataType(type);
		column.setDataTypeName(dataTypeName);
		column.setLength(maxlength);
		column.setScale(scale);
		typeDefinition(column);
		return instance();
	}

	/**
	 * カラムの型の定義を追加します
	 * 
	 * @param column カラム
	 */
	protected T typeDefinition(final AbstractColumn<?> column) {
		return typeDefinition(column.getDataType(), column.getDataTypeName(),
				CommonUtils.notZero(column.getLength(), column.getOctetLength()), column.getScale());
	}

	/**
	 * カラムの型の定義を追加します
	 * 
	 * @param column カラム
	 */
	protected T typeDefinition(final DataTypeSetProperties<?> column) {
		return typeDefinition(column.getDataType(), column.getDataTypeName(),
				CommonUtils.notZero(column.getLength(), column.getOctetLength()), column.getScale());
	}

	/**
	 * カラムの型の定義を追加します
	 * 
	 * @param column カラム
	 */
	public T typeDefinition(final DataTypeProperties<?> column) {
		return typeDefinition(column.getDataType(), column.getDataTypeName(), null, null);
	}

	/**
	 * カラムの型の定義を追加します
	 * 
	 * @param column カラム
	 */
	protected T typeDefinition(final FunctionReturning column) {
		return typeDefinition(column.getDataType(), column.getDataTypeName(),
				CommonUtils.notZero(column.getLength(), column.getOctetLength()), column.getScale());
	}

	/**
	 * カラムのCharacter Setの定義を追加します
	 * 
	 * @param column カラム
	 */
	protected T characterSetDefinition(final Column column) {
		return instance();
	}

	/**
	 * カラムのColationの定義を追加します
	 * 
	 * @param column カラム
	 */
	protected T collateDefinition(final Column column) {
		return instance();
	}

	/**
	 * カラムのチェック制約の定義を追加します
	 * 
	 * @param column カラム
	 */
	protected T checkConstraintDefinition(final Column column) {
		check().space()._add("(")._add(column.getCheck())._add(")");
		return instance();
	}

	protected T autoIncrement(final AbstractColumn<?> column) {
		generated().always().as().identity();
		return instance();
	}

	protected T comment(final AbstractColumn<?> column) {
		return instance();
	}

	protected T appendElement(final String value) {
		if (isEmpty(value)) {
			return instance();
		}
		if (builder.length() != 0) {
			final char c = builder.charAt(builder.length() - 1);
			if (c == ' ' || c == '\t' || c == '\n' || c == '/' || c == '.') {
				_add(value);
				return instance();
			}
			if (isAppendAutoSpace()) {
				space(builder.length() > 0);
			}
		}
		_add(value);
		return instance();
	}

	protected int getIndentSize() {
		return indentSize;
	}

	public T appendIndent(final int indentSize) {
		this.indentSize = this.indentSize + indentSize;
		return instance();
	}

	public T indent(final Runnable run) {
		this.appendIndent(+1);
		try {
			run.run();
		} finally {
			this.appendIndent(-1);
		}
		return instance();
	}

	/**
	 * @return the indentString
	 */
	public String getIndentString() {
		return indentString;
	}

	/**
	 * @param indentString the indentString to set
	 */
	public T setIndentString(final String indentString) {
		this.indentString = indentString;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return builder.toString();
	}

	/**
	 * @return the dialect
	 */
	public Dialect getDialect() {
		return dialect;
	}

	/**
	 * 引数コレクションを追加します
	 * 
	 * @param arguments
	 */
	public T arguments(final NamedArgumentCollection<?> arguments) {
		return arguments("(", arguments, ")", ", ");
	}

	/**
	 * 引数コレクションを追加します
	 * 
	 * @param arguments
	 */
	public T arguments(final String start, final NamedArgumentCollection<?> arguments, final String end,
			final String separator) {
		this._add(start);
		boolean first = true;
		for (final NamedArgument argument : arguments) {
			if (!first) {
				this._add(separator);
			} else {
				first = false;
			}
			argument(argument);
		}
		this._add(end);
		return instance();
	}

	/**
	 * 引数を追加します
	 * 
	 * @param obj
	 */
	public T argument(final NamedArgument obj) {
		argumentBefore(obj);
		if (obj.getName() != null) {
			this._add(obj.getName());
			this.space();
		}
		this.typeDefinition(obj);
		if (obj.getDirection() != null && obj.getDirection() != ParameterDirection.Input) {
			this.space()._add(obj.getDirection());
		}
		argumentAfter(obj);
		return instance();
	}

	protected void argumentBefore(final NamedArgument obj) {
		argumentDirection(obj);
	}

	protected void argumentAfter(final NamedArgument obj) {
	}

	protected void argumentDirection(final NamedArgument obj) {
		if (obj.getDirection() != null && obj.getDirection() != ParameterDirection.Input) {
			this._add(obj.getDirection());
			this.space();
		}
	}

	/**
	 * RETURNINGの型を追加します
	 * 
	 * @param argument
	 */
	public T _add(final FunctionReturning argument) {
		this.typeDefinition(argument);
		return this.instance();
	}

	/**
	 * Foreign Key制約のMATCH OPTIONを追加します
	 * 
	 * @param constraint
	 */
	public T matchOption(final ForeignKeyConstraint constraint) {
		if (constraint.getMatchOption() != null) {
			match().space()._add(constraint.getMatchOption());
		}
		return instance();
	}

	/**
	 * 外部キー制約のカスケードルールを追加します。
	 * 
	 * @param constraint
	 */
	public T cascadeRule(final ForeignKeyConstraint constraint) {
		if (constraint.getDeleteRule() != null) {
			space().on().space().delete().space()._add(constraint.getDeleteRule());
		}
		if (constraint.getUpdateRule() != null) {
			space().on().space().update().space()._add(constraint.getUpdateRule());
		}
		return instance();
	}

	/**
	 * 条件がtrueの場合のみ処理を実行します。
	 * 
	 * @param condition
	 * @param r
	 */
	public T $if(final boolean condition, final Runnable r) {
		if (condition) {
			r.run();
		}
		return instance();
	}

	/**
	 * 条件がtrueの場合に処理1をそれ以外の場合に処理2を実行します。
	 * 
	 * @param condition 条件
	 * @param c1        処理1
	 * @param c2        処理2
	 */
	public T $if(final boolean condition, final Runnable c1, final Runnable c2) {
		if (condition) {
			c1.run();
		} else {
			c2.run();
		}
		return instance();
	}

	/**
	 * IN条件でプリペアードパラメーターを追加します。
	 * 
	 * @param columnName    カラム名
	 * @param parameterName パラメーター名
	 * @return this
	 */
	public T _parameterIn(final String columnName, final String parameterName) {
		return _parameterForIn(columnName, () -> {
			in();
		}, parameterName, "1");
	}

	private T _parameter(final String columnName, final Runnable run, final String parameterName, final String value) {
		this.appendElement(columnName).space();
		run.run();
		space();
		_add("/*" + parameterName + "*/" + value);
		return instance();
	}

	private T _parameterForIn(final String columnName, final Runnable run, final String parameterName,
			final String value) {
		this.appendElement(columnName).space();
		run.run();
		space();
		_add("/*" + parameterName + "*/(" + value + ")");
		return instance();
	}

	/**
	 * =条件でプリペアードパラメーターを追加します。
	 * 
	 * @param columnName    カラム名
	 * @param parameterName パラメーター名
	 * @return this
	 */
	public T _parameterEq(final String columnName, final String parameterName) {
		return _parameter(columnName, () -> {
			eq();
		}, parameterName, "'1'");
	}

	/**
	 * &gt;条件でプリペアードパラメーターを追加します。
	 * 
	 * @param columnName    カラム名
	 * @param parameterName パラメーター名
	 * @return this
	 */
	public T _parameterGt(final String columnName, final String parameterName) {
		return _parameter(columnName, () -> {
			gt();
		}, parameterName, "'1'");
	}

	/**
	 * &gt;=条件でプリペアードパラメーターを追加します。
	 * 
	 * @param columnName    カラム名
	 * @param parameterName パラメーター名
	 * @return this
	 */
	public T _parameterGte(final String columnName, final String parameterName) {
		return _parameter(columnName, () -> {
			gte();
		}, parameterName, "'1'");
	}

	/**
	 * &lt;条件でプリペアードパラメーターを追加します。
	 * 
	 * @param columnName    カラム名
	 * @param parameterName パラメーター名
	 * @return this
	 */
	public T _parameterLt(final String columnName, final String parameterName) {
		return _parameter(columnName, () -> {
			lt();
		}, parameterName, "'1'");
	}

	/**
	 * &lt;=条件でプリペアードパラメーターを追加します。
	 * 
	 * @param columnName    カラム名
	 * @param parameterName パラメーター名
	 * @return this
	 */
	public T _parameterLte(final String columnName, final String parameterName) {
		return _parameter(columnName, () -> {
			lte();
		}, parameterName, "'1'");
	}

	/**
	 * LIKE条件でプリペアードパラメーターを追加します。
	 * 
	 * @param columnName    カラム名
	 * @param parameterName パラメーター名
	 * @return this
	 */
	public T _parameterLike(final String columnName, final String parameterName) {
		return _parameter(columnName, () -> {
			like();
		}, parameterName, "'1'");
	}

	@Override
	@SuppressWarnings("unchecked")
	public AbstractSqlBuilder<T> clone() {
		AbstractSqlBuilder<T> clone;
		try {
			clone = (AbstractSqlBuilder<T>) super.clone();
			clone.builder = new StringBuilder(this.builder.length());
			clone.builder.append(this.builder.toString());
			return clone;
		} catch (final CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	public T mergeBuilder(final AbstractSqlBuilder<?> builder) {
		this.builder.append(builder.builder.toString());
		return instance();
	}

	public T addComment(final String value) {
		if (CommonUtils.isEmpty(value)) {
			return instance();
		}
		_add("/*");
		_add(value);
		_add("*/");
		return instance();
	}

	public T addLineComment(final String value) {
		if (CommonUtils.isEmpty(value)) {
			return instance();
		}
		_add("-- ");
		return instance();
	}

	public T clearBuilder() {
		this.builder = new StringBuilder();
		return instance();
	}
}
