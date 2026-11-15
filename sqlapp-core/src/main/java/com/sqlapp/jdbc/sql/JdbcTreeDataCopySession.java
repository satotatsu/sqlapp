/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.jdbc.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.jdbc.function.SQLConsumer;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableRowConsumer;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;

public class JdbcTreeDataCopySession implements AutoCloseable {

	private int rootBatchSize = 500;

	private JdbcTreeDataSession source;
	private JdbcTreeDataSession target;
	private BiPredicate<Column, Column> copyColumnPredicate = (sourceColumn, column) -> {
		return true;
	};

	private HoldCursorStrategy holdCursorStrategy = HoldCursorStrategy.DIALECT;

	public void setHoldCursorStrategy(HoldCursorStrategy holdCursorStrategy) {
		this.holdCursorStrategy = Objects.requireNonNull(holdCursorStrategy);
	}

	public void setCopyColumnPredicate(BiPredicate<Column, Column> copyColumnPredicate) {
		this.copyColumnPredicate = Objects.requireNonNull(copyColumnPredicate);
		// Rebuild lazily from the stored rules with the new predicate.
		this.columnMappingMap.clear();
	}

	/** Explicit handler; {@code null} selects the default for the restart policy. */
	private SQLConsumer<List<Row>> processedRootHandler;

	private JdbcTreeDataRestart restart = JdbcTreeDataRestart.none();

	/** Source roots executed by the source session but not yet recorded as copied. */
	private final List<Row> pendingSourceRoots = CommonUtils.list();

	/**
	 * Sets how copying continues after commits and after an interrupted execution.
	 * <ul>
	 * <li>{@link JdbcTreeDataRestart#none()} (default): copied source roots are
	 * removed by the processed-root handler, which requires one connection for
	 * source and target so removal and copy commit atomically.</li>
	 * <li>{@link JdbcTreeDataRestart#selfExcluding()}: copied roots are removed or
	 * marked by the processed-root handler on separate connections as well; the
	 * caller guarantees that target writes are idempotent (for example
	 * {@code INSERT_IGNORE} by natural keys), because the source commit follows the
	 * target commit.</li>
	 * <li>{@link JdbcTreeDataRestart#checkpoint checkpoint(...)}: the source is
	 * only read. The last copied source root key is saved through a store on the
	 * <em>target</em> connection in the target transaction, and the source root SQL
	 * starts after it.</li>
	 * </ul>
	 */
	public void setRestart(JdbcTreeDataRestart restart) {
		this.restart = Objects.requireNonNull(restart, "restart");
		source.setRestart(restart);
		source.checkpointConnection = target.getConnection();
	}

	public JdbcTreeDataRestart getRestart() {
		return restart;
	}

	private SQLConsumer<List<Row>> processedRootHandler() {
		if (processedRootHandler != null) {
			return processedRootHandler;
		}
		if (restart instanceof JdbcTreeDataRestart.Checkpoint) {
			return rows -> {
			};
		}
		return rows -> source.deleteByRows(source.getRootTableRelation(), rows);
	}

	/**
	 * Sets the handler invoked after a root batch has been successfully copied.
	 * <p>
	 * The handler is typically used to remove or mark the copied source rows so
	 * that they are not processed again. By default copied roots are deleted, except
	 * with checkpoint restart, where the source is only read.
	 * </p>
	 * <p>
	 * When {@link HoldCursorStrategy#REOPEN} is used, or when the root cursor is
	 * reopened after a commit, the handler must ensure that processed root rows are
	 * excluded from subsequent root selections.
	 * </p>
	 *
	 * @param deleteSourceRowHandler handler invoked for successfully copied source
	 *                               root rows
	 */
	public void setProcessedRootHandler(SQLConsumer<List<Row>> processedRootHandler) {
		this.processedRootHandler = Objects.requireNonNull(processedRootHandler);
	}

	public void setRootBatchSize(int rootBatchSize) {
		if (rootBatchSize <= 0) {
			throw new IllegalArgumentException("rootBatchSize must be greater than zero.");
		}
		this.rootBatchSize = rootBatchSize;
		source.setRootBatchSize(rootBatchSize);
		target.setRootBatchSize(rootBatchSize);
	}

