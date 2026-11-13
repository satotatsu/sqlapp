/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import java.util.List;

import com.sqlapp.data.db.dialect.db2.common.AbstractDb2HostDialect.Platform;
import com.sqlapp.data.db.dialect.db2.sql.Db2CreateTableFactory;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TemporalPeriod;
import com.sqlapp.data.schemas.TemporalPeriodType;

/**
 * Host CREATE TABLE. Besides columns and keys it recreates the modeled physical
 * and temporal attributes the host products expose: z/OS {@code IN db.ts} /
 * {@code IN DATABASE}, CCSID encoding scheme and table-controlled range
 * partitioning; IBM i FOR SYSTEM NAME and range/hash partitioning; SYSTEM_TIME
 * (and z/OS BUSINESS_TIME) periods and system-period versioning. Anything the
 * target cannot express fails instead of being dropped.
 */
public class Db2HostCreateTableFactory extends Db2CreateTableFactory {
	private static final String SYSTEM_TIME = "SYSTEM_TIME";
	private static final String BUSINESS_TIME = "BUSINESS_TIME";

	private Platform platform() {
		return ((AbstractDb2HostDialect) getDialect()).getPlatform();
	}

	@Override
	public List<SqlOperation> createSql(Table table) {
		Platform platform = platform();
		if (getOptions().isCreateIfNotExists())
			throw new IllegalArgumentException("IF NOT EXISTS is outside basic Db2 host CREATE support");
		if (!table.getDefinition().isEmpty() || table.getIndexTableSpaceName() != null
				|| table.getLobTableSpaceName() != null || table.getCollation() != null
				|| com.sqlapp.data.db.dialect.db2.Db2Nickname.isNickname(table))
			throw new IllegalArgumentException(
					"Basic Db2 host CREATE supports modeled columns, keys and host attributes only: "
							+ table.getName());
		if (platform != Platform.ZOS
				&& (table.getTableSpaceName() != null || table.getSpecifics().get(Db2HostTables.DATABASE) != null
						|| table.getSpecifics().get(Db2HostTables.ENCODING_SCHEME) != null))
			throw new IllegalArgumentException(
					"Table spaces and CCSID encoding schemes require Db2 for z/OS: " + table.getName());
		if (platform != Platform.IBM_I && table.getSpecifics().get(Db2HostTables.SYSTEM_NAME) != null)
			throw new IllegalArgumentException("FOR SYSTEM NAME requires Db2 for i: " + table.getName());
		if (table.getTableSpaceName() != null && table.getSpecifics().get(Db2HostTables.DATABASE) == null)
			throw new IllegalArgumentException("A z/OS table space requires its database: " + table.getName());
		for (var constraint : table.getConstraints()) {
			if (!constraint.isEnable())
				throw new IllegalArgumentException(
						"Non-enforced constraints require extended Db2 host support: " + constraint.getName());
		}
		validateTemporal(table, platform);
		if (table.getPartitioning() != null)
			validatePartitioning(table, table.getPartitioning(), platform);
		return super.createSql(table);
	}

	@Override
	protected void addCreateObject(final Table table, final Db2SqlBuilder builder) {
		builder.create().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		String system = table.getSpecifics().get(Db2HostTables.SYSTEM_NAME);
		if (system != null) {
			if (!system.matches("[A-Z#@$][A-Z0-9#@$_]{0,9}"))
				throw new IllegalArgumentException("Invalid IBM i system name: " + system);
			builder._add(" FOR SYSTEM NAME ")._add(system);
		}
	}

	@Override
	protected void addOption(final Table table, final Db2SqlBuilder builder) {
		if (table.getPartitioning() != null)
			addPartitioning(table.getPartitioning(), builder);
		if (platform() != Platform.ZOS)
			return;
		String database = table.getSpecifics().get(Db2HostTables.DATABASE);
		if (database != null) {
			builder.lineBreak()._add("IN ");
			if (table.getTableSpaceName() == null)
				builder._add("DATABASE ").name(database);
			else
				builder.name(database)._add(".").name(table.getTableSpaceName());
		}
		String encoding = table.getSpecifics().get(Db2HostTables.ENCODING_SCHEME);
		if (encoding != null) {
			if (!List.of("EBCDIC", "ASCII", "UNICODE").contains(encoding))
				throw new IllegalArgumentException("Invalid z/OS encoding scheme: " + encoding);
			builder.lineBreak()._add("CCSID ")._add(encoding);
		}
	}

