/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

/** Executes a table's rows in restartable chunks through the bulk providers. */
public final class ChunkedBulkMigrationExecutor {
	private ChunkedBulkMigrationExecutor() {
	}

	/** Uses the target-database checkpoint table, which is the default mode. */
	public static ChunkedBulkMigrationResult execute(final Connection targetConnection,
			final Table sourceTable, final ChunkedBulkMigrationOption options) throws SQLException {
		return executeWithListener(targetConnection, sourceTable, options,
				ChunkedBulkMigrationListener.NO_OP);
	}

	public static ChunkedBulkMigrationResult executeWithListener(final Connection targetConnection,
			final Table sourceTable, final ChunkedBulkMigrationOption options,
			final ChunkedBulkMigrationListener listener) throws SQLException {
		Objects.requireNonNull(options, "options");
		if (options.getCheckpointMode() != BulkMigrationCheckpointMode.DATABASE) {
			throw new IllegalArgumentException("A checkpoint store is required for checkpointMode="
					+ options.getCheckpointMode());
		}
		if (!targetConnection.getAutoCommit()) {
			throw new IllegalStateException("Database checkpoint mode requires an auto-commit connection so the "
					+ "chunk executor can own transaction boundaries");
		}
		return execute(targetConnection, sourceTable, options,
				new JdbcBulkMigrationCheckpointStore(targetConnection,
						options.getCheckpointTableName()), listener);
	}

	/** Executes a keyset source using the default target-database checkpoint. */
	public static ChunkedBulkMigrationResult execute(final Connection targetConnection,
			final BulkMigrationKeysetSource source, final ChunkedBulkMigrationOption options)
			throws SQLException {
		return executeWithListener(targetConnection, source, options,
				ChunkedBulkMigrationListener.NO_OP);
	}

	public static ChunkedBulkMigrationResult executeWithListener(final Connection targetConnection,
			final BulkMigrationKeysetSource source, final ChunkedBulkMigrationOption options,
			final ChunkedBulkMigrationListener listener) throws SQLException {
		Objects.requireNonNull(options, "options");
		if (options.getCheckpointMode() != BulkMigrationCheckpointMode.DATABASE) {
			throw new IllegalArgumentException("A checkpoint store is required for checkpointMode="
					+ options.getCheckpointMode());
		}
		if (!targetConnection.getAutoCommit()) {
			throw new IllegalStateException("Database checkpoint mode requires an auto-commit connection so the "
					+ "chunk executor can own transaction boundaries");
		}
		return execute(targetConnection, source, options,
				new JdbcBulkMigrationCheckpointStore(targetConnection,
						options.getCheckpointTableName()), listener);
	}

	public static ChunkedBulkMigrationResult execute(final Connection targetConnection,
			final Table sourceTable, final ChunkedBulkMigrationOption options,
			final BulkMigrationCheckpointStore checkpointStore) throws SQLException {
		return execute(targetConnection, sourceTable, options, checkpointStore,
				ChunkedBulkMigrationListener.NO_OP);
	}

	public static ChunkedBulkMigrationResult execute(final Connection targetConnection,
			final Table sourceTable, final ChunkedBulkMigrationOption options,
			final BulkMigrationCheckpointStore checkpointStore,
			final ChunkedBulkMigrationListener listener) throws SQLException {
		return executeInternal(targetConnection, sourceTable, null, options, checkpointStore, listener);
	}

	/** Executes a source that resumes by its unique ordered key rather than row count. */
	public static ChunkedBulkMigrationResult execute(final Connection targetConnection,
			final BulkMigrationKeysetSource source, final ChunkedBulkMigrationOption options,
			final BulkMigrationCheckpointStore checkpointStore) throws SQLException {
		return execute(targetConnection, source, options, checkpointStore,
				ChunkedBulkMigrationListener.NO_OP);
	}

	public static ChunkedBulkMigrationResult execute(final Connection targetConnection,
			final BulkMigrationKeysetSource source, final ChunkedBulkMigrationOption options,
			final BulkMigrationCheckpointStore checkpointStore,
			final ChunkedBulkMigrationListener listener) throws SQLException {
		Objects.requireNonNull(source, "source");
		return executeInternal(targetConnection, source.getTable(), source, options, checkpointStore, listener);
	}

