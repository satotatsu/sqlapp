/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.oracle;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.oracle.OracleContainer;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.SubPartition;
import com.sqlapp.data.schemas.Table;

class OracleCompositePartitionTest {
	private static final OracleContainer ORACLE = ReusableTestcontainers.configure(new OracleContainer(OracleTestEnvironment.image()));
	@BeforeAll static void start() { ReusableTestcontainers.start(ORACLE); }
	@AfterAll static void stop() { ReusableTestcontainers.stop(ORACLE); }
	private enum Mode { PARENT, CHILD, COALESCE }
	private record Row(int id, String region) { }
	@Test void addsAndDropsCompositeParentWithNamedLocalSegments() throws Exception { verify(Mode.PARENT); }
	@Test void addsAndDropsListChildWithNamedLocalSegment() throws Exception { verify(Mode.CHILD); }
	@Test void hashChildCoalescePreservesEveryRowAndUsableIndexes() throws Exception { verify(Mode.COALESCE); }
	@Test void catalogOnlyAccessContinuesAndCatalogRoleEnablesCompleteDdl() throws Exception {
		try (Connection admin = java.sql.DriverManager.getConnection(ORACLE.getJdbcUrl(), "system", ORACLE.getPassword());
				var sql = admin.createStatement(); Connection limited = ORACLE.createConnection("")) {
			String user = ORACLE.getUsername().toUpperCase(Locale.ROOT);
			boolean created = false, granted = false;
			try {
				sql.execute("CREATE TABLE SYSTEM.COMPOSITE_ACCESS (ID NUMBER, REGION NUMBER) PARTITION BY RANGE(ID) SUBPARTITION BY HASH(REGION) SUBPARTITIONS 2 (PARTITION P1 VALUES LESS THAN(100))");
				created = true;
				sql.execute("GRANT SELECT ON SYSTEM.COMPOSITE_ACCESS TO " + user);
				var dialect = DialectResolver.getInstance().getDialect(limited); OracleTestEnvironment.assertExpectedDialect(dialect);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName("SYSTEM"); reader.setObjectName("COMPOSITE_ACCESS");
				Table partial = reader.getAllFull(limited).get(0);
				assertNull(com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition.get(partial));
				assertEquals(2, partial.getPartitioning().getPartitions().get(0).getSubPartitions().size());
				sql.execute("GRANT SELECT_CATALOG_ROLE TO " + user); granted = true;
				try (Connection privileged = ORACLE.createConnection("")) {
					Table complete = reader.getAllFull(privileged).get(0);
					String ddl = com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition.get(complete);
					assertNotNull(ddl); assertTrue(ddl.contains("COMPOSITE_ACCESS"), ddl);
					assertTrue(ddl.contains("SUBPARTITION BY HASH"), ddl);
				}
			} finally {
				if (granted) sql.execute("REVOKE SELECT_CATALOG_ROLE FROM " + user);
				if (created) sql.execute("DROP TABLE SYSTEM.COMPOSITE_ACCESS PURGE");
			}
		}
	}

