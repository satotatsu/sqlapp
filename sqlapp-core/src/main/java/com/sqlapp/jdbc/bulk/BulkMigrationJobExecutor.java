/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Table.TableOrder;

/** Executes table migrations in validated dependency order. */
public final class BulkMigrationJobExecutor {
	private BulkMigrationJobExecutor() {
	}

	public static BulkMigrationJobResult execute(final Connection targetConnection,
			final List<BulkMigrationJobTask> tasks) throws SQLException {
		return execute(targetConnection, tasks, BulkMigrationJobListener.NO_OP);
	}

	public static BulkMigrationJobResult execute(final Connection targetConnection,
			final List<BulkMigrationJobTask> tasks,
			final BulkMigrationJobListener listener) throws SQLException {
		return executePlan(targetConnection, BulkMigrationJobPlanner.plan(tasks), listener);
	}

	public static BulkMigrationJobResult executePlan(final Connection targetConnection,
			final BulkMigrationJobPlan plan) throws SQLException {
		return executePlan(targetConnection, plan, BulkMigrationJobListener.NO_OP);
	}

	public static BulkMigrationJobResult executePlan(final Connection targetConnection,
			final BulkMigrationJobPlan plan,
			final BulkMigrationJobListener listener) throws SQLException {
		return executePlan(targetConnection, plan, listener,
				ChunkedBulkMigrationListener.NO_OP);
	}

	public static BulkMigrationJobResult executePlan(final Connection targetConnection,
			final BulkMigrationJobPlan plan,
			final BulkMigrationJobListener listener,
			final ChunkedBulkMigrationListener commonChunkListener) throws SQLException {
		Objects.requireNonNull(targetConnection, "targetConnection");
		Objects.requireNonNull(plan, "plan");
		Objects.requireNonNull(listener, "listener");
		Objects.requireNonNull(commonChunkListener, "commonChunkListener");
		plan.validateUnchanged();
		final BulkMigrationJobLifecycle lifecycle = plan.getLifecycle();
		listener.onJobStarted(plan.getFingerprint(), plan.getTasks().size());
		final BulkMigrationJobResult result;
		try {
			lifecycle.before(targetConnection, plan);
			result = executeTasks(targetConnection, plan, listener, commonChunkListener);
			lifecycle.after(targetConnection, plan, result);
		} catch (SQLException | RuntimeException | Error failure) {
			try {
				lifecycle.restore(targetConnection, plan, failure);
			} catch (SQLException | RuntimeException | Error restoreFailure) {
				failure.addSuppressed(restoreFailure);
			}
			try {
				if (failure instanceof BulkMigrationJobPausedException paused) {
					listener.onJobPaused(plan.getFingerprint(), paused.getPausedTaskId(),
							paused.getProgress());
				} else {
					listener.onJobFailed(plan.getFingerprint(), failure);
				}
			} catch (RuntimeException listenerFailure) {
				failure.addSuppressed(listenerFailure);
			}
			throw failure;
		}
		listener.onJobCompleted(result);
		return result;
	}

	public static BulkMigrationJobResult executePlan(final Connection targetConnection,
			final BulkMigrationJobPlan plan,
			final BulkMigrationJobListener listener,
			final ChunkedBulkMigrationListener commonChunkListener,
			final BulkMigrationJobLeaseManager leaseManager) throws SQLException {
		Objects.requireNonNull(leaseManager, "leaseManager");
		Objects.requireNonNull(plan, "plan").validateUnchanged();
		try (var lease = leaseManager.acquire(plan.getFingerprint())) {
			try (var heartbeat = lease.startHeartbeat()) {
				final ChunkedBulkMigrationListener renewing =
						new BulkMigrationJobLeaseChunkListener(lease, heartbeat);
				final ChunkedBulkMigrationListener effective =
						commonChunkListener == ChunkedBulkMigrationListener.NO_OP ? renewing
								: CompositeChunkedBulkMigrationListener.of(renewing,
										commonChunkListener);
				return executePlan(targetConnection, plan, listener, effective);
			}
		}
	}

