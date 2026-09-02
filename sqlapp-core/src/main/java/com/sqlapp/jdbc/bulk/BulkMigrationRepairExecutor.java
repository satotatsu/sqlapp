/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Column;

/** Replays only expected rows belonging to mismatched verification chunks. */
public final class BulkMigrationRepairExecutor {
	private BulkMigrationRepairExecutor() {
	}

	public static BulkMigrationRepairResult execute(final Connection targetConnection,
			final Table expected, final BulkMigrationVerificationResult verification,
			final BulkMigrationRepairOption options) throws SQLException {
		Objects.requireNonNull(expected, "expected");
		return execute(targetConnection, expected, () -> expected.getRows().iterator(), verification,
				options);
	}

	/**
	 * Repairs a verification result from a reopenable keyset source.
	 *
	 * <p>The source fingerprint must exactly match the source used during
	 * verification. This prevents durable key boundaries from being interpreted
	 * with a different key order or token codec.</p>
	 */
	public static BulkMigrationRepairResult execute(final Connection targetConnection,
			final BulkMigrationKeysetSource expected,
			final BulkMigrationVerificationResult verification,
			final BulkMigrationRepairOption options) throws SQLException {
		Objects.requireNonNull(expected, "expected");
		Objects.requireNonNull(verification, "verification");
		final String verifiedFingerprint = verification.getExpectedKeysetFingerprint();
		final String sourceFingerprint = expected.getConfigurationFingerprint();
		if (verifiedFingerprint == null) {
			throw new IllegalArgumentException("Verification result does not contain an "
					+ "expected keyset source fingerprint");
		}
		if (!verifiedFingerprint.equals(sourceFingerprint)) {
			throw new IllegalArgumentException("Expected keyset source fingerprint differs "
					+ "from the source used during verification");
		}
		return executeKeyset(targetConnection, expected, verification, options);
	}

	private static BulkMigrationRepairResult executeKeyset(final Connection targetConnection,
			final BulkMigrationKeysetSource expected,
			final BulkMigrationVerificationResult verification,
			final BulkMigrationRepairOption options) throws SQLException {
		Objects.requireNonNull(targetConnection, "targetConnection");
		Objects.requireNonNull(options, "options");
		final Table table = Objects.requireNonNull(expected.getTable(), "expected.table");
		final List<BulkMigrationVerificationChunk> mismatches = verification.getMismatches();
		if (mismatches.isEmpty()) {
			return new BulkMigrationRepairResult(0, 0, 0, 0, List.of(), List.of());
		}
		final KeysetReplay replay = readKeysetReplay(expected, verification, options);
		final List<Long> extraActual = mismatches.stream()
				.filter(chunk -> chunk.getActualRows() > chunk.getExpectedRows())
				.map(BulkMigrationVerificationChunk::getIndex).toList();
		return executeReplayChunks(targetConnection, table, options,
				mismatches.size(), replay.chunks(), replay.rows(), extraActual,
				replay.withoutExpected());
	}

	static KeysetReplay readKeysetReplay(final BulkMigrationKeysetSource expected,
			final BulkMigrationVerificationResult verification,
			final BulkMigrationRepairOption options) throws SQLException {
		Objects.requireNonNull(expected, "expected");
		Objects.requireNonNull(verification, "verification");
		Objects.requireNonNull(options, "options");
		final Table table = Objects.requireNonNull(expected.getTable(), "expected.table");
		final List<BulkMigrationVerificationChunk> mismatches = verification.getMismatches();
		final List<Column> columns = verificationColumns(table, verification.getColumns());
		final List<Table> replayChunks = new ArrayList<>(mismatches.size());
		final List<Long> withoutExpected = new ArrayList<>();
		long replayedRows = 0;
		for (final BulkMigrationVerificationChunk mismatch : mismatches) {
			if (mismatch.getExpectedRows() == 0) {
				withoutExpected.add(mismatch.getIndex());
				continue;
			}
			if (mismatch.getExpectedFirstKey() == null
					|| mismatch.getExpectedLastKey() == null) {
				throw new IllegalArgumentException("Verification chunk " + mismatch.getIndex()
						+ " does not contain expected key boundaries");
			}
			final String startAfter = mismatch.getIndex() == 0 ? null
					: verification.getChunks().get((int) mismatch.getIndex() - 1)
							.getExpectedLastKey();
			Iterator<Row> iterator = null;
			Throwable failure = null;
			try {
				iterator = Objects.requireNonNull(expected.iterator(startAfter), "iterator");
				final List<Row> rows = take(iterator, mismatch.getExpectedRows());
				if (options.isVerifyExpectedHashes()) {
					validateExpectedChunk(rows, mismatch, mismatch.getIndex(), columns);
				}
				validateExpectedBoundaries(expected, rows, mismatch);
				replayChunks.add(ChunkedBulkMigrationExecutor.chunkTable(table, rows));
				replayedRows += rows.size();
			} catch (SQLException | RuntimeException | Error e) {
				failure = e;
				throw e;
			} finally {
				BulkMigrationIteratorSupport.close(failure, iterator);
			}
		}
		return new KeysetReplay(List.copyOf(replayChunks), replayedRows,
				List.copyOf(withoutExpected));
	}

