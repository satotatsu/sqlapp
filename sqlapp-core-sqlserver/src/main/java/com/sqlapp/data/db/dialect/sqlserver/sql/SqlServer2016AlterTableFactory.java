/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.sql;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.SystemVersioning;
import com.sqlapp.data.schemas.DbObjectDifference;

/**
 * SQL Server 2016+ temporal table alteration
 * 
 * @author tatsuo satoh
 * 
 */
public class SqlServer2016AlterTableFactory extends SqlServer2014AlterTableFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Table original = difference.getOriginal(Table.class);
		Table target = difference.getTarget(Table.class);
		if (original == null || target == null
				|| (original.getTemporalPeriods().isEmpty() && target.getTemporalPeriods().isEmpty()
						&& original.getSystemVersioning() == null && target.getSystemVersioning() == null)) {
			return super.createDiffSql(difference);
		}
		for (Column column : target.getColumns()) {
			Column oldColumn = original.getColumns().get(column.getName());
			if (oldColumn != null
					&& !java.util.Objects.equals(oldColumn.getCheckConstraint(), column.getCheckConstraint()))
				throw unsupported(target, "Changing a temporal column CHECK constraint");
		}
		if (original.equals(target))
			return java.util.Collections.emptyList();
		SqlServerTemporalSupport.retention(original, getDialect());
		SqlServerTemporalSupport.retention(target, getDialect());
		var oldPeriod = SqlServerTemporalSupport.period(original);
		var period = SqlServerTemporalSupport.period(target);
		if ((oldPeriod == null) != (period == null))
			return changePeriod(original, target, oldPeriod == null);
		if (period == null || !original.getTemporalPeriods().equals(target.getTemporalPeriods()))
			throw unsupported(target, "Changing the SYSTEM_TIME period");

		if (SqlServerTemporalSupport.enabled(original) && SqlServerTemporalSupport.enabled(target))
			return changeEnabledTable(original, target);

		// Permit only the changes generated below. Never let the generic ALTER path
		// silently discard other temporal properties or alter only the current table.
		Table normalized = target.clone();
		var oldConfig = original.getSystemVersioning() == null ? new SystemVersioning().setEnable(false)
				: original.getSystemVersioning();
		var nextConfig = target.getSystemVersioning() == null ? new SystemVersioning().setEnable(false)
				: target.getSystemVersioning();
		if (original.getSystemVersioning() != null && target.getSystemVersioning() == null)
			throw unsupported(target,
					"Removing systemVersioning configuration while keeping its period; set enable=false instead");
		var normalizedConfig = nextConfig.clone().setEnable(oldConfig.isEnable());
		// A reader cannot resolve the former history relationship once versioning
		// is off. Allow the caller to provide that explicit identity for re-enable.
		if (!oldConfig.isEnable() && nextConfig.isEnable() && oldConfig.getHistoryTableName() == null
				&& oldConfig.getHistoryTableSchemaName() == null) {
			normalizedConfig.setHistoryTableName(null).setHistoryTableSchemaName(null);
		}
		String oldRetention = SqlServerTemporalSupport.retention(original, getDialect());
		String nextRetention = SqlServerTemporalSupport.retention(target, getDialect());
		boolean enabling = !oldConfig.isEnable() && nextConfig.isEnable();
		boolean disabling = oldConfig.isEnable() && !nextConfig.isEnable();
		if (enabling || disabling && nextRetention == null) {
			String key = SqlServerTemporalOptions.HISTORY_RETENTION_PERIOD;
			if (oldConfig.getSpecifics().containsKey(key))
				normalizedConfig.getSpecifics().put(key, oldConfig.getSpecifics().get(key));
			else
				normalizedConfig.getSpecifics().remove(key);
		} else if (!java.util.Objects.equals(oldRetention, nextRetention)) {
			throw unsupported(target, "Changing retention while versioning stays disabled or is being disabled");
		}
		if (!oldConfig.equals(normalizedConfig))
			throw unsupported(target, "Changing history identity or other systemVersioning options");
		normalized.setSystemVersioning(original.getSystemVersioning() == null ? null : oldConfig.clone());
		String oldType = original.getSpecifics().get("temporal_type");
		String nextType = target.getSpecifics().get("temporal_type");
		if (expectedTemporalType(oldType, oldConfig.isEnable())
				&& expectedTemporalType(nextType, nextConfig.isEnable())) {
			if (oldType == null)
				normalized.getSpecifics().remove("temporal_type");
			else
				normalized.getSpecifics().put("temporal_type", oldType);
		}
		List<Column> hiddenChanges = new ArrayList<>();
		for (String name : List.of(period.getStartColumnName(), period.getEndColumnName())) {
			Column before = original.getColumns().get(name);
			Column after = target.getColumns().get(name);
			if (before.isHidden() != after.isHidden())
				hiddenChanges.add(after);
			normalized.getColumns().get(name).setHidden(before.isHidden());
		}
		if (!normalized.equals(original))
			throw unsupported(target, "Changing table structure while versioning is disabled or changing");

		boolean wasEnabled = SqlServerTemporalSupport.enabled(original);
		boolean enable = SqlServerTemporalSupport.enabled(target);
		if (!wasEnabled && enable
				&& (nextConfig.getHistoryTableName() == null || nextConfig.getHistoryTableSchemaName() == null))
			throw new IllegalArgumentException("Re-enabling SYSTEM_VERSIONING requires explicit historyTableSchemaName "
					+ "and historyTableName to preserve history: " + target.getName());
		List<SqlOperation> result = new ArrayList<>();
		if (wasEnabled && !enable)
			result.add(SqlServerTemporalSupport.versioning(original, createSqlBuilder(), false,
					getOptions().isDecorateSchemaName()));
		for (Column column : hiddenChanges) {
			var builder = createSqlBuilder();
			builder.alter().table().space().name(target, getOptions().isDecorateSchemaName())._add(" ALTER COLUMN ")
					.name(column)._add(column.isHidden() ? " ADD HIDDEN" : " DROP HIDDEN");
			result.add(createOperation(builder.toString(), SqlType.ALTER, original.getColumns().get(column.getName()),
					column));
		}
		if (!wasEnabled && enable)
			result.add(SqlServerTemporalSupport.versioning(target, createSqlBuilder(), true,
					getOptions().isDecorateSchemaName()));
		return result;
	}

	/**
	 * Adds/removes the period on existing columns, without changing row data or
	 * history.
	 */
	private List<SqlOperation> changePeriod(Table original, Table target, boolean adding) {
		var period = adding ? target.getTemporalPeriods().get(0) : original.getTemporalPeriods().get(0);
		if (adding && original.getSystemVersioning() != null)
			throw unsupported(target, "Adding a period to a table with existing versioning configuration");
		if (!adding && target.getSystemVersioning() != null)
			throw unsupported(target, "Retaining systemVersioning configuration after removing its period");
		Table normalized = target.clone();
		normalized.setTemporalPeriods(original.getTemporalPeriods().clone());
		normalized.setSystemVersioning(
				original.getSystemVersioning() == null ? null : original.getSystemVersioning().clone());
		List<Column> hiddenChanges = new ArrayList<>();
		for (String name : List.of(period.getStartColumnName(), period.getEndColumnName())) {
			Column before = original.getColumns().get(name), after = target.getColumns().get(name);
			if (before == null || after == null)
				throw unsupported(target, "Adding or deleting period columns in the same migration");
			if ((!adding && after.isHidden()) || (adding && before.isHidden()))
				throw unsupported(target, "Hidden columns without a SYSTEM_TIME period");
			if (before.isHidden() != after.isHidden())
				hiddenChanges.add(after);
			normalized.getColumns().get(name).setHidden(before.isHidden());
		}
		String oldType = original.getSpecifics().get("temporal_type");
		String nextType = target.getSpecifics().get("temporal_type");
		if (expectedTemporalType(oldType, SqlServerTemporalSupport.enabled(original))
				&& expectedTemporalType(nextType, SqlServerTemporalSupport.enabled(target))) {
			if (oldType == null)
				normalized.getSpecifics().remove("temporal_type");
			else
				normalized.getSpecifics().put("temporal_type", oldType);
		}
		if (!normalized.equals(original))
			throw unsupported(target, "Changing table structure together with its SYSTEM_TIME period");
		List<SqlOperation> result = new ArrayList<>();
		if (!adding && SqlServerTemporalSupport.enabled(original))
			result.add(SqlServerTemporalSupport.versioning(original, createSqlBuilder(), false,
					getOptions().isDecorateSchemaName()));
		var builder = createSqlBuilder();
		builder.alter().table().space().name(target, getOptions().isDecorateSchemaName());
		if (adding) {
			builder._add(" ADD PERIOD FOR SYSTEM_TIME (").name(period.getStartColumnName()).comma().space()
					.name(period.getEndColumnName())._add(")");
			result.add(createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
		// HIDDEN is changed while the period exists, before DROP or after ADD.
		for (Column column : hiddenChanges) {
			var hidden = createSqlBuilder();
			hidden.alter().table().space().name(target, getOptions().isDecorateSchemaName())._add(" ALTER COLUMN ")
					.name(column)._add(column.isHidden() ? " ADD HIDDEN" : " DROP HIDDEN");
			result.add(createOperation(hidden.toString(), SqlType.ALTER, original.getColumns().get(column.getName()),
					column));
		}
		if (!adding) {
			builder._add(" DROP PERIOD FOR SYSTEM_TIME");
			result.add(createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
		if (adding && SqlServerTemporalSupport.enabled(target))
			result.add(SqlServerTemporalSupport.versioning(target, createSqlBuilder(), true,
					getOptions().isDecorateSchemaName()));
		return result;
	}

	private static final Set<DataType> STORED_TYPES = Set.of(DataType.INT, DataType.BIGINT, DataType.VARCHAR,
			DataType.NVARCHAR, DataType.DECIMAL, DataType.DATE, DataType.TIMESTAMP, DataType.VARBINARY,
			DataType.BOOLEAN);

	/**
	 * Validates the complete diff before composing SQL for changes SQL Server
	 * propagates to history.
	 */
	private List<SqlOperation> changeEnabledTable(Table original, Table target) {
		Table normalized = target.clone();
		String oldRetention = SqlServerTemporalSupport.retention(original, getDialect());
		String nextRetention = SqlServerTemporalSupport.retention(target, getDialect());
		if (oldRetention != null && nextRetention == null)
			throw new IllegalArgumentException("Use HISTORY_RETENTION_PERIOD = INFINITE to explicitly reset retention");
		var oldSpecifics = original.getSystemVersioning().getSpecifics();
		var normalizedSpecifics = normalized.getSystemVersioning().getSpecifics();
		String key = SqlServerTemporalOptions.HISTORY_RETENTION_PERIOD;
		if (oldSpecifics.containsKey(key))
			normalizedSpecifics.put(key, oldSpecifics.get(key));
		else
			normalizedSpecifics.remove(key);
		List<Column> added = new ArrayList<>(), altered = new ArrayList<>(), hidden = new ArrayList<>();
		var period = target.getTemporalPeriods().get(0);
		for (int i = 0; i < target.getColumns().size(); i++) {
			Column after = target.getColumns().get(i), before = original.getColumns().get(after.getName());
			if (before == null) {
				if (i < original.getColumns().size())
					throw unsupported(target, "Inserting a new column before existing columns");
				validateAddedColumn(target, after);
				added.add(after);
				normalized.getColumns().remove(after.getName());
				continue;
			}
			if (!java.util.Objects.equals(before.getLength(), after.getLength())
					|| before.isNotNull() != after.isNotNull()) {
				validateStoredColumnChange(original, before, after);
				altered.add(after);
				normalized.getColumns().get(after.getName()).setLength(before.getLength())
						.setNotNull(before.isNotNull());
			}
			if (before.isHidden() != after.isHidden() && (after == target.getColumns().get(period.getStartColumnName())
					|| after == target.getColumns().get(period.getEndColumnName()))) {
				hidden.add(after);
				normalized.getColumns().get(after.getName()).setHidden(before.isHidden());
			}
		}
		if ((!added.isEmpty() || !altered.isEmpty()) && original.getTableType() == Table.TableType.Memory)
			throw unsupported(target, "Changing stored columns of memory-optimized temporal tables");
		if (!normalized.equals(original))
			throw unsupported(target,
					"Changing unsupported temporal properties, deleting columns or replacing column types");
		List<SqlOperation> result = new ArrayList<>();
		for (Column column : altered)
			result.add(alterStoredColumn(original, target, column));
		for (Column column : added) {
			var builder = createSqlBuilder();
			builder.alter().table().space().name(target, getOptions().isDecorateSchemaName()).add().name(column).space()
					.definition(column, false)._add(" NULL");
			result.add(createOperation(builder.toString(), SqlType.ALTER, null, column));
		}
		for (Column column : hidden) {
			var builder = createSqlBuilder();
			builder.alter().table().space().name(target, getOptions().isDecorateSchemaName())._add(" ALTER COLUMN ")
					.name(column)._add(column.isHidden() ? " ADD HIDDEN" : " DROP HIDDEN");
			result.add(createOperation(builder.toString(), SqlType.ALTER, original.getColumns().get(column.getName()),
					column));
		}
		if (!java.util.Objects.equals(oldRetention, nextRetention)) {
			var builder = createSqlBuilder();
			builder.alter().table().space().name(target, getOptions().isDecorateSchemaName())
					._add(" SET (SYSTEM_VERSIONING = ON (HISTORY_RETENTION_PERIOD = ")._add(nextRetention)._add("))");
			result.add(createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
		return result;
	}

	private void validateAddedColumn(Table table, Column column) {
		Set<String> properties = Set.of(SchemaProperties.DATA_TYPE.getLabel(), SchemaProperties.LENGTH.getLabel(),
				SchemaProperties.SCALE.getLabel(), SchemaProperties.ORDINAL.getLabel());
		if (column.isNotNull() || column.getDataType() == null || !STORED_TYPES.contains(column.getDataType()))
			throw unsupported(table, "Only appended nullable columns of supported built-in types can be added");
		var changed = new Column(column.getName()).diff(column).toDifference().getChangedProperties(getDialect());
		if (!properties.containsAll(changed.keySet()))
			throw unsupported(table, "Adding a column with advanced properties: " + column.getName());
	}

	private void validateStoredColumnChange(Table table, Column before, Column after) {
		DataType type = before.getDataType();
		if (type == null || !STORED_TYPES.contains(type))
			throw unsupported(table, "Changing an unsupported stored column type: " + after.getName());
		if (!java.util.Objects.equals(before.getLength(), after.getLength())) {
			if (type != DataType.VARCHAR && type != DataType.NVARCHAR && type != DataType.VARBINARY)
				throw unsupported(table, "Expanding a non-variable-length column: " + after.getName());
			long maximum = type == DataType.NVARCHAR ? 4000 : 8000;
			if (before.getLength() == null || after.getLength() == null || before.getLength() <= 0
					|| after.getLength() <= before.getLength() || after.getLength() > maximum)
				throw unsupported(table, "Shrinking or changing a column to/from MAX: " + after.getName());
		}
		if (!before.isNotNull() && after.isNotNull())
			throw unsupported(table,
					"Tightening nullability requires verifying current and historical rows: " + after.getName());
		if (before.isNotNull() != after.isNotNull() && table.getPrimaryKeyConstraint() != null
				&& table.getPrimaryKeyConstraint().getColumns().contains(before.getName()))
			throw unsupported(table, "Relaxing nullability of a primary-key column: " + after.getName());
		if (before.isIdentity() || before.isHidden() || before.getFormula() != null && !before.getFormula().isEmpty()
				|| before.getMaskingFunction() != null && !before.getMaskingFunction().isEmpty()
				|| !supportedNativeType(before))
			throw unsupported(table,
					"Changing a column with generated or vendor-specific behavior: " + after.getName());
		for (var entry : before.getSpecifics().entrySet()) {
			if (!entry.getKey().equalsIgnoreCase("is_sparse")
					|| !("false".equalsIgnoreCase(entry.getValue()) || "0".equals(entry.getValue())))
				throw unsupported(table, "Changing a column with advanced specifics: " + after.getName());
		}
	}

	private boolean supportedNativeType(Column column) {
		if (column.getDataTypeName() == null)
			return true;
		String name = column.getDataTypeName().toUpperCase(java.util.Locale.ROOT);
		return switch (column.getDataType()) {
		case TIMESTAMP -> name.equals("DATETIME2");
		case BOOLEAN -> name.equals("BIT");
		case DECIMAL -> name.equals("DECIMAL") || name.equals("NUMERIC");
		case INT -> name.equals("INT") || name.equals("INTEGER");
		default -> name.equals(column.getDataType().name());
		};
	}

	private SqlOperation alterStoredColumn(Table original, Table target, Column column) {
		// Only type, collation and nullability belong in ALTER COLUMN. Existing
		// defaults, checks and comments are retained without inline recreation.
		Column definition = new Column(column.getName()).setDataType(column.getDataType()).setLength(column.getLength())
				.setScale(column.getScale());
		var builder = createSqlBuilder();
		builder.alter().table().space().name(target, getOptions().isDecorateSchemaName())._add(" ALTER COLUMN ")
				.name(column).space().definitionForAlterColumn(definition);
		if (column.getCollation() != null && !column.getCollation().isEmpty())
			builder._add(" COLLATE ").name(column.getCollation());
		builder._add(column.isNotNull() ? " NOT NULL" : " NULL");
		return createOperation(builder.toString(), SqlType.ALTER, original.getColumns().get(column.getName()), column);
	}

	private boolean expectedTemporalType(String value, boolean enabled) {
		return value == null || (enabled ? "SYSTEM_VERSIONED_TEMPORAL_TABLE" : "NON_TEMPORAL_TABLE").equals(value);
	}

	private IllegalArgumentException unsupported(Table table, String change) {
		return new IllegalArgumentException(change + " is unsupported by automatic temporal ALTER; "
				+ "migrate current and history tables explicitly: " + table.getName());
	}
}