	private static BulkMigrationJobResult executeTasks(final Connection targetConnection,
			final BulkMigrationJobPlan plan, final BulkMigrationJobListener listener,
			final ChunkedBulkMigrationListener commonChunkListener)
			throws SQLException {
		final List<BulkMigrationJobTask> ordered = plan.getTasks();
		final List<BulkMigrationJobTaskResult> results = new ArrayList<>(ordered.size());
		for (int taskIndex = 0; taskIndex < ordered.size(); taskIndex++) {
			final BulkMigrationJobTask task = ordered.get(taskIndex);
			listener.onTaskStarted(task.getTaskId(), taskIndex, ordered.size());
			try {
				final ChunkedBulkMigrationResult result;
				final ChunkedBulkMigrationListener taskListener = task.getChunkListener() == null
						? ChunkedBulkMigrationListener.NO_OP : task.getChunkListener();
				final ChunkedBulkMigrationListener chunkListener;
				if (taskListener == ChunkedBulkMigrationListener.NO_OP) {
					chunkListener = commonChunkListener;
				} else if (commonChunkListener == ChunkedBulkMigrationListener.NO_OP) {
					chunkListener = taskListener;
				} else {
					chunkListener = CompositeChunkedBulkMigrationListener.of(taskListener,
							commonChunkListener);
				}
				if (task.getKeysetSource() != null) {
					result = task.getCheckpointStore() == null
							? ChunkedBulkMigrationExecutor.executeWithListener(targetConnection,
									task.getKeysetSource(), task.getOptions(), chunkListener)
							: ChunkedBulkMigrationExecutor.execute(targetConnection,
									task.getKeysetSource(), task.getOptions(), task.getCheckpointStore(),
									chunkListener);
				} else {
					result = task.getCheckpointStore() == null
							? ChunkedBulkMigrationExecutor.executeWithListener(targetConnection,
									task.getSourceTable(), task.getOptions(), chunkListener)
							: ChunkedBulkMigrationExecutor.execute(targetConnection,
									task.getSourceTable(), task.getOptions(), task.getCheckpointStore(),
									chunkListener);
				}
				results.add(new BulkMigrationJobTaskResult(task.getTaskId(), result));
				listener.onTaskCompleted(task.getTaskId(), result, taskIndex, ordered.size());
			} catch (ChunkedBulkMigrationPausedException e) {
				try {
					listener.onTaskPaused(task.getTaskId(), e.getProgress(), taskIndex, ordered.size());
				} catch (RuntimeException listenerFailure) {
					e.addSuppressed(listenerFailure);
				}
				throw new BulkMigrationJobPausedException(task.getTaskId(),
						result(plan, results), e);
			} catch (SQLException e) {
				try {
					listener.onTaskFailed(task.getTaskId(), e, taskIndex, ordered.size());
				} catch (RuntimeException listenerFailure) {
					e.addSuppressed(listenerFailure);
				}
				throw new BulkMigrationJobException(task.getTaskId(),
						result(plan, results), e);
			}
		}
		return result(plan, results);
	}

	private static BulkMigrationJobResult result(final BulkMigrationJobPlan plan,
			final List<BulkMigrationJobTaskResult> results) {
		return new BulkMigrationJobResult(plan.getFingerprint(), results);
	}

	static List<BulkMigrationJobTask> order(final List<BulkMigrationJobTask> tasks) {
		Objects.requireNonNull(tasks, "tasks");
		final Set<String> taskIds = new HashSet<>();
		final Set<String> migrationIds = new HashSet<>();
		for (final BulkMigrationJobTask task : tasks) {
			Objects.requireNonNull(task, "task");
			if (task.getTaskId() == null || task.getTaskId().isBlank()) {
				throw new IllegalArgumentException("taskId must not be empty");
			}
			if (!taskIds.add(task.getTaskId())) {
				throw new IllegalArgumentException("Duplicate migration task ID: " + task.getTaskId());
			}
			if ((task.getSourceTable() == null) == (task.getKeysetSource() == null)) {
				throw new IllegalArgumentException("Task must have exactly one Table or keyset source: "
						+ task.getTaskId());
			}
			if (task.getOptions() == null) {
				throw new IllegalArgumentException("Task options require a migrationId: " + task.getTaskId());
			}
			task.getOptions().validate();
			if (!migrationIds.add(task.getOptions().getMigrationId())) {
				throw new IllegalArgumentException("Duplicate checkpoint migrationId: "
						+ task.getOptions().getMigrationId());
			}
		}
		validateAcyclic(tasks);
		return TableOrder.CREATE.sort(tasks, BulkMigrationJobExecutor::sourceTable);
	}

	private static void validateAcyclic(final List<BulkMigrationJobTask> tasks) {
		final Map<Table, List<BulkMigrationJobTask>> tasksByTable = new HashMap<>();
		final Map<BulkMigrationJobTask, Integer> indegree = new HashMap<>();
		final Map<BulkMigrationJobTask, Set<BulkMigrationJobTask>> graph = new HashMap<>();
		for (final BulkMigrationJobTask task : tasks) {
			tasksByTable.computeIfAbsent(sourceTable(task), key -> new ArrayList<>()).add(task);
			indegree.put(task, 0);
			graph.put(task, new LinkedHashSet<>());
		}
		for (final BulkMigrationJobTask child : tasks) {
			final Table childTable = sourceTable(child);
			childTable.getConstraints().getForeignKeyConstraints(
					fk -> !fk.getRelatedTable().equals(childTable)).forEach(fk -> {
				final List<BulkMigrationJobTask> parents = tasksByTable.get(fk.getRelatedTable());
				if (parents == null) {
					return;
				}
				for (final BulkMigrationJobTask parent : parents) {
					if (graph.get(parent).add(child)) {
						indegree.put(child, indegree.get(child) + 1);
					}
				}
			});
		}
		final Queue<BulkMigrationJobTask> ready = new ArrayDeque<>();
		indegree.forEach((task, count) -> {
			if (count == 0) {
				ready.add(task);
			}
		});
		int visited = 0;
		while (!ready.isEmpty()) {
			final BulkMigrationJobTask parent = ready.remove();
			visited++;
			for (final BulkMigrationJobTask child : graph.get(parent)) {
				final int count = indegree.get(child) - 1;
				indegree.put(child, count);
				if (count == 0) {
					ready.add(child);
				}
			}
		}
		if (visited != tasks.size()) {
			final List<String> blocked = tasks.stream().filter(task -> indegree.get(task) > 0)
					.map(BulkMigrationJobTask::getTaskId).toList();
			throw new IllegalArgumentException("Migration job contains cyclic or cycle-dependent tasks: "
					+ blocked);
		}
	}

	private static Table sourceTable(final BulkMigrationJobTask task) {
		return task.getSourceTable() != null ? task.getSourceTable()
				: task.getKeysetSource().getTable();
	}
}
