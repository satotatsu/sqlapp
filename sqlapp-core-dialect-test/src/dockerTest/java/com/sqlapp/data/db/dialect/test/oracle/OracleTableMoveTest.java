/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.oracle;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.*;
import org.testcontainers.oracle.OracleContainer;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class OracleTableMoveTest {
	private static final OracleContainer ORACLE = ReusableTestcontainers.configure(new OracleContainer(OracleTestEnvironment.image()));
	private static final String SPACE = "SQLAPP_MOVE";
	private static boolean spaceCreated;
	@BeforeAll static void start() throws Exception {
		ReusableTestcontainers.start(ORACLE);
		try (var connection = admin(); var sql = connection.createStatement()) {
			String file;
			try (var rows = sql.executeQuery("SELECT FILE_NAME FROM DBA_DATA_FILES WHERE TABLESPACE_NAME='USERS' ORDER BY FILE_ID")) {
				assertTrue(rows.next()); file = rows.getString(1);
			}
			// Derive the directory from this disposable database, never from a host path.
			file = file.substring(0, file.lastIndexOf('/') + 1) + "sqlapp_move_" + java.util.UUID.randomUUID() + ".dbf";
			sql.execute("CREATE TABLESPACE " + SPACE + " DATAFILE '" + file.replace("'", "''") + "' SIZE 128M");
			spaceCreated = true;
			sql.execute("ALTER USER " + ORACLE.getUsername() + " QUOTA 128M ON " + SPACE);
		}
	}
	@AfterAll static void stop() throws Exception {
		try {
			if (spaceCreated) try (var connection = admin(); var sql = connection.createStatement()) {
				sql.execute("DROP TABLESPACE " + SPACE + " INCLUDING CONTENTS AND DATAFILES");
			}
		} finally { ReusableTestcontainers.stop(ORACLE); }
	}
	private static Connection admin() throws Exception {
		return DriverManager.getConnection(ORACLE.getJdbcUrl(), "system", ORACLE.getPassword());
	}
	@Test void heapRootMovePreservesIndexes() throws Exception { verify(false, false); }
	@Test void heapPartitionMovePreservesIndexes() throws Exception { verify(false, true); }
	@Test void compressedIotRootMovePreservesPrefixAndIndexes() throws Exception { verify(true, false); }
	@Test void iotPartitionMovePreservesIndexes() throws Exception { verify(true, true); }
	private void verify(boolean iot, boolean partitioned) throws Exception {
		String name = "MOVE_" + (iot ? "IOT" : "HEAP") + (partitioned ? "_PART" : "_ROOT");
		try (Connection connection = ORACLE.createConnection(""); var sql = connection.createStatement()) {
			boolean created = false;
			try {
				sql.execute("CREATE TABLE " + name + " (ID NUMBER, VALUE NUMBER, PRIMARY KEY(ID, VALUE)) "
						+ (iot ? "ORGANIZATION INDEX " + (partitioned ? "" : "COMPRESS 1 ") : "")
						+ (partitioned ? "PARTITION BY RANGE(ID) (PARTITION P1 VALUES LESS THAN(100), PARTITION P2 VALUES LESS THAN(200))" : ""));
				created = true;
				sql.execute("CREATE INDEX " + name + "_IX ON " + name + "(VALUE)" + (partitioned ? " LOCAL (PARTITION IP1, PARTITION IP2)" : ""));
				for (int value : List.of(1, 120, 170)) sql.execute("INSERT INTO " + name + " VALUES (" + value + ", " + value + ")");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				OracleTestEnvironment.assertExpectedDialect(dialect);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(connection.getMetaData().getUserName().toUpperCase(Locale.ROOT)); reader.setObjectName(name);
				Table before = reader.getAllFull(connection).get(0), after = before.clone();
				if (partitioned) after.getPartitioning().getPartitions().get(0).setTableSpaceName(SPACE);
				else after.setTableSpaceName(SPACE);
				var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
				var operations = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
				assertFalse(operations.isEmpty());
				for (var operation : operations) sql.execute(operation.getSqlText());
				Table actual = reader.getAllFull(connection).get(0);
				if (partitioned) {
					assertEquals(SPACE, actual.getPartitioning().getPartitions().get(0).getTableSpaceName());
					assertEquals(before.getPartitioning().getPartitions().get(1).getTableSpaceName(), actual.getPartitioning().getPartitions().get(1).getTableSpaceName());
					assertEquals(List.of("IP1", "IP2"), actual.getIndexes().get(name + "_IX").getPartitioning().getPartitions().stream().map(p -> p.getName()).toList());
					for (var part : actual.getIndexes().get(name + "_IX").getPartitioning().getPartitions()) assertEquals("USABLE", part.getSpecifics().get("oracle.index.status"));
					for (int i = 0; i < 2; i++) assertEquals(before.getIndexes().get(name + "_IX").getPartitioning().getPartitions().get(i).getTableSpaceName(), actual.getIndexes().get(name + "_IX").getPartitioning().getPartitions().get(i).getTableSpaceName());
				} else {
					assertEquals(SPACE, actual.getTableSpaceName());
					if (iot) assertEquals("1", actual.getSpecifics().get("oracle.table.iot.compressionPrefixLength"));
				}
				try (var rows = sql.executeQuery("SELECT /*+ INDEX(t " + name + "_IX) */ ID, VALUE FROM " + name + " t ORDER BY ID")) {
					for (int value : List.of(1, 120, 170)) { assertTrue(rows.next()); assertEquals(value, rows.getInt(1)); assertEquals(value, rows.getInt(2)); }
					assertFalse(rows.next());
				}
				try (var rows = sql.executeQuery("SELECT COUNT(*) FROM USER_INDEXES WHERE TABLE_NAME='" + name + "' AND STATUS NOT IN ('VALID','N/A')")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
				assertEquals(before.getIndexes().get(name + "_IX").getTableSpaceName(), actual.getIndexes().get(name + "_IX").getTableSpaceName());
			} finally { if (created) sql.execute("DROP TABLE " + name + " PURGE"); }
		}
	}
}
