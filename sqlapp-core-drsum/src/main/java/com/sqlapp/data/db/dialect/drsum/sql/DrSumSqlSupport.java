/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-drsum.
 *
 * sqlapp-core-drsum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-drsum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-drsum.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.drsum.sql;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.drsum.DrSum;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Shared Dr.Sum DDL rules.
 * <p>
 * Dr.Sum has no PRIMARY KEY. A primary key is translated to constraints that
 * enforce the same rule: a single-column key becomes the column constraints
 * {@code NOT NULL UNIQUE}; a multi-column key becomes
 * {@code COMPOUND KEY <name>(...) NOT NULL UNIQUE}. A single-column unique
 * constraint becomes the column constraint {@code UNIQUE} (Dr.Sum rejects a
 * table constraint on one column). FOREIGN KEY, CHECK and exclusion constraints
 * have no Dr.Sum equivalent and are rejected.
 */
public final class DrSumSqlSupport {

	private DrSumSqlSupport() {
	}

	/** Validates that a table can be created on Dr.Sum without losing meaning. */
	public static void validateTable(final Table table) {
		final String name = table.getName();
		if (table.getTableType() != null) {
			throw new IllegalArgumentException(
					"Dr.Sum does not support table type " + table.getTableType() + ". table=" + name);
		}
		if (table.getPartitioning() != null) {
			throw new IllegalArgumentException("Dr.Sum does not support table partitioning. table=" + name);
		}
		if (!table.getConstraints().getForeignKeyConstraints().isEmpty()) {
			throw new IllegalArgumentException(
					"Dr.Sum does not support FOREIGN KEY constraints; remove them from the table or use a Dr.Sum join index. table="
							+ name);
		}
		if (!table.getConstraints().getCheckConstraints().isEmpty()) {
			throw new IllegalArgumentException("Dr.Sum does not support CHECK constraints. table=" + name);
		}
		if (!table.getConstraints().getExcludeConstraints().isEmpty()) {
			throw new IllegalArgumentException("Dr.Sum does not support exclusion constraints. table=" + name);
		}
		if (table.getColumns().isEmpty()) {
			throw new IllegalArgumentException("Dr.Sum table requires at least one column. table=" + name);
		}
		for (final Column column : table.getColumns()) {
			validateColumn(column);
		}
		final Set<String> columnNames = new LinkedHashSet<>();
		for (final Column column : table.getColumns()) {
			columnNames.add(column.getName());
		}
		for (final UniqueConstraint constraint : table.getConstraints().getUniqueConstraints()) {
			validateUniqueConstraint(constraint, columnNames);
		}
		for (final Index index : table.getIndexes()) {
			validateIndex(index);
		}
	}

	static void validateColumn(final Column column) {
		final String name = column.getTableName() + "." + column.getName();
		if (column.isIdentity()) {
			throw new IllegalArgumentException("Dr.Sum does not support identity columns. column=" + name);
		}
		if (column.getFormula() != null) {
			throw new IllegalArgumentException("Dr.Sum does not support computed columns. column=" + name);
		}
		if (column.getCheck() != null) {
			throw new IllegalArgumentException("Dr.Sum does not support CHECK constraints. column=" + name);
		}
		if (column.getOnUpdate() != null) {
			throw new IllegalArgumentException("Dr.Sum does not support ON UPDATE. column=" + name);
		}
		if (column.getArrayDimension() > 0) {
			throw new IllegalArgumentException("Dr.Sum does not support array columns. column=" + name);
		}
		final DataType type = column.getDataType();
		if (type == null || type == DataType.OTHER) {
			throw new IllegalArgumentException("Dr.Sum column requires a known data type. column=" + name
					+ ", dataTypeName=" + column.getDataTypeName());
		}
		if (type.isCharacter() && column.getLength() != null
				&& column.getLength() > DrSum.MAX_VARCHAR_LENGTH) {
			throw new IllegalArgumentException("Dr.Sum VARCHAR length must not exceed " + DrSum.MAX_VARCHAR_LENGTH
					+ ". column=" + name + ", length=" + column.getLength());
		}
		if (type.isBinary()) {
			throw new IllegalArgumentException(
					"Dr.Sum has no binary data type. column=" + name + ", dataType=" + type);
		}
	}

	static void validateUniqueConstraint(final UniqueConstraint constraint, final Set<String> columnNames) {
		final int size = constraint.getColumns().size();
		if (size == 0) {
			throw new IllegalArgumentException("Dr.Sum key requires columns. constraint=" + constraint.getName());
		}
		if (size == 1) {
			return;
		}
		if (constraint.getName() == null || constraint.getName().isEmpty()) {
			throw new IllegalArgumentException(
					"Dr.Sum COMPOUND KEY requires a name for a multi-column key. table=" + constraint.getTableName());
		}
		if (columnNames.contains(constraint.getName())) {
			throw new IllegalArgumentException("Dr.Sum COMPOUND KEY name must differ from every column name. key="
					+ constraint.getName() + ", table=" + constraint.getTableName());
		}
	}