	/**
	 * Sets the number of completed root JDBC batches between commits.
	 *
	 * @param long root batch count
	 */
	public void setCommitEveryRootBatches(long commitEveryRootBatches) {
		if (commitEveryRootBatches <= 0) {
			throw new IllegalArgumentException("commitEveryRootBatches must be greater than zero.");
		}
		source.setCommitEveryRootBatches(Long.MAX_VALUE);
		target.setCommitEveryRootBatches(commitEveryRootBatches);
	}

	public JdbcTreeDataCopySession(JdbcTreeDataSession source, JdbcTreeDataSession target) {
		this.source = Objects.requireNonNull(source, "source");
		this.target = Objects.requireNonNull(target, "target");
		setRootBatchSize(this.rootBatchSize);
		this.source.setAfterRootBatchHandler((i, t, rows) -> {
			pendingSourceRoots.addAll(rows);
		});
		this.target.setAfterRootBatchHandler((i, t, rows) -> {
			handleProcessedRoot(false);
		});
		this.target.beforeCommit = connection -> verifyCounts();
		this.target.setAfterCommitEveryRootBatchesHandler((i, row) -> {
			if (!source.isSameConnection(target)) {
				source.commitForce();
			} else {
				source.recordSharedCommit();
			}
			rootCursorCommitted = true;
		});
	}

	/**
	 * Records executed source roots as copied in the target transaction: saves the
	 * checkpoint and runs the processed-root handler.
	 *
	 * @param outsideTargetBatch true when called outside a target root batch, which
	 *                           then needs an explicit commit
	 */
	private void handleProcessedRoot(final boolean outsideTargetBatch) throws SQLException {
		if (pendingSourceRoots.isEmpty()) {
			return;
		}
		final JdbcTreeDataCheckpointState checkpoint = source.checkpointState();
		if (checkpoint != null) {
			checkpoint.processed(pendingSourceRoots);
		}
		processedRootHandler().accept(pendingSourceRoots);
		pendingSourceRoots.clear();
		if (outsideTargetBatch) {
			target.markPendingCommit();
		}
	}

	/**
	 * Records the remaining source roots and, after the source cursor was read
	 * completely, the completed checkpoint before the final target commit.
	 */
	private void completeProcessedRoots() throws SQLException {
		handleProcessedRoot(true);
		final JdbcTreeDataCheckpointState checkpoint = source.checkpointState();
		if (checkpoint != null && source.isRootExhausted()) {
			checkpoint.completed();
			target.markPendingCommit();
		}
	}

	private void validateRestart() {
		if (!source.isSameConnection(target) && restart instanceof JdbcTreeDataRestart.None) {
			throw new IllegalStateException("Separate source and target connections cannot remove copied source roots"
					+ " atomically with the target commit, so an interruption may copy roots twice. Use"
					+ " JdbcTreeDataRestart.checkpoint(...) with a checkpoint store on the target connection, or"
					+ " selfExcluding() when target writes are idempotent.");
		}
	}

	public Row getRow(Table table) throws SQLException {
		return source.getRow(table);
	}

	private boolean rootCursorCommitted;

	public boolean next(Table table) throws SQLException {
		final TableRelation tableRelation = source.getTableRelation(table);
		if (!tableRelation.isRoot()) {
			return countRead(table, source.next(tableRelation));
		}
		settleCounts();
		return countRead(table, nextRoot(tableRelation));
	}

	private boolean nextRoot(final TableRelation tableRelation) throws SQLException {
		if (rootCursorCommitted) {
			boolean useHoldableCursor = holdCursorStrategy.useHoldableRootCursor(source.getConnection(),
					source.getDialect());
			if (!useHoldableCursor) {
				// Finish consuming the buffered source batch before replacing its
				// cursor. A commit may occur while the next target batch is started.
				if (tableRelation.hasBufferedRows()) {
					return source.next(tableRelation);
				}
				source.flushRootBatch();
				target.flushRootBatch();
				// Roots without target rows produce no target batch; record them before
				// the reopened cursor excludes copied roots.
				handleProcessedRoot(true);
				source.reSelectRoot();
			}
			rootCursorCommitted = false;
		}
		return source.next(tableRelation);
	}

