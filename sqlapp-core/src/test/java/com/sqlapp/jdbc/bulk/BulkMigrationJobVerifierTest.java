/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

class BulkMigrationJobVerifierTest {
	@Test
	void closesEveryStreamWithoutMaskingTheProcessingFailure() {
		final var processingFailure = new IllegalArgumentException("read failed");
		final var first = new FailingCloseIterator("first close failed");
		final var second = new FailingCloseIterator("second close failed");

		BulkMigrationIteratorSupport.close(processingFailure, first, second);

		assertTrue(first.closed);
		assertTrue(second.closed);
		assertEquals(2, processingFailure.getSuppressed().length);
		assertSame(first.failure, processingFailure.getSuppressed()[0]);
		assertSame(second.failure, processingFailure.getSuppressed()[1]);
	}

	@Test
	void closesRemainingStreamsWhenTheFirstCloseFails() {
		final var first = new FailingCloseIterator("first close failed");
		final var second = new FailingCloseIterator("second close failed");

		final var failure = assertThrows(IllegalStateException.class,
				() -> BulkMigrationIteratorSupport.close(null, first, second));

		assertSame(first.failure, failure);
		assertTrue(first.closed);
		assertTrue(second.closed);
		assertEquals(1, failure.getSuppressed().length);
		assertSame(second.failure, failure.getSuppressed()[0]);
	}

	@Test
	void verificationResultOwnsValidImmutableChunkBoundaries() {
		final var chunks = new java.util.ArrayList<BulkMigrationVerificationChunk>();
		final var result = new BulkMigrationVerificationResult(10, 0, 0, chunks);
		chunks.add(new BulkMigrationVerificationChunk(0, 0, 0, "x", "x"));

		assertEquals(List.of(), result.getChunks());
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationVerificationResult(0, 0, 0, List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationVerificationResult(1, -1, 0, List.of()));
	}

	@Test
	void rejectsInternallyInconsistentVerificationResults() {
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationVerificationChunk(-1, 0, 0, "x", "x"));
		assertThrows(NullPointerException.class,
				() -> new BulkMigrationVerificationChunk(0, 0, 0, null, "x"));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationVerificationResult(2, 1, 1, List.of(
						new BulkMigrationVerificationChunk(1, 1, 1, "x", "x"))));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationVerificationResult(2, 3, 1, List.of(
						new BulkMigrationVerificationChunk(0, 3, 1, "x", "x"))));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationVerificationResult(2, 2, 2, List.of(
						new BulkMigrationVerificationChunk(0, 1, 1, "x", "x"))));
	}

	@Test
	void aggregatesResultsInDependencyOrder() {
		final Table expectedParent = table("PARENT", "parent");
		final Table expectedChild = table("CHILD", "child");
		expectedChild.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT",
				expectedChild.getColumns().get("ID"), expectedParent.getColumns().get("ID"));
		final Table actualParent = table("PARENT", "parent");
		final Table actualChild = table("CHILD", "changed");
		final var parent = BulkMigrationJobVerificationTask.builder().taskId("parent")
				.expected(expectedParent).actual(actualParent).chunkSize(1).build();
		final var child = BulkMigrationJobVerificationTask.builder().taskId("child")
				.expected(expectedChild).actual(actualChild).chunkSize(1).build();

		final var result = BulkMigrationJobVerifier.verify(List.of(child, parent));

		assertEquals(List.of("parent", "child"), result.getTasks().stream()
				.map(BulkMigrationJobTaskVerificationResult::getTaskId).toList());
		assertFalse(result.isMatch());
		assertEquals(1, result.getMismatchedTasks());
		assertEquals(2, result.getExpectedRows());
		assertEquals(2, result.getActualRows());
		assertEquals(List.of(1, 1), result.getTasks().stream()
				.map(task -> task.getVerificationResult().getChunkSize()).toList());
	}

	private static Table table(final String name, final String text) {
		final Table table = new Table(name);
		table.getColumns().add(new Column("ID"));
		table.getColumns().add(new Column("TXT"));
		table.getRows().add(row -> {
			row.put("ID", 1);
			row.put("TXT", text);
		});
		return table;
	}

	private static final class FailingCloseIterator
			implements Iterator<Row>, AutoCloseable {
		private final IllegalStateException failure;
		private boolean closed;

		private FailingCloseIterator(final String message) {
			this.failure = new IllegalStateException(message);
		}

		@Override
		public boolean hasNext() {
			return false;
		}

		@Override
		public Row next() {
			throw new java.util.NoSuchElementException();
		}

		@Override
		public void close() {
			closed = true;
			throw failure;
		}
	}
}