	static void validateIndex(final Index index) {
		if (index.isUnique()) {
			throw new IllegalArgumentException(
					"Dr.Sum search indexes cannot be unique; use a UNIQUE constraint instead. index=" + index.getName());
		}
		if (index.getColumns().size() != 1) {
			throw new IllegalArgumentException(
					"Dr.Sum search index must have exactly one column. index=" + index.getName());
		}
		if (index.getWhere() != null) {
			throw new IllegalArgumentException("Dr.Sum does not support partial indexes. index=" + index.getName());
		}
	}

	/** Whether the column is the single column of a unique or primary key. */
	static boolean isSingleColumnUnique(final Table table, final Column column) {
		for (final UniqueConstraint constraint : table.getConstraints().getUniqueConstraints()) {
			if (constraint.getColumns().size() == 1
					&& constraint.getColumns().get(0).getName().equals(column.getName())) {
				return true;
			}
		}
		return false;
	}

	/** Whether the column is the single column of a primary key. */
	static boolean isSingleColumnPrimaryKey(final Table table, final Column column) {
		for (final UniqueConstraint constraint : table.getConstraints().getUniqueConstraints()) {
			if (constraint.isPrimaryKey() && constraint.getColumns().size() == 1
					&& constraint.getColumns().get(0).getName().equals(column.getName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Appends the Dr.Sum type of the column. An unsized NUMERIC/DECIMAL stays
	 * unsized (a 33-digit floating NUMERIC in Dr.Sum), and TIME, TIMESTAMP and
	 * INTERVAL carry their fractional second digits only when specified.
	 */
	static void addType(final Column column, final AbstractSqlBuilder<?> builder) {
		final DataType type = column.getDataType();
		if ((type == DataType.NUMERIC || type == DataType.DECIMAL) && column.getLength() == null) {
			builder._add(type == DataType.DECIMAL ? "DECIMAL" : "NUMERIC");
			return;
		}
		if (type == DataType.FLOAT) {
			// FLOAT(p) has no Dr.Sum form; Dr.Sum FLOAT is REAL.
			builder._add("FLOAT");
			return;
		}
		final String fractional = fractionalType(type);
		if (fractional != null) {
			final Long digits = column.getLength();
			if (digits != null && (digits < 0 || digits > DrSum.MAX_FRACTIONAL_SECONDS)) {
				throw new IllegalArgumentException("Dr.Sum " + fractional
						+ " fractional second digits must be 0 to 6. column=" + column.getName() + ", digits=" + digits);
			}
			builder._add(fractional);
			if (digits != null) {
				builder._add("(")._add(String.valueOf(digits))._add(")");
			}
			return;
		}
		builder.typeDefinition(type, column.getDataTypeName(), column.getLength(), column.getScale());
	}

	private static String fractionalType(final DataType type) {
		if (type == DataType.TIME) {
			return "TIME";
		}
		if (type == DataType.TIMESTAMP || type == DataType.DATETIME || type == DataType.SMALLDATETIME) {
			return "TIMESTAMP";
		}
		if (type == DataType.INTERVAL || type == DataType.INTERVAL_DAY_TO_SECOND) {
			return "INTERVAL";
		}
		return null;
	}

	/**
	 * Appends {@code COMPOUND KEY name(col, ...) [NOT NULL UNIQUE | UNIQUE]} for a
	 * multi-column key.
	 */
	static void addCompoundKey(final UniqueConstraint constraint, final AbstractSqlBuilder<?> builder) {
		builder._add("COMPOUND KEY ").name(constraint.getName())._add("(");
		final List<ReferenceColumn> columns = constraint.getColumns();
		for (int i = 0; i < columns.size(); i++) {
			builder.comma(i > 0).name(columns.get(i).getName());
		}
		builder._add(")");
		builder._add(constraint.isPrimaryKey() ? " NOT NULL UNIQUE" : " UNIQUE");
	}

	/** Validates a Dr.Sum comment (at most 1,023 characters, no tab or newline). */
	static String validateComment(final String comment, final String target) {
		if (comment.length() > 1023) {
			throw new IllegalArgumentException(
					"Dr.Sum comment must not exceed 1023 characters. target=" + target);
		}
		if (comment.indexOf('\t') >= 0 || comment.indexOf('\n') >= 0 || comment.indexOf('\r') >= 0) {
			throw new IllegalArgumentException(
					"Dr.Sum comment must not contain tabs or line breaks. target=" + target);
		}
		return comment;
	}
}
