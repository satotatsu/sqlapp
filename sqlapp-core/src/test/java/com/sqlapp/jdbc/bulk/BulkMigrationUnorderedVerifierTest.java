/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;

class BulkMigrationUnorderedVerifierTest {
	@Test void fullScanPlanRejectsMergeResumeAndNonemptyTargetPolicy() {
		var table=table("VALUE");
		assertThrows(IllegalArgumentException.class,() -> BulkMigrationJobTask.builder().taskId("merge").sourceTable(table).fullScan(true).requireEmptyTarget(true)
				.options(ChunkedBulkMigrationOption.builder().migrationId("merge").mode(BulkMigrationMode.UPSERT).resume(false).build()).build());
		assertThrows(IllegalArgumentException.class,() -> BulkMigrationJobTask.builder().taskId("resume").sourceTable(table).fullScan(true).requireEmptyTarget(true)
				.options(ChunkedBulkMigrationOption.builder().migrationId("resume").mode(BulkMigrationMode.INSERT).resume(true).build()).build());
		assertThrows(IllegalArgumentException.class,() -> BulkMigrationJobTask.builder().taskId("append").sourceTable(table).fullScan(true).requireEmptyTarget(false)
				.options(ChunkedBulkMigrationOption.builder().migrationId("append").mode(BulkMigrationMode.INSERT).resume(false).build()).build());
	}
	@Test void externalRunsCompareReorderedDuplicatesNullsAndBinaryValues() throws Exception {
		var expected = table("VALUE"); var actual = table("RENAMED");
		for (int i = 0; i < 90; i++) {
			add(expected, "VALUE", i % 3 == 0 ? null : i % 7);
		}
		for (int i = 89; i >= 0; i--) add(actual, "RENAMED", i % 3 == 0 ? null : i % 7);
		var result = BulkMigrationUnorderedVerifier.verify(expected, expected.getRows().iterator(), actual,
				actual.getRows().iterator(), List.of("VALUE", "BYTES"), List.of("RENAMED", "BYTES"), 7, 2);
		assertTrue(result.isMatch()); assertEquals(90, result.getExpectedRows());
		assertTrue(result.isUnordered());
		assertNull(result.getExpectedKeysetFingerprint());
		actual.getRows().getFirst().put("RENAMED", 999);
		assertFalse(BulkMigrationUnorderedVerifier.verify(expected, expected.getRows().iterator(), actual,
				actual.getRows().iterator(), List.of("VALUE", "BYTES"), List.of("RENAMED", "BYTES"), 7, 2).isMatch());
	}

	@Test void unorderedMismatchCannotBeReplayedAsAnOrderedRepairEvenWithARealPrimaryKey() throws Exception {
		var expected=table("VALUE"); var actual=table("VALUE");
		for(var table:List.of(expected,actual)) { table.getColumns().get("VALUE").setNotNull(true); table.setPrimaryKey(table.getColumns().get("VALUE")); }
		add(expected,"VALUE",1); add(actual,"VALUE",2);
		var result=BulkMigrationUnorderedVerifier.verify(expected,expected.getRows().iterator(),actual,actual.getRows().iterator(),List.of("VALUE"),List.of("VALUE"),2,2);
		assertFalse(result.isMatch());
		assertTrue(assertThrows(IllegalArgumentException.class,() -> BulkMigrationRepairExecutor.validateConfiguration(expected,actual,result,BulkMigrationRepairOption.defaults())).getMessage().contains("Unordered multiset"));
	}

	@Test void equalCountsWithDifferentDuplicateMultiplicityDoNotMatch() throws Exception {
		var expected = table("VALUE"); var actual = table("VALUE");
		for (int value : List.of(1,1,2,2)) add(expected, "VALUE", value);
		for (int value : List.of(1,2,2,2)) add(actual, "VALUE", value);
		assertFalse(BulkMigrationUnorderedVerifier.verify(expected, expected.getRows().iterator(), actual,
				actual.getRows().iterator(), List.of("VALUE"), List.of("VALUE"), 2, 2).isMatch());
	}

	@Test void bothIteratorsAreClosedOnFailureAndInvalidConfiguration() {
		var table = table("VALUE");
		class Rows implements Iterator<Row>, AutoCloseable {
			boolean closed;
			public boolean hasNext() { throw new IllegalStateException("read failed"); }
			public Row next() { throw new NoSuchElementException(); }
			public void close() { closed = true; }
		}
		var left = new Rows(); var right = new Rows();
		assertThrows(IllegalStateException.class, () -> BulkMigrationUnorderedVerifier.verify(table,left,table,right,List.of("VALUE"),List.of("VALUE"),2,2));
		assertTrue(left.closed); assertTrue(right.closed);
		var invalidLeft = new Rows(); var invalidRight = new Rows();
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationUnorderedVerifier.verify(table,invalidLeft,table,invalidRight,List.of("VALUE"),List.of("VALUE"),0,2));
		assertTrue(invalidLeft.closed); assertTrue(invalidRight.closed);
	}

	private static Table table(String name) {
		var table = new Table("NO_KEY");
		table.getColumns().add(new Column(name).setDataType(DataType.INT));
		table.getColumns().add(new Column("BYTES").setDataType(DataType.VARBINARY).setLength(10));
		return table;
	}
	private static void add(Table table, String name, Integer value) {
		var row = table.newRow(); row.put(name,value); row.put("BYTES",value == null ? null : new byte[]{0,127,(byte)255}); table.getRows().add(row);
	}
}