	public void readAll(TableRowConsumer consumer) throws SQLException {
		source.readAll((table, rowNo, row) -> {
			if (source.getTableRelation(table).isRoot()) {
				settleCounts();
			}
			countRead(table, true);
			consumer.accept(table, rowNo, row);
		});
	}

	public Row newRow(Table table) throws SQLException {
		if (countedTargets.containsKey(table)) {
			throw new IllegalStateException("Rows of " + table.getName()
					+ " are verified by count and must be created with newCopy from "
					+ countedTargets.get(table).getName() + ".");
		}
		return target.newRow(table);
	}

	public Row newCopy(Row sourceRow, Table table) throws SQLException {
		Table source = sourceRow.getTable();
		final Table countedSource = countedTargets.get(table);
		if (countedSource != null && countedSource != source) {
			throw new IllegalStateException("Rows of " + table.getName() + " are verified by count against "
					+ countedSource.getName() + " and cannot be copied from " + source.getName() + ".");
		}
		Row row = target.newRow(table);
		copied.computeIfAbsent(new TablePair(source, table), key -> new long[2])[0]++;
		ColumnMapping columnMapping = columnMappingMap.get(source, table);
		if (columnMapping == null) {
			columnMapping = new ColumnMapping(source, table);
			columnMappingMap.put(source, table, columnMapping);
		}
		columnMapping.copyValue(sourceRow, row);
		return row;
	}

	private final DoubleKeyMap<Table, Table, ColumnMapping> columnMappingMap = CommonUtils.doubleKeyMap();

	private final DoubleKeyMap<Table, Table, JdbcTreeDataColumnMapping> columnRules = CommonUtils.doubleKeyMap();

	/**
	 * Sets column rules for {@link #newCopy} from a source table to a target table.
	 * Unknown columns, two source columns written to one target column and invalid
	 * expressions are rejected immediately.
	 */
	public void setColumnMapping(Table source, Table target, JdbcTreeDataColumnMapping mapping) {
		Objects.requireNonNull(mapping, "mapping");
		final ColumnMapping resolved = new ColumnMapping(source, target, mapping);
		columnRules.put(source, target, mapping);
		columnMappingMap.put(source, target, resolved);
	}

	class ColumnMapping {
		ColumnMapping(Table source, Table table) {
			this(source, table, columnRules.get(source, table));
		}

		ColumnMapping(Table source, Table table, JdbcTreeDataColumnMapping rules) {
			final JdbcTreeDataColumnMapping mapping = rules == null ? JdbcTreeDataColumnMapping.builder().build()
					: rules;
			for (final String name : java.util.stream.Stream.of(mapping.getNames().keySet(),
					mapping.getConversions().keySet(), mapping.getExcluded()).flatMap(java.util.Collection::stream)
					.toList()) {
				if (source.getColumns().get(name) == null) {
					throw new IllegalArgumentException(
							"Copy mapping source column is missing: " + source.getName() + "." + name);
				}
			}
			final java.util.Set<String> targets = CommonUtils.set();
			for (int i = 0; i < source.getColumns().size(); i++) {
				Column sourceColumn = source.getColumns().get(i);
				if (mapping.getExcluded().contains(sourceColumn.getName())) {
					continue;
				}
				final String targetName = mapping.getNames().get(sourceColumn.getName());
				Column column = table.getColumns().get(targetName == null ? sourceColumn.getName() : targetName);
				if (column == null) {
					if (targetName != null || mapping.getConversions().containsKey(sourceColumn.getName())) {
						throw new IllegalArgumentException("Copy mapping target column is missing: " + table.getName()
								+ "." + (targetName == null ? sourceColumn.getName() : targetName));
					}
					continue;
				}
				if (copyColumnPredicate.test(sourceColumn, column)) {
					if (!targets.add(column.getName())) {
						throw new IllegalArgumentException(
								"Two source columns are copied to " + table.getName() + "." + column.getName());
					}
					sourceColumns.add(sourceColumn);
					columns.add(column);
				}
			}
			if (mapping.getConversions().isEmpty()) {
				conversions = null;
			} else {
				// Conversion results take the target column type, like bulk copy types.
				final Table logical = source.clone();
				for (int i = 0; i < sourceColumns.size(); i++) {
					if (!mapping.getConversions().containsKey(sourceColumns.get(i).getName())) {
						continue;
					}
					final Column column = columns.get(i);
					final Column projected = logical.getColumns().get(sourceColumns.get(i).getName());
					projected.setDataType(column.getDataType());
					com.sqlapp.data.schemas.SchemaProperties.DATA_TYPE_NAME.setValue(projected, null);
					projected.setLength(column.getLength());
					projected.setScale(column.getScale());
				}
				conversions = com.sqlapp.jdbc.bulk.BulkMigrationColumnConversions.of(source, logical,
						mapping.getConversions());
			}
		}

