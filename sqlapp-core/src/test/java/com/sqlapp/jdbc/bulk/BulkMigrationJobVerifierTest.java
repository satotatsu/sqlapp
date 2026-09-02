/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

import javax.sql.rowset.serial.SerialBlob;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

class BulkMigrationJobVerifierTest {
	@Test
	void verifiesCallerSuppliedOrderedStreamsWithoutMaterializedTableRows() {
		final Table expected = new Table("EXPECTED");
		expected.getColumns().add(new Column("ID").setDataType(DataType.INT));
		final Table actual = new Table("ACTUAL");
		actual.getColumns().add(new Column("ID").setDataType(DataType.BIGINT));
		final Row expectedOne = expected.newRow();
		expectedOne.put("ID", 1);
		final Row expectedTwo = expected.newRow();
		expectedTwo.put("ID", 2);
		final Row actualOne = actual.newRow();
		actualOne.put("ID", 1L);
		final Row actualTwo = actual.newRow();
		actualTwo.put("ID", 2L);

		final var result = BulkMigrationVerifier.verify(expected,
				List.of(expectedOne, expectedTwo).iterator(), actual,
				List.of(actualOne, actualTwo).iterator(), 1);

		assertTrue(result.isMatch());
		assertEquals(2, result.getExpectedRows());
		assertEquals(2, result.getChunks().size());
		assertTrue(expected.getRows().isEmpty());
		assertTrue(actual.getRows().isEmpty());
	}