	private static BulkMigrationRepairResult execute(final Connection targetConnection,
			final Table expected, final IteratorFactory iteratorFactory,
			final BulkMigrationVerificationResult verification,
			final BulkMigrationRepairOption options) throws SQLException {
		Objects.requireNonNull(targetConnection, "targetConnection");
		Objects.requireNonNull(expected, "expected");
		Objects.requireNonNull(iteratorFactory, "iteratorFactory");
		Objects.requireNonNull(verification, "verification");
		Objects.requireNonNull(options, "options");
		if (verification.getChunkSize() <= 0) {
			throw new IllegalArgumentException(
					"Verification result chunkSize must be greater than zero");
		}
		final List<BulkMigrationVerificationChunk> mismatches = verification.getMismatches();
		if (mismatches.isEmpty()) {
			return new BulkMigrationRepairResult(0, 0, 0, 0, List.of(), List.of());
		}
		final Map<Long, BulkMigrationVerificationChunk> byIndex = new HashMap<>();
		for (final BulkMigrationVerificationChunk mismatch : mismatches) {
			if (mismatch.getIndex() < 0 || byIndex.put(mismatch.getIndex(), mismatch) != null) {
				throw new IllegalArgumentException("Invalid or duplicate verification chunk index: "
						+ mismatch.getIndex());
			}
		}
		final List<Long> extraActual = mismatches.stream()
				.filter(chunk -> chunk.getActualRows() > chunk.getExpectedRows())
				.map(BulkMigrationVerificationChunk::getIndex).toList();
		final List<Long> withoutExpected = new ArrayList<>();
		long replayedRows = 0;
		long chunkIndex = 0;
		final List<Table> replayChunks = new ArrayList<>(mismatches.size());
		final List<Column> verificationColumns = verificationColumns(expected,
				verification.getColumns());
		Iterator<Row> iterator = null;
		Throwable failure = null;
		try {
			iterator = Objects.requireNonNull(iteratorFactory.open(), "iterator");
			while (iterator.hasNext()) {
				final List<Row> rows = take(iterator, verification.getChunkSize());
				final BulkMigrationVerificationChunk verified = chunkIndex < verification.getChunks().size()
						? verification.getChunks().get((int) chunkIndex) : null;
				if (options.isVerifyExpectedHashes()) {
					validateExpectedChunk(rows, verified, chunkIndex, verificationColumns);
				}
				final BulkMigrationVerificationChunk mismatch = byIndex.remove(chunkIndex);
				if (mismatch != null) {
					if (!rows.isEmpty()) {
						replayChunks.add(ChunkedBulkMigrationExecutor.chunkTable(expected, rows));
						replayedRows += rows.size();
					}
				}
				chunkIndex++;
			}
			if (options.isVerifyExpectedHashes()) {
				validateExpectedEnd(verification, chunkIndex, verificationColumns);
			}
			withoutExpected.addAll(byIndex.keySet().stream().sorted().toList());
			return executeReplayChunks(targetConnection, expected, options, mismatches.size(),
					replayChunks, replayedRows, extraActual, withoutExpected);
		} catch (RuntimeException | Error e) {
			failure = e;
			throw e;
		} finally {
			BulkMigrationIteratorSupport.close(failure, iterator);
		}
	}

