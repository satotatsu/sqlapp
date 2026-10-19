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
import com.sqlapp.data.schemas.AbstractPartition;
import com.sqlapp.data.schemas.SubPartition;
import com.sqlapp.data.schemas.Table;

class OracleCompositePartitionTest {
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

	private enum Mode {
		PARENT, CHILD, COALESCE, HASH_ADD, RANGE_CHILD
	}

	@Test
	void finiteMaxvalueTextSimpleAdd() throws Exception {
		verifyFiniteTextBoundary(0);
	}

	@Test
	void finiteMaxvalueTextParentAdd() throws Exception {
		verifyFiniteTextBoundary(1);
	}

	@Test
	void finiteMaxvalueTextChildAdd() throws Exception {
		verifyFiniteTextBoundary(2);
	}

	@Test
	void finiteTupleSimpleAdd() throws Exception {
		verifyFiniteTextBoundary(0, true);
	}

	@Test
	void finiteTupleParentAdd() throws Exception {
		verifyFiniteTextBoundary(1, true);
	}

	@Test
	void finiteTupleChildAdd() throws Exception {
		verifyFiniteTextBoundary(2, true);
	}

	private void verifyFiniteTextBoundary(int shape) throws Exception {
		verifyFiniteTextBoundary(shape, false);
	}

