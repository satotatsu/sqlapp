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
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Table;
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
		if (original == null || target == null || (original.getTemporalPeriods().isEmpty()
				&& target.getTemporalPeriods().isEmpty() && !SqlServerTemporalSupport.enabled(original)
				&& !SqlServerTemporalSupport.enabled(target))) {
			return super.createDiffSql(difference);
		}
		if (original.equals(target)) return java.util.Collections.emptyList();
		var oldPeriod = SqlServerTemporalSupport.period(original);
		var period = SqlServerTemporalSupport.period(target);
		if ((oldPeriod == null) != (period == null))
			return changePeriod(original, target, oldPeriod == null);
		if (period == null || !original.getTemporalPeriods().equals(target.getTemporalPeriods()))
			throw unsupported(target, "Changing the SYSTEM_TIME period");

		if (target.getColumns().size() > original.getColumns().size())
			return addNullableColumns(original, target);

		// Permit only the changes generated below. Never let the generic ALTER path
		// silently discard other temporal properties or alter only the current table.
		Table normalized = target.clone();
		var oldConfig = original.getSystemVersioning();
		var nextConfig = target.getSystemVersioning();
		if (oldConfig == null || nextConfig == null)
			throw unsupported(target, "Removing or introducing systemVersioning configuration");
		var normalizedConfig = nextConfig.clone().setEnable(oldConfig.isEnable());
		// A reader cannot resolve the former history relationship once versioning
		// is off. Allow the caller to provide that explicit identity for re-enable.
		if (!oldConfig.isEnable() && nextConfig.isEnable() && oldConfig.getHistoryTableName() == null
				&& oldConfig.getHistoryTableSchemaName() == null) {
			normalizedConfig.setHistoryTableName(null).setHistoryTableSchemaName(null);
		}
		if (!oldConfig.equals(normalizedConfig))
			throw unsupported(target, "Changing history identity or other systemVersioning options");
		normalized.setSystemVersioning(oldConfig.clone());
		String oldType = original.getSpecifics().get("temporal_type");
		String nextType = target.getSpecifics().get("temporal_type");
		if (expectedTemporalType(oldType, oldConfig.isEnable()) && expectedTemporalType(nextType, nextConfig.isEnable())) {
			if (oldType == null) normalized.getSpecifics().remove("temporal_type");
			else normalized.getSpecifics().put("temporal_type", oldType);
		}
		List<Column> hiddenChanges = new ArrayList<>();
		for (String name : List.of(period.getStartColumnName(), period.getEndColumnName())) {
			Column before = original.getColumns().get(name);
			Column after = target.getColumns().get(name);
			if (before.isHidden() != after.isHidden()) hiddenChanges.add(after);
			normalized.getColumns().get(name).setHidden(before.isHidden());
		}
		if (!normalized.equals(original))
			return widenColumns(original, target);

		boolean wasEnabled = SqlServerTemporalSupport.enabled(original);
		boolean enable = SqlServerTemporalSupport.enabled(target);
		if (!wasEnabled && enable && (nextConfig.getHistoryTableName() == null
				|| nextConfig.getHistoryTableSchemaName() == null))
			throw new IllegalArgumentException("Re-enabling SYSTEM_VERSIONING requires explicit historyTableSchemaName "
					+ "and historyTableName to preserve history: " + target.getName());
		List<SqlOperation> result = new ArrayList<>();
		if (wasEnabled && !enable)
			result.add(SqlServerTemporalSupport.versioning(original, createSqlBuilder(), false,
					getOptions().isDecorateSchemaName()));
		for (Column column : hiddenChanges) {
			var builder = createSqlBuilder();
			builder.alter().table().space().name(target, getOptions().isDecorateSchemaName())
					._add(" ALTER COLUMN ").name(column)._add(column.isHidden() ? " ADD HIDDEN" : " DROP HIDDEN");
			result.add(createOperation(builder.toString(), SqlType.ALTER, original.getColumns().get(column.getName()), column));
		}
		if (!wasEnabled && enable)
			result.add(SqlServerTemporalSupport.versioning(target, createSqlBuilder(), true,
					getOptions().isDecorateSchemaName()));
		return result;
	}

	/** Adds/removes the period on existing columns, without changing row data or history. */
	private List<SqlOperation> changePeriod(Table original, Table target, boolean adding) {
		var period = adding ? target.getTemporalPeriods().get(0) : original.getTemporalPeriods().get(0);
		if (adding && original.getSystemVersioning() != null)
			throw unsupported(target, "Adding a period to a table with existing versioning configuration");
		if (!adding && target.getSystemVersioning() != null)
			throw unsupported(target, "Retaining systemVersioning configuration after removing its period");
		Table normalized = target.clone();
		normalized.setTemporalPeriods(original.getTemporalPeriods().clone());
		normalized.setSystemVersioning(original.getSystemVersioning() == null ? null : original.getSystemVersioning().clone());
		List<Column> hiddenChanges = new ArrayList<>();
		for (String name : List.of(period.getStartColumnName(), period.getEndColumnName())) {
			Column before = original.getColumns().get(name), after = target.getColumns().get(name);
			if (before == null || after == null)
				throw unsupported(target, "Adding or deleting period columns in the same migration");
			if ((!adding && after.isHidden()) || (adding && before.isHidden()))
				throw unsupported(target, "Hidden columns without a SYSTEM_TIME period");
			if (before.isHidden() != after.isHidden()) hiddenChanges.add(after);
			normalized.getColumns().get(name).setHidden(before.isHidden());
		}
		String oldType = original.getSpecifics().get("temporal_type");
		String nextType = target.getSpecifics().get("temporal_type");
		if (expectedTemporalType(oldType, SqlServerTemporalSupport.enabled(original))
				&& expectedTemporalType(nextType, SqlServerTemporalSupport.enabled(target))) {
			if (oldType == null) normalized.getSpecifics().remove("temporal_type");
			else normalized.getSpecifics().put("temporal_type", oldType);
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
			hidden.alter().table().space().name(target, getOptions().isDecorateSchemaName())
					._add(" ALTER COLUMN ").name(column)._add(column.isHidden() ? " ADD HIDDEN" : " DROP HIDDEN");
			result.add(createOperation(hidden.toString(), SqlType.ALTER, original.getColumns().get(column.getName()), column));
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

	/** SQL Server propagates ordinary column additions to history while versioning is on. */
	private List<SqlOperation> addNullableColumns(Table original, Table target) {
		if (!SqlServerTemporalSupport.enabled(original) || !SqlServerTemporalSupport.enabled(target)
				|| original.getTableType() == Table.TableType.Memory)
			throw unsupported(target, "Adding columns while versioning is disabled or changing");
		Table normalized = target.clone();
		List<Column> added = new ArrayList<>();
		Set<DataType> types = Set.of(DataType.INT, DataType.BIGINT, DataType.VARCHAR, DataType.NVARCHAR,
				DataType.DECIMAL, DataType.DATE, DataType.TIMESTAMP, DataType.VARBINARY, DataType.BOOLEAN);
		Set<String> properties = Set.of(SchemaProperties.DATA_TYPE.getLabel(), SchemaProperties.LENGTH.getLabel(),
				SchemaProperties.PRECISION.getLabel(), SchemaProperties.SCALE.getLabel(), SchemaProperties.ORDINAL.getLabel());
		for (int i = original.getColumns().size(); i < target.getColumns().size(); i++) {
			Column column = target.getColumns().get(i);
			if (original.getColumns().get(column.getName()) != null || column.isNotNull()
					|| column.getDataType() == null || !types.contains(column.getDataType()))
				throw unsupported(target, "Only appended nullable columns of supported built-in types can be added");
			var changed = new Column(column.getName()).diff(column).toDifference().getChangedProperties(getDialect());
			if (!properties.containsAll(changed.keySet()))
				throw unsupported(target, "Adding a column with advanced properties: " + column.getName());
			added.add(column);
			normalized.getColumns().remove(column.getName());
		}
		if (!normalized.equals(original))
			throw unsupported(target, "Combining column additions with other temporal table changes");
		List<SqlOperation> result = new ArrayList<>();
		for (Column column : added) {
			var builder = createSqlBuilder();
			builder.alter().table().space().name(target, getOptions().isDecorateSchemaName()).add()
					.name(column).space().definition(column, false)._add(" NULL");
			result.add(createOperation(builder.toString(), SqlType.ALTER, null, column));
		}
		return result;
	}

	/** Widens bounded variable-length columns using the shared dialect type generator. */
	private List<SqlOperation> widenColumns(Table original, Table target) {
		if (!SqlServerTemporalSupport.enabled(original) || !SqlServerTemporalSupport.enabled(target)
				|| original.getTableType() == Table.TableType.Memory)
			throw unsupported(target, "Widening columns while versioning is disabled or changing");
		Table normalized = target.clone();
		List<Column> widened = new ArrayList<>();
		for (Column after : target.getColumns()) {
			Column before = original.getColumns().get(after.getName());
			if (before == null || java.util.Objects.equals(before.getLength(), after.getLength())) continue;
			DataType type = before.getDataType();
			if (type != DataType.VARCHAR && type != DataType.NVARCHAR && type != DataType.VARBINARY)
				throw unsupported(target, "Widening a non-variable-length column: " + after.getName());
			long maximum = type == DataType.NVARCHAR ? 4000 : 8000;
			if (before.getLength() == null || after.getLength() == null || before.getLength() <= 0
					|| after.getLength() <= before.getLength() || after.getLength() > maximum)
				throw unsupported(target, "Shrinking or changing a column to/from MAX: " + after.getName());
			if (before.isIdentity() || before.isHidden()
					|| before.getFormula() != null && !before.getFormula().isEmpty()
					|| before.getMaskingFunction() != null && !before.getMaskingFunction().isEmpty()
					|| before.getDataTypeName() != null && !before.getDataTypeName().equalsIgnoreCase(type.name()))
				throw unsupported(target, "Widening a column with generated or vendor-specific behavior: " + after.getName());
			for (var entry : before.getSpecifics().entrySet()) {
				if (!entry.getKey().equalsIgnoreCase("is_sparse")
						|| !("false".equalsIgnoreCase(entry.getValue()) || "0".equals(entry.getValue())))
					throw unsupported(target, "Widening a column with advanced specifics: " + after.getName());
			}
			normalized.getColumns().get(after.getName()).setLength(before.getLength());
			widened.add(after);
		}
		if (widened.isEmpty() || !normalized.equals(original))
			throw unsupported(target, "Combining length expansion with other temporal table changes");
		List<SqlOperation> result = new ArrayList<>();
		for (Column column : widened) {
			// ALTER COLUMN restates type, collation and nullability only. Existing
			// defaults, checks and comments must not be recreated inline.
			Column definition = new Column(column.getName()).setDataType(column.getDataType())
					.setLength(column.getLength());
			var builder = createSqlBuilder();
			builder.alter().table().space().name(target, getOptions().isDecorateSchemaName())
					._add(" ALTER COLUMN ").name(column).space().definitionForAlterColumn(definition);
			if (column.getCollation() != null && !column.getCollation().isEmpty())
				builder._add(" COLLATE ").name(column.getCollation());
			builder._add(column.isNotNull() ? " NOT NULL" : " NULL");
			result.add(createOperation(builder.toString(), SqlType.ALTER, original.getColumns().get(column.getName()), column));
		}
		return result;
	}

	private boolean expectedTemporalType(String value, boolean enabled) {
		return value == null || (enabled ? "SYSTEM_VERSIONED_TEMPORAL_TABLE" : "NON_TEMPORAL_TABLE").equals(value);
	}

	private IllegalArgumentException unsupported(Table table, String change) {
		return new IllegalArgumentException(change + " is unsupported by automatic temporal ALTER; "
				+ "migrate current and history tables explicitly: " + table.getName());
	}
}