		private final List<Column> sourceColumns = CommonUtils.list();
		private final List<Column> columns = CommonUtils.list();
		private final com.sqlapp.jdbc.bulk.BulkMigrationColumnConversions conversions;

		public void copyValue(Row source, Row row) {
			if (conversions == null) {
				for (int i = 0; i < columns.size(); i++) {
					row.put(columns.get(i), source.get(sourceColumns.get(i)));
				}
				return;
			}
			final Row values = conversions.apply(source);
			for (int i = 0; i < columns.size(); i++) {
				row.put(columns.get(i), values.get(sourceColumns.get(i).getName()));
			}
		}
	}

	@Override
	public void close() throws SQLException {
		SQLException exception = null;
		try {
			source.close();
		} catch (SQLException e) {
			exception = e;
		}
		try {
			target.close();
		} catch (SQLException e) {
			if (exception == null) {
				exception = e;
			} else {
				exception.addSuppressed(e);
			}
		}
		if (exception != null) {
			throw exception;
		}
	}

	/**
	 * Copies rows within the source and target execution boundaries. On success,
	 * the final source batch is processed before the target batch is committed.
	 * On failure both sessions discard buffered rows and roll back uncommitted work.
	 * Separate connections do not provide an atomic distributed transaction.
	 *
	 * @param work copy configuration and row processing
	 * @throws SQLException if copying or transaction completion fails
	 */
	public JdbcTreeDataCopyExecutionResult execute(SQLConsumer<JdbcTreeDataCopySession> work) throws SQLException {
		validateRestart();
		validateCountVerification();
		read.clear();
		copied.clear();
		JdbcTreeDataExecutionResult[] targetResult = new JdbcTreeDataExecutionResult[1];
		boolean previousCommitManagement = source.externallyManagedCommits;
		source.externallyManagedCommits = true;
		pendingSourceRoots.clear();
		try (this) {
			JdbcTreeDataExecutionResult sourceResult = source.execute(reader -> {
				targetResult[0] = target.execute(writer -> {
					work.accept(this);
					settleCounts();
					if (!countedTargets.isEmpty()) {
						// The final commit verifies the counts after the last flush.
						target.markPendingCommit();
					}
					source.finishExecution();
					completeProcessedRoots();
				});
			});
			return new JdbcTreeDataCopyExecutionResult(sourceResult, targetResult[0], copyCounts());
		} finally {
			source.externallyManagedCommits = previousCommitManagement;
		}
	}

	private record TablePair(Table source, Table target) {
	}

	/** Target table to its single counted source table. */
	private final Map<Table, Table> countedTargets = new IdentityHashMap<>();
	/** Rows read by the work: [pending for the current root, settled]. */
	private final Map<Table, long[]> read = new LinkedHashMap<>();
	/** Rows copied by newCopy: [pending for the current root, settled]. */
	private final Map<TablePair, long[]> copied = new LinkedHashMap<>();

	/**
	 * Verifies that every row of {@code sourceTable} read by the work is copied once
	 * to {@code targetTable} and reported as inserted by the JDBC driver.
	 * <p>
	 * The check runs before every target commit, so a mismatch rolls back the
	 * uncommitted transaction. Rows of the target table must be created with
	 * {@link #newCopy} from the source table only, the target table must use
	 * {@link JdbcTreeDataSession.TableOperationMode#INSERT}, and a driver that does
	 * not report insert counts fails the check. Counts cover one {@link #execute}
	 * call; rows copied by an earlier restarted execution are not included.
	 * </p>
	 *
	 * @param sourceTable source table
	 * @param targetTable target table
	 */
	public void verifyCounts(Table sourceTable, Table targetTable) {
		Objects.requireNonNull(sourceTable, "sourceTable");
		Objects.requireNonNull(targetTable, "targetTable");
		final Table previous = countedTargets.get(targetTable);
		if (previous != null && previous != sourceTable) {
			throw new IllegalArgumentException(targetTable.getName() + " is already verified against "
					+ previous.getName() + ".");
		}
		countedTargets.put(targetTable, sourceTable);
	}