	private static BulkMigrationRepairResult executeReplayChunks(
			final Connection targetConnection, final Table expected,
			final BulkMigrationRepairOption options, final int mismatchCount,
			final List<Table> replayChunks, final long replayedRows,
			final List<Long> extraActual, final List<Long> withoutExpected)
			throws SQLException {
		if (replayChunks.isEmpty()) {
			return new BulkMigrationRepairResult(mismatchCount, 0, 0, 0,
					List.copyOf(extraActual), List.copyOf(withoutExpected));
		}
		final BulkUpsertOption configured = options.getBulkUpsertOption() == null
				? BulkUpsertOption.defaults() : options.getBulkUpsertOption();
		final BulkUpsertOption effective = configured.getKeyColumns().isEmpty()
				? ChunkedBulkMigrationExecutor.copyWithKeys(configured,
						ChunkedBulkMigrationExecutor.primaryKeyNames(expected))
				: configured;
		final BulkUpsertExecutor executor = BulkUpsertResolver.resolve(targetConnection);
		if (effective.isUseTransaction() && !executor.supportsCallerTransactionAtomicity()) {
			throw new IllegalStateException("The selected bulk upsert provider uses "
					+ "transaction-breaking staging DDL and cannot atomically repair all chunks; "
					+ "set bulkUpsertOption.useTransaction=false to allow non-atomic repair");
		}
		long affectedRows = 0;
		try (var transaction = BulkUpsertTransaction.begin(targetConnection,
				effective.isUseTransaction())) {
			try {
				for (final Table chunk : replayChunks) {
					affectedRows += executor.execute(targetConnection, chunk, effective);
				}
				transaction.commit();
			} catch (SQLException | RuntimeException e) {
				transaction.rollback(e);
				throw e;
			}
		}
		return new BulkMigrationRepairResult(mismatchCount, replayChunks.size(), replayedRows,
				affectedRows, List.copyOf(extraActual), List.copyOf(withoutExpected));
	}

	private static void validateExpectedBoundaries(final BulkMigrationKeysetSource source,
			final List<Row> rows, final BulkMigrationVerificationChunk chunk)
			throws SQLException {
		if (rows.isEmpty()
				|| !chunk.getExpectedFirstKey().equals(source.resumeToken(rows.get(0)))
				|| !chunk.getExpectedLastKey()
						.equals(source.resumeToken(rows.get(rows.size() - 1)))) {
			throw changed(chunk.getIndex());
		}
	}

	static void validateExpectedChunk(final List<Row> rows,
			final BulkMigrationVerificationChunk verified, final long chunkIndex,
			final List<Column> columns) {
		if (verified == null || rows.size() != verified.getExpectedRows()
				|| !BulkMigrationHash.rows(rows, columns)
						.equals(verified.getExpectedHash())) {
			throw changed(chunkIndex);
		}
	}

	private static void validateExpectedEnd(
			final BulkMigrationVerificationResult verification, final long chunkIndex,
			final List<Column> columns) {
		final String emptyHash = BulkMigrationHash.rows(List.of(), columns);
		for (long i = chunkIndex; i < verification.getChunks().size(); i++) {
			final BulkMigrationVerificationChunk chunk = verification.getChunks().get((int) i);
			if (chunk.getExpectedRows() != 0 || !emptyHash.equals(chunk.getExpectedHash())) {
				throw changed(i);
			}
		}
	}

	private static List<Column> verificationColumns(final Table expected,
			final List<String> names) {
		if (names == null || names.isEmpty()) {
			return List.copyOf(expected.getColumns());
		}
		return names.stream().map(name -> {
			final Column column = expected.getColumns().get(name);
			if (column == null) {
				throw new IllegalArgumentException(
						"Expected table is missing verification column: " + name);
			}
			return column;
		}).toList();
	}

	private static IllegalStateException changed(final long chunkIndex) {
		return new IllegalStateException(
				"Expected source changed after verification at chunk " + chunkIndex);
	}

	private static List<Row> take(final Iterator<Row> iterator, final int size) {
		final List<Row> rows = new ArrayList<>(size);
		while (rows.size() < size && iterator.hasNext()) {
			rows.add(iterator.next());
		}
		return rows;
	}

	@FunctionalInterface
	private interface IteratorFactory {
		Iterator<Row> open() throws SQLException;
	}

	record KeysetReplay(List<Table> chunks, long rows, List<Long> withoutExpected) {
	}

}
