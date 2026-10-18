/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.oracle;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.*;
import org.testcontainers.oracle.OracleContainer;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleCompositeAlterTest {
	private static final OracleContainer ORACLE = ReusableTestcontainers
			.configure(new OracleContainer(OracleTestEnvironment.image()));
	private static final String SPACE = "SQLAPP_COMPOSITE";
	private static boolean spaceCreated;

	@BeforeAll
	static void start() throws Exception {
		ReusableTestcontainers.start(ORACLE);
		try (var connection = admin(); var sql = connection.createStatement()) {
			String file;
			try (var rows = sql.executeQuery(
					"SELECT FILE_NAME FROM DBA_DATA_FILES WHERE TABLESPACE_NAME='USERS' ORDER BY FILE_ID")) {
				assertTrue(rows.next());
				file = rows.getString(1);
			}
			file = file.substring(0, file.lastIndexOf('/') + 1) + "sqlapp_composite_" + java.util.UUID.randomUUID()
					+ ".dbf";
			sql.execute("CREATE TABLESPACE " + SPACE + " DATAFILE '" + file.replace("'", "''") + "' SIZE 128M");
			spaceCreated = true;
			sql.execute("ALTER USER " + ORACLE.getUsername() + " QUOTA 128M ON " + SPACE);
		}
	}

	@AfterAll
	static void stop() throws Exception {
		try {
			if (spaceCreated)
				try (var connection = admin(); var sql = connection.createStatement()) {
					sql.execute("DROP TABLESPACE " + SPACE + " INCLUDING CONTENTS AND DATAFILES");
				}
		} finally {
			ReusableTestcontainers.stop(ORACLE);
		}
	}

	private static Connection admin() throws Exception {
		return java.sql.DriverManager.getConnection(ORACLE.getJdbcUrl(), "system", ORACLE.getPassword());
	}

	@Test
	void rangeChildSplitAndMergePreserveRowsAndLocalIndexes() throws Exception {
		verify("RANGE", true, false);
	}

	@Test
	void listChildSplitAndMergePreserveRowsAndLocalIndexes() throws Exception {
		verify("LIST", true, false);
	}

	@Test
	void rangeChildMovePreservesRowsAndIndexPlacement() throws Exception {
		verify("RANGE", false, false);
	}

	@Test
	void listChildMovePreservesRowsAndIndexPlacement() throws Exception {
		verify("LIST", false, false);
	}

	@Test
	void hashChildMovePreservesRowsAndIndexPlacement() throws Exception {
		verify("HASH", false, false);
	}

	@Test
	void simpleHashMovePreservesRowsAndIndexPlacement() throws Exception {
		verify("HASH", false, true);
	}

	@Test
	void parentRangeHashSplitAndMergePreserveChildNamesAndIndexes() throws Exception {
		verify("HASH", true, false, true);
	}

	@Test
	void parentRangeHashMergeCoalescesLargerTableDefaultWithoutLosingRows() throws Exception {
		verify("HASH", true, false, true, true);
	}

	@Test
	void parentListHashSplitAndMergePreserveChildNamesAndIndexes() throws Exception {
		verify("HASH", true, false, true, false, true);
	}

	@Test
	void parentListHashMergeCoalescesLargerTableDefaultWithoutLosingRows() throws Exception {
		verify("HASH", true, false, true, true, true);
	}

	private void verify(String kind, boolean split, boolean simple) throws Exception {
		verify(kind, split, simple, false);
	}

	@Test
	void groupedRangeChildrenMoveAndReturnPreserveRowsAndIndexes() throws Exception {
		verify("RANGE", false, false, false, false, false, true);
	}

	@Test
	void groupedListChildrenMoveAndReturnPreserveRowsAndIndexes() throws Exception {
		verify("LIST", false, false, false, false, false, true);
	}

	@Test
	void groupedHashChildrenMoveAndReturnPreserveRowsAndIndexes() throws Exception {
		verify("HASH", false, false, false, false, false, true);
	}

	private void verify(String kind, boolean split, boolean simple, boolean parent) throws Exception {
		verify(kind, split, simple, parent, false);
	}

	private void verify(String kind, boolean split, boolean simple, boolean parent, boolean largerDefault)
			throws Exception {
		verify(kind, split, simple, parent, largerDefault, false);
	}

	private void verify(String kind, boolean split, boolean simple, boolean parent, boolean largerDefault,
			boolean listRoot) throws Exception {
		verify(kind, split, simple, parent, largerDefault, listRoot, false);
	}

	private void verify(String kind, boolean split, boolean simple, boolean parent, boolean largerDefault,
			boolean listRoot, boolean groupedMove) throws Exception {
		String name = "ALTER_" + kind + (listRoot ? "_LIST" : "") + (groupedMove ? "_GROUP"
				: parent ? largerDefault ? "_PARENT_BIG" : "_PARENT" : split ? "_SPLIT" : simple ? "_SIMPLE" : "_MOVE");
		try (Connection connection = ORACLE.createConnection(""); var sql = connection.createStatement()) {
			boolean created = false;
			try {
				String suffix = simple ? "PARTITION BY HASH(ID) (PARTITION P1, PARTITION P2)"
						: "PARTITION BY RANGE(ID) SUBPARTITION BY " + kind + "(REGION)"
								+ (largerDefault ? " SUBPARTITIONS 4" : "")
								+ " (PARTITION P1 VALUES LESS THAN(100) (SUBPARTITION S11" + bound(kind, true)
								+ ", SUBPARTITION S12" + bound(kind, false)
								+ "), PARTITION P2 VALUES LESS THAN(200) (SUBPARTITION S21" + bound(kind, true)
								+ ", SUBPARTITION S22" + bound(kind, false) + "))";
				if (listRoot)
					suffix = suffix.replace("PARTITION BY RANGE(ID)", "PARTITION BY LIST(ID)").replace(
							"VALUES LESS THAN(100)",
							"VALUES (" + java.util.stream.IntStream.rangeClosed(1, 32).mapToObj(Integer::toString)
									.collect(java.util.stream.Collectors.joining(",")) + ")")
							.replace("VALUES LESS THAN(200)", "VALUES (120,170)");
				sql.execute("CREATE TABLE " + name + " (ID NUMBER PRIMARY KEY, REGION NUMBER) " + suffix);
				created = true;
				sql.execute("CREATE INDEX " + name + "_IX ON " + name + "(REGION) LOCAL (PARTITION IP1"
						+ (simple ? "" : " (SUBPARTITION IS11, SUBPARTITION IS12)") + ", PARTITION IP2"
						+ (simple ? "" : " (SUBPARTITION IS21, SUBPARTITION IS22)") + ")");
				List<Integer> ids = kind.equals("HASH") ? java.util.stream.IntStream
						.concat(java.util.stream.IntStream.rangeClosed(1, 32),
								parent ? java.util.stream.IntStream.of(120, 170) : java.util.stream.IntStream.empty())
						.boxed().toList() : List.of(1, 2, 3, 120);
				for (int id : ids)
					sql.execute("INSERT INTO " + name + " VALUES (" + id + "," + region(kind, id) + ")");
				Table before = read(connection, name), after = before.clone();
				if (parent) {
					for (Partitioning info : List.of(after.getPartitioning(),
							after.getIndexes().get(name + "_IX").getPartitioning())) {
						Partition original = info.getPartitions().remove(1);
						boolean index = info.getIndex() != null;
						for (boolean left : List.of(true, false)) {
							Partition part = original.clone().setName((index ? "I" : "P") + (left ? "LOW" : "HIGH"))
									.setHighValue(listRoot ? left ? "120" : "170" : left ? "150" : "200");
							if (index && part.getTableSpaceName() == null)
								part.setTableSpaceName(part.getSubPartitions().get(0).getTableSpaceName());
							for (int j = 0; j < part.getSubPartitions().size(); j++)
								part.getSubPartitions().get(j).setName((index ? "IC" : "C") + (left ? "L" : "H") + j);
							info.getPartitions().add(part);
						}
					}
					execute(connection, before, after);
					verifyState(connection, after, kind, ids);
					sql.execute("BEGIN DBMS_STATS.GATHER_TABLE_STATS(USER, '" + name
							+ "', granularity => 'ALL', cascade => TRUE); END;");
					Table actual = read(connection, name), merged = actual.clone();
					for (Partitioning info : List.of(merged.getPartitioning(),
							merged.getIndexes().get(name + "_IX").getPartitioning())) {
						Partition part = info.getPartitions().remove(2);
						info.getPartitions().remove(1);
						boolean index = info.getIndex() != null;
						part.setName(index ? "IP2" : "P2");
						if (listRoot)
							part.setHighValue("120, 170");
						for (int j = 0; j < part.getSubPartitions().size(); j++)
							part.getSubPartitions().get(j).setName((index ? "IS2" : "S2") + (j + 1));
						info.getPartitions().add(part);
					}
					execute(connection, actual, merged);
					verifyState(connection, merged, kind, ids);
				} else if (split) {
					for (Partitioning info : List.of(after.getPartitioning(),
							after.getIndexes().get(name + "_IX").getPartitioning())) {
						var children = info.getPartitions().get(0).getSubPartitions();
						SubPartition original = children.remove(1);
						boolean index = info.getIndex() != null;
						children.add(original.clone().setName(index ? "IL" : "SL")
								.setHighValue(kind.equals("LIST") ? "20" : "30"));
						children.add(original.clone().setName(index ? "IR" : "SR")
								.setHighValue(kind.equals("LIST") ? "30" : "40"));
					}
					execute(connection, before, after);
					verifyState(connection, after, kind, ids);
					sql.execute("BEGIN DBMS_STATS.GATHER_TABLE_STATS(USER, '" + name
							+ "', granularity => 'ALL', cascade => TRUE); END;");
					Table actual = read(connection, name), merged = actual.clone();
					for (Partitioning info : List.of(merged.getPartitioning(),
							merged.getIndexes().get(name + "_IX").getPartitioning())) {
						var children = info.getPartitions().get(0).getSubPartitions();
						SubPartition target = children.remove(2);
						children.remove(1);
						children.add(target.setName(info.getIndex() == null ? "S12" : "IS12")
								.setHighValue(kind.equals("LIST") ? "20,30" : "40"));
					}
					execute(connection, actual, merged);
					verifyState(connection, merged, kind, ids);
				} else {
					if (simple)
						after.getPartitioning().getPartitions().get(0).setTableSpaceName(SPACE);
					else
						after.getPartitioning().getPartitions().get(0).getSubPartitions().get(0)
								.setTableSpaceName(SPACE);
					try (var rows = sql.executeQuery(
							"SELECT COUNT(*) FROM " + name + (simple ? " PARTITION(P1)" : " SUBPARTITION(S11)"))) {
						assertTrue(rows.next());
						assertTrue(rows.getInt(1) > 0, "Moved segment must contain data");
					}
					if (groupedMove) {
						after.getPartitioning().getPartitions().get(0).getSubPartitions().get(1)
								.setTableSpaceName(SPACE);
						try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + name + " SUBPARTITION(S12)")) {
							assertTrue(rows.next());
							assertTrue(rows.getInt(1) > 0, "Second moved child must contain data");
						}
					}
					execute(connection, before, after, groupedMove ? 2 : 1);
					verifyState(connection, after, kind, ids);
					if (groupedMove) {
						Table actual = read(connection, name), restored = actual.clone();
						for (int i = 0; i < 2; i++)
							restored.getPartitioning().getPartitions().get(0).getSubPartitions().get(i)
									.setTableSpaceName(before.getPartitioning().getPartitions().get(0)
											.getSubPartitions().get(i).getTableSpaceName());
						execute(connection, actual, restored, 2);
						verifyState(connection, restored, kind, ids);
					}
				}
			} finally {
				if (created)
					sql.execute("DROP TABLE " + name + " PURGE");
			}
		}
	}

	private String bound(String kind, boolean first) {
		return kind.equals("HASH") ? ""
				: kind.equals("LIST") ? " VALUES (" + (first ? "10" : "20,30") + ")"
						: " VALUES LESS THAN(" + (first ? "20" : "40") + ")";
	}

	private int region(String kind, int id) {
		return kind.equals("HASH") ? id
				: id == 1 ? 10 : id == 3 ? (kind.equals("LIST") ? 30 : 35) : kind.equals("LIST") ? 20 : 25;
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
		execute(connection, before, after,
				before.getPartitioning().getPartitions().size() == after.getPartitioning().getPartitions().size() ? 1
						: 2);
	}

	private void execute(Connection connection, Table before, Table after, int expectedStatements) throws Exception {
		var spaces = indexSpaces(connection, before.getName());
		var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var operations = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
		assertEquals(expectedStatements, operations.size());
		try (var sql = connection.createStatement()) {
			for (int i = 0; i < operations.size(); i++) {
				sql.execute(operations.get(i).getSqlText());
				if (i == 0 && before.getPartitioning().getPartitions().size() > after.getPartitioning().getPartitions()
						.size()) {
					try (var rows = sql.executeQuery("SELECT COUNT(*) FROM USER_TAB_SUBPARTITIONS WHERE TABLE_NAME='"
							+ before.getName() + "' AND PARTITION_NAME='P2'")) {
						assertTrue(rows.next());
						assertTrue(rows.getInt(1) > 0);
						System.out.println("Native MERGE HASH child count: " + rows.getInt(1) + "; requested: "
								+ after.getPartitioning().getPartitions().get(1).getSubPartitions().size());
					}
				}
			}
		}
		assertEquals(spaces, indexSpaces(connection, before.getName()),
				"Global and Local root index placement must remain unchanged");
	}

	private java.util.Map<String, String> indexSpaces(Connection connection, String name) throws Exception {
		var spaces = new java.util.TreeMap<String, String>();
		try (var sql = connection
				.prepareStatement("SELECT INDEX_NAME,TABLESPACE_NAME FROM USER_INDEXES WHERE TABLE_NAME=?")) {
			sql.setString(1, name);
			try (var rows = sql.executeQuery()) {
				while (rows.next())
					spaces.put(rows.getString(1), rows.getString(2));
			}
		}
		return spaces;
	}

	private void verifyState(Connection connection, Table expected, String kind, List<Integer> ids) throws Exception {
		try (var sql = connection.createStatement();
				var rows = sql.executeQuery("SELECT /*+ INDEX(t " + expected.getName() + "_IX) */ ID,REGION FROM "
						+ expected.getName() + " t ORDER BY ID")) {
			for (int id : ids) {
				assertTrue(rows.next());
				assertEquals(id, rows.getInt(1));
				assertEquals(region(kind, id), rows.getInt(2));
			}
			assertFalse(rows.next());
		}
		Table actual = read(connection, expected.getName());
		compare(expected.getPartitioning(), actual.getPartitioning(), false);
		compare(expected.getIndexes().get(expected.getName() + "_IX").getPartitioning(),
				actual.getIndexes().get(expected.getName() + "_IX").getPartitioning(), true);
		try (var sql = connection.createStatement();
				var rows = sql.executeQuery("SELECT COUNT(*) FROM USER_INDEXES WHERE TABLE_NAME='" + expected.getName()
						+ "' AND STATUS NOT IN ('VALID','N/A')")) {
			assertTrue(rows.next());
			assertEquals(0, rows.getInt(1));
		}
	}

	private void compare(Partitioning expected, Partitioning actual, boolean index) {
		assertEquals(expected.getPartitions().size(), actual.getPartitions().size());
		for (int i = 0; i < expected.getPartitions().size(); i++) {
			var a = expected.getPartitions().get(i);
			var b = actual.getPartitions().get(i);
			assertEquals(a.getName(), b.getName());
			assertEquals(a.getTableSpaceName(), b.getTableSpaceName());
			assertEquals(a.getHighValue(), b.getHighValue());
			assertEquals(a.getSubPartitions().size(), b.getSubPartitions().size());
			if (index && a.getSubPartitions().isEmpty())
				assertEquals("USABLE", b.getSpecifics().get("oracle.index.status"));
			for (int j = 0; j < a.getSubPartitions().size(); j++) {
				var c = a.getSubPartitions().get(j);
				var d = b.getSubPartitions().get(j);
				assertEquals(c.getName(), d.getName());
				assertEquals(c.getTableSpaceName(), d.getTableSpaceName());
				assertEquals(c.isCompression(), d.isCompression());
				assertEquals(c.getCompressionType(), d.getCompressionType());
				for (var entry : c.getSpecifics().entrySet())
					assertEquals(entry.getValue(), d.getSpecifics().get(entry.getKey()),
							"Preserve child attribute " + entry.getKey());
				for (String key : d.getSpecifics().keySet())
					if (!c.getSpecifics().containsKey(key))
						assertTrue(
								java.util.Set.of("INITIAL_EXTENT", "NEXT_EXTENT", "MIN_EXTENTS", "MAX_EXTENTS",
										"PCT_INCREASE", "FREELISTS", "FREELIST_GROUPS").contains(key),
								"Only allocation attributes of previously deferred segments may become concrete: "
										+ key);
				if (expected.getSubPartitioningType() == PartitioningType.List)
					assertEquals(c.getHighValue().replaceAll("\\s+", ""), d.getHighValue().replaceAll("\\s+", ""));
				else
					assertEquals(c.getHighValue(), d.getHighValue());
				if (index)
					assertEquals("USABLE", d.getSpecifics().get("oracle.index.status"));
			}
		}
	}
}
