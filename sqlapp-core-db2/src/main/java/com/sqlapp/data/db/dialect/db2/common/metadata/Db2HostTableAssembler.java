/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common.metadata;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.sqlapp.data.db.dialect.db2.common.AbstractDb2HostDialect.Platform;
import com.sqlapp.data.db.dialect.db2.common.Db2HostTables;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbInfo;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TemporalPeriod;
import com.sqlapp.data.schemas.TemporalPeriodType;
import com.sqlapp.jdbc.ExResultSet;

/**
 * Maps table-level rows of tables.sql (one row per table, or one per partition)
 * and completes temporal periods, versioning and partitioning once the columns
 * are loaded. Every unrecognized catalog value fails instead of being dropped.
 */
final class Db2HostTableAssembler {
	static final String SYSTEM_TIME = "SYSTEM_TIME";
	static final String BUSINESS_TIME = "BUSINESS_TIME";

	/** Table attributes that need the loaded columns. */
	static final class Pending {
		String periodName, periodBegin, periodEnd;
		String historySchema, historyTable, versioningStatus;
		boolean extraRow;
		String partitionType, partitionKeys;
		int partitionKeyCount;
		final List<Partition> partitions = new ArrayList<>();
	}

	private Db2HostTableAssembler() {
	}

	/** Reads the optional table-level columns of the first row of a table. */
	static Pending readTable(ExResultSet rs, Table table, Platform platform) throws SQLException {
		String unsupported = Db2HostSupplementalReaders.optional(rs, "UNSUPPORTED_TABLE");
		if (unsupported != null && !unsupported.isBlank())
			throw new SQLException("Unsupported table attributes: " + unsupported + " (" + table.getName() + ")");
		var pending = new Pending();
		String kind = Db2HostSupplementalReaders.optional(rs, "TABLE_KIND");
		if ("H".equals(kind))
			table.getSpecifics().put(Db2HostTables.TABLE_KIND, "HISTORY");
		else if ("R".equals(kind))
			table.getSpecifics().put(Db2HostTables.TABLE_KIND, "ARCHIVE");
		String control = Db2HostSupplementalReaders.optional(rs, "ACCESS_CONTROL");
		if (control != null)
			table.getSpecifics().put(Db2HostSecurityReaders.ACCESS_CONTROL, control.strip());
		if (platform == Platform.ZOS)
			readZos(rs, table);
		else {
			String system = Db2HostSupplementalReaders.optional(rs, "SYSTEM_NAME");
			if (system != null && !system.isBlank() && !system.equals(table.getName()))
				table.getSpecifics().put(Db2HostTables.SYSTEM_NAME, system);
			pending.periodName = Db2HostSupplementalReaders.optional(rs, "PERIOD_NAME");
			pending.periodBegin = Db2HostSupplementalReaders.optional(rs, "PERIOD_BEGIN");
			pending.periodEnd = Db2HostSupplementalReaders.optional(rs, "PERIOD_END");
			pending.versioningStatus = Db2HostSupplementalReaders.optional(rs, "VERSIONING_STATUS");
			pending.partitionType = blankToNull(Db2HostSupplementalReaders.optional(rs, "PARTITION_TYPE"));
			pending.partitionKeys = Db2HostSupplementalReaders.optional(rs, "PARTITION_KEYS");
		}
		pending.historySchema = blankToNull(Db2HostSupplementalReaders.optional(rs, "HISTORY_SCHEMA"));
		pending.historyTable = blankToNull(Db2HostSupplementalReaders.optional(rs, "HISTORY_TABLE"));
		pending.extraRow = "Y".equals(Db2HostSupplementalReaders.optional(rs, "VERSIONING_EXTRA_ROW"));
		if (platform == Platform.ZOS && Db2HostSupplementalReaders.has(rs, "PARTITION_KEY_COUNT")) {
			Integer count = rs.getInteger("PARTITION_KEY_COUNT");
			pending.partitionKeyCount = count == null ? 0 : count;
			if (pending.partitionKeyCount > 0)
				pending.partitionType = "R";
		}
		return pending;
	}