	private void verifyFiniteTextBoundary(int shape, boolean tuple) throws Exception {
		String name = "FINITE_TEXT_BOUND_" + shape + (tuple ? "_TUPLE" : "");
		String max = tuple ? "'MAXVALUE', MAXVALUE" : "'MAXVALUE'";
		String last = tuple ? "'ZZZ', MAXVALUE" : "'ZZZ'";
		try (Connection connection = ORACLE.createConnection(""); var sql = connection.createStatement()) {
			boolean created = false;
			try {
				String shapeSql = shape == 0
						? "PARTITION BY RANGE(ID) (PARTITION P1 VALUES LESS THAN('B'), PARTITION P2 VALUES LESS THAN('MAXVALUE'))"
						: shape == 1
								? "PARTITION BY RANGE(ID) SUBPARTITION BY HASH(REGION) (PARTITION P1 VALUES LESS THAN('B') (SUBPARTITION S11, SUBPARTITION S12), PARTITION P2 VALUES LESS THAN('MAXVALUE') (SUBPARTITION S21, SUBPARTITION S22))"
								: "PARTITION BY RANGE(REGION) SUBPARTITION BY RANGE(ID) (PARTITION P1 VALUES LESS THAN(100) (SUBPARTITION S11 VALUES LESS THAN('B'), SUBPARTITION S12 VALUES LESS THAN('MAXVALUE')))";
				if (tuple)
					shapeSql = shapeSql.replace("RANGE(REGION)", "RANGE(SHARD)").replace("HASH(REGION)", "HASH(SHARD)")
							.replace("RANGE(ID)", "RANGE(ID, REGION)").replace("'B'", "'B', MAXVALUE")
							.replace("'MAXVALUE'", max);
				sql.execute("CREATE TABLE " + name + " (ID VARCHAR2(30), REGION NUMBER"
						+ (tuple ? ", SHARD NUMBER DEFAULT 1" : "") + ") " + shapeSql);
				created = true;
				for (String id : List.of("A", "L"))
					sql.execute("INSERT INTO " + name + " (ID, REGION) VALUES ('" + id + "', 1)");
				Table before = read(connection, name), after = before.clone();
				if (shape == 2)
					after.getPartitioning().getPartitions().get(0).getSubPartitions()
							.add(new SubPartition("S13").setHighValue(last));
				else {
					Partition added = new Partition("P3").setHighValue(last);
					if (shape == 1) {
						added.getSubPartitions().add(new SubPartition("S31"));
						added.getSubPartitions().add(new SubPartition("S32"));
					}
					after.getPartitioning().getPartitions().add(added);
				}
				var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
				var operations = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
				assertEquals(1, operations.size());
				sql.execute(operations.get(0).getSqlText());
				for (String id : List.of("MAXVALUE", "Z"))
					sql.execute("INSERT INTO " + name + " (ID, REGION) VALUES ('" + id + "', 1)");
				try (var rows = sql.executeQuery("SELECT ID FROM " + name + " ORDER BY ID")) {
					for (String id : List.of("A", "L", "MAXVALUE", "Z")) {
						assertTrue(rows.next());
						assertEquals(id, rows.getString(1));
					}
					assertFalse(rows.next());
				}
				if (tuple)
					try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + name
							+ (shape == 2 ? " SUBPARTITION(S12)" : " PARTITION(P2)") + " WHERE ID='MAXVALUE'")) {
						assertTrue(rows.next());
						assertEquals(1, rows.getInt(1), "A trailing MAXVALUE still includes rows at the finite prefix");
					}
				Table actual = read(connection, name);
				if (shape == 2) {
					var children = actual.getPartitioning().getPartitions().get(0).getSubPartitions();
					assertEquals(3, children.size());
					assertEquals(max, children.get(1).getHighValue());
					assertEquals(last, children.get(2).getHighValue());
				} else {
					var parts = actual.getPartitioning().getPartitions();
					assertEquals(3, parts.size());
					assertEquals(max, parts.get(1).getHighValue());
					assertEquals(last, parts.get(2).getHighValue());
					if (shape == 1)
						assertEquals(2, parts.get(2).getSubPartitions().size());
				}
			} finally {
				if (created)
					sql.execute("DROP TABLE " + name + " PURGE");
			}
		}
	}

	private record Row(int id, String region) {
	}

	@Test
	void addsAndDropsCompositeParentWithNamedLocalSegments() throws Exception {
		verify(Mode.PARENT);
	}

	@Test
	void addsAndDropsListChildWithNamedLocalSegment() throws Exception {
		verify(Mode.CHILD);
	}

	@Test
	void hashChildCoalescePreservesEveryRowAndUsableIndexes() throws Exception {
		verify(Mode.COALESCE);
	}

	@Test
	void catalogOnlyAccessContinuesAndCatalogRoleEnablesCompleteDdl() throws Exception {
		try (Connection admin = java.sql.DriverManager.getConnection(ORACLE.getJdbcUrl(), "system",
				ORACLE.getPassword());
				var sql = admin.createStatement();
				Connection limited = ORACLE.createConnection("")) {
			String user = ORACLE.getUsername().toUpperCase(Locale.ROOT);
			boolean created = false, granted = false;
			try {
				sql.execute(
						"CREATE TABLE SYSTEM.COMPOSITE_ACCESS (ID NUMBER, REGION NUMBER) PARTITION BY RANGE(ID) SUBPARTITION BY HASH(REGION) SUBPARTITIONS 2 (PARTITION P1 VALUES LESS THAN(100))");
				created = true;
				sql.execute("GRANT SELECT ON SYSTEM.COMPOSITE_ACCESS TO " + user);
				var dialect = DialectResolver.getInstance().getDialect(limited);
				OracleTestEnvironment.assertExpectedDialect(dialect);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName("SYSTEM");
				reader.setObjectName("COMPOSITE_ACCESS");
				Table partial = reader.getAllFull(limited).get(0);
				assertNull(com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition.get(partial));
				assertEquals(2, partial.getPartitioning().getPartitions().get(0).getSubPartitions().size());
				sql.execute("GRANT SELECT_CATALOG_ROLE TO " + user);
				granted = true;
				try (Connection privileged = ORACLE.createConnection("")) {
					Table complete = reader.getAllFull(privileged).get(0);
					String ddl = com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition.get(complete);
					assertNotNull(ddl);
					assertTrue(ddl.contains("COMPOSITE_ACCESS"), ddl);
					assertTrue(ddl.contains("SUBPARTITION BY HASH"), ddl);
				}
			} finally {
				if (granted)
					sql.execute("REVOKE SELECT_CATALOG_ROLE FROM " + user);
				if (created)
					sql.execute("DROP TABLE SYSTEM.COMPOSITE_ACCESS PURGE");
			}
		}
	}

	@Test
	void compressedParentPrefix() throws Exception {
		verify(Mode.PARENT, "ENABLED");
	}

	@Test
	void compressedChildPrefix() throws Exception {
		verify(Mode.CHILD, "ENABLED");
	}

	@Test
	void compressedCoalescePrefix() throws Exception {
		verify(Mode.COALESCE, "ENABLED");
	}

	@Test
	void compressedParentAdvancedLow() throws Exception {
		verify(Mode.PARENT, "ADVANCED LOW");
	}

	@Test
	void compressedChildAdvancedLow() throws Exception {
		verify(Mode.CHILD, "ADVANCED LOW");
	}

	@Test
	void compressedCoalesceAdvancedLow() throws Exception {
		verify(Mode.COALESCE, "ADVANCED LOW");
	}

	@Test
	void compressedParentAdvancedHigh() throws Exception {
		verify(Mode.PARENT, "ADVANCED HIGH");
	}

	@Test
	void compressedChildAdvancedHigh() throws Exception {
		verify(Mode.CHILD, "ADVANCED HIGH");
	}

	@Test
	void compressedCoalesceAdvancedHigh() throws Exception {
		verify(Mode.COALESCE, "ADVANCED HIGH");
	}

	private void verify(Mode mode) throws Exception {
		verify(mode, null);
	}

	@Test
	void compressedListParentPrefix() throws Exception {
		verify(Mode.PARENT, "ENABLED", true);
	}

	@Test
	void compressedListChildPrefix() throws Exception {
		verify(Mode.CHILD, "ENABLED", true);
	}

	@Test
	void compressedListParentAdvancedLow() throws Exception {
		verify(Mode.PARENT, "ADVANCED LOW", true);
	}

	@Test
	void compressedListChildAdvancedLow() throws Exception {
		verify(Mode.CHILD, "ADVANCED LOW", true);
	}

	@Test
	void compressedListParentAdvancedHigh() throws Exception {
		verify(Mode.PARENT, "ADVANCED HIGH", true);
	}

	@Test
	void compressedListChildAdvancedHigh() throws Exception {
		verify(Mode.CHILD, "ADVANCED HIGH", true);
	}

	@Test
	void compressedHashChildPrefix() throws Exception {
		verify(Mode.HASH_ADD, "ENABLED");
	}

	@Test
	void compressedRangeChildPrefix() throws Exception {
		verify(Mode.RANGE_CHILD, "ENABLED");
	}

	@Test
	void compressedHashChildAdvancedLow() throws Exception {
		verify(Mode.HASH_ADD, "ADVANCED LOW");
	}

	@Test
	void compressedRangeChildAdvancedLow() throws Exception {
		verify(Mode.RANGE_CHILD, "ADVANCED LOW");
	}

	@Test
	void compressedHashChildAdvancedHigh() throws Exception {
		verify(Mode.HASH_ADD, "ADVANCED HIGH");
	}

	@Test
	void compressedRangeChildAdvancedHigh() throws Exception {
		verify(Mode.RANGE_CHILD, "ADVANCED HIGH");
	}

	private void verify(Mode mode, String compression) throws Exception {
		verify(mode, compression, false);
	}

	private void verify(Mode mode, String compression, boolean listRoot) throws Exception {
		verify(mode, compression, listRoot, null);
	}

	@Test
	void rowParentBasic() throws Exception {
		verify(Mode.PARENT, null, false, "BASIC");
	}

	@Test
	void rowListParentBasic() throws Exception {
		verify(Mode.PARENT, null, true, "BASIC");
	}

	@Test
	void rowListChildBasic() throws Exception {
		verify(Mode.CHILD, null, true, "BASIC");
	}

	@Test
	void rowHashChildBasic() throws Exception {
		verify(Mode.HASH_ADD, null, false, "BASIC");
	}

	@Test
	void rowRangeChildBasic() throws Exception {
		verify(Mode.RANGE_CHILD, null, false, "BASIC");
	}

	@Test
	void rowChildBasic() throws Exception {
		verify(Mode.CHILD, null, false, "BASIC");
	}

	@Test
	void rowParentAdvanced() throws Exception {
		verify(Mode.PARENT, null, false, "ADVANCED");
	}

	@Test
	void rowListParentAdvanced() throws Exception {
		verify(Mode.PARENT, null, true, "ADVANCED");
	}

	@Test
	void rowListChildAdvanced() throws Exception {
		verify(Mode.CHILD, null, true, "ADVANCED");
	}

	@Test
	void rowHashChildAdvanced() throws Exception {
		verify(Mode.HASH_ADD, null, false, "ADVANCED");
	}

	@Test
	void rowRangeChildAdvanced() throws Exception {
		verify(Mode.RANGE_CHILD, null, false, "ADVANCED");
	}

	@Test
	void rowChildAdvanced() throws Exception {
		verify(Mode.CHILD, null, false, "ADVANCED");
	}

	@Test
	void rowLocalBasic() throws Exception {
		verify(Mode.PARENT, "ENABLED", false, "BASIC");
	}

	@Test
	void rowLocalAdvanced() throws Exception {
		verify(Mode.CHILD, "ADVANCED HIGH", true, "ADVANCED");
	}

	@Test
	void rowParentNocompress() throws Exception {
		verify(Mode.PARENT, null, false, "BASIC_OFF");
	}

	@Test
	void rowChildNocompress() throws Exception {
		verify(Mode.CHILD, null, false, "ADVANCED_OFF");
	}

	private void rowMode(AbstractPartition<?> part, String mode) {
		part.setCompression(mode != null);
		part.setCompressionType(mode);
		if ("BASIC".equals(mode))
			part.getSpecifics().put("PCT_FREE", "0");
		if (mode != null)
			part.getSpecifics().put("COMPRESS_FOR", mode);
		else
			part.getSpecifics().remove("COMPRESS_FOR");
	}

	private void verify(Mode mode, String compression, boolean listRoot, String rowCompression) throws Exception {
		verify(mode, compression, listRoot, rowCompression, false);
	}

	@Test
	void rowChildNocompressWithUncompressedParent() throws Exception {
		verify(Mode.CHILD, null, false, "BASIC_OFF", true);
	}

	@Test
	void quotedCaseChildAddDropAndRecreate() throws Exception {
		verify(Mode.CHILD, null, false, null, false, true);
	}

	@Test
	void quotedCaseParentAddDropAndRecreate() throws Exception {
		verify(Mode.PARENT, null, false, null, false, true);
	}

	private void verify(Mode mode, String compression, boolean listRoot, String rowCompression,
			boolean uncompressedParent) throws Exception {
		verify(mode, compression, listRoot, rowCompression, uncompressedParent, false);
	}

	private void verify(Mode mode, String compression, boolean listRoot, String rowCompression,
			boolean uncompressedParent, boolean quotedCase) throws Exception {
		String name = "COMPOSITE_" + mode + (listRoot ? "_LIST" : "")
				+ (compression == null ? "" : "_" + compression.replace(" ", "_"));
		name += rowCompression == null ? "" : "_ROW_" + rowCompression;
		if (uncompressedParent)
			name += "_PARENT_OFF";
		if (quotedCase)
			name += "_CASE";
		String rowMode = rowCompression == null ? null : rowCompression.replace("_OFF", "");
		boolean hash = mode != Mode.CHILD && mode != Mode.RANGE_CHILD;
		String firstBound = hash ? "" : mode == Mode.RANGE_CHILD ? " VALUES LESS THAN ('M')" : " VALUES ('JP')";
		String secondBound = hash ? "" : mode == Mode.RANGE_CHILD ? " VALUES LESS THAN ('Z')" : " VALUES ('US')";
		try (Connection connection = ORACLE.createConnection(""); var sql = connection.createStatement()) {
			boolean created = false;
			try {
				String createTable = "CREATE TABLE " + name + " (ID NUMBER PRIMARY KEY, REGION VARCHAR2(8)) "
						+ (rowMode == null ? "" : rowMode.equals("BASIC") ? "COMPRESS " : "COMPRESS FOR OLTP ")
						+ "PARTITION BY RANGE(ID) SUBPARTITION BY "
						+ (hash ? "HASH" : mode == Mode.RANGE_CHILD ? "RANGE" : "LIST")
						+ "(REGION) (PARTITION P1 VALUES LESS THAN(100) (SUBPARTITION S11" + firstBound
						+ ", SUBPARTITION S12" + secondBound + "), PARTITION P2 VALUES LESS THAN(200) (SUBPARTITION S21"
						+ firstBound + ", SUBPARTITION S22" + secondBound + "))";
				if (listRoot)
					createTable = createTable.replace("PARTITION BY RANGE(ID)", "PARTITION BY LIST(ID)")
							.replace("VALUES LESS THAN(100)", "VALUES (1,2,3)")
							.replace("VALUES LESS THAN(200)", "VALUES (120,121)");
				if (uncompressedParent)
					createTable = createTable.replace(" (SUBPARTITION S11", " NOCOMPRESS (SUBPARTITION S11");
				if (quotedCase)
					createTable = createTable.replace("SUBPARTITION S21", "SUBPARTITION \"s11\"");
				sql.execute(createTable);
				created = true;
				sql.execute("CREATE INDEX " + name + "_IX ON " + name + "(REGION)"
						+ (compression == null ? ""
								: compression.equals("ENABLED") ? " COMPRESS 1" : " COMPRESS " + compression)
						+ " LOCAL (PARTITION IP1 (SUBPARTITION IS11, SUBPARTITION IS12), PARTITION IP2 (SUBPARTITION "
						+ (quotedCase ? "\"is11\"" : "IS21") + ", SUBPARTITION IS22))");
				List<Row> original = new ArrayList<>();
				if (mode == Mode.COALESCE || mode == Mode.HASH_ADD) {
					for (int i = 1; i <= 32; i++)
						original.add(new Row(i, "R" + i));
					original.add(new Row(120, "JP"));
				} else
					original.addAll(
							List.of(new Row(1, "JP"), new Row(2, "US"), new Row(120, "JP"), new Row(121, "US")));
				for (Row row : original)
					sql.execute("INSERT INTO " + name + " VALUES (" + row.id() + ", '" + row.region() + "')");
				Table before = read(connection, name), target = before.clone();
				if (rowCompression != null) {
					assertTrue(before.isCompression(), "Table compression default must be read");
					assertEquals(rowMode, before.getCompressionType());
				}
				if (uncompressedParent)
					assertFalse(before.getPartitioning().getPartitions().get(0).isCompression());
				if (compression != null) {
					assertTrue(before.getIndexes().get(name + "_IX").isCompression());
					assertEquals(compression, before.getIndexes().get(name + "_IX").getCompressionType());
				}
				if (mode == Mode.CHILD && rowCompression == null) {
					try (var rows = sql.executeQuery("SELECT COMPRESSION FROM USER_TAB_PARTITIONS WHERE TABLE_NAME='"
							+ name + "' AND PARTITION_NAME='P1'")) {
						assertTrue(rows.next());
						assertEquals("NONE", rows.getString(1));
					}
					assertFalse(before.getPartitioning().getPartitions().get(0).isCompression());
				}
				var segments = target.getIndexes().get(name + "_IX").getPartitioning().getPartitions();
				if (mode == Mode.PARENT) {
					Partition parent = new Partition("P3").setHighValue(listRoot ? "220" : "300");
					parent.getSubPartitions().add(new SubPartition("S31"));
					parent.getSubPartitions().add(new SubPartition("S32"));
					target.getPartitioning().getPartitions().add(parent);
					Partition index = new Partition("IP3").setHighValue(listRoot ? "220" : "300");
					index.getSpecifics().put("oracle.index.status", "N/A");
					index.getSubPartitions().add(segment("IS31", null));
					index.getSubPartitions().add(segment("IS32", null));
					segments.add(index);
				} else if (mode == Mode.CHILD || mode == Mode.HASH_ADD || mode == Mode.RANGE_CHILD) {
					String boundary = hash ? null : mode == Mode.RANGE_CHILD ? "'ZZZ'" : "'DE'";
					target.getPartitioning().getPartitions().get(0).getSubPartitions()
							.add(new SubPartition("S13").setHighValue(boundary));
					segments.get(0).getSubPartitions().add(segment("IS13", boundary));
				} else {
					try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + name + " SUBPARTITION(S12)")) {
						assertTrue(rows.next());
						assertTrue(rows.getInt(1) > 0, "Removed HASH bucket must contain data");
					}
					target.getPartitioning().getPartitions().get(0).getSubPartitions().remove(1);
					segments.get(0).getSubPartitions().remove(1);
				}
				if (quotedCase) {
					Partition added = target.getPartitioning().getPartitions().get(mode == Mode.PARENT ? 2 : 0);
					Partition local = segments.get(mode == Mode.PARENT ? 2 : 0);
					added.getSubPartitions().get(mode == Mode.PARENT ? 0 : 2).setName("s12");
					local.getSubPartitions().get(mode == Mode.PARENT ? 0 : 2).setName("is12");
				}
				if (rowCompression != null) {
					String requestedMode = rowCompression.endsWith("_OFF") ? null : rowMode;
					if (mode == Mode.PARENT) {
						Partition addedParent = target.getPartitioning().getPartitions().get(2);
						rowMode(addedParent, requestedMode);
						for (SubPartition child : addedParent.getSubPartitions())
							rowMode(child, requestedMode);
					} else
						rowMode(target.getPartitioning().getPartitions().get(0).getSubPartitions().get(2),
								requestedMode);
				}
				if (compression != null)
					for (Partition parent : segments) {
						parent.setCompression(true);
						parent.setCompressionType(compression);
						for (SubPartition child : parent.getSubPartitions()) {
							child.setCompression(true);
							child.setCompressionType(compression);
						}
					}
				if (uncompressedParent) {
					var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
					assertTrue(
							registry.getSqlFactory(target, SqlType.ALTER).createDiffSql(before.diff(target)).get(0)
									.getSqlText().contains(" NOCOMPRESS"),
							"Compressed table default requires explicit NOCOMPRESS");
				}
				execute(connection, before, target);
				verifyState(connection, target, original);
				if (mode == Mode.COALESCE) {
					recreate(connection, read(connection, name), original);
					return;
				}
				Row added = new Row(mode == Mode.PARENT ? 220 : mode == Mode.HASH_ADD ? 33 : 3,
						mode == Mode.RANGE_CHILD ? "ZZ" : "DE");
				sql.execute("INSERT INTO " + name + " VALUES (" + added.id() + ", '" + added.region() + "')");
				List<Row> withAdded = new ArrayList<>(original);
				withAdded.add(added);
				withAdded.sort(Comparator.comparingInt(Row::id));
				verifyState(connection, target, withAdded);
				Table actual = read(connection, name), dropped = actual.clone();
				if (mode == Mode.PARENT) {
					dropped.getPartitioning().getPartitions().remove(2);
					dropped.getIndexes().get(name + "_IX").getPartitioning().getPartitions().remove(2);
				} else {
					dropped.getPartitioning().getPartitions().get(0).getSubPartitions().remove(2);
					dropped.getIndexes().get(name + "_IX").getPartitioning().getPartitions().get(0).getSubPartitions()
							.remove(2);
				}
				execute(connection, actual, dropped);
				List<Row> remaining = mode == Mode.HASH_ADD ? withAdded : original;
				verifyState(connection, dropped, remaining);
				recreate(connection, read(connection, name), remaining);
			} finally {
				if (created)
					sql.execute("DROP TABLE " + name + " PURGE");
			}
		}
	}

	private void recreate(Connection connection, Table table, List<Row> original) throws Exception {
		Table restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		assertNotNull(com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition.get(restored));
		var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var operations = registry.createSql(restored, SqlType.CREATE);
		assertFalse(operations.isEmpty());
		try (var sql = connection.createStatement()) {
			sql.execute("DROP TABLE " + table.getName() + " PURGE");
			for (var operation : operations)
				sql.execute(operation.getSqlText());
			for (Row row : original)
				sql.execute("INSERT INTO " + table.getName() + " VALUES (" + row.id() + ", '" + row.region() + "')");
		}
		verifyState(connection, restored, original);
		Table actual = read(connection, table.getName());
		for (int i = 0; i < restored.getPartitioning().getPartitions().size(); i++) {
			var wanted = restored.getPartitioning().getPartitions().get(i);
			var found = actual.getPartitioning().getPartitions().get(i);
			for (int j = 0; j < wanted.getSubPartitions().size(); j++) {
				assertEquals(wanted.getSubPartitions().get(j).getSpecifics(),
						found.getSubPartitions().get(j).getSpecifics());
				assertEquals(wanted.getSubPartitions().get(j).getTableSpaceName(),
						found.getSubPartitions().get(j).getTableSpaceName());
			}
		}
	}

	private SubPartition segment(String name, String bound) {
		SubPartition part = new SubPartition(name).setHighValue(bound);
		part.getSpecifics().put("oracle.index.status", "USABLE");
		return part;
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
		assertEquals(1, operations.size());
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(after, SqlType.CREATE),
				"A changed partition model must not replay the old complete CREATE DDL");
		try (var sql = connection.createStatement()) {
			sql.execute(operations.get(0).getSqlText());
		}
	}

	private void verifyState(Connection connection, Table target, List<Row> expected) throws Exception {
		String name = target.getName();
		try (var sql = connection.createStatement();
				var rows = sql.executeQuery(
						"SELECT /*+ INDEX(t " + name + "_IX) */ ID, REGION FROM " + name + " t ORDER BY ID")) {
			for (Row row : expected) {
				assertTrue(rows.next());
				assertEquals(row.id(), rows.getInt(1));
				assertEquals(row.region(), rows.getString(2));
			}
			assertFalse(rows.next());
		}
		Table actual = read(connection, name);
		assertEquals(target.isCompression(), actual.isCompression(), "Table compression");
		assertEquals(target.getCompressionType(), actual.getCompressionType(), "Table compression mode");
		var expectedIndex = target.getIndexes().get(name + "_IX");
		var actualIndex = actual.getIndexes().get(name + "_IX");
		assertEquals(expectedIndex.isCompression(), actualIndex.isCompression());
		assertEquals(expectedIndex.getCompressionType(), actualIndex.getCompressionType());
		assertEquals(expectedIndex.getSpecifics().get("oracle.index.prefixLength"),
				actualIndex.getSpecifics().get("oracle.index.prefixLength"));
		assertEquals(target.getPartitioning().getPartitions().stream().map(Partition::getName).toList(),
				actual.getPartitioning().getPartitions().stream().map(Partition::getName).toList());
		for (int i = 0; i < target.getPartitioning().getPartitions().size(); i++) {
			var expectedParent = target.getPartitioning().getPartitions().get(i);
			var actualParent = actual.getPartitioning().getPartitions().get(i);
			assertEquals(expectedParent.isCompression(), actualParent.isCompression(), "Table parent compression");
			assertEquals(expectedParent.getCompressionType(), actualParent.getCompressionType(), "Table parent mode");
			if (expectedParent.getSpecifics().containsKey("PCT_FREE"))
				assertEquals(expectedParent.getSpecifics().get("PCT_FREE"),
						actualParent.getSpecifics().get("PCT_FREE"));
			for (int j = 0; j < expectedParent.getSubPartitions().size(); j++) {
				var expectedChild = expectedParent.getSubPartitions().get(j);
				var actualChild = actualParent.getSubPartitions().get(j);
				assertEquals(expectedChild.isCompression(), actualChild.isCompression(), "Table child compression");
				assertEquals(expectedChild.getCompressionType(), actualChild.getCompressionType(), "Table child mode");
				if (expectedChild.getSpecifics().containsKey("PCT_FREE"))
					assertEquals(expectedChild.getSpecifics().get("PCT_FREE"),
							actualChild.getSpecifics().get("PCT_FREE"));
			}
			assertEquals(
					target.getPartitioning().getPartitions().get(i).getSubPartitions().stream()
							.map(SubPartition::getName).toList(),
					actual.getPartitioning().getPartitions().get(i).getSubPartitions().stream()
							.map(SubPartition::getName).toList());
			var wanted = target.getIndexes().get(name + "_IX").getPartitioning().getPartitions().get(i);
			var found = actual.getIndexes().get(name + "_IX").getPartitioning().getPartitions().get(i);
			assertEquals(wanted.getName(), found.getName());
			assertEquals(wanted.isCompression(), found.isCompression());
			if (wanted.getCompressionType() != null)
				assertEquals(wanted.getCompressionType(), found.getCompressionType());
			assertEquals(wanted.getSubPartitions().stream().map(SubPartition::getName).toList(),
					found.getSubPartitions().stream().map(SubPartition::getName).toList());
			for (var sub : found.getSubPartitions())
				assertEquals("USABLE", sub.getSpecifics().get("oracle.index.status"));
			for (int j = 0; j < wanted.getSubPartitions().size(); j++) {
				var expectedChild = wanted.getSubPartitions().get(j);
				var actualChild = found.getSubPartitions().get(j);
				assertEquals(expectedChild.isCompression(), actualChild.isCompression());
				if (expectedChild.getCompressionType() != null)
					assertEquals(expectedChild.getCompressionType(), actualChild.getCompressionType());
			}
		}
		try (var sql = connection.createStatement();
				var rows = sql.executeQuery("SELECT COUNT(*) FROM USER_INDEXES WHERE TABLE_NAME='" + name
						+ "' AND STATUS NOT IN ('VALID','N/A')")) {
			assertTrue(rows.next());
			assertEquals(0, rows.getInt(1));
		}
	}
}
