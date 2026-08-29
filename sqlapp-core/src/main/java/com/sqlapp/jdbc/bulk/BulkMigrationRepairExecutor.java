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

/** Replays only expected rows belonging to mismatched verification chunks. */
public final class BulkMigrationRepairExecutor {
	private BulkMigrationRepairExecutor() {
	}

	public static BulkMigrationRepairResult execute(final Connection targetConnection,
			final Table expected, final BulkMigrationVerificationResult verification,
			final BulkMigrationRepairOption options) throws SQLException {
		Objects.requireNonNull(targetConnection, "targetConnection");
		Objects.requireNonNull(expected, "expected");
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
		long affectedRows = 0;
		long chunkIndex = 0;
		final List<Table> replayChunks = new ArrayList<>(mismatches.size());
		final Iterator<Row> iterator = expected.getRows().iterator();
		Throwable failure = null;
		try {
			while (iterator.hasNext()) {
				final List<Row> rows = take(iterator, verification.getChunkSize());
				final BulkMigrationVerificationChunk verified = chunkIndex < verification.getChunks().size()
						? verification.getChunks().get((int) chunkIndex) : null;
				if (options.isVerifyExpectedHashes()) {
					validateExpectedChunk(expected, rows, verified, chunkIndex);
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
				validateExpectedEnd(expected, verification, chunkIndex);
			}
			withoutExpected.addAll(byIndex.keySet().stream().sorted().toList());
			if (replayChunks.isEmpty()) {
				return new BulkMigrationRepairResult(mismatches.size(), 0, 0, 0,
						List.copyOf(extraActual), List.copyOf(withoutExpected));
			}
			final BulkUpsertOption configured = options.getBulkUpsertOption() == null
					? BulkUpsertOption.defaults() : options.getBulkUpsertOption();
			final BulkUpsertOption effective = configured.getKeyColumns().isEmpty()
					? ChunkedBulkMigrationExecutor.copyWithKeys(configured,
							ChunkedBulkMigrationExecutor.primaryKeyNames(expected))
					: configured;
			final BulkUpsertExecutor executor = BulkUpsertResolver.resolve(targetConnection);
			if (effective.isUseTransaction()
					&& !executor.supportsCallerTransactionAtomicity()) {
				throw new IllegalStateException("The selected bulk upsert provider uses "
						+ "transaction-breaking staging DDL and cannot atomically repair all chunks; "
						+ "set bulkUpsertOption.useTransaction=false to allow non-atomic repair");
			}
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
			return new BulkMigrationRepairResult(mismatches.size(), replayChunks.size(),
					replayedRows, affectedRows, List.copyOf(extraActual),
					List.copyOf(withoutExpected));
		} catch (RuntimeException | Error e) {
			failure = e;
			throw e;
		} finally {
			BulkMigrationIteratorSupport.close(failure, iterator);
		}
	}

	private static void validateExpectedChunk(final Table expected, final List<Row> rows,
			final BulkMigrationVerificationChunk verified, final long chunkIndex) {
		if (verified == null || rows.size() != verified.getExpectedRows()
				|| !BulkMigrationHash.rows(rows, expected.getColumns())
						.equals(verified.getExpectedHash())) {
			throw changed(chunkIndex);
		}
	}

	private static void validateExpectedEnd(final Table expected,
			final BulkMigrationVerificationResult verification, final long chunkIndex) {
		final String emptyHash = BulkMigrationHash.rows(List.of(), expected.getColumns());
		for (long i = chunkIndex; i < verification.getChunks().size(); i++) {
			final BulkMigrationVerificationChunk chunk = verification.getChunks().get((int) i);
			if (chunk.getExpectedRows() != 0 || !emptyHash.equals(chunk.getExpectedHash())) {
				throw changed(i);
			}
		}
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

}