	private static ChunkedBulkMigrationResult executeInternal(final Connection targetConnection,
			final Table sourceTable, final BulkMigrationKeysetSource keysetSource,
			final ChunkedBulkMigrationOption options,
			final BulkMigrationCheckpointStore checkpointStore,
			final ChunkedBulkMigrationListener listener) throws SQLException {
		Objects.requireNonNull(targetConnection, "targetConnection");
		Objects.requireNonNull(sourceTable, "sourceTable");
		Objects.requireNonNull(options, "options");
		Objects.requireNonNull(checkpointStore, "checkpointStore");
		Objects.requireNonNull(listener, "listener");
		options.validate();
		final boolean transactional = checkpointStore instanceof TransactionalBulkMigrationCheckpointStore store
				&& store.participatesIn(targetConnection);
		validateCheckpointMode(options.getCheckpointMode(), transactional);

		BulkMigrationCheckpoint checkpoint = options.isResume()
				? checkpointStore.load(options.getMigrationId()).orElse(null) : null;
		if (checkpoint != null) {
			checkpoint.validate();
			validateCheckpoint(checkpoint, options);
			validateResumeStyle(checkpoint, keysetSource != null);
			if (checkpoint.isComplete()) {
				return new ChunkedBulkMigrationResult(checkpoint.getProcessedRows(), 0,
						checkpoint.getCompletedChunks(), true);
			}
		} else {
			checkpoint = BulkMigrationCheckpoint.builder()
					.migrationId(options.getMigrationId())
					.sourceFingerprint(options.getSourceFingerprint())
					.targetFingerprint(options.getTargetFingerprint()).build();
		}

		if (transactional && !targetConnection.getAutoCommit()) {
			throw new IllegalStateException("Database checkpoint mode requires an auto-commit connection so the "
					+ "chunk executor can own transaction boundaries");
		}
		final long previouslyProcessed = checkpoint.getProcessedRows();
		long processed = 0;
		long chunks = checkpoint.getCompletedChunks();
		final BulkUpsertDuplicateTracker duplicateTracker = duplicateTracker(sourceTable,
				options, keysetSource != null, checkpoint);
		final Iterator<Row> iterator = keysetSource == null
				? sourceTable.getRows().iterator()
				: keysetSource.iterator(checkpoint.getResumeToken());
		try {
			if (keysetSource == null) {
				skipAndValidateBoundary(iterator, checkpoint, sourceTable,
						options.getChunkSize(), duplicateTracker);
			}
			while (true) {
				final List<Row> rows = nextChunk(iterator, options.getChunkSize());
				if (rows.isEmpty()) {
					break;
				}
				final List<Row> writeRows = duplicateTracker == null
						? rows : duplicateTracker.filter(rows);
				final Table chunk = chunkTable(sourceTable, writeRows);
				final String nextToken = keysetSource == null ? null
						: keysetSource.resumeToken(rows.get(rows.size() - 1));
				if (keysetSource != null && (nextToken == null || nextToken.isBlank())) {
					throw new IllegalStateException("A keyset source must return a non-empty resume token");
				}
				if (keysetSource != null && Objects.equals(checkpoint.getResumeToken(), nextToken)) {
					throw new IllegalStateException("A keyset source did not advance its resume token");
				}
				final BulkMigrationCheckpoint nextCheckpoint = checkpoint.toBuilder()
						.processedRows(previouslyProcessed + processed + rows.size())
						.completedChunks(chunks + 1)
						.lastChunkHash(BulkMigrationHash.rows(rows,
								sourceTable.getColumns()))
						.resumeToken(nextToken).complete(false).build();
				final ChunkedBulkMigrationProgress progress = new ChunkedBulkMigrationProgress(
						options.getMigrationId(), chunks, rows.size(),
						previouslyProcessed + processed, nextCheckpoint.getProcessedRows());
				listener.onChunkStarted(progress);
				try {
					if (transactional) {
						targetConnection.setAutoCommit(false);
						try {
							if (!writeRows.isEmpty()) {
								write(targetConnection, chunk, sourceTable, options, true);
							}
							checkpointStore.save(nextCheckpoint);
							targetConnection.commit();
						} catch (SQLException | RuntimeException e) {
							rollback(targetConnection, e);
							throw e;
						} finally {
							targetConnection.setAutoCommit(true);
						}
					} else {
						if (!writeRows.isEmpty()) {
							write(targetConnection, chunk, sourceTable, options, false);
						}
						checkpointStore.save(nextCheckpoint);
					}
				} catch (SQLException | RuntimeException e) {
					try {
						listener.onChunkFailed(progress, e);
					} catch (RuntimeException listenerFailure) {
						e.addSuppressed(listenerFailure);
					}
					throw e;
				}
				processed += rows.size();
				chunks++;
				checkpoint = nextCheckpoint;
				listener.onChunkCompleted(progress);
				if (listener.pauseAfterChunk(progress)) {
					throw new ChunkedBulkMigrationPausedException(progress);
				}
			}
			final BulkMigrationCheckpoint complete = checkpoint.toBuilder().complete(true).build();
			if (transactional) {
				targetConnection.setAutoCommit(false);
				try {
					checkpointStore.save(complete);
					targetConnection.commit();
				} catch (SQLException | RuntimeException e) {
					rollback(targetConnection, e);
					throw e;
				} finally {
					targetConnection.setAutoCommit(true);
				}
			} else {
				checkpointStore.save(complete);
			}
			return new ChunkedBulkMigrationResult(previouslyProcessed, processed,
					chunks, false);
		} finally {
			close(iterator);
		}
	}

