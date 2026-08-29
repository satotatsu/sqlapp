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
		int replayedChunks = 0;
		long chunkIndex = 0;
		final Iterator<Row> iterator = expected.getRows().iterator();
		Throwable failure = null;
		try {
			while (iterator.hasNext()) {
				final List<Row> rows = take(iterator, verification.getChunkSize());
				final BulkMigrationVerificationChunk mismatch = byIndex.remove(chunkIndex);
				if (mismatch != null) {
					if (options.isVerifyExpectedHashes()) {
						final String hash = BulkMigrationHash.rows(rows, expected.getColumns());
						if (rows.size() != mismatch.getExpectedRows()
								|| !hash.equals(mismatch.getExpectedHash())) {
							throw new IllegalStateException("Expected source changed after verification at chunk "
									+ chunkIndex);
						}
					}
					if (!rows.isEmpty()) {
						final Table chunk = ChunkedBulkMigrationExecutor.chunkTable(expected, rows);
						final BulkUpsertOption configured = options.getBulkUpsertOption() == null
								? BulkUpsertOption.defaults() : options.getBulkUpsertOption();
						final BulkUpsertOption effective = configured.getKeyColumns().isEmpty()
								? ChunkedBulkMigrationExecutor.copyWithKeys(configured,
										ChunkedBulkMigrationExecutor.primaryKeyNames(expected))
								: configured;
						affectedRows += BulkUpsertResolver.execute(targetConnection, chunk, effective);
						replayedRows += rows.size();
						replayedChunks++;
					}
				}
				chunkIndex++;
			}
			withoutExpected.addAll(byIndex.keySet().stream().sorted().toList());
			return new BulkMigrationRepairResult(mismatches.size(), replayedChunks,
					replayedRows, affectedRows, List.copyOf(extraActual),
					List.copyOf(withoutExpected));
		} catch (RuntimeException | Error e) {
			failure = e;
			throw e;
		} finally {
			BulkMigrationIteratorSupport.close(failure, iterator);
		}
	}

	private static List<Row> take(final Iterator<Row> iterator, final int size) {
		final List<Row> rows = new ArrayList<>(size);
		while (rows.size() < size && iterator.hasNext()) {
			rows.add(iterator.next());
		}
		return rows;
	}

}
