/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-spanner.
 *
 * sqlapp-core-spanner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-spanner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-spanner.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.spanner.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.ProductVersionInfo;

/**
 * Spanner Column Reader
 * 
 * @author satoh
 * 
 */
public class SpannerColumnReader extends ColumnReader {
	private static final Pattern VECTOR_TYPE = Pattern.compile(
			"ARRAY\\s*<\\s*(FLOAT32|FLOAT64)\\s*>\\s*\\(\\s*vector_length\\s*=>\\s*(-?[0-9]+)\\s*\\)",
			Pattern.CASE_INSENSITIVE);

	public SpannerColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Column> result = list();
		final Set<List<String>> identities = new HashSet<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Column obj = createColumn(rs);
				if (!identities.add(
						Arrays.asList(obj.getCatalogName(), obj.getSchemaName(), obj.getTableName(), obj.getName())))
					throw new IllegalArgumentException(
							"Duplicate Spanner column metadata: " + obj.getTableName() + "." + obj.getName());
				result.add(obj);
			}
		});
		return result;
	}

	protected Column createColumn(ExResultSet rs) throws SQLException {
		Column obj = new Column(getString(rs, COLUMN_NAME));
		obj.setCatalogName(getString(rs, TABLE_CATALOG));
		obj.setSchemaName(getString(rs, TABLE_SCHEMA));
		obj.setTableName(getString(rs, TABLE_NAME));
		if (obj.getName() == null || obj.getName().isBlank() || obj.getTableName() == null
				|| obj.getTableName().isBlank())
			throw new IllegalArgumentException("Spanner column metadata requires table and column names");
		final String nullability = getString(rs, "is_nullable");
		if (!"YES".equalsIgnoreCase(nullability) && !"NO".equalsIgnoreCase(nullability))
			throw new IllegalArgumentException(
					"Unknown Spanner column nullability for " + obj.getName() + ": " + nullability);
		boolean nullable = "YES".equalsIgnoreCase(nullability);
		String data_type = getString(rs, "spanner_type");
		if (data_type == null || data_type.isBlank())
			throw new IllegalArgumentException("Missing Spanner column type: " + obj.getName());
		obj.setNullable(nullable);
		setColumnType(obj, data_type, getString(rs, "vector_length"));
		obj.setDefaultValue(getString(rs, "column_default"));
		obj.setOnUpdate(getString(rs, "ON_UPDATE_EXPRESSION"));
		final String generated = getString(rs, "IS_GENERATED");
		if (generated != null && !"ALWAYS".equalsIgnoreCase(generated) && !"NEVER".equalsIgnoreCase(generated))
			throw new IllegalArgumentException("Unknown Spanner generated-column flag: " + obj.getName());
		if ("ALWAYS".equalsIgnoreCase(generated)) {
			obj.setFormula(getString(rs, "GENERATION_EXPRESSION"));
			if (obj.getFormula() == null || obj.getFormula().isBlank())
				throw new IllegalArgumentException("Missing Spanner generated-column expression: " + obj.getName());
			final String stored = getString(rs, "IS_STORED");
			if (!"YES".equalsIgnoreCase(stored) && !"NO".equalsIgnoreCase(stored))
				throw new IllegalArgumentException("Unknown Spanner generated-column storage: " + obj.getName());
			obj.setFormulaPersisted("YES".equalsIgnoreCase(stored));
		}
		final String hidden = getString(rs, "IS_HIDDEN");
		if (hidden != null && !"TRUE".equalsIgnoreCase(hidden) && !"FALSE".equalsIgnoreCase(hidden))
			throw new IllegalArgumentException("Unknown Spanner hidden-column flag: " + obj.getName());
		obj.setHidden("TRUE".equalsIgnoreCase(hidden));
		setSpecifics(rs, "allow_commit_timestamp", obj);
		setSpecifics(rs, "locality_group", obj);
		final String identity = getString(rs, "IS_IDENTITY");
		if (identity != null && !"YES".equalsIgnoreCase(identity) && !"NO".equalsIgnoreCase(identity))
			throw new IllegalArgumentException("Unknown Spanner identity flag for " + obj.getName() + ": " + identity);
		if ("YES".equalsIgnoreCase(identity)) {
			obj.setIdentity(true);
			final String generation = getString(rs, "IDENTITY_GENERATION");
			if (generation != null && !"BY DEFAULT".equalsIgnoreCase(generation)
					&& !"BY_DEFAULT".equalsIgnoreCase(generation) && !"ALWAYS".equalsIgnoreCase(generation))
				throw new IllegalArgumentException(
						"Unknown Spanner identity generation for " + obj.getName() + ": " + generation);
			obj.setIdentityGenerationType(IdentityGenerationType.parse(generation));
			final Long start = getString(rs, "IDENTITY_START_WITH_COUNTER") == null ? null
					: getLong(rs, "IDENTITY_START_WITH_COUNTER");
			if (start != null) {
				obj.setIdentityStartValue(start);
			}
			final String kind = getString(rs, "IDENTITY_KIND");
			if (kind != null) {
				if (!"BIT_REVERSED_POSITIVE_SEQUENCE".equalsIgnoreCase(kind))
					throw new IllegalArgumentException(
							"Unknown Spanner identity kind for " + obj.getName() + ": " + kind);
				obj.getSpecifics().put(SpannerSqlBuilder.IDENTITY_BIT_REVERSED_POSITIVE, true);
			}
			final Long skipMin = getString(rs, "IDENTITY_SKIP_RANGE_MIN") == null ? null
					: getLong(rs, "IDENTITY_SKIP_RANGE_MIN");
			final Long skipMax = getString(rs, "IDENTITY_SKIP_RANGE_MAX") == null ? null
					: getLong(rs, "IDENTITY_SKIP_RANGE_MAX");
			if (skipMin != null) {
				obj.getSpecifics().put(SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MIN, skipMin);
			}
			if (skipMax != null) {
				obj.getSpecifics().put(SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MAX, skipMax);
			}
		}
		return obj;
	}

	private void setColumnType(final Column column, final String type, final String optionLength) {
		final var annotation = VECTOR_TYPE.matcher(type.trim());
		final boolean annotated = annotation.matches();
		final String baseType = annotated ? "ARRAY<" + annotation.group(1).toUpperCase(Locale.ROOT) + ">" : type;
		getDialect().setDbType(baseType, null, null, column);
		if (!annotated && optionLength == null)
			return;
		final int length = vectorLength(column, annotated ? annotation.group(2) : optionLength);
		if (annotated && optionLength != null && length != vectorLength(column, optionLength))
			throw new IllegalArgumentException("Conflicting Spanner vector lengths for " + column.getName());
		if (column.getArrayDimension() != 1
				|| (column.getDataType() != DataType.REAL && column.getDataType() != DataType.DOUBLE))
			throw new IllegalArgumentException("Spanner vector_length requires a FLOAT32 or FLOAT64 array: "
					+ column.getName());
		column.getSpecifics().put(SpannerSqlBuilder.VECTOR_LENGTH, length);
	}

	private int vectorLength(final Column column, final String value) {
		try {
			final int length = Integer.parseInt(value);
			if (length >= 0)
				return length;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Invalid Spanner vector length for " + column.getName() + ": " + value,
					exception);
		}
		throw new IllegalArgumentException("Invalid Spanner vector length for " + column.getName() + ": " + value);
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("columns.sql");
	}
}