	private static void validateCheckpointMode(final BulkMigrationCheckpointMode mode,
			final boolean transactional) {
		if (mode == BulkMigrationCheckpointMode.DATABASE && !transactional) {
			throw new IllegalArgumentException("DATABASE checkpoint mode requires a transactional store "
					+ "using the target connection");
		}
		if (mode == BulkMigrationCheckpointMode.FILE && transactional) {
			throw new IllegalArgumentException("FILE checkpoint mode requires an external checkpoint store");
		}
	}

	private static void write(final Connection connection, final Table chunk,
			final Table source, final ChunkedBulkMigrationOption options,
			final boolean requireAtomicity) throws SQLException {
		if (options.getMode() == BulkMigrationMode.INSERT) {
			final BulkInsertExecutor executor = BulkInsertResolver.resolve(connection);
			if (requireAtomicity && !executor.supportsCallerTransactionAtomicity()) {
				throw new IllegalStateException("The selected bulk insert provider does not participate in the "
						+ "caller transaction; use FILE checkpoint mode");
			}
			executor.execute(connection, chunk, options.getBulkOption());
			return;
		}
		final BulkUpsertOption sourceOption = options.getBulkUpsertOption() == null
				? BulkUpsertOption.defaults() : options.getBulkUpsertOption();
		final BulkUpsertOption effective = sourceOption.getKeyColumns().isEmpty()
				? copyWithKeys(sourceOption, primaryKeyNames(source)) : sourceOption;
		final BulkUpsertExecutor executor = BulkUpsertResolver.resolve(connection);
		if (requireAtomicity && !executor.supportsCallerTransactionAtomicity()) {
			throw new IllegalStateException("The selected bulk upsert provider uses transaction-breaking staging DDL; "
					+ "use FILE checkpoint mode or a provider with externally managed staging");
		}
		executor.execute(connection, chunk, effective);
	}

	private static void rollback(final Connection connection, final Throwable failure) {
		try {
			connection.rollback();
		} catch (SQLException e) {
			failure.addSuppressed(e);
		}
	}

	static BulkUpsertOption copyWithKeys(final BulkUpsertOption option,
			final List<String> keys) {
		final var builder = BulkUpsertOption.builder().keyColumns(keys)
				.updateColumns(option.getUpdateColumns())
				.updateWhenMatched(option.isUpdateWhenMatched())
				.insertWhenNotMatched(option.isInsertWhenNotMatched())
				.useTransaction(option.isUseTransaction())
				.duplicateKeyStrategy(option.getDuplicateKeyStrategy())
				.duplicateRowSelector(option.getDuplicateRowSelector())
				.duplicateRowSelectorFingerprint(option.getDuplicateRowSelectorFingerprint())
				.stagingTableName(option.getStagingTableName())
				.bulkOption(option.getBulkOption());
		return builder.build();
	}

	static List<String> primaryKeyNames(final Table table) {
		if (table.getPrimaryKeyConstraint() == null) {
			return List.of();
		}
		return table.getPrimaryKeyConstraint().getColumns().stream()
				.map(column -> column.getName()).toList();
	}

	static Table chunkTable(final Table source, final List<Row> rows) {
		final Table chunk = new Table(source.getName()).setCatalogName(source.getCatalogName())
				.setSchemaName(source.getSchemaName());
		for (final Column column : source.getColumns()) {
			chunk.getColumns().add(column.clone());
		}
		for (final Row row : rows) {
			final Row copy = chunk.newRow();
			for (final Column sourceColumn : source.getColumns()) {
				copy.put(chunk.getColumns().get(sourceColumn.getName()), row.get(sourceColumn));
			}
			chunk.getRows().add(copy);
		}
		return chunk;
	}

	private static List<Row> nextChunk(final Iterator<Row> iterator, final int size) {
		final List<Row> rows = new ArrayList<>(size);
		while (rows.size() < size && iterator.hasNext()) {
			rows.add(iterator.next());
		}
		return rows;
	}

