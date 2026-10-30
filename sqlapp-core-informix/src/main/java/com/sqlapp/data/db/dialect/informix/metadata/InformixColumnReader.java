/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.sql.SQLException;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.informix.util.InformixConstraintModes;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcColumnReader;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** Normalizes Informix SERIAL family columns as generated identities. */
public class InformixColumnReader extends JdbcColumnReader {
	public static final String INFORMIX_NATIVE_TYPE = "INFORMIX_NATIVE_TYPE";

	public InformixColumnReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		final List<Column> columns = readJdbcColumns(connection, context, version);
		setDefaults(connection, context, columns);
		return columns;
	}

	protected List<Column> readJdbcColumns(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		return super.doGetAll(connection, context, version);
	}

	protected void setDefaults(Connection connection, ParametersContext context, List<Column> columns) {
		if (columns.isEmpty())
			return;
		final Map<List<String>, Column> selected = new LinkedHashMap<>();
		final Map<List<String>, List<Column>> selectedTables = new LinkedHashMap<>();
		for (var column : columns) {
			selected.put(java.util.Arrays.asList(column.getSchemaName(), column.getTableName(), column.getName()),
					column);
			selectedTables.computeIfAbsent(java.util.Arrays.asList(column.getSchemaName(), column.getTableName()),
					key -> new java.util.ArrayList<>()).add(column);
		}
		final var seen = new HashSet<List<String>>();
		final java.util.Set<Column> nonColumns = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		final Map<String, InformixIndexColumns> primaryKeys = new LinkedHashMap<>();
		final Map<Column, Integer> complexColumns = new LinkedHashMap<>();
		final var parameters = context.clone();
		parameters.put("catalogName",
				columns.stream().map(Column::getCatalogName).filter(java.util.Objects::nonNull).distinct().toList());
		parameters.put("schemaName",
				columns.stream().map(Column::getSchemaName).filter(java.util.Objects::nonNull).distinct().toList());
		parameters.put("tableName", columns.stream().map(Column::getTableName).distinct().toList());
		parameters.put("columnName", columns.stream().map(Column::getName).distinct().toList());
		queryDefaults(connection, parameters, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				final String objectType = getString(rs, "object_type");
				if (objectType != null && !"T".equals(objectType) && !"V".equals(objectType)) {
					if (!java.util.Set.of("Q", "P", "S").contains(objectType))
						throw new UnsupportedOperationException("Unsupported Informix column object type: " + objectType
								+ " for " + getString(rs, TABLE_NAME));
					final var tableColumns = selectedTables.getOrDefault(
							java.util.Arrays.asList(getString(rs, SCHEMA_NAME), getString(rs, TABLE_NAME)), List.of());
					for (var column : tableColumns) {
						if (column.getCatalogName() != null
								&& !column.getCatalogName().equals(getString(rs, CATALOG_NAME)))
							continue;
						seen.add(java.util.Arrays.asList(column.getSchemaName(), column.getTableName(),
								column.getName()));
						nonColumns.add(column);
					}
					return;
				}
				final var key = java.util.Arrays.asList(getString(rs, SCHEMA_NAME), getString(rs, TABLE_NAME),
						getString(rs, COLUMN_NAME));
				final Column column = selected.get(key);
				if (column == null || column.getCatalogName() != null
						&& !column.getCatalogName().equals(getString(rs, CATALOG_NAME)))
					return;
				seen.add(key);
				final String mode = getString(rs, "column_type_mode");
				if ("C".equals(mode))
					complexColumns.put(column, rs.getInt("column_type_id"));
				else if ("D".equals(mode) || "R".equals(mode)
						|| "B".equals(mode) && rs.getInt("column_type_id") >= 2048) {
					final String name = getString(rs, "column_type_name"), owner = getString(rs, "column_type_owner");
					if (name == null || name.isBlank() || owner == null || owner.isBlank())
						throw new IllegalStateException("Missing Informix named column type: " + column.getName());
					setNativeType(column,
							"\"" + owner.replace("\"", "\"\"") + "\".\"" + name.replace("\"", "\"\"") + "\"");
				}
				column.setDefaultValue(column.isIdentity() ? null
						: InformixColumnDefaults.expression(column, getString(rs, "default_kind"),
								rs.getString("default_value")));
				setNullability(rs, column, primaryKeys);
			}
		});
		columns.removeIf(nonColumns::contains);
		if (!complexColumns.isEmpty()) {
			final var declarations = readComplexDeclarations(connection, new HashSet<>(complexColumns.values()));
			complexColumns.forEach((column, id) -> {
				final String declaration = declarations.get(id);
				if (declaration == null)
					throw new IllegalStateException("Missing Informix column type declaration: " + column.getName());
				setNativeType(column, declaration);
			});
		}
		if (!seen.containsAll(selected.keySet())) {
			final var missing = new java.util.LinkedHashSet<>(selected.keySet());
			missing.removeAll(seen);
			throw new IllegalStateException(
					"Cannot resolve Informix column defaults in the connected database; missing="
							+ missing.stream().limit(10).toList() + "; catalogs=" + parameters.get("catalogName"));
		}
	}

	protected Map<Integer, String> readComplexDeclarations(Connection connection, java.util.Set<Integer> ids) {
		return new InformixRowTypeReader(getDialect()).readComplexDeclarations(connection, ids);
	}

	private void setNativeType(Column column, String declaration) {
		column.setDataTypeName(declaration);
		column.getSpecifics().put(INFORMIX_NATIVE_TYPE, declaration);
	}

	private void setNullability(ExResultSet rs, Column column, Map<String, InformixIndexColumns> primaryKeys)
			throws SQLException {
		boolean primary = false;
		if (getString(rs, "primary_key_name") != null) {
			String keys = getString(rs, "primary_key_columns");
			if (keys == null)
				throw new IllegalStateException(
						"Missing Informix primary-key column identities: " + column.getTableName());
			primary = primaryKeys.computeIfAbsent(keys, InformixIndexColumns::new)
					.containsColumn(rs.getInt("column_number"));
		}
		boolean primaryEnforced = primary
				&& !"DISABLED".equals(InformixConstraintModes.decode(getString(rs, "primary_key_state")));
		String notNull = getString(rs, "not_null_name");
		if (notNull != null) {
			String mode = InformixConstraintModes.decode(getString(rs, "not_null_state"));
			column.getSpecifics().put(InformixConstraintModes.NOT_NULL_NAME, notNull);
			String owner = getString(rs, "not_null_owner");
			if (owner != null && !owner.equals(column.getSchemaName()))
				column.getSpecifics().put(InformixConstraintModes.NOT_NULL_OWNER, owner);
			if (!"ENABLED".equals(mode))
				column.getSpecifics().put(InformixConstraintModes.NOT_NULL_MODE, mode);
			column.setNullable("DISABLED".equals(mode) && !primaryEnforced && !column.isIdentity());
		} else if (primary && !column.isIdentity())
			column.setNullable(!primaryEnforced);
	}

	protected void queryDefaults(Connection connection, ParametersContext context, ResultSetNextHandler handler) {
		execute(connection, getSqlNodeCache().getString("columnDefaults.sql"), context, handler);
	}

	@Override
	protected Column createColumn(final ExResultSet resultSet) throws SQLException {
		Column column = super.createColumn(resultSet);
		String typeName = getString(resultSet, "TYPE_NAME");
		if (typeName == null)
			throw new IllegalStateException("Missing Informix column type: " + column.getName());
		final String nativeType = typeName.toUpperCase(java.util.Locale.ROOT).strip();
		if (nativeType.startsWith("DATETIME ")) {
			column.setDataType(nativeType.startsWith("DATETIME HOUR") || nativeType.startsWith("DATETIME MINUTE")
					|| nativeType.startsWith("DATETIME SECOND") || nativeType.startsWith("DATETIME FRACTION")
							? DataType.TIME
							: DataType.TIMESTAMP);
			column.setDataTypeName(
					com.sqlapp.data.db.dialect.informix.util.InformixTemporalTypes.normalize(nativeType));
			column.setLength(null);
			column.setScale(null);
		} else if (nativeType.startsWith("INTERVAL ")) {
			final String declaration = com.sqlapp.data.db.dialect.informix.util.InformixTemporalTypes
					.interval(resultSet.getLong("COLUMN_SIZE"));
			column.setDataType(declaration.startsWith("INTERVAL YEAR") || declaration.startsWith("INTERVAL MONTH")
					? DataType.INTERVAL_YEAR_TO_MONTH
					: DataType.INTERVAL_DAY_TO_SECOND);
			column.setDataTypeName(declaration);
			column.setLength(null);
			column.setScale(null);
		} else if ("DECIMAL".equals(nativeType) && resultSet.getInt("DECIMAL_DIGITS") == 255) {
			long precision = resultSet.getLong("COLUMN_SIZE");
			if (precision < 1 || precision > 32)
				throw new IllegalStateException("Invalid Informix floating DECIMAL precision: " + precision);
			column.setDataType(DataType.DECIMALFLOAT).setLength(precision).setScale(null)
					.setDataTypeName("DECIMAL(" + precision + ")");
		} else if ("DECIMAL".equals(nativeType)) {
			long precision = resultSet.getLong("COLUMN_SIZE");
			int scale = resultSet.getInt("DECIMAL_DIGITS");
			if (precision < 1 || precision > 32 || scale < 0 || scale > 32)
				throw new IllegalStateException(
						"Invalid Informix fixed DECIMAL precision/scale: " + precision + "," + scale);
			column.setDataType(DataType.DECIMAL).setLength(precision).setScale(scale)
					.setDataTypeName("DECIMAL(" + precision + "," + scale + ")");
		} else if ("BIGINT".equals(nativeType) || "INT8".equals(nativeType)) {
			column.setDataType(DataType.BIGINT).setDataTypeName(nativeType);
		}
		if ("SERIAL".equalsIgnoreCase(typeName)) {
			column.setDataType(DataType.SERIAL);
			column.setIdentity(true);
			column.setIdentityGenerationType(IdentityGenerationType.ByDefault);
			column.setDefaultValue(null);
			column.setDataTypeName("SERIAL");
		} else if ("SERIAL8".equalsIgnoreCase(typeName) || "BIGSERIAL".equalsIgnoreCase(typeName)) {
			column.setDataType(DataType.BIGSERIAL);
			column.setIdentity(true);
			column.setIdentityGenerationType(IdentityGenerationType.ByDefault);
			column.setDefaultValue(null);
			column.setDataTypeName(nativeType);
		}
		return column;
	}
}
