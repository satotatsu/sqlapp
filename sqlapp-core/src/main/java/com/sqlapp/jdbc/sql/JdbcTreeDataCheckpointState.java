/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.sql;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint;
import com.sqlapp.jdbc.bulk.DefaultBulkMigrationKeysetCodec;
import com.sqlapp.util.JsonUtils;
import com.sqlapp.util.MessageDigests;

/**
 * Checkpoint progress of one {@link JdbcTreeDataSession} execution with
 * {@link JdbcTreeDataRestart#checkpoint checkpoint restart}.
 */
final class JdbcTreeDataCheckpointState {
	private final JdbcTreeDataRestart.Checkpoint restart;
	private final Table table;
	private final List<Column> keyColumns;
	private final String fingerprint;
	private final DefaultBulkMigrationKeysetCodec codec = new DefaultBulkMigrationKeysetCodec();
	private final boolean complete;
	private long processedRows;
	private String resumeToken;
	/** Last key read from the root cursor, including roots not yet committed. */
	private List<Object> lastReadKey;
	/** Last key whose root batch was executed in the current transaction. */
	private List<Object> lastProcessedKey;

	private JdbcTreeDataCheckpointState(final JdbcTreeDataRestart.Checkpoint restart, final Table table,
			final List<Column> keyColumns, final String fingerprint, final BulkMigrationCheckpoint checkpoint) {
		this.restart = restart;
		this.table = table;
		this.keyColumns = keyColumns;
		this.fingerprint = fingerprint;
		this.complete = checkpoint != null && checkpoint.isComplete();
		if (checkpoint != null) {
			this.processedRows = checkpoint.getProcessedRows();
			this.resumeToken = checkpoint.getResumeToken();
			if (resumeToken != null) {
				this.lastProcessedKey = codec.decode(keyColumns, resumeToken);
				this.lastReadKey = lastProcessedKey;
			}
		}
	}

	static JdbcTreeDataCheckpointState load(final JdbcTreeDataRestart.Checkpoint restart, final Connection connection,
			final Table table, final String rootSql) throws SQLException {
		if (!restart.store().participatesIn(connection)) {
			throw new IllegalArgumentException("The checkpoint store must use the session connection so checkpoints"
					+ " are committed atomically with root batches.");
		}
		if (rootSql == null || !rootSql.contains("checkpoint")) {
			throw new IllegalStateException("Checkpoint restart requires a root SELECT registered with select(table, sql)"
					+ " that skips processed roots with the 'checkpoint' variable, for example /*if checkpoint != null*/"
					+ " AND key > /*checkpoint.KEY*/0 /*end*/ and ORDER BY the key columns.");
		}
		final List<Column> keys = new ArrayList<>();
		for (final String name : restart.keyColumns()) {
			final Column column = table.getColumns().get(name);
			if (column == null) {
				throw new IllegalArgumentException(
						"Checkpoint key column is not a column of root table " + table.getName() + ": " + name);
			}
			keys.add(column);
		}
		final String fingerprint = fingerprint(table, rootSql, keys);
		final BulkMigrationCheckpoint checkpoint = restart.store().load(restart.jobId()).orElse(null);
		if (checkpoint != null && !fingerprint.equals(checkpoint.getSourceFingerprint())) {
			throw new IllegalStateException("The root SELECT or checkpoint key columns changed since checkpoint '"
					+ restart.jobId() + "' was saved. Delete the checkpoint deliberately to start again.");
		}
		return new JdbcTreeDataCheckpointState(restart, table, List.copyOf(keys), fingerprint, checkpoint);
	}

	/** True when an earlier execution read every root. */
	boolean isComplete() {
		return complete;
	}

	/**
	 * The {@code checkpoint} SQL variable: last processed key values by column name,
	 * or {@code null} before the first root batch.
	 */
	Map<String, Object> variable() {
		if (lastProcessedKey == null) {
			return null;
		}
		final Map<String, Object> values = new LinkedHashMap<>();
		for (int i = 0; i < keyColumns.size(); i++) {
			values.put(keyColumns.get(i).getName(), lastProcessedKey.get(i));
		}
		return Collections.unmodifiableMap(values);
	}

	/** Rejects root rows that are not strictly after the previous key. */
	void validateOrder(final List<Row> rows) {
		for (final Row row : rows) {
			final List<Object> key = key(row);
			if (lastReadKey != null && compare(key, lastReadKey) <= 0) {
				throw new IllegalStateException("Root rows of " + table.getName()
						+ " must be returned in strictly increasing order of checkpoint key " + restart.keyColumns()
						+ ", starting after the checkpoint. Add ORDER BY on unique key columns and the checkpoint condition.");
			}
			lastReadKey = key;
		}
	}

	/** Records an executed root batch and saves progress in the current transaction. */
	void processed(final List<Row> rows) throws SQLException {
		if (rows.isEmpty()) {
			return;
		}
		final Row last = rows.get(rows.size() - 1);
		processedRows += rows.size();
		lastProcessedKey = key(last);
		resumeToken = codec.encode(keyColumns, last);
		save(false);
	}

	/** Saves the final state after every root was read. */
	void completed() throws SQLException {
		save(true);
	}

	private void save(final boolean done) throws SQLException {
		final int chunkSize = 1;
		restart.store().save(BulkMigrationCheckpoint.builder().migrationId(restart.jobId())
				.sourceFingerprint(fingerprint).processedRows(processedRows).completedChunks(processedRows)
				.chunkSize(chunkSize)
				.lastChunkHash(resumeToken == null ? null : sha256(resumeToken))
				.resumeToken(resumeToken).complete(done).build().validate());
	}

	private List<Object> key(final Row row) {
		final List<Object> values = new ArrayList<>(keyColumns.size());
		for (final Column column : keyColumns) {
			final Object value = row.get(column);
			if (value == null) {
				throw new IllegalStateException(
						"Checkpoint key column must not be NULL: " + table.getName() + "." + column.getName());
			}
			values.add(value);
		}
		return values;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private int compare(final List<Object> left, final List<Object> right) {
		for (int i = 0; i < left.size(); i++) {
			final Object a = left.get(i);
			final Object b = right.get(i);
			if (!(a instanceof Comparable comparable) || !a.getClass().isInstance(b)) {
				throw new IllegalStateException("Checkpoint key values must be comparable values of one type: "
						+ table.getName() + "." + keyColumns.get(i).getName());
			}
			final int result = comparable.compareTo(b);
			if (result != 0) {
				return result;
			}
		}
		return 0;
	}

	private static String fingerprint(final Table table, final String rootSql, final List<Column> keys) {
		final String text = JsonUtils.toJsonString(List.of("JDBC_TREE_DATA_CHECKPOINT_V1", table.getName(), rootSql,
				keys.stream().map(Column::getName).toList()));
		return "tree-root:sha256:" + sha256(text);
	}

	private static String sha256(final String text) {
		return MessageDigests.SHA256.checksumAsString(text.getBytes(StandardCharsets.UTF_8)).toLowerCase(Locale.ROOT);
	}
}