	private void addPartitioning(Partitioning partitioning, Db2SqlBuilder builder) {
		boolean hash = partitioning.getPartitioningType() == PartitioningType.Hash;
		builder.lineBreak()._add(hash ? "PARTITION BY HASH (" : "PARTITION BY RANGE (");
		int count = 0;
		for (var column : partitioning.getPartitioningColumns()) {
			builder.comma(count++ > 0).name(column.getName());
			if (column.getOrder() == Order.Desc)
				builder._add(" DESC");
		}
		builder._add(")");
		if (hash) {
			builder._add(" INTO ")._add(partitioning.getPartitionSize())._add(" PARTITIONS");
			return;
		}
		builder.lineBreak()._add("(");
		builder.appendIndent(1);
		count = 0;
		for (Partition partition : partitioning.getPartitions()) {
			builder.lineBreak().comma(count++ > 0)._add("PARTITION ");
			if (platform() == Platform.ZOS)
				builder._add(partition.getName());
			else {
				builder.name(partition.getName());
				builder._add(" STARTING FROM (")._add(partition.getLowValue())._add(")")
						._add(partition.isLowValueInclusive() ? " INCLUSIVE" : " EXCLUSIVE");
			}
			builder._add(" ENDING AT (")._add(partition.getHighValue())._add(")")
					._add(partition.isHighValueInclusive() ? " INCLUSIVE" : " EXCLUSIVE");
		}
		builder.appendIndent(-1);
		builder.lineBreak()._add(")");
	}

	private void validatePartitioning(Table table, Partitioning partitioning, Platform platform) {
		boolean hash = partitioning.getPartitioningType() == PartitioningType.Hash;
		if ((partitioning.getPartitioningType() != PartitioningType.Range && !hash)
				|| partitioning.getSubPartitioningType() != null || !partitioning.getSubPartitioningColumns().isEmpty()
				|| partitioning.getPartitioningColumns().isEmpty())
			throw new IllegalArgumentException(
					"Host partitioning requires RANGE (or IBM i HASH) key columns without subpartitions: "
							+ table.getName());
		if (hash && (platform != Platform.IBM_I || partitioning.getPartitionSize() == null
				|| partitioning.getPartitionSize() < 1 || !partitioning.getPartitions().isEmpty()))
			throw new IllegalArgumentException(
					"HASH partitioning requires Db2 for i and a positive partition count: " + table.getName());
		if (!hash && partitioning.getPartitions().isEmpty())
			throw new IllegalArgumentException("Range partitioning requires explicit partitions: " + table.getName());
		for (var column : partitioning.getPartitioningColumns()) {
			if (!column.getStatement().isEmpty() || column.isIncludedColumn() || column.getNullsOrder() != null
					|| table.getColumns().get(column.getName()) == null
					|| (column.getOrder() == Order.Desc && platform != Platform.ZOS))
				throw new IllegalArgumentException("Unsupported partitioning key column: " + column.getName());
		}
		for (Partition partition : partitioning.getPartitions()) {
			if (!partition.getSubPartitions().isEmpty() || partition.getTableSpaceName() != null
					|| partition.getIndexTableSpaceName() != null || partition.getLobTableSpaceName() != null
					|| blank(partition.getHighValue()))
				throw new IllegalArgumentException("Unsupported partition definition: " + partition.getName());
			if (platform == Platform.ZOS) {
				if (partition.getName() == null || !partition.getName().matches("[1-9][0-9]{0,4}")
						|| !blank(partition.getLowValue()) || !partition.isHighValueInclusive())
					throw new IllegalArgumentException(
							"z/OS partitions require a number and an inclusive ENDING AT limit: "
									+ partition.getName());
			} else if (blank(partition.getLowValue()) || blank(partition.getName()))
				throw new IllegalArgumentException(
						"IBM i range partitions require a name and both bounds: " + partition.getName());
		}
	}

