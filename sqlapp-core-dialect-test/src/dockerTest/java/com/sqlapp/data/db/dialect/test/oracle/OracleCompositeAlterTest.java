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
	void parentRangeRangeRestoresRoutingAndIndexes() throws Exception {
		verify("RANGE", true, false, true, false, false);
	}

	@Test
	void parentRangeListRestoresRoutingAndIndexes() throws Exception {
		verify("LIST", true, false, true, false, false);
	}

	@Test
	void parentListRangeRestoresRoutingAndIndexes() throws Exception {
		verify("RANGE", true, false, true, false, true);
	}

	@Test
	void parentListListRestoresRoutingAndIndexes() throws Exception {
		verify("LIST", true, false, true, false, true);
	}

	@Test
	void compressedOnlineRangeChildrenMoveAndReturn() throws Exception {
		verify("RANGE", false, false, false, false, false, true, true);
	}

	@Test
	void compressedOnlineListChildrenMoveAndReturn() throws Exception {
		verify("LIST", false, false, false, false, false, true, true);
	}

	@Test
	void compressedOnlineHashChildrenMoveAndReturn() throws Exception {
		verify("HASH", false, false, false, false, false, true, true);
	}

	@Test
	void compressedOnlineRangeChildSplitAndMerge() throws Exception {
		verify("RANGE", true, false, false, false, false, false, true);
	}

	@Test
	void compressedOnlineListChildSplitAndMerge() throws Exception {
		verify("LIST", true, false, false, false, false, false, true);
	}

	@Test
	void finiteParentRangeRangeSplitPreservesBounds() throws Exception {
		verifyParentCase("RANGE", false, null, true);
	}

	@Test
	void finiteParentRangeListSplitPreservesBounds() throws Exception {
		verifyParentCase("LIST", false, null, true);
	}

	@Test
	void finiteParentListRangeSplitPreservesBounds() throws Exception {
		verifyParentCase("RANGE", true, null, true);
	}

	@Test
	void finiteParentListListSplitPreservesBounds() throws Exception {
		verifyParentCase("LIST", true, null, true);
	}

	@Test
	void compressedParentRangeHashBasic() throws Exception {
		verifyParentCase("HASH", false, "BASIC", false);
	}

	@Test
	void compressedParentRangeRangeBasic() throws Exception {
		verifyParentCase("RANGE", false, "BASIC", false);
	}

	@Test
	void compressedParentRangeListBasic() throws Exception {
		verifyParentCase("LIST", false, "BASIC", false);
	}

	@Test
	void compressedParentListHashBasic() throws Exception {
		verifyParentCase("HASH", true, "BASIC", false);
	}

	@Test
	void compressedParentListRangeBasic() throws Exception {
		verifyParentCase("RANGE", true, "BASIC", false);
	}

	@Test
	void compressedParentListListBasic() throws Exception {
		verifyParentCase("LIST", true, "BASIC", false);
	}

	@Test
	void compressedParentRangeHashAdvanced() throws Exception {
		verifyParentCase("HASH", false, "ADVANCED", false);
	}

	@Test
	void compressedParentRangeRangeAdvanced() throws Exception {
		verifyParentCase("RANGE", false, "ADVANCED", false);
	}

	@Test
	void compressedParentRangeListAdvanced() throws Exception {
		verifyParentCase("LIST", false, "ADVANCED", false);
	}

	@Test
	void compressedParentListHashAdvanced() throws Exception {
		verifyParentCase("HASH", true, "ADVANCED", false);
	}

	@Test
	void compressedParentListRangeAdvanced() throws Exception {
		verifyParentCase("RANGE", true, "ADVANCED", false);
	}

	@Test
	void compressedParentListListAdvanced() throws Exception {
		verifyParentCase("LIST", true, "ADVANCED", false);
	}

	@Test
	void compressedLocalParentRangeHashPrefix() throws Exception {
		verify("HASH", true, false, true, false, false, false, false, null, false, "ENABLED");
	}

	@Test
	void compressedLocalParentRangeRangePrefix() throws Exception {
		verify("RANGE", true, false, true, false, false, false, false, null, false, "ENABLED");
	}

	@Test
	void compressedLocalParentRangeListPrefix() throws Exception {
		verify("LIST", true, false, true, false, false, false, false, null, false, "ENABLED");
	}

	@Test
	void compressedLocalParentListHashPrefix() throws Exception {
		verify("HASH", true, false, true, false, true, false, false, null, false, "ENABLED");
	}

	@Test
	void compressedLocalParentListRangePrefix() throws Exception {
		verify("RANGE", true, false, true, false, true, false, false, null, false, "ENABLED");
	}

	@Test
	void compressedLocalParentListListPrefix() throws Exception {
		verify("LIST", true, false, true, false, true, false, false, null, false, "ENABLED");
	}

	@Test
	void compressedLocalChildRangeSplitPrefix() throws Exception {
		verify("RANGE", true, false, false, false, false, false, false, null, false, "ENABLED");
	}

	@Test
	void compressedLocalChildListSplitPrefix() throws Exception {
		verify("LIST", true, false, false, false, false, false, false, null, false, "ENABLED");
	}

	@Test
	void compressedLocalChildHashMovePrefix() throws Exception {
		verify("HASH", false, false, false, false, false, true, false, null, false, "ENABLED");
	}

	@Test
	void compressedLocalChildRangeMovePrefix() throws Exception {
		verify("RANGE", false, false, false, false, false, true, false, null, false, "ENABLED");
	}

	@Test
	void compressedLocalChildListMovePrefix() throws Exception {
		verify("LIST", false, false, false, false, false, true, false, null, false, "ENABLED");
	}

	@Test
	void compressedLocalParentRangeHashAdvancedLow() throws Exception {
		verify("HASH", true, false, true, false, false, false, false, null, false, "ADVANCED LOW");
	}

	@Test
	void compressedLocalParentRangeRangeAdvancedLow() throws Exception {
		verify("RANGE", true, false, true, false, false, false, false, null, false, "ADVANCED LOW");
	}

	@Test
	void compressedLocalParentRangeListAdvancedLow() throws Exception {
		verify("LIST", true, false, true, false, false, false, false, null, false, "ADVANCED LOW");
	}

	@Test
	void compressedLocalParentListHashAdvancedLow() throws Exception {
		verify("HASH", true, false, true, false, true, false, false, null, false, "ADVANCED LOW");
	}

	@Test
	void compressedLocalParentListRangeAdvancedLow() throws Exception {
		verify("RANGE", true, false, true, false, true, false, false, null, false, "ADVANCED LOW");
	}

	@Test
	void compressedLocalParentListListAdvancedLow() throws Exception {
		verify("LIST", true, false, true, false, true, false, false, null, false, "ADVANCED LOW");
	}

	@Test
	void compressedLocalChildRangeSplitAdvancedLow() throws Exception {
		verify("RANGE", true, false, false, false, false, false, false, null, false, "ADVANCED LOW");
	}

	@Test
	void compressedLocalChildListSplitAdvancedLow() throws Exception {
		verify("LIST", true, false, false, false, false, false, false, null, false, "ADVANCED LOW");
	}

	@Test
	void compressedLocalChildHashMoveAdvancedLow() throws Exception {
		verify("HASH", false, false, false, false, false, true, false, null, false, "ADVANCED LOW");
	}

	@Test
	void compressedLocalChildRangeMoveAdvancedLow() throws Exception {
		verify("RANGE", false, false, false, false, false, true, false, null, false, "ADVANCED LOW");
	}

	@Test
	void compressedLocalChildListMoveAdvancedLow() throws Exception {
		verify("LIST", false, false, false, false, false, true, false, null, false, "ADVANCED LOW");
	}

	@Test
	void compressedLocalParentRangeHashAdvancedHigh() throws Exception {
		verify("HASH", true, false, true, false, false, false, false, null, false, "ADVANCED HIGH");
	}

	@Test
	void compressedLocalParentRangeRangeAdvancedHigh() throws Exception {
		verify("RANGE", true, false, true, false, false, false, false, null, false, "ADVANCED HIGH");
	}

	@Test
	void compressedLocalParentRangeListAdvancedHigh() throws Exception {
		verify("LIST", true, false, true, false, false, false, false, null, false, "ADVANCED HIGH");
	}

	@Test
	void compressedLocalParentListHashAdvancedHigh() throws Exception {
		verify("HASH", true, false, true, false, true, false, false, null, false, "ADVANCED HIGH");
	}

	@Test
	void compressedLocalParentListRangeAdvancedHigh() throws Exception {
		verify("RANGE", true, false, true, false, true, false, false, null, false, "ADVANCED HIGH");
	}

	@Test
	void compressedLocalParentListListAdvancedHigh() throws Exception {
		verify("LIST", true, false, true, false, true, false, false, null, false, "ADVANCED HIGH");
	}

	@Test
	void compressedLocalChildRangeSplitAdvancedHigh() throws Exception {
		verify("RANGE", true, false, false, false, false, false, false, null, false, "ADVANCED HIGH");
	}

	@Test
	void compressedLocalChildListSplitAdvancedHigh() throws Exception {
		verify("LIST", true, false, false, false, false, false, false, null, false, "ADVANCED HIGH");
	}

	@Test
	void compressedLocalChildHashMoveAdvancedHigh() throws Exception {
		verify("HASH", false, false, false, false, false, true, false, null, false, "ADVANCED HIGH");
	}

	@Test
	void compressedLocalChildRangeMoveAdvancedHigh() throws Exception {
		verify("RANGE", false, false, false, false, false, true, false, null, false, "ADVANCED HIGH");
	}

	@Test
	void compressedLocalChildListMoveAdvancedHigh() throws Exception {
		verify("LIST", false, false, false, false, false, true, false, null, false, "ADVANCED HIGH");
	}

	@Test
	void compressedLocalAndTableDefaultsBasic() throws Exception {
		verify("HASH", true, false, true, false, false, false, false, "TABLE_BASIC", false, "ENABLED");
	}

	@Test
	void compressedLocalAndTableDefaultsAdvanced() throws Exception {
		verify("LIST", true, false, true, false, true, false, false, "TABLE_ADVANCED", false, "ADVANCED HIGH");
	}

	@Test
	void compressedLocalFiniteRangeListPrefix() throws Exception {
		verify("LIST", true, false, true, false, false, false, false, null, true, "ENABLED");
	}

	@Test
	void compressedLocalFiniteListRangeAdvanced() throws Exception {
		verify("RANGE", true, false, true, false, true, false, false, null, true, "ADVANCED HIGH");
	}

	private void verifyParentCase(String kind, boolean listRoot, String compression, boolean finite) throws Exception {
		verify(kind, true, false, true, false, listRoot, false, false, compression, finite);
	}

	@Test
	void tableCompressedRangeHashBasic() throws Exception {
		verifyParentCase("HASH", false, "TABLE_BASIC", false);
	}

	@Test
	void tableCompressedRangeRangeBasic() throws Exception {
		verifyParentCase("RANGE", false, "TABLE_BASIC", false);
	}

	@Test
	void tableCompressedRangeListBasic() throws Exception {
		verifyParentCase("LIST", false, "TABLE_BASIC", false);
	}

	@Test
	void tableCompressedListHashBasic() throws Exception {
		verifyParentCase("HASH", true, "TABLE_BASIC", false);
	}

	@Test
	void tableCompressedListRangeBasic() throws Exception {
		verifyParentCase("RANGE", true, "TABLE_BASIC", false);
	}

	@Test
	void tableCompressedListListBasic() throws Exception {
		verifyParentCase("LIST", true, "TABLE_BASIC", false);
	}

	@Test
	void tableCompressedRangeHashAdvanced() throws Exception {
		verifyParentCase("HASH", false, "TABLE_ADVANCED", false);
	}

	@Test
	void tableCompressedRangeRangeAdvanced() throws Exception {
		verifyParentCase("RANGE", false, "TABLE_ADVANCED", false);
	}

	@Test
	void tableCompressedRangeListAdvanced() throws Exception {
		verifyParentCase("LIST", false, "TABLE_ADVANCED", false);
	}

	@Test
	void tableCompressedListHashAdvanced() throws Exception {
		verifyParentCase("HASH", true, "TABLE_ADVANCED", false);
	}

	@Test
	void tableCompressedListRangeAdvanced() throws Exception {
		verifyParentCase("RANGE", true, "TABLE_ADVANCED", false);
	}

	@Test
	void tableCompressedListListAdvanced() throws Exception {
		verifyParentCase("LIST", true, "TABLE_ADVANCED", false);
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

	@Test
	void defaultChildRangeTableBasic() throws Exception {
		verifyChildDefault("RANGE", "TABLE_BASIC");
	}

	@Test
	void defaultChildListTableBasic() throws Exception {
		verifyChildDefault("LIST", "TABLE_BASIC");
	}

	@Test
	void defaultChildRangeTableAdvanced() throws Exception {
		verifyChildDefault("RANGE", "TABLE_ADVANCED");
	}

	@Test
	void defaultChildListTableAdvanced() throws Exception {
		verifyChildDefault("LIST", "TABLE_ADVANCED");
	}

	@Test
	void defaultChildRangeParentBasic() throws Exception {
		verifyChildDefault("RANGE", "BASIC");
	}

	@Test
	void defaultChildListParentBasic() throws Exception {
		verifyChildDefault("LIST", "BASIC");
	}

	@Test
	void defaultChildRangeParentAdvanced() throws Exception {
		verifyChildDefault("RANGE", "ADVANCED");
	}

	@Test
	void defaultChildListParentAdvanced() throws Exception {
		verifyChildDefault("LIST", "ADVANCED");
	}

	@Test
	void defaultChildRangeNocompress() throws Exception {
		verifyChildDefault("RANGE", "TABLE_BASIC_OFF");
	}

	@Test
	void defaultChildListNocompress() throws Exception {
		verifyChildDefault("LIST", "TABLE_ADVANCED_OFF");
	}

	private void verifyChildDefault(String kind, String mode) throws Exception {
		verify(kind, true, false, false, false, false, false, false, mode, false, "ENABLED");
	}

	@Test
	void quotedRoutingParentHash() throws Exception {
		verifyQuotedRouting("HASH", true);
	}

	@Test
	void quotedRoutingParentRange() throws Exception {
		verifyQuotedRouting("RANGE", true);
	}

	@Test
	void quotedRoutingParentList() throws Exception {
		verifyQuotedRouting("LIST", true);
	}

	@Test
	void quotedRoutingChildRange() throws Exception {
		verifyQuotedRouting("RANGE", false);
	}

	@Test
	void quotedRoutingChildList() throws Exception {
		verifyQuotedRouting("LIST", false);
	}

	@Test
	void quotedListParentHash() throws Exception {
		verifyQuotedRouting("HASH", true, true, false);
	}

	@Test
	void quotedListParentRange() throws Exception {
		verifyQuotedRouting("RANGE", true, true, false);
	}

	@Test
	void quotedListParentList() throws Exception {
		verifyQuotedRouting("LIST", true, true, false);
	}

	@Test
	void quotedOnlineChildRange() throws Exception {
		verifyQuotedRouting("RANGE", false, false, true);
	}

	@Test
	void quotedOnlineChildList() throws Exception {
		verifyQuotedRouting("LIST", false, false, true);
	}

	private enum QuotedRouting {
		NONE, OFFLINE, ONLINE
	}

	private void verifyQuotedRouting(String kind, boolean parent) throws Exception {
		verifyQuotedRouting(kind, parent, false, false);
	}

	private void verifyQuotedRouting(String kind, boolean parent, boolean listRoot, boolean online) throws Exception {
		assertFalse(parent && online, "Multi-step parent plans do not support whole-plan ONLINE");
		verify(kind, true, false, parent, false, listRoot, false, false, parent ? "TABLE_BASIC" : "TABLE_ADVANCED",
				false, "ENABLED", online ? QuotedRouting.ONLINE : QuotedRouting.OFFLINE);
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
		verify(kind, split, simple, parent, largerDefault, listRoot, groupedMove, false);
	}

	private void verify(String kind, boolean split, boolean simple, boolean parent, boolean largerDefault,
			boolean listRoot, boolean groupedMove, boolean rowCompression) throws Exception {
		verify(kind, split, simple, parent, largerDefault, listRoot, groupedMove, rowCompression, null, false);
	}

	private void verify(String kind, boolean split, boolean simple, boolean parent, boolean largerDefault,
			boolean listRoot, boolean groupedMove, boolean rowCompression, String parentCompression,
			boolean finiteParent) throws Exception {
		verify(kind, split, simple, parent, largerDefault, listRoot, groupedMove, rowCompression, parentCompression,
				finiteParent, null);
	}

	private void verify(String kind, boolean split, boolean simple, boolean parent, boolean largerDefault,
			boolean listRoot, boolean groupedMove, boolean rowCompression, String parentCompression,
			boolean finiteParent, String localCompression) throws Exception {
		verify(kind, split, simple, parent, largerDefault, listRoot, groupedMove, rowCompression, parentCompression,
				finiteParent, localCompression, QuotedRouting.NONE);
	}

	private void verify(String kind, boolean split, boolean simple, boolean parent, boolean largerDefault,
			boolean listRoot, boolean groupedMove, boolean rowCompression, String parentCompression,
			boolean finiteParent, String localCompression, QuotedRouting quoting) throws Exception {
		boolean quotedCase = quoting != QuotedRouting.NONE;
		String name = "ALTER_" + kind + (listRoot ? "_LIST" : "")
				+ (rowCompression ? "_COMP"
						: groupedMove ? "_GROUP"
								: parent ? largerDefault ? "_PARENT_BIG" : "_PARENT"
										: split ? "_SPLIT" : simple ? "_SIMPLE" : "_MOVE")
				+ (finiteParent ? "_FINITE" : parentCompression == null ? "" : "_" + parentCompression)
				+ (localCompression == null ? "" : "_IX_" + localCompression.replace(" ", "_"))
				+ (quotedCase ? "_CASE" : "") + (quoting == QuotedRouting.ONLINE ? "_ONLINE" : "");
		try (Connection connection = ORACLE.createConnection(""); var sql = connection.createStatement()) {
			boolean created = false;
			try {
				String compressionMode = parentCompression == null ? null : parentCompression.replace("_OFF", "");
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
				if (parent && !kind.equals("HASH")) {
					suffix = suffix.replace("VALUES (120,170)", "VALUES (120,121,170,171)");
					if (!finiteParent)
						suffix = suffix.replace("VALUES LESS THAN(40)", "VALUES LESS THAN(MAXVALUE)")
								.replace("VALUES (20,30)", "VALUES (DEFAULT)");
				}
				if (parentCompression != null)
					suffix = suffix.replace(" (SUBPARTITION S21",
							" " + (parentCompression.endsWith("BASIC") ? "COMPRESS" : "COMPRESS FOR OLTP")
									+ " (SUBPARTITION S21");
				if (parentCompression != null && parentCompression.startsWith("TABLE_"))
					suffix = (compressionMode.endsWith("BASIC") ? "COMPRESS " : "COMPRESS FOR OLTP ") + suffix;
				if (!parent && compressionMode != null) {
					suffix = suffix.replace(" (SUBPARTITION S11",
							" " + (compressionMode.endsWith("BASIC") ? "COMPRESS" : "COMPRESS FOR OLTP")
									+ " (SUBPARTITION S11");
					if (parentCompression.endsWith("_OFF"))
						suffix = suffix.replace("SUBPARTITION S12" + bound(kind, false),
								"SUBPARTITION S12" + bound(kind, false) + " NOCOMPRESS");
				}
				if (rowCompression && split && !parent)
					suffix = suffix.replace("SUBPARTITION S12" + bound(kind, false),
							"SUBPARTITION S12" + bound(kind, false) + " COMPRESS");
				if (quotedCase)
					suffix = suffix.replace("PARTITION P2 ", "PARTITION \"p1\" ").replace("SUBPARTITION S21",
							"SUBPARTITION \"s11\"");
				sql.execute("CREATE TABLE " + name + " (ID NUMBER PRIMARY KEY, REGION NUMBER) " + suffix);
				created = true;
				sql.execute("CREATE INDEX " + name + "_IX ON " + name + "(REGION)"
						+ (localCompression == null ? ""
								: localCompression.equals("ENABLED") ? " COMPRESS 1" : " COMPRESS " + localCompression)
						+ " LOCAL (PARTITION IP1" + (simple ? "" : " (SUBPARTITION IS11, SUBPARTITION IS12)")
						+ ", PARTITION " + (quotedCase ? "\"ip1\"" : "IP2")
						+ (simple ? ""
								: " (SUBPARTITION " + (quotedCase ? "\"is11\"" : "IS21") + ", SUBPARTITION IS22)")
						+ ")");
				List<Integer> ids = kind.equals("HASH") ? java.util.stream.IntStream
						.concat(java.util.stream.IntStream.rangeClosed(1, 32),
								parent ? java.util.stream.IntStream.of(120, 170) : java.util.stream.IntStream.empty())
						.boxed().toList() : parent ? List.of(1, 2, 3, 120, 121, 170, 171) : List.of(1, 2, 3, 120);
				for (int id : ids)
					sql.execute("INSERT INTO " + name + " VALUES (" + id + "," + region(kind, id) + ")");
				Table before = read(connection, name), after = before.clone();
				if (localCompression != null) {
					Index index = before.getIndexes().get(name + "_IX");
					assertTrue(index.isCompression(), "Local index compression must be read");
					assertEquals(localCompression, index.getCompressionType());
					if (localCompression.equals("ENABLED"))
						assertEquals("1", index.getSpecifics().get("oracle.index.prefixLength"));
				}
				if (parentCompression != null && parentCompression.startsWith("TABLE_")) {
					assertTrue(before.isCompression(), "Table default must be read from ALL_PART_TABLES");
					assertEquals(compressionMode.substring("TABLE_".length()), before.getCompressionType());
				}
				if (!parent && compressionMode != null) {
					Partition p = before.getPartitioning().getPartitions().get(0);
					assertTrue(p.isCompression());
					assertEquals(compressionMode.endsWith("BASIC") ? "BASIC" : "ADVANCED", p.getCompressionType());
					assertEquals(!parentCompression.endsWith("_OFF"), p.getSubPartitions().get(1).isCompression());
				}
				if (parent) {
					for (Partitioning info : List.of(after.getPartitioning(),
							after.getIndexes().get(name + "_IX").getPartitioning())) {
						Partition original = info.getPartitions().remove(1);
						boolean index = info.getIndex() != null;
						for (boolean left : List.of(true, false)) {
							Partition part = original.clone()
									.setName((quotedCase ? index ? left ? "ilow" : "ILOW" : left ? "plow" : "PLOW"
											: (index ? "I" : "P") + (left ? "LOW" : "HIGH")))
									.setHighValue(listRoot ? kind.equals("HASH") ? left ? "120" : "170"
											: left ? "120, 121" : "170, 171" : left ? "150" : "200");
							if (index && part.getTableSpaceName() == null)
								part.setTableSpaceName(part.getSubPartitions().get(0).getTableSpaceName());
							for (int j = 0; j < part.getSubPartitions().size(); j++)
								part.getSubPartitions().get(j)
										.setName((quotedCase ? index ? left ? "ic" : "IC" : left ? "c" : "C"
												: (index ? "IC" : "C") + (left ? "L" : "H")) + j);
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
						part.setName(quotedCase ? index ? "ip1" : "p1" : index ? "IP2" : "P2");
						if (listRoot)
							part.setHighValue(kind.equals("HASH") ? "120, 170" : "120, 121, 170, 171");
						for (int j = 0; j < part.getSubPartitions().size(); j++)
							part.getSubPartitions().get(j).setName(
									quotedCase && j == 0 ? index ? "is11" : "s11" : (index ? "IS2" : "S2") + (j + 1));
						info.getPartitions().add(part);
					}
					if (finiteParent) {
						var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
						assertThrows(IllegalArgumentException.class,
								() -> registry.getSqlFactory(merged, SqlType.ALTER).createDiffSql(actual.diff(merged)));
					} else {
						execute(connection, actual, merged);
						verifyState(connection, merged, kind, ids);
					}
				} else if (split) {
					for (Partitioning info : List.of(after.getPartitioning(),
							after.getIndexes().get(name + "_IX").getPartitioning())) {
						var children = info.getPartitions().get(0).getSubPartitions();
						SubPartition original = children.remove(1);
						boolean index = info.getIndex() != null;
						children.add(original.clone().setName(quotedCase ? index ? "il" : "sl" : index ? "IL" : "SL")
								.setHighValue(kind.equals("LIST") ? "20" : "30"));
						children.add(original.clone().setName(quotedCase ? index ? "IL" : "SL" : index ? "IR" : "SR")
								.setHighValue(kind.equals("LIST") ? "30" : "40"));
					}
					execute(connection, before, after, 1, rowCompression || quoting == QuotedRouting.ONLINE);
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
					execute(connection, actual, merged, 1, rowCompression || quoting == QuotedRouting.ONLINE);
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
					if (rowCompression)
						for (int i = 0; i < 2; i++) {
							SubPartition child = after.getPartitioning().getPartitions().get(0).getSubPartitions()
									.get(i);
							child.setCompression(true);
							child.setCompressionType(i == 0 ? "BASIC" : "ADVANCED");
							child.getSpecifics().put("COMPRESS_FOR", child.getCompressionType());
							child.getSpecifics().put("PCT_FREE", i == 0 ? "0" : "10");
						}
					if (rowCompression)
						after.getPartitioning().getPartitions().get(1).getSubPartitions().get(0)
								.setTableSpaceName(SPACE);
					execute(connection, before, after, rowCompression ? 3 : groupedMove ? 2 : 1, rowCompression);
					verifyState(connection, after, kind, ids);
					if (groupedMove) {
						Table actual = read(connection, name), restored = actual.clone();
						for (int i = 0; i < 2; i++)
							restored.getPartitioning().getPartitions().get(0).getSubPartitions().get(i)
									.setTableSpaceName(before.getPartitioning().getPartitions().get(0)
											.getSubPartitions().get(i).getTableSpaceName());
						if (rowCompression)
							for (SubPartition child : restored.getPartitioning().getPartitions().get(0)
									.getSubPartitions()) {
								child.setCompression(false);
								child.setCompressionType(null);
								child.getSpecifics().remove("COMPRESS_FOR");
							}
						if (rowCompression)
							restored.getPartitioning().getPartitions().get(1).getSubPartitions().get(0)
									.setTableSpaceName(before.getPartitioning().getPartitions().get(1)
											.getSubPartitions().get(0).getTableSpaceName());
						execute(connection, actual, restored, rowCompression ? 3 : 2, rowCompression);
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
				: id == 1 || id == 121 || id == 171 ? 10
						: id == 3 ? (kind.equals("LIST") ? 30 : 35) : kind.equals("LIST") ? 20 : 25;
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
						: after.getPartitioning().getSubPartitioningType() == PartitioningType.Hash ? 2 : 3);
	}

	private void execute(Connection connection, Table before, Table after, int expectedStatements) throws Exception {
		execute(connection, before, after, expectedStatements, false);
	}

	private void execute(Connection connection, Table before, Table after, int expectedStatements, boolean online)
			throws Exception {
		var spaces = indexSpaces(connection, before.getName());
		var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var factory = (com.sqlapp.data.db.dialect.oracle.sql.OracleAlterTableFactory) registry.getSqlFactory(after,
				SqlType.ALTER);
		factory.setOnlinePartitionMaintenance(online);
		var operations = factory.createDiffSql(before.diff(after));
		if (online)
			assertTrue(
					operations.stream().filter(operation -> !operation.getSqlText().contains(" MODIFY SUBPARTITION "))
							.allMatch(operation -> operation.getSqlText().endsWith(" ONLINE")));
		assertEquals(expectedStatements, operations.size());
		try (var sql = connection.createStatement()) {
			for (int i = 0; i < operations.size(); i++) {
				sql.execute(operations.get(i).getSqlText());
				if (i == 0 && before.getPartitioning().getPartitions().size() > after.getPartitioning().getPartitions()
						.size()) {
					try (var count = connection.prepareStatement(
							"SELECT COUNT(*) FROM USER_TAB_SUBPARTITIONS WHERE TABLE_NAME=? AND PARTITION_NAME=?")) {
						count.setString(1, before.getName());
						count.setString(2, after.getPartitioning().getPartitions().get(1).getName());
						try (var rows = count.executeQuery()) {
							assertTrue(rows.next());
							assertTrue(rows.getInt(1) > 0);
							System.out.println("Native MERGE child count: " + rows.getInt(1) + "; requested: "
									+ after.getPartitioning().getPartitions().get(1).getSubPartitions().size());
						}
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
		assertEquals(expected.isCompression(), actual.isCompression(), "Table compression default");
		assertEquals(expected.getCompressionType(), actual.getCompressionType(), "Table compression type default");
		assertEquals(expected.getSpecifics().get("COMPRESS_FOR"), actual.getSpecifics().get("COMPRESS_FOR"),
				"Table compression catalog default");
		Index expectedLocal = expected.getIndexes().get(expected.getName() + "_IX");
		Index actualLocal = actual.getIndexes().get(expected.getName() + "_IX");
		assertEquals(expectedLocal.isCompression(), actualLocal.isCompression(), "Local index compression");
		assertEquals(expectedLocal.getCompressionType(), actualLocal.getCompressionType(),
				"Local index compression mode");
		assertEquals(expectedLocal.getSpecifics().get("oracle.index.prefixLength"),
				actualLocal.getSpecifics().get("oracle.index.prefixLength"), "Local prefix length");
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
			if (!index) {
				assertEquals(a.isCompression(), b.isCompression(), "Parent compression");
				assertEquals(a.getCompressionType(), b.getCompressionType(), "Parent compression type");
				assertEquals(a.getSpecifics().get("COMPRESS_FOR"), b.getSpecifics().get("COMPRESS_FOR"),
						"Parent compression mode");
				assertEquals(a.getSpecifics().get("PCT_FREE"), b.getSpecifics().get("PCT_FREE"), "Parent PCT_FREE");
			} else {
				assertEquals(a.isCompression(), b.isCompression(), "Local parent compression");
				assertEquals(a.getCompressionType(), b.getCompressionType(), "Local parent compression mode");
				assertEquals(a.getSpecifics().get("oracle.index.compression"),
						b.getSpecifics().get("oracle.index.compression"), "Local parent catalog mode");
			}
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
