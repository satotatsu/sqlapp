/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO;
import com.sqlapp.data.db.command.migration.bulk.ExecuteBulkMigrationJobCommand;
import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import com.sqlapp.data.db.command.migration.schema.MigrationPlan.DatabaseIdentity;
import com.sqlapp.data.db.command.migration.schema.MigrationPlanCommand;
import com.sqlapp.jdbc.bulk.BulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobVerificationResult;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationListener;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationProgress;

/**
 * Shared explicit review/execute entry; uses existing copy and verification
 * semantics.
 */
public final class TransferJobExecutionService {
	public record Review(Path definitionFile, TransferJobPreparationService.Prepared prepared,
			DatabaseIdentity sourceIdentity, DatabaseIdentity targetIdentity) {
		public Review {
			definitionFile = Objects.requireNonNull(definitionFile, "definitionFile").toAbsolutePath().normalize();
			Objects.requireNonNull(prepared, "prepared");
			Objects.requireNonNull(sourceIdentity, "sourceIdentity");
			Objects.requireNonNull(targetIdentity, "targetIdentity");
		}
	}

	public enum Phase {
		REVALIDATION, SCHEMA_PREPARATION, COPY, VERIFICATION, FINALIZATION, COMPLETE
	}

	/**
	 * Failure outcomes retain copy results and committed progress; they do not
	 * imply rollback.
	 */
	public record Outcome(TransferJobPreparationService.Prepared prepared, Phase phase,
			BulkMigrationJobResult copyResult, BulkMigrationJobVerificationResult verificationResult,
			List<String> completedTasks, Map<String, ChunkedBulkMigrationProgress> committedProgress,
			String stoppedTaskId, TransferSchemaPreparation.Progress schemaPreparation) {
		public Outcome(TransferJobPreparationService.Prepared prepared, Phase phase, BulkMigrationJobResult copyResult,
				BulkMigrationJobVerificationResult verificationResult, List<String> completedTasks,
				Map<String, ChunkedBulkMigrationProgress> committedProgress, String stoppedTaskId) {
			this(prepared, phase, copyResult, verificationResult, completedTasks, committedProgress, stoppedTaskId,
					new TransferSchemaPreparation.Progress(List.of(), 0, null, prepared.warnings()));
		}

		public Outcome {
			completedTasks = List.copyOf(completedTasks);
			committedProgress = Map.copyOf(committedProgress);
		}
	}

	public static final class ExecutionException extends IllegalStateException {
		private static final long serialVersionUID = 1L;
		private final Outcome outcome;

		private ExecutionException(Outcome outcome) {
			super("Transfer failed during " + outcome.phase() + ". Inspect the outcome before retrying; "
					+ "committed copy work may remain. Raw driver exceptions are omitted from this exception.");
			this.outcome = outcome;
		}

		public Outcome outcome() {
			return outcome;
		}
	}

	/** Read-only preparation bound to the actual source/target connections used. */
	public Review review(Path jobFile, Map<String, ? extends DataSource> connections, Path workspace)
			throws java.io.IOException {
		final var definition = new TransferJobConfigurationIO().read(jobFile);
		final var bindings = bindings(definition.configuration(), connections, null, null);
		final var prepared = new TransferJobPreparationService().prepare(jobFile, bindings.connections(), workspace);
		if (!definition.fingerprint().equals(prepared.job().fingerprint()))
			throw new IllegalArgumentException("Transfer definition changed during review. Review it again.");
		return new Review(jobFile, prepared, bindings.source().identity, bindings.target().identity);
	}