	@Override
	protected void addConstraintDefinitions(final Table table, final Db2SqlBuilder builder) {
		super.addConstraintDefinitions(table, builder);
		for (final TemporalPeriod period : table.getTemporalPeriods()) {
			builder.lineBreak().comma()._add("PERIOD ")._add(period.getName()).space()._add("(")
					.name(period.getStartColumnName()).comma().name(period.getEndColumnName())._add(")");
		}
	}

	@Override
	protected void addOtherDefinitions(final Table table, final List<SqlOperation> result) {
		super.addOtherDefinitions(table, result);
		var versioning = table.getSystemVersioning();
		if (versioning == null || !versioning.isEnable())
			return;
		Db2SqlBuilder builder = createSqlBuilder();
		builder._add("ALTER TABLE ").name(table, true)._add(" ADD VERSIONING USE HISTORY TABLE ");
		var history = new Table(versioning.getHistoryTableName());
		history.setSchemaName(versioning.getHistoryTableSchemaName());
		builder.name(history, true);
		if ("Y".equals(table.getSpecifics().get(Db2HostTables.VERSIONING_EXTRA_ROW)))
			builder._add(" ON DELETE ADD EXTRA ROW");
		addSql(result, builder, SqlType.ALTER, table);
	}

	/** Versioning ALTERs; schema CREATE moves them after every table. */
	static boolean isVersioning(SqlOperation operation) {
		return operation.getSqlType() == SqlType.ALTER && operation.getSqlText() != null
				&& operation.getSqlText().contains(" ADD VERSIONING USE HISTORY TABLE ");
	}

	private void validateTemporal(Table table, Platform platform) {
		TemporalPeriod system = null;
		for (TemporalPeriod period : table.getTemporalPeriods()) {
			boolean isSystem = period.getPeriodType() == TemporalPeriodType.SYSTEM_TIME;
			String expected = isSystem ? SYSTEM_TIME : BUSINESS_TIME;
			if (!expected.equals(period.getName()) || (!isSystem && platform != Platform.ZOS)
					|| period.getPeriodType() == null)
				throw new IllegalArgumentException("Unsupported host period: " + period.getName());
			if (table.getColumns().get(period.getStartColumnName()) == null
					|| table.getColumns().get(period.getEndColumnName()) == null)
				throw new IllegalArgumentException("Period columns not found: " + period.getName());
			if (isSystem) {
				if (system != null)
					throw new IllegalArgumentException("Duplicate SYSTEM_TIME period: " + table.getName());
				system = period;
			}
		}
		if (table.getTemporalPeriods().stream().map(TemporalPeriod::getName).distinct().count() != table
				.getTemporalPeriods().size())
			throw new IllegalArgumentException("Duplicate period: " + table.getName());
		var versioning = table.getSystemVersioning();
		if (versioning != null && versioning.isEnable() && (system == null || blank(versioning.getHistoryTableName())
				|| (versioning.getPeriodName() != null && !SYSTEM_TIME.equals(versioning.getPeriodName()))))
			throw new IllegalArgumentException(
					"System-period versioning requires SYSTEM_TIME and a history table: " + table.getName());
		for (Column column : table.getColumns()) {
			String generation = column.getSpecifics().get(Db2HostTables.GENERATION);
			if (generation == null)
				continue;
			boolean ok = switch (generation) {
			case "ROW_BEGIN" -> system != null && column.getName().equals(system.getStartColumnName());
			case "ROW_END" -> system != null && column.getName().equals(system.getEndColumnName());
			case "TRANSACTION_START_ID" ->
				versioning != null && column.getName().equals(versioning.getTransactionIdColumnName());
			default -> false;
			};
			if (!ok)
				throw new IllegalArgumentException(
						"Generated temporal column is not represented by the table's period/versioning: "
								+ column.getName());
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