	private static void readZos(ExResultSet rs, Table table) throws SQLException {
		String database = blankToNull(Db2HostSupplementalReaders.optional(rs, "TS_DATABASE"));
		String tableSpace = blankToNull(Db2HostSupplementalReaders.optional(rs, "TS_NAME"));
		boolean implicitDatabase = "Y".equals(Db2HostSupplementalReaders.optional(rs, "DB_IMPLICIT"));
		boolean implicitSpace = "Y".equals(Db2HostSupplementalReaders.optional(rs, "TS_IMPLICIT"));
		// Implicitly created objects are recreated implicitly; explicit ones are
		// named so the table does not move to the subsystem default.
		if (database != null && !implicitDatabase) {
			table.getSpecifics().put(Db2HostTables.DATABASE, database);
			if (tableSpace != null && !implicitSpace)
				table.setTableSpaceName(tableSpace);
		}
		String archive = blankToNull(Db2HostSupplementalReaders.optional(rs, "ARCHIVE_TABLE"));
		if (archive != null) {
			String archiveSchema = blankToNull(Db2HostSupplementalReaders.optional(rs, "ARCHIVE_SCHEMA"));
			if (archiveSchema == null)
				throw new SQLException("Archive table without schema: " + table.getName());
			table.getSpecifics().put(Db2HostTables.ARCHIVE_SCHEMA, archiveSchema);
			table.getSpecifics().put(Db2HostTables.ARCHIVE_TABLE, archive);
		}
		if (yes(rs, "APPEND_FLAG", "N"))
			table.getSpecifics().put(Db2HostTables.APPEND, "Y");
		if (yes(rs, "VOLATILE_FLAG", "N"))
			table.getSpecifics().put(Db2HostTables.VOLATILE, "Y");
		if (yes(rs, "DATA_CAPTURE", " "))
			table.getSpecifics().put(Db2HostTables.DATA_CAPTURE, "Y");
		String audit = Db2HostSupplementalReaders.optional(rs, "AUDIT_MODE");
		if (audit != null && !audit.isBlank())
			table.getSpecifics().put(Db2HostTables.AUDIT, switch (audit) {
			case "C" -> "CHANGES";
			case "A" -> "ALL";
			default -> throw new SQLException("Unknown audit option " + audit + ": " + table.getName());
			});
		String validproc = blankToNull(Db2HostSupplementalReaders.optional(rs, "VALIDPROC"));
		if (validproc != null)
			table.getSpecifics().put(Db2HostTables.VALIDPROC, validproc);
		String encoding = Db2HostSupplementalReaders.optional(rs, "ENCODING_SCHEME");
		if (encoding != null && !encoding.isBlank())
			table.getSpecifics().put(Db2HostTables.ENCODING_SCHEME, switch (encoding) {
			case "E" -> "EBCDIC";
			case "A" -> "ASCII";
			case "U" -> "UNICODE";
			default -> throw new SQLException("Unsupported table encoding scheme: " + encoding);
			});
	}

	/**
	 * Reads a Y/no flag; {@code no} is the catalog's negative value (blank is also
	 * accepted). Any other value fails.
	 */
	private static boolean yes(ExResultSet rs, String name, String no) throws SQLException {
		String value = Db2HostSupplementalReaders.optional(rs, name);
		if (value == null || value.isBlank() || value.equals(no.strip()))
			return false;
		if (!"Y".equals(value))
			throw new SQLException("Invalid catalog flag " + name + ": " + value);
		return true;
	}

	/** Adds the partition described by one tables.sql row, if any. */
	static void readPartition(ExResultSet rs, Table table, Pending pending, Platform platform) throws SQLException {
		if (pending.partitionType == null)
			return;
		if (platform == Platform.ZOS) {
			Integer number = rs.getInteger("PARTITION_NUMBER");
			if (number == null)
				throw new SQLException("Missing partition of table-controlled partitioning: " + table.getName());
			String limit = rs.getString("PARTITION_HIGH_VALUE");
			if (limit == null || limit.isBlank() || limit.indexOf('\0') >= 0)
				throw new SQLException("Missing partition limit key: " + table.getName() + " partition " + number);
			var partition = new Partition(String.valueOf(number));
			partition.setHighValue(limit.strip());
			partition.setHighValueInclusive(true);
			pending.partitions.add(partition);
			return;
		}
		String type = pending.partitionType;
		if (!"R".equals(type) && !"H".equals(type))
			throw new SQLException("Unsupported partitioning type " + type + ": " + table.getName());
		String rowType = blankToNull(Db2HostSupplementalReaders.optional(rs, "PARTITION_TYPE"));
		if (!type.equals(rowType))
			throw new SQLException("Inconsistent partitioning rows: " + table.getName());
		String name = blankToNull(Db2HostSupplementalReaders.optional(rs, "PARTITION_NAME"));
		var partition = new Partition(name == null ? "P" + rs.getInteger("PARTITION_NUMBER") : name);
		if ("R".equals(type)) {
			String low = rs.getString("PARTITION_LOW_VALUE"), high = rs.getString("PARTITION_HIGH_VALUE");
			if (low == null || low.isBlank() || high == null || high.isBlank())
				throw new SQLException("Missing range partition bounds: " + table.getName());
			partition.setLowValue(low.strip());
			partition.setHighValue(high.strip());
			partition.setLowValueInclusive(inclusive(rs, "PARTITION_LOW_INCLUSIVE"));
			partition.setHighValueInclusive(inclusive(rs, "PARTITION_HIGH_INCLUSIVE"));
		}
		pending.partitions.add(partition);
	}