	private void validateCountVerification() {
		for (final Map.Entry<Table, Table> entry : countedTargets.entrySet()) {
			if (source.getTableRelation(entry.getValue()) == null) {
				throw new IllegalStateException(
						"Count verification source table is not read by the source session: " + entry.getValue().getName());
			}
			if (target.getTableRelation(entry.getKey()) == null) {
				throw new IllegalStateException(
						"Count verification target table is not written by the target session: " + entry.getKey().getName());
			}
			if (target.tableOperationMode(entry.getKey()) != JdbcTreeDataSession.TableOperationMode.INSERT) {
				throw new IllegalStateException("Count verification requires TableOperationMode.INSERT for "
						+ entry.getKey().getName() + "; other modes may legitimately write fewer or more rows.");
			}
		}
	}

	private boolean countRead(final Table table, final boolean found) {
		if (found) {
			read.computeIfAbsent(table, key -> new long[2])[0]++;
		}
		return found;
	}

	/** The rows of finished roots are complete; their target rows are written before the next commit. */
	private void settleCounts() {
		read.values().forEach(JdbcTreeDataCopySession::settle);
		copied.values().forEach(JdbcTreeDataCopySession::settle);
	}

	private static void settle(final long[] counts) {
		counts[1] += counts[0];
		counts[0] = 0;
	}

	private static long settled(final long[] counts) {
		return counts == null ? 0 : counts[1];
	}

	private void verifyCounts() {
		for (final Map.Entry<Table, Table> entry : countedTargets.entrySet()) {
			final Table targetTable = entry.getKey();
			final Table sourceTable = entry.getValue();
			final long readRows = settled(read.get(sourceTable));
			final long copiedRows = settled(copied.get(new TablePair(sourceTable, targetTable)));
			final JdbcTreeDataExecutionResult.Counts written = target.executedCounts(targetTable, SqlType.INSERT);
			final String counts = " (read " + readRows + ", copied " + copiedRows + ", inserted "
					+ written.knownAffectedRows() + ")";
			if (written.unknownCounts() + written.failedCounts() + written.unreportedCounts() > 0) {
				throw new IllegalStateException("Cannot verify row counts of " + sourceTable.getName() + " -> "
						+ targetTable.getName() + ": the JDBC driver did not report every insert count" + counts);
			}
			if (readRows != copiedRows || copiedRows != written.knownAffectedRows()) {
				throw new IllegalStateException("Row count mismatch " + sourceTable.getName() + " -> "
						+ targetTable.getName() + counts);
			}
		}
	}

	private List<JdbcTreeDataCopyExecutionResult.CopyCount> copyCounts() {
		final Map<TablePair, Boolean> pairs = new LinkedHashMap<>();
		copied.keySet().forEach(pair -> pairs.put(pair, countedTargets.get(pair.target()) == pair.source()));
		countedTargets.forEach((targetTable, sourceTable) -> pairs.put(new TablePair(sourceTable, targetTable), true));
		return pairs.entrySet().stream()
				.map(entry -> new JdbcTreeDataCopyExecutionResult.CopyCount(
						JdbcTreeDataExecutionTracker.identity(entry.getKey().source()),
						JdbcTreeDataExecutionTracker.identity(entry.getKey().target()),
						settled(read.get(entry.getKey().source())), settled(copied.get(entry.getKey())),
						entry.getValue()))
				.toList();
	}

	public static enum HoldCursorStrategy {
		HOLD {
			@Override
			public boolean useHoldableRootCursor(Connection connection, Dialect dialect) {
				return true;
			}
		},
		REOPEN {
			@Override
			public boolean useHoldableRootCursor(Connection connection, Dialect dialect) {
				return false;
			}
		},
		DIALECT {
			@Override
			public boolean useHoldableRootCursor(Connection connection, Dialect dialect) throws SQLException {
				return dialect.supportsHoldCursorsOverCommit(connection);
			}
		},;

		public abstract boolean useHoldableRootCursor(Connection connection, Dialect dialect) throws SQLException;
	}
}
