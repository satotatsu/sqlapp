/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TemporalPeriod;
import com.sqlapp.data.schemas.TemporalPeriodType;

/**
 * SQL Server 2016+ system-time validation and SQL shared by table factories.
 */
final class SqlServerTemporalSupport {
	private SqlServerTemporalSupport() {
	}

	static boolean enabled(Table table) {
		return table != null && table.getSystemVersioning() != null && table.getSystemVersioning().isEnable();
	}

	static TemporalPeriod period(Table table) {
		if (table.getTemporalPeriods().isEmpty()) {
			if (table.getSystemVersioning() != null)
				throw new IllegalArgumentException(
						"systemVersioning configuration requires a SYSTEM_TIME period: " + table.getName());
			return null;
		}
		if (table.getTemporalPeriods().size() != 1
				|| table.getTemporalPeriods().get(0).getPeriodType() != TemporalPeriodType.SYSTEM_TIME) {
			throw new IllegalArgumentException(
					"SQL Server requires exactly one SYSTEM_TIME period: " + table.getName());
		}
		TemporalPeriod period = table.getTemporalPeriods().get(0);
		String start = period.getStartColumnName(), end = period.getEndColumnName();
		if (start == null || end == null || start.equals(end))
			throw new IllegalArgumentException(
					"SYSTEM_TIME requires distinct start and end columns: " + table.getName());
		for (String name : List.of(start, end)) {
			Column column = table.getColumns().get(name);
			if (table.getColumns().get(start) == table.getColumns().get(end))
				throw new IllegalArgumentException("SYSTEM_TIME requires distinct start and end columns");
			if (column == null || column.getDataType() != DataType.TIMESTAMP || !column.isNotNull()
					|| column.isIdentity() || column.getFormula() != null && !column.getFormula().isEmpty()
					|| column.getDataTypeName() != null
							&& !column.getDataTypeName().matches("(?i)DATETIME2(?:\\([0-7]\\))?"))
				throw new IllegalArgumentException("SYSTEM_TIME column must be a non-null datetime2 column: " + name);
		}
		Column startColumn = table.getColumns().get(start), endColumn = table.getColumns().get(end);
		long startPrecision = startColumn.getLength() == null ? 7 : startColumn.getLength();
		long endPrecision = endColumn.getLength() == null ? 7 : endColumn.getLength();
		if (startPrecision < 0 || startPrecision > 7 || endPrecision != startPrecision)
			throw new IllegalArgumentException("SYSTEM_TIME columns require matching datetime2 precision from 0 to 7");
		var versioning = table.getSystemVersioning();
		if (versioning != null) {
			if (versioning.getHistoryTableName() != null && versioning.getHistoryTableName().isBlank()
					|| versioning.getHistoryTableSchemaName() != null
							&& versioning.getHistoryTableSchemaName().isBlank())
				throw new IllegalArgumentException("History table and schema names must not be blank");
			if (java.util.Objects.equals(table.getName(), versioning.getHistoryTableName())
					&& java.util.Objects.equals(table.getSchemaName(), versioning.getHistoryTableSchemaName()))
				throw new IllegalArgumentException("A temporal table cannot be its own history table");
			if (versioning.isImplicit() || versioning.getTransactionIdColumnName() != null)
				throw new IllegalArgumentException("SQL Server requires explicit datetime2 period columns");
			if (versioning.getPeriodName() != null && !versioning.getPeriodName().equals(period.getName()))
				throw new IllegalArgumentException("System-versioning period does not match SYSTEM_TIME period");
			if (versioning.getHistoryTableSchemaName() != null && versioning.getHistoryTableName() == null)
				throw new IllegalArgumentException("historyTableSchemaName requires historyTableName");
			if (versioning.getHistoryTableName() != null && versioning.getHistoryTableSchemaName() == null)
				throw new IllegalArgumentException("historyTableName requires historyTableSchemaName");
		}
		if (enabled(table) && table.getPrimaryKeyConstraint() == null)
			throw new IllegalArgumentException("SYSTEM_VERSIONING requires a primary key: " + table.getName());
		return period;
	}

	static String retention(Table table, Dialect dialect) {
		if (table.getSystemVersioning() != null) {
			for (String key : table.getSystemVersioning().getSpecifics().keySet()) {
				if (!SqlServerTemporalOptions.HISTORY_RETENTION_PERIOD.equalsIgnoreCase(key))
					throw new IllegalArgumentException("Unsupported SQL Server systemVersioning option: " + key);
			}
		}
		String value = SqlServerTemporalOptions.getHistoryRetentionPeriod(table.getSystemVersioning());
		if (value != null && dialect.compareTo(SqlServerDialectResolver.getInstance().getDialect(14, 0)) < 0)
			throw new IllegalArgumentException("HISTORY_RETENTION_PERIOD requires SQL Server 2017 or later");
		return value;
	}

	static SqlOperation versioning(Table table, SqlServerSqlBuilder builder, boolean on, boolean decorate) {
		String retention = retention(table, builder.getDialect());
		builder.alter().table().space().name(table, decorate)._add(" SET (SYSTEM_VERSIONING = ")
				._add(on ? "ON" : "OFF");
		if (on) {
			var config = table.getSystemVersioning();
			if (config.getHistoryTableName() != null || retention != null) {
				builder._add(" (");
				if (config.getHistoryTableName() != null) {
					builder._add("HISTORY_TABLE = ").name(config.getHistoryTableSchemaName())._add(".")
							.name(config.getHistoryTableName())._add(", ");
				}
				if (retention != null)
					builder._add("HISTORY_RETENTION_PERIOD = ")._add(retention)._add(", ");
				builder._add("DATA_CONSISTENCY_CHECK = ON)");
			}
		}
		builder._add(")");
		return on ? new EnableOperation(builder.toString(), table)
				: new SqlOperation(builder.toString(), SqlType.ALTER, table);
	}

	static List<SqlOperation> enableLast(List<SqlOperation> operations) {
		List<SqlOperation> result = new ArrayList<>();
		for (var operation : operations)
			if (!(operation instanceof EnableOperation))
				result.add(operation);
		for (var operation : operations)
			if (operation instanceof EnableOperation)
				result.add(operation);
		return result;
	}

	private static final class EnableOperation extends SqlOperation {
		EnableOperation(String sql, Table table) {
			super(sql, SqlType.ALTER, table);
		}
	}
}