	@Test
	void verifiesOnlyExplicitColumns() {
		final Table expected = new Table("EXPECTED");
		expected.getColumns().add(new Column("ID").setDataType(DataType.INT));
		expected.getColumns().add(new Column("GENERATED_VALUE").setDataType(DataType.INT));
		final Table actual = expected.clone().setName("ACTUAL");
		final Row left = expected.newRow();
		left.put("ID", 1);
		left.put("GENERATED_VALUE", 10);
		final Row right = actual.newRow();
		right.put("ID", 1);
		right.put("GENERATED_VALUE", 99);

		final var result = BulkMigrationVerifier.verify(expected, List.of(left).iterator(), actual,
				List.of(right).iterator(), List.of("ID"), 10);
		assertTrue(result.isMatch());
		assertEquals(List.of("ID"), result.getColumns());
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationVerifier.verify(
				expected, List.of(left).iterator(), actual, List.of(right).iterator(),
				List.of("ID", "ID"), 10));
	}

	@Test
	void repairValidationUsesTheSameExplicitVerificationColumns() {
		final Table expected = table("ITEMS", "source");
		final Table actual = table("ITEMS", "target");
		final var result = BulkMigrationVerifier.verify(expected,
				expected.getRows().iterator(), actual, actual.getRows().iterator(),
				List.of("ID"), 10);
		final var chunk = result.getChunks().get(0);
		final var idColumns = List.of(expected.getColumns().get("ID"));

		BulkMigrationRepairExecutor.validateExpectedChunk(expected.getRows(), chunk, 0,
				idColumns);
		expected.getRows().get(0).put("TXT", "changed but not verified");
		BulkMigrationRepairExecutor.validateExpectedChunk(expected.getRows(), chunk, 0,
				idColumns);
		expected.getRows().get(0).put("ID", 2);
		assertThrows(IllegalStateException.class, () ->
				BulkMigrationRepairExecutor.validateExpectedChunk(expected.getRows(), chunk,
						0, idColumns));
	}

	@Test
	void normalizesDriverValueTypesThroughTheExpectedSchema() throws Exception {
		final Table expected = new Table("EXPECTED");
		expected.getColumns().add(new Column("ID").setDataType(DataType.BIGINT));
		expected.getColumns().add(new Column("AMOUNT").setDataType(DataType.DECIMAL));
		expected.getColumns().add(new Column("PAYLOAD").setDataType(DataType.BLOB));
		expected.getRows().add(row -> {
			row.put("ID", 1);
			row.put("AMOUNT", new BigDecimal("1.00"));
			row.put("PAYLOAD", new byte[] { 1, 2, 3 });
		});
		final Table actual = new Table("ACTUAL");
		actual.getColumns().add(new Column("ID").setDataType(DataType.INT));
		actual.getColumns().add(new Column("AMOUNT").setDataType(DataType.DECIMAL));
		actual.getColumns().add(new Column("PAYLOAD").setDataType(DataType.BLOB));
		final SerialBlob payload = new SerialBlob(new byte[] { 1, 2, 3 });
		actual.getRows().add(row -> {
			row.put("ID", 1L);
			row.put("AMOUNT", new BigDecimal("1.0"));
			row.put("PAYLOAD", payload);
		});

		assertTrue(BulkMigrationVerifier.verify(expected, actual, 10).isMatch());
	}

	@Test
	void numericNormalizationDoesNotWrapValuesToTheExpectedJavaType() {
		final Table expected = new Table("EXPECTED");
		expected.getColumns().add(new Column("ID").setDataType(DataType.INT));
		expected.getRows().add(row -> row.put("ID", Integer.MIN_VALUE));
		final Table actual = new Table("ACTUAL");
		actual.getColumns().add(new Column("ID").setDataType(DataType.BIGINT));
		actual.getRows().add(row -> row.put("ID", 2_147_483_648L));

		assertFalse(BulkMigrationVerifier.verify(expected, actual, 10).isMatch());
	}

	@Test
	void normalizesEquivalentFloatAndDoubleDriverValues() {
		final Table expected = new Table("EXPECTED");
		expected.getColumns().add(new Column("VALUE").setDataType(DataType.REAL));
		expected.getRows().add(row -> row.put("VALUE", 0.1f));
		final Table actual = new Table("ACTUAL");
		actual.getColumns().add(new Column("VALUE").setDataType(DataType.DOUBLE));
		actual.getRows().add(row -> row.put("VALUE", 0.1d));

		assertTrue(BulkMigrationVerifier.verify(expected, actual, 10).isMatch());
	}

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
	void keysetVerificationClosesTheFirstStreamWhenTheSecondCannotOpen() {
		final Table table = table("ITEMS", "value");
		final var expectedRows = new TrackingIterator(table.getRows().iterator());
		final BulkMigrationKeysetSource expected = keysetSource(table, "expected", expectedRows);
		final BulkMigrationKeysetSource actual = new BulkMigrationKeysetSource() {
			@Override
			public Table getTable() {
				return table;
			}

			@Override
			public String getConfigurationFingerprint() {
				return "actual";
			}

			@Override
			public Iterator<Row> iterator(final String resumeToken) throws SQLException {
				throw new SQLException("open failed");
			}

			@Override
			public String resumeToken(final Row row) {
				return row.get("ID").toString();
			}
		};

		assertThrows(SQLException.class, () -> BulkMigrationVerifier.verify(expected, actual,
				List.of("ID"), 10));
		assertTrue(expectedRows.closed);
	}

	@Test
	void keysetVerificationRetainsBothConfigurationFingerprints() throws Exception {
		final Table expectedTable = table("ITEMS", "value");
		final Table actualTable = table("ITEMS", "value");
		final var result = BulkMigrationVerifier.verify(
				keysetSource(expectedTable, "expected-fingerprint",
						expectedTable.getRows().iterator()),
				keysetSource(actualTable, "actual-fingerprint",
						actualTable.getRows().iterator()),
				List.of("ID", "TXT"), 10);

		assertTrue(result.isMatch());
		assertEquals("expected-fingerprint", result.getExpectedKeysetFingerprint());
		assertEquals("actual-fingerprint", result.getActualKeysetFingerprint());
	}

	@Test
	void keysetRepairRejectsAChangedOrMissingSourceFingerprint() {
		final Table expectedTable = table("ITEMS", "expected");
		final var chunk = new BulkMigrationVerificationChunk(0, 1, 1,
				"expected-hash", "actual-hash", "1", "1", "1", "1");
		final var verification = new BulkMigrationVerificationResult(10, 1, 1,
				List.of("ID", "TXT"), "verified", "actual", List.of(chunk));
		final var changedSource = keysetSource(expectedTable, "changed",
				expectedTable.getRows().iterator());

		final var changed = assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationRepairExecutor.execute(null, changedSource, verification,
						BulkMigrationRepairOption.builder().build()));
		assertTrue(changed.getMessage().contains("fingerprint differs"));

		final var withoutFingerprint = new BulkMigrationVerificationResult(10, 1, 1,
				List.of("ID", "TXT"), List.of(chunk));
		final var missing = assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationRepairExecutor.execute(null, changedSource, withoutFingerprint,
						BulkMigrationRepairOption.builder().build()));
		assertTrue(missing.getMessage().contains("does not contain"));
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
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationVerificationResult(1, 0, 0, List.of("ID"),
						"expected", null, List.of()));
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
		final var verification = new BulkMigrationVerificationResult(1, 0, 0, List.of());
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobTaskVerificationResult("task", List.of(),
						verification));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobTaskVerificationResult("task", List.of("ID", "ID"),
						verification));
		final var task = new BulkMigrationJobTaskVerificationResult("task", List.of("ID"),
				verification);
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobVerificationResult(List.of(task, task)));
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
		assertEquals(List.of(List.of("ID", "TXT"), List.of("ID", "TXT")),
				result.getTasks().stream()
						.map(BulkMigrationJobTaskVerificationResult::getColumns).toList());
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

	private static BulkMigrationKeysetSource keysetSource(final Table table,
			final String fingerprint, final Iterator<Row> rows) {
		return new BulkMigrationKeysetSource() {
			@Override
			public Table getTable() {
				return table;
			}

			@Override
			public String getConfigurationFingerprint() {
				return fingerprint;
			}

			@Override
			public Iterator<Row> iterator(final String resumeToken) {
				return rows;
			}

			@Override
			public String resumeToken(final Row row) {
				return row.get("ID").toString();
			}
		};
	}

	private static final class TrackingIterator implements Iterator<Row>, AutoCloseable {
		private final Iterator<Row> delegate;
		private boolean closed;

		private TrackingIterator(final Iterator<Row> delegate) {
			this.delegate = delegate;
		}

		@Override
		public boolean hasNext() {
			return delegate.hasNext();
		}

		@Override
		public Row next() {
			return delegate.next();
		}

		@Override
		public void close() {
			closed = true;
		}
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
