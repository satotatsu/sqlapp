/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 */
package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Statement;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataExecutionResult.TransactionOutcome;

class JdbcTreeDataExecutionTrackerTest {

	@Test
	void absentBatchResponseDoesNotInventAffectedRows() {
		var tracker = new JdbcTreeDataExecutionTracker();
		tracker.record(new Table("A"), SqlType.INSERT, null, 3);
		assertEquals(new JdbcTreeDataExecutionResult.Counts(0, 0, 0, 3),
				tracker.snapshot(false, TransactionOutcome.ROLLED_BACK).operations().getFirst().executed());
	}

	@Test
	void truncatedBatchResponsePreservesKnownUnknownFailedAndUnreportedEntries() {
		var tracker = new JdbcTreeDataExecutionTracker();
		tracker.record(new Table("A"), SqlType.UPDATE,
				new int[] { 2, 0, Statement.SUCCESS_NO_INFO, Statement.EXECUTE_FAILED }, 6);
		var operation = tracker.snapshot(false, TransactionOutcome.ROLLED_BACK).operations().getFirst();
		assertEquals(new JdbcTreeDataExecutionResult.Counts(2, 1, 1, 2), operation.executed());
		assertEquals(new JdbcTreeDataExecutionResult.Counts(0, 0, 0, 0), operation.committed());
	}

	@Test
	void longUpdateCountsAreNotNarrowedToInt() {
		var tracker = new JdbcTreeDataExecutionTracker();
		tracker.record(new Table("A"), SqlType.DELETE_BY_ROOT_ROWS, (long) Integer.MAX_VALUE + 10);
		tracker.committed();
		assertEquals((long) Integer.MAX_VALUE + 10, tracker.snapshot(true, TransactionOutcome.COMMITTED)
				.operations().getFirst().committed().knownAffectedRows());
	}

	@Test
	void sameTableNamesInDifferentSchemasRemainDistinct() {
		var tracker = new JdbcTreeDataExecutionTracker();
		Table first = new Table("A");
		first.setSchemaName("FIRST");
		Table second = new Table("A");
		second.setSchemaName("SECOND");
		tracker.record(first, SqlType.DELETE, 1L);
		tracker.record(second, SqlType.DELETE, 2L);
		assertEquals(2, tracker.snapshot(true, TransactionOutcome.COMMITTED).operations().size());
	}
}