	private static boolean inclusive(ExResultSet rs, String name) throws SQLException {
		String value = Db2HostSupplementalReaders.optional(rs, name);
		if (!"Y".equals(value) && !"N".equals(value))
			throw new SQLException("Invalid partition bound inclusiveness " + name + ": " + value);
		return "Y".equals(value);
	}

	/** Completes the table once its columns are attached. */
	static void complete(Table table, Pending pending, Platform platform) {
		if (pending == null)
			pending = new Pending();
		completePeriods(table, pending, platform);
		completeVersioning(table, pending, platform);
		completePartitioning(table, pending, platform);
		for (Column column : table.getColumns())
			removeTransient(column);
	}

	private static void completePeriods(Table table, Pending pending, Platform platform) {
		if (platform == Platform.ZOS) {
			Map<String, String> roles = new TreeMap<>();
			for (Column column : table.getColumns()) {
				String role = column.getSpecifics().get(Db2HostTables.TMP_PERIOD);
				if (role != null && roles.put(role, column.getName()) != null)
					throw new IllegalArgumentException("Duplicate period column role " + role + ": " + table.getName());
			}
			addPeriod(table, SYSTEM_TIME, TemporalPeriodType.SYSTEM_TIME, roles.get("S"), roles.get("T"));
			addPeriod(table, BUSINESS_TIME, TemporalPeriodType.APPLICATION_TIME, roles.get("B"), roles.get("C"));
		} else if (pending.periodName != null && !pending.periodName.isBlank()) {
			if (!SYSTEM_TIME.equals(pending.periodName))
				throw new IllegalArgumentException("Unsupported IBM i period: " + pending.periodName);
			if (table.getColumns().get(pending.periodBegin) == null
					|| table.getColumns().get(pending.periodEnd) == null)
				throw new IllegalArgumentException("SYSTEM_TIME period columns not found: " + table.getName());
			addPeriod(table, SYSTEM_TIME, TemporalPeriodType.SYSTEM_TIME, pending.periodBegin, pending.periodEnd);
		}
		for (Column column : table.getColumns()) {
			String generation = column.getSpecifics().get(Db2HostTables.GENERATION);
			if (("ROW_BEGIN".equals(generation) || "ROW_END".equals(generation))
					&& !isSystemPeriodColumn(table, column.getName(), "ROW_BEGIN".equals(generation)))
				throw new IllegalArgumentException(
						"Row-begin/end column outside a SYSTEM_TIME period is not supported: " + column.getName());
		}
	}

	private static void addPeriod(Table table, String name, TemporalPeriodType type, String start, String end) {
		if (start == null && end == null)
			return;
		if (start == null || end == null)
			throw new IllegalArgumentException("Incomplete " + name + " period: " + table.getName());
		var period = new TemporalPeriod(name);
		period.setPeriodType(type);
		period.setStartColumnName(start);
		period.setEndColumnName(end);
		table.getTemporalPeriods().add(period);
	}

	static boolean isSystemPeriodColumn(Table table, String column, boolean start) {
		for (TemporalPeriod period : table.getTemporalPeriods())
			if (period.getPeriodType() == TemporalPeriodType.SYSTEM_TIME
					&& column.equals(start ? period.getStartColumnName() : period.getEndColumnName()))
				return true;
		return false;
	}

