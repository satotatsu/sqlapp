/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.oracle;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.oracle.OracleContainer;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class OraclePartitionMaintenanceTest {
	@Test
	void finiteTextIotAddAndDrop() throws Exception {
		verifyFiniteTextIot(true);
	}

	@Test
	void finiteTextIotSplitAndMerge() throws Exception {
		verifyFiniteTextIot(false);
	}

	@Test
	void finiteTupleIotAddAndDrop() throws Exception {
		verifyFiniteTextIot(true, true);
	}

	@Test
	void finiteTupleIotSplitAndMerge() throws Exception {
		verifyFiniteTextIot(false, true);
	}

	private void verifyFiniteTextIot(boolean add) throws Exception {
		verifyFiniteTextIot(add, false);
	}

	private void verifyFiniteTextIot(boolean add, boolean tuple) throws Exception {
		String name = (add ? "TEXT_IOT_ADD" : "TEXT_IOT_SPLIT") + (tuple ? "_TUPLE" : "");
		String first = tuple ? "'B', MAXVALUE" : "'B'", max = tuple ? "'MAXVALUE', MAXVALUE" : "'MAXVALUE'",
				last = tuple ? "'ZZZ', MAXVALUE" : "'ZZZ'";
		try (Connection connection = ORACLE.createConnection(""); var sql = connection.createStatement()) {
			boolean created = false;
			try {
				sql.execute("CREATE TABLE " + name + " (ID VARCHAR2(30), VALUE NUMBER, PRIMARY KEY("
						+ (tuple ? "ID, VALUE" : "ID") + ")) ORGANIZATION INDEX PARTITION BY RANGE("
						+ (tuple ? "ID, VALUE" : "ID") + ") (PARTITION P1 VALUES LESS THAN(" + first
						+ "), PARTITION P2 VALUES LESS THAN(" + (add ? max : last) + "))");
				created = true;
				sql.execute("CREATE INDEX " + name + "_IX ON " + name + "(VALUE) LOCAL (PARTITION IP1, PARTITION IP2)");
				List<String> ids = add ? List.of("A", "L") : List.of("A", "L", "MAXVALUE", "Z");
				for (String id : ids)
					sql.execute("INSERT INTO " + name + " VALUES ('" + id + "', " + id.length() + ")");
				Table before = read(connection, name), changed = before.clone();
				if (add) {
					changed.getPartitioning().getPartitions()
							.add(new com.sqlapp.data.schemas.Partition("P3").setHighValue(last));
					var segment = new com.sqlapp.data.schemas.Partition("IP3").setHighValue(last);
					segment.getSpecifics().put("oracle.index.status", "USABLE");
					changed.getIndexes().get(name + "_IX").getPartitioning().getPartitions().add(segment);
				} else {
					for (var info : List.of(changed.getPartitioning(),
							changed.getIndexes().get(name + "_IX").getPartitioning())) {
						var low = info.getPartitions().get(1);
						var high = low.clone();
						low.setName(info.getIndex() == null ? "LOW_PART" : "ILOW").setHighValue(max);
						high.setName(info.getIndex() == null ? "HIGH_PART" : "IHIGH");
						info.getPartitions().add(high);
					}
				}
				execute(connection, before, changed);
				if (add)
					for (String id : List.of("MAXVALUE", "Z"))
						sql.execute("INSERT INTO " + name + " VALUES ('" + id + "', " + id.length() + ")");
				verifyFiniteTextIotState(connection, name, List.of("A", "L", "MAXVALUE", "Z"),
						List.of(first, max, last),
						add ? List.of("IP1", "IP2", "IP3") : List.of("IP1", "ILOW", "IHIGH"));
				Table actual = read(connection, name), restored = actual.clone();
				for (var info : List.of(restored.getPartitioning(),
						restored.getIndexes().get(name + "_IX").getPartitioning())) {
					if (add)
						info.getPartitions().remove(2);
					else {
						var merged = info.getPartitions().remove(2);
						info.getPartitions().remove(1);
						merged.setName(info.getIndex() == null ? "P2" : "IP2");
						info.getPartitions().add(merged);
					}
				}
				execute(connection, actual, restored);
				verifyFiniteTextIotState(connection, name, add && tuple ? List.of("A", "L", "MAXVALUE") : ids,
						List.of(first, add ? max : last), List.of("IP1", "IP2"));
			} finally {
				if (created)
					sql.execute("DROP TABLE " + name + " PURGE");
			}
		}
	}

	private void verifyFiniteTextIotState(Connection connection, String name, List<String> ids, List<String> bounds,
			List<String> indexNames) throws Exception {
		try (var sql = connection.createStatement();
				var rows = sql.executeQuery("SELECT ID, VALUE FROM " + name + " ORDER BY ID")) {
			for (String id : ids) {
				assertTrue(rows.next());
				assertEquals(id, rows.getString(1));
				assertEquals(id.length(), rows.getInt(2));
			}
			assertFalse(rows.next());
		}
		Table actual = read(connection, name);
		assertEquals(bounds,
				actual.getPartitioning().getPartitions().stream().map(part -> part.getHighValue()).toList());
		var segments = actual.getIndexes().get(name + "_IX").getPartitioning().getPartitions();
		assertEquals(indexNames, segments.stream().map(part -> part.getName()).toList());
		for (var segment : segments)
			assertEquals("USABLE", segment.getSpecifics().get("oracle.index.status"));
		try (var sql = connection.prepareStatement(
				"SELECT IOT_TYPE, (SELECT COUNT(*) FROM USER_CONSTRAINTS C WHERE C.TABLE_NAME=T.TABLE_NAME AND C.CONSTRAINT_TYPE='P' AND C.STATUS='ENABLED') AS PK_COUNT, (SELECT COUNT(*) FROM USER_INDEXES I WHERE I.TABLE_NAME=T.TABLE_NAME AND I.STATUS NOT IN ('VALID','N/A')) AS BAD_INDEXES FROM USER_TABLES T WHERE T.TABLE_NAME=?")) {
			sql.setString(1, name);
			try (var rows = sql.executeQuery()) {
				assertTrue(rows.next());
				assertEquals("IOT", rows.getString("IOT_TYPE"));
				assertEquals(1, rows.getInt("PK_COUNT"));
				assertEquals(0, rows.getInt("BAD_INDEXES"));
			}
		}
	}

	private static final OracleContainer ORACLE = ReusableTestcontainers
			.configure(new OracleContainer(OracleTestEnvironment.image()));

	@BeforeAll
	static void start() {
		ReusableTestcontainers.start(ORACLE);
	}

	@AfterAll
	static void stop() {
		ReusableTestcontainers.stop(ORACLE);
	}

	@Test
	void heapSplitAndMergePreserveRowsAndLocalIndexes() throws Exception {
		verify(false);
	}

	@Test
	void iotSplitAndMergeExecuteLocalIndexMaintenance() throws Exception {
		verify(true);
	}

	@Test
	void heapAddAndDropMaintainLocalIndexesAndDeleteOnlyDroppedRows() throws Exception {
		verifyAddDrop(false);
	}

	@Test
	void iotAddAndDropMaintainLocalIndexesAndDeleteOnlyDroppedRows() throws Exception {
		verifyAddDrop(true);
	}

	private void verifyAddDrop(boolean iot) throws Exception {
		String name = iot ? "APPEND_IOT" : "APPEND_HEAP";
		try (Connection connection = ORACLE.createConnection(""); var sql = connection.createStatement()) {
			try {
				sql.execute("CREATE TABLE " + name + " (ID NUMBER PRIMARY KEY, VALUE NUMBER) "
						+ (iot ? "ORGANIZATION INDEX " : "")
						+ "PARTITION BY RANGE(ID) (PARTITION P1 VALUES LESS THAN(100), PARTITION P2 VALUES LESS THAN(200))");
				sql.execute("CREATE INDEX " + name + "_IX ON " + name + "(VALUE) LOCAL (PARTITION IP1, PARTITION IP2)");
				for (int value : List.of(1, 120, 170))
					sql.execute("INSERT INTO " + name + " VALUES (" + value + ", " + value + ")");
				Table before = read(connection, name), added = before.clone();
				added.getPartitioning().getPartitions()
						.add(new com.sqlapp.data.schemas.Partition("P3").setHighValue("300"));
				var segment = new com.sqlapp.data.schemas.Partition("IP3").setHighValue("300");
				segment.getSpecifics().put("oracle.index.status", "USABLE");
				added.getIndexes().get(name + "_IX").getPartitioning().getPartitions().add(segment);
				execute(connection, before, added);
				verifyState(connection, name, List.of("IP1", "IP2", "IP3"));
				sql.execute("INSERT INTO " + name + " VALUES (220, 220)");
				Table actual = read(connection, name), dropped = actual.clone();
				dropped.getPartitioning().getPartitions().remove(2);
				dropped.getIndexes().get(name + "_IX").getPartitioning().getPartitions().remove(2);
				execute(connection, actual, dropped);
				verifyState(connection, name, List.of("IP1", "IP2"));
				try (var status = sql.executeQuery("SELECT COUNT(*) FROM USER_INDEXES WHERE TABLE_NAME = '" + name
						+ "' AND STATUS NOT IN ('VALID', 'N/A')")) {
					assertTrue(status.next());
					assertEquals(0, status.getInt(1), "Global indexes must remain usable");
				}
			} finally {
				sql.execute("DROP TABLE " + name + " PURGE");
			}
		}
	}

	private void verify(boolean iot) throws Exception {
		String name = iot ? "MAINT_IOT" : "MAINT_HEAP";
		try (Connection connection = ORACLE.createConnection(""); var sql = connection.createStatement()) {
			try {
				sql.execute("CREATE TABLE " + name + " (ID NUMBER PRIMARY KEY, VALUE NUMBER) "
						+ (iot ? "ORGANIZATION INDEX " : "")
						+ "PARTITION BY RANGE(ID) (PARTITION P1 VALUES LESS THAN(100), PARTITION P2 VALUES LESS THAN(200))");
				sql.execute("CREATE INDEX " + name + "_IX ON " + name + "(VALUE) LOCAL (PARTITION IP1, PARTITION IP2)");
				for (int value : List.of(1, 120, 170))
					sql.execute("INSERT INTO " + name + " VALUES (" + value + ", " + value + ")");
				Table before = read(connection, name);
				Table split = before.clone();
				var original = split.getPartitioning().getPartitions().get(1);
				var high = original.clone();
				original.setName("LOW_PART");
				original.setHighValue("150");
				high.setName("HIGH_PART");
				split.getPartitioning().getPartitions().add(high);
				var index = split.getIndexes().get(name + "_IX");
				var lowIndex = index.getPartitioning().getPartitions().get(1);
				var highIndex = lowIndex.clone();
				lowIndex.setName("ILOW");
				lowIndex.setHighValue("150");
				highIndex.setName("IHIGH");
				index.getPartitioning().getPartitions().add(highIndex);
				execute(connection, before, split);
				verifyState(connection, name, List.of("IP1", "ILOW", "IHIGH"));
				Table actual = read(connection, name);
				Table merged = actual.clone();
				var mergedPart = merged.getPartitioning().getPartitions().get(2).clone();
				mergedPart.setName("P2");
				merged.getPartitioning().getPartitions().remove(2);
				merged.getPartitioning().getPartitions().remove(1);
				merged.getPartitioning().getPartitions().add(mergedPart);
				var mergedIndex = merged.getIndexes().get(name + "_IX").getPartitioning().getPartitions();
				var mergedSegment = mergedIndex.get(2).clone();
				mergedSegment.setName("IP2");
				mergedIndex.remove(2);
				mergedIndex.remove(1);
				mergedIndex.add(mergedSegment);
				execute(connection, actual, merged);
				verifyState(connection, name, List.of("IP1", "IP2"));
			} finally {
				sql.execute("DROP TABLE " + name + " PURGE");
			}
		}
	}

	private Table read(Connection connection, String name) throws Exception {
		var dialect = DialectResolver.getInstance().getDialect(connection);
		OracleTestEnvironment.assertExpectedDialect(dialect);
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(connection.getMetaData().getUserName().toUpperCase(Locale.ROOT));
		reader.setObjectName(name);
		return reader.getAllFull(connection).get(0);
	}

	private void execute(Connection connection, Table before, Table after) throws Exception {
		var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var operations = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
		assertFalse(operations.isEmpty());
		try (var sql = connection.createStatement()) {
			for (var operation : operations)
				sql.execute(operation.getSqlText());
		}
	}

	private void verifyState(Connection connection, String name, List<String> partitions) throws Exception {
		try (var sql = connection.createStatement();
				var rows = sql.executeQuery("SELECT ID, VALUE FROM " + name + " ORDER BY ID")) {
			for (int value : List.of(1, 120, 170)) {
				assertTrue(rows.next());
				assertEquals(value, rows.getInt(1));
				assertEquals(value, rows.getInt(2));
			}
			assertFalse(rows.next());
		}
		Table actual = read(connection, name);
		var segments = actual.getIndexes().get(name + "_IX").getPartitioning().getPartitions();
		assertEquals(partitions, segments.stream().map(p -> p.getName()).toList());
		for (var part : segments)
			assertEquals("USABLE", part.getSpecifics().get("oracle.index.status"));
	}
}