	private void verify(Mode mode) throws Exception {
		String name = "COMPOSITE_" + mode;
		boolean hash = mode != Mode.CHILD;
		try (Connection connection = ORACLE.createConnection(""); var sql = connection.createStatement()) {
			boolean created = false;
			try {
				sql.execute("CREATE TABLE " + name + " (ID NUMBER PRIMARY KEY, REGION VARCHAR2(8)) PARTITION BY RANGE(ID) SUBPARTITION BY "
						+ (hash ? "HASH" : "LIST") + "(REGION) (PARTITION P1 VALUES LESS THAN(100) (SUBPARTITION S11"
						+ (hash ? "" : " VALUES ('JP')") + ", SUBPARTITION S12" + (hash ? "" : " VALUES ('US')")
						+ "), PARTITION P2 VALUES LESS THAN(200) (SUBPARTITION S21" + (hash ? "" : " VALUES ('JP')")
						+ ", SUBPARTITION S22" + (hash ? "" : " VALUES ('US')") + "))");
				created = true;
				sql.execute("CREATE INDEX " + name + "_IX ON " + name + "(REGION) LOCAL (PARTITION IP1 (SUBPARTITION IS11, SUBPARTITION IS12), PARTITION IP2 (SUBPARTITION IS21, SUBPARTITION IS22))");
				List<Row> original = new ArrayList<>();
				if (mode == Mode.COALESCE) {
					for (int i = 1; i <= 32; i++) original.add(new Row(i, "R" + i));
					original.add(new Row(120, "JP"));
				} else original.addAll(List.of(new Row(1, "JP"), new Row(2, "US"), new Row(120, "JP"), new Row(121, "US")));
				for (Row row : original) sql.execute("INSERT INTO " + name + " VALUES (" + row.id() + ", '" + row.region() + "')");
				Table before = read(connection, name), target = before.clone();
				if (mode == Mode.CHILD) {
					try (var rows = sql.executeQuery("SELECT COMPRESSION FROM USER_TAB_PARTITIONS WHERE TABLE_NAME='" + name + "' AND PARTITION_NAME='P1'")) { assertTrue(rows.next()); assertEquals("NONE", rows.getString(1)); }
					assertFalse(before.getPartitioning().getPartitions().get(0).isCompression());
				}
				var segments = target.getIndexes().get(name + "_IX").getPartitioning().getPartitions();
				if (mode == Mode.PARENT) {
					Partition parent = new Partition("P3").setHighValue("300");
					parent.getSubPartitions().add(new SubPartition("S31")); parent.getSubPartitions().add(new SubPartition("S32"));
					target.getPartitioning().getPartitions().add(parent);
					Partition index = new Partition("IP3").setHighValue("300"); index.getSpecifics().put("oracle.index.status", "N/A");
					index.getSubPartitions().add(segment("IS31", null)); index.getSubPartitions().add(segment("IS32", null)); segments.add(index);
				} else if (mode == Mode.CHILD) {
					target.getPartitioning().getPartitions().get(0).getSubPartitions().add(new SubPartition("S13").setHighValue("'DE'"));
					segments.get(0).getSubPartitions().add(segment("IS13", "'DE'"));
				} else {
					try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + name + " SUBPARTITION(S12)")) { assertTrue(rows.next()); assertTrue(rows.getInt(1) > 0, "Removed HASH bucket must contain data"); }
					target.getPartitioning().getPartitions().get(0).getSubPartitions().remove(1);
					segments.get(0).getSubPartitions().remove(1);
				}
				execute(connection, before, target);
				verifyState(connection, target, original);
				if (mode == Mode.COALESCE) { recreate(connection, read(connection, name), original); return; }
				Row added = new Row(mode == Mode.PARENT ? 220 : 3, "DE");
				sql.execute("INSERT INTO " + name + " VALUES (" + added.id() + ", 'DE')");
				List<Row> withAdded = new ArrayList<>(original); withAdded.add(added); withAdded.sort(Comparator.comparingInt(Row::id));
				verifyState(connection, target, withAdded);
				Table actual = read(connection, name), dropped = actual.clone();
				if (mode == Mode.PARENT) {
					dropped.getPartitioning().getPartitions().remove(2); dropped.getIndexes().get(name + "_IX").getPartitioning().getPartitions().remove(2);
				} else {
					dropped.getPartitioning().getPartitions().get(0).getSubPartitions().remove(2); dropped.getIndexes().get(name + "_IX").getPartitioning().getPartitions().get(0).getSubPartitions().remove(2);
				}
				execute(connection, actual, dropped); verifyState(connection, dropped, original);
				recreate(connection, read(connection, name), original);
			} finally { if (created) sql.execute("DROP TABLE " + name + " PURGE"); }
		}
	}
	private void recreate(Connection connection, Table table, List<Row> original) throws Exception {
		Table restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		assertNotNull(com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition.get(restored));
		var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
		var operations = registry.createSql(restored, SqlType.CREATE); assertFalse(operations.isEmpty());
		try (var sql = connection.createStatement()) {
			sql.execute("DROP TABLE " + table.getName() + " PURGE");
			for (var operation : operations) sql.execute(operation.getSqlText());
			for (Row row : original) sql.execute("INSERT INTO " + table.getName() + " VALUES (" + row.id() + ", '" + row.region() + "')");
		}
		verifyState(connection, restored, original);
		Table actual = read(connection, table.getName());
		for (int i = 0; i < restored.getPartitioning().getPartitions().size(); i++) {
			var wanted = restored.getPartitioning().getPartitions().get(i);
			var found = actual.getPartitioning().getPartitions().get(i);
			for (int j = 0; j < wanted.getSubPartitions().size(); j++) {
				assertEquals(wanted.getSubPartitions().get(j).getSpecifics(), found.getSubPartitions().get(j).getSpecifics());
				assertEquals(wanted.getSubPartitions().get(j).getTableSpaceName(), found.getSubPartitions().get(j).getTableSpaceName());
			}
		}
	}
	private SubPartition segment(String name, String bound) {
		SubPartition part = new SubPartition(name).setHighValue(bound); part.getSpecifics().put("oracle.index.status", "USABLE"); return part;
	}
	private Table read(Connection connection, String name) throws Exception {
		var dialect = DialectResolver.getInstance().getDialect(connection); OracleTestEnvironment.assertExpectedDialect(dialect);
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(connection.getMetaData().getUserName().toUpperCase(Locale.ROOT)); reader.setObjectName(name);
		return reader.getAllFull(connection).get(0);
	}
	private void execute(Connection connection, Table before, Table after) throws Exception {
		var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
		var operations = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)); assertEquals(1, operations.size());
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(after, SqlType.CREATE),
				"A changed partition model must not replay the old complete CREATE DDL");
		try (var sql = connection.createStatement()) { sql.execute(operations.get(0).getSqlText()); }
	}
	private void verifyState(Connection connection, Table target, List<Row> expected) throws Exception {
		String name = target.getName();
		try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT /*+ INDEX(t " + name + "_IX) */ ID, REGION FROM " + name + " t ORDER BY ID")) {
			for (Row row : expected) { assertTrue(rows.next()); assertEquals(row.id(), rows.getInt(1)); assertEquals(row.region(), rows.getString(2)); } assertFalse(rows.next());
		}
		Table actual = read(connection, name);
		assertEquals(target.getPartitioning().getPartitions().stream().map(Partition::getName).toList(), actual.getPartitioning().getPartitions().stream().map(Partition::getName).toList());
		for (int i = 0; i < target.getPartitioning().getPartitions().size(); i++) {
			assertEquals(target.getPartitioning().getPartitions().get(i).getSubPartitions().stream().map(SubPartition::getName).toList(), actual.getPartitioning().getPartitions().get(i).getSubPartitions().stream().map(SubPartition::getName).toList());
			var wanted = target.getIndexes().get(name + "_IX").getPartitioning().getPartitions().get(i);
			var found = actual.getIndexes().get(name + "_IX").getPartitioning().getPartitions().get(i);
			assertEquals(wanted.getName(), found.getName());
			assertEquals(wanted.getSubPartitions().stream().map(SubPartition::getName).toList(), found.getSubPartitions().stream().map(SubPartition::getName).toList());
			for (var sub : found.getSubPartitions()) assertEquals("USABLE", sub.getSpecifics().get("oracle.index.status"));
		}
		try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT COUNT(*) FROM USER_INDEXES WHERE TABLE_NAME='" + name + "' AND STATUS NOT IN ('VALID','N/A')")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
	}
}