	private static void completeVersioning(Table table, Pending pending, Platform platform) {
		String transactionId = null;
		for (Column column : table.getColumns())
			if ("TRANSACTION_START_ID".equals(column.getSpecifics().get(Db2HostTables.GENERATION))) {
				if (transactionId != null)
					throw new IllegalArgumentException("Multiple transaction-start-ID columns: " + table.getName());
				transactionId = column.getName();
			}
		if (pending.historyTable != null) {
			if (platform == Platform.IBM_I && !"E".equals(pending.versioningStatus))
				throw new IllegalArgumentException(
						"Versioning relationship is defined but not established: " + table.getName());
			if (table.getTemporalPeriods().stream().noneMatch(p -> p.getPeriodType() == TemporalPeriodType.SYSTEM_TIME))
				throw new IllegalArgumentException("Versioned table without SYSTEM_TIME period: " + table.getName());
			table.toSystemVersioning().setPeriodName(SYSTEM_TIME)
					.setHistoryTableSchemaName(
							pending.historySchema == null ? table.getSchemaName() : pending.historySchema)
					.setHistoryTableName(pending.historyTable).setEnable(true);
			if (pending.extraRow)
				table.getSpecifics().put(Db2HostTables.VERSIONING_EXTRA_ROW, "Y");
		}
		if (transactionId != null)
			table.toSystemVersioning().setTransactionIdColumnName(transactionId);
	}

	private static void completePartitioning(Table table, Pending pending, Platform platform) {
		if (pending.partitionType == null) {
			for (Column column : table.getColumns())
				if (column.getSpecifics().get(Db2HostTables.TMP_PARTITION_KEY_SEQ) != null)
					throw new IllegalArgumentException(
							"Partition key column on an unpartitioned table: " + table.getName());
			return;
		}
		if (pending.partitions.isEmpty())
			throw new IllegalArgumentException("Partitioned table without partitions: " + table.getName());
		var partitioning = new Partitioning(
				"H".equals(pending.partitionType) ? PartitioningType.Hash : PartitioningType.Range);
		if (platform == Platform.ZOS) {
			Map<Integer, Column> keys = new TreeMap<>();
			for (Column column : table.getColumns()) {
				String seq = column.getSpecifics().get(Db2HostTables.TMP_PARTITION_KEY_SEQ);
				if (seq != null && keys.put(Integer.valueOf(seq), column) != null)
					throw new IllegalArgumentException("Duplicate partition key position: " + table.getName());
			}
			if (keys.size() != pending.partitionKeyCount || (!keys.isEmpty() && keys.keySet().stream()
					.mapToInt(Integer::intValue).max().getAsInt() != pending.partitionKeyCount))
				throw new IllegalArgumentException("Incomplete partitioning key: " + table.getName());
			for (Column column : keys.values()) {
				var reference = partitioning.getPartitioningColumns().add(column.getName());
				reference.setOrder(
						"D".equals(column.getSpecifics().get(Db2HostTables.TMP_PARTITION_KEY_ORDER)) ? Order.Desc
								: Order.Asc);
			}
		} else {
			if (pending.partitionKeys == null || pending.partitionKeys.isBlank())
				throw new IllegalArgumentException("Missing partitioning key list: " + table.getName());
			for (String token : pending.partitionKeys.split(",")) {
				String name = token.strip();
				if (name.length() > 1 && name.startsWith("\"") && name.endsWith("\""))
					name = name.substring(1, name.length() - 1).replace("\"\"", "\"");
				if (table.getColumns().get(name) == null)
					throw new IllegalArgumentException(
							"Partitioning key does not name a column of " + table.getName() + ": " + token);
				partitioning.getPartitioningColumns().add(name);
			}
		}
		if ("H".equals(pending.partitionType))
			partitioning.setPartitionSize(pending.partitions.size());
		else
			pending.partitions.forEach(partitioning.getPartitions()::add);
		table.setPartitioning(partitioning);
	}

	private static void removeTransient(Column column) {
		if (column.getSpecifics().get(Db2HostTables.TMP_PERIOD) == null
				&& column.getSpecifics().get(Db2HostTables.TMP_PARTITION_KEY_SEQ) == null)
			return;
		var retained = new DbInfo();
		column.getSpecifics().forEach((key, value) -> {
			if (!key.startsWith("DB2_HOST_TMP_"))
				retained.put(key, value);
		});
		column.setSpecifics(retained);
	}

	static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