	/**
	 * Explicit write boundary. GUI callers confirm the Review first; headless
	 * callers authorize this invocation themselves. Revalidates everything and
	 * never resumes or resets an existing target automatically. DataSources remain
	 * caller-owned.
	 */
	public Outcome execute(Review review, Map<String, ? extends DataSource> connections) {
		Objects.requireNonNull(review, "review");
		final var command = new ExecuteBulkMigrationJobCommand();
		final var completed = new ArrayList<String>();
		final var progress = new LinkedHashMap<String, ChunkedBulkMigrationProgress>();
		final Phase[] phase = { Phase.REVALIDATION };
		final String[] activeTask = { null };
		final TransferSchemaPreparation.Progress[] schemaProgress = {
				new TransferSchemaPreparation.Progress(List.of(), 0, null) };
		var current = review.prepared();
		try {
			checkArtifacts(review);
			final var bindings = bindings(review.prepared().job().configuration(), connections, review.sourceIdentity(),
					review.targetIdentity());
			current = new TransferJobPreparationService().prepare(review.definitionFile(), bindings.connections(),
					review.prepared().directory().getParent());
			if (!current.job().fingerprint().equals(review.prepared().job().fingerprint())
					|| !current.planFingerprint().equals(review.prepared().planFingerprint())
					|| !current.warnings().equals(review.prepared().warnings())
					|| !current.schemaPreparation().equals(review.prepared().schemaPreparation()))
				throw new IllegalArgumentException("Definition or plan changed. Review again before executing.");
			schemaProgress[0] = new TransferSchemaPreparation.Progress(current.schemaPreparation().statements(), 0,
					null, current.warnings());
			if (!current.schemaPreparation().statements().isEmpty()) {
				try (var target = bindings.target().getConnection()) {
					if (!target.getMetaData().supportsTransactions()
							|| current.job().configuration().verify() && !target.getMetaData()
									.supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE))
						throw new IllegalArgumentException("Target transaction/verification support is insufficient.");
					target.setReadOnly(false);
					target.setAutoCommit(true);
					checkArtifacts(review);
					phase[0] = Phase.SCHEMA_PREPARATION;
					TransferSchemaPreparation.execute(target, current.schemaPreparation(), value -> {
						schemaProgress[0] = value;
						activeTask[0] = value.attemptedStatement() == null ? null
								: value.planned().get(value.attemptedStatement() - 1).taskId();
					});
				}
			}
			final var execution = current;
			command.setConfigurationFile(execution.dataJob().file().toFile());
			command.setExpectedConfigurationFingerprint(execution.dataJob().fingerprint());
			command.setMaxConfigurationFileSizeBytes(1_048_576L);
			command.setMaxSchemaFileSizeBytes(16_777_216L);
			command.setCloseDataSource(false);
			command.setSourceDataSource(bindings.source());
			command.setDataSource(bindings.target());
			command.setListener(new BulkMigrationJobListener() {
				@Override
				public void onJobStarted(String fingerprint, int taskCount) {
					if (!execution.planFingerprint().equals(fingerprint))
						throw new IllegalArgumentException("Execution plan changed before job start.");
					try {
						checkArtifacts(review);
					} catch (Exception failure) {
						throw new IllegalArgumentException("Reviewed artifacts changed before job start.");
					}
					phase[0] = Phase.COPY;
				}

				@Override
				public void onTaskStarted(String taskId, int index, int count) {
					activeTask[0] = taskId;
				}

				@Override
				public void onTaskCompleted(String taskId, com.sqlapp.jdbc.bulk.ChunkedBulkMigrationResult result,
						int index, int count) {
					completed.add(taskId);
					activeTask[0] = null;
				}

				@Override
				public void onJobCompleted(BulkMigrationJobResult result) {
					if (execution.job().configuration().verify())
						phase[0] = Phase.VERIFICATION;
				}
			});
			command.setChunkListener(new ChunkedBulkMigrationListener() {
				@Override
				public void onChunkCompleted(ChunkedBulkMigrationProgress value) {
					progress.put(value.getMigrationId(), value);
				}
			});
			command.run();
			if (current.job().configuration().verify()
					&& (command.getVerificationResult() == null || !command.getVerificationResult().isMatch()))
				throw new IllegalStateException("Transfer verification did not succeed.");
			return new Outcome(current, Phase.COMPLETE, command.getResult(), command.getVerificationResult(), completed,
					progress, null, schemaProgress[0]);
		} catch (Exception failure) {
			// Driver exceptions and their causes may include credentials or secret-bearing
			// URLs.
			if (command.getResult() != null && (!current.job().configuration().verify()
					|| command.getVerificationResult() != null && command.getVerificationResult().isMatch()))
				phase[0] = Phase.FINALIZATION;
			throw new ExecutionException(new Outcome(current, phase[0], command.getResult(),
					command.getVerificationResult(), completed, progress, activeTask[0], schemaProgress[0]));
		}
	}

	private static void checkArtifacts(Review review) throws Exception {
		final var prepared = review.prepared();
		final var io = new TransferJobConfigurationIO();
		if (!io.read(review.definitionFile()).fingerprint().equals(prepared.job().fingerprint())
				|| !io.read(prepared.job().file()).fingerprint().equals(prepared.job().fingerprint()))
			throw new IllegalArgumentException("Reviewed transfer definition changed.");
		final var bulkIO = new BulkMigrationJobConfigurationEditorIO();
		final var bulk = bulkIO.read(prepared.dataJob().file());
		if (!bulk.fingerprint().equals(prepared.dataJob().fingerprint()))
			throw new IllegalArgumentException("Reviewed data job changed.");
		final var configuration = bulkIO.validate(bulk.text());
		if (!Objects.equals(configuration.getSchemaFingerprint(),
				BoundedMigrationFile.sha256(prepared.sourceSchemaFile(), 16_777_216L, "maxSchemaBytes", "Schema")))
			throw new IllegalArgumentException("Reviewed Schema artifact changed.");
	}

	private record Bindings(Map<String, DataSource> connections, BoundDataSource source, BoundDataSource target) {
	}

	private static Bindings bindings(TransferJobConfiguration configuration, Map<String, ? extends DataSource> supplied,
			DatabaseIdentity sourceIdentity, DatabaseIdentity targetIdentity) {
		Objects.requireNonNull(supplied, "connections");
		final var from = supplied.get(configuration.source());
		final var to = supplied.get(configuration.target());
		if (from == null || to == null)
			throw new IllegalArgumentException("Both runtime connection bindings are required.");
		final var source = new BoundDataSource(from, sourceIdentity);
		final var target = configuration.source().equals(configuration.target()) ? source
				: new BoundDataSource(to, targetIdentity);
		if (target == source && !Objects.equals(sourceIdentity, targetIdentity))
			throw new IllegalArgumentException("One connection name cannot identify different reviewed databases.");
		final var connections = new LinkedHashMap<String, DataSource>();
		connections.put(configuration.source(), source);
		connections.put(configuration.target(), target);
		return new Bindings(Map.copyOf(connections), source, target);
	}

	/**
	 * Checks every borrowed connection, including those opened after fresh
	 * preparation.
	 */
	private static final class BoundDataSource implements DataSource {
		private final DataSource delegate;
		private DatabaseIdentity identity;

		BoundDataSource(DataSource delegate, DatabaseIdentity identity) {
			this.delegate = delegate;
			this.identity = identity;
		}

		private Connection checked(Connection connection) throws SQLException {
			try {
				final var actual = MigrationPlanCommand.databaseIdentity(connection);
				if (identity == null)
					identity = actual;
				else if (!identity.equals(actual))
					throw new SQLException("Runtime database identity changed.");
				return connection;
			} catch (Exception failure) {
				try {
					connection.close();
				} catch (SQLException closeFailure) {
					throw new SQLException("Runtime database identity validation and connection release failed.");
				}
				throw new SQLException("Runtime database identity validation failed. Review the bindings again.");
			}
		}

		@Override
		public Connection getConnection() throws SQLException {
			return checked(delegate.getConnection());
		}

		@Override
		public Connection getConnection(String user, String password) throws SQLException {
			return checked(delegate.getConnection(user, password));
		}

		@Override
		public PrintWriter getLogWriter() throws SQLException {
			return delegate.getLogWriter();
		}

		@Override
		public void setLogWriter(PrintWriter writer) throws SQLException {
			delegate.setLogWriter(writer);
		}

		@Override
		public void setLoginTimeout(int seconds) throws SQLException {
			delegate.setLoginTimeout(seconds);
		}

		@Override
		public int getLoginTimeout() throws SQLException {
			return delegate.getLoginTimeout();
		}

		@Override
		public Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
			return delegate.getParentLogger();
		}

		@Override
		public <T> T unwrap(Class<T> type) throws SQLException {
			return type.isInstance(this) ? type.cast(this) : delegate.unwrap(type);
		}

		@Override
		public boolean isWrapperFor(Class<?> type) throws SQLException {
			return type.isInstance(this) || delegate.isWrapperFor(type);
		}
	}
}