	private static void skipAndValidateBoundary(final Iterator<Row> iterator,
			final BulkMigrationCheckpoint checkpoint, final Table source,
			final int chunkSize, final BulkUpsertDuplicateTracker duplicateTracker) {
		final long count = checkpoint.getProcessedRows();
		final int boundarySize = boundarySize(checkpoint, chunkSize);
		final Deque<Row> boundary = new ArrayDeque<>(Math.max(1, boundarySize));
		for (long i = 0; i < count; i++) {
			if (!iterator.hasNext()) {
				throw new IllegalStateException("Checkpoint is beyond the end of the source at row " + (i + 1));
			}
			final Row row = iterator.next();
			if (duplicateTracker != null) {
				duplicateTracker.skip(row);
			}
			if (boundarySize > 0) {
				if (boundary.size() == boundarySize) {
					boundary.removeFirst();
				}
				boundary.addLast(row);
			}
		}
		if (boundarySize > 0) {
			final String actual = BulkMigrationHash.rows(new ArrayList<>(boundary),
					source.getColumns());
			if (!actual.equals(checkpoint.getLastChunkHash())) {
				throw new IllegalStateException("The source rows at the checkpoint boundary "
						+ "no longer match lastChunkHash");
			}
		}
	}

	private static int boundarySize(final BulkMigrationCheckpoint checkpoint,
			final int chunkSize) {
		if (checkpoint.getProcessedRows() == 0) {
			return 0;
		}
		if (checkpoint.getCompletedChunks() <= 0) {
			throw new IllegalStateException("A checkpoint with processed rows must contain "
					+ "at least one completed chunk");
		}
		if (checkpoint.getLastChunkHash() == null
				|| checkpoint.getLastChunkHash().isBlank()) {
			throw new IllegalStateException("A count-based resume checkpoint with processed "
					+ "rows must contain lastChunkHash");
		}
		final long preceding;
		try {
			preceding = Math.multiplyExact(checkpoint.getCompletedChunks() - 1L,
					(long) chunkSize);
		} catch (ArithmeticException e) {
			throw new IllegalStateException("Checkpoint chunk progress exceeds the supported range", e);
		}
		final long size = checkpoint.getProcessedRows() - preceding;
		if (size <= 0 || size > chunkSize) {
			throw new IllegalStateException("Checkpoint progress is inconsistent with chunkSize="
					+ chunkSize);
		}
		return (int) size;
	}

	private static BulkUpsertDuplicateTracker duplicateTracker(final Table source,
			final ChunkedBulkMigrationOption options, final boolean keyset,
			final BulkMigrationCheckpoint checkpoint) {
		if (options.getMode() != BulkMigrationMode.UPSERT) {
			return null;
		}
		final BulkUpsertOption configured = options.getBulkUpsertOption() == null
				? BulkUpsertOption.defaults() : options.getBulkUpsertOption();
		final BulkUpsertOption effective = configured.getKeyColumns().isEmpty()
				? copyWithKeys(configured, primaryKeyNames(source)) : configured;
		final BulkUpsertPlan plan = BulkUpsertPlan.resolve(source, effective);
		// Sequential upserts already give KEEP_LAST global semantics, including after
		// resume. Avoid retaining every source key for this common migration mode.
		if (effective.getDuplicateKeyStrategy() == BulkUpsertDuplicateKeyStrategy.KEEP_LAST) {
			return null;
		}
		if (keyset && checkpoint.getProcessedRows() > 0) {
			throw new IllegalStateException("A resumed keyset migration cannot reconstruct "
					+ "duplicate-key history for " + effective.getDuplicateKeyStrategy()
					+ "; use KEEP_LAST or restart without a checkpoint");
		}
		return new BulkUpsertDuplicateTracker(plan);
	}

	private static void validateCheckpoint(final BulkMigrationCheckpoint checkpoint,
			final ChunkedBulkMigrationOption options) {
		if (!Objects.equals(checkpoint.getSourceFingerprint(), options.getSourceFingerprint())
				|| !Objects.equals(checkpoint.getTargetFingerprint(), options.getTargetFingerprint())) {
			throw new IllegalArgumentException("Checkpoint fingerprints do not match the migration options");
		}
	}

	private static void validateResumeStyle(final BulkMigrationCheckpoint checkpoint,
			final boolean keyset) {
		if (checkpoint.getProcessedRows() == 0) {
			return;
		}
		if (keyset && checkpoint.getResumeToken() == null) {
			throw new IllegalArgumentException("The checkpoint was created by count-based resume and cannot "
					+ "be resumed as a keyset source");
		}
		if (!keyset && checkpoint.getResumeToken() != null) {
			throw new IllegalArgumentException("The checkpoint was created by keyset resume and requires "
					+ "a BulkMigrationKeysetSource");
		}
	}

	private static void close(final Iterator<Row> iterator) {
		if (iterator instanceof AutoCloseable closeable) {
			try {
				closeable.close();
			} catch (Exception e) {
				throw e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e);
			}
		}
	}
}
