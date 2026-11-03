package com.sqlapp.data.db.dialect.vertica.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import com.sqlapp.data.schemas.Schema;

/**
 * Restores an exported definition into a new schema during an exclusive
 * stopped-scheduler maintenance window.
 */
public final class VerticaSchemaRestorer {
	public record Result(List<String> completedStatements, VerticaSchedulingStateRestorer.Result schedulingStates) {
		public Result {
			completedStatements = List.copyOf(completedStatements);
		}
	}

	public record DetailedResult(Result result, List<String> verifiedGeneratedStatements) {
		public DetailedResult {
			verifiedGeneratedStatements = List.copyOf(verifiedGeneratedStatements);
		}
	}

	public enum Phase {
		DDL, GENERATED_OBJECTS, SCHEDULING_STATES
	}

	/** DDL is not transactional. No automatic DROP/rollback is attempted. */
	public static final class RestorationException extends SQLException {
		private static final long serialVersionUID = 1L;
		private final List<String> completedStatements;
		private final String failedStatement;
		private final Phase phase;
		private final List<String> verifiedGeneratedStatements;

		RestorationException(SQLException cause, List<String> completed, String failed) {
			this(cause, completed, failed, failed == null ? Phase.SCHEDULING_STATES : Phase.DDL, List.of());
		}

		RestorationException(SQLException cause, List<String> completed, String failed, Phase phase,
				List<String> verified) {
			super("Vertica schema restoration failed; inspect completed statements before retrying",
					cause.getSQLState(), cause.getErrorCode(), cause);
			completedStatements = List.copyOf(completed);
			failedStatement = failed;
			this.phase = phase;
			verifiedGeneratedStatements = List.copyOf(verified);
		}

		public List<String> getCompletedStatements() {
			return completedStatements;
		}

		/** Null means failure outside a DDL call; inspect getPhase() and getCause(). */
		public String getFailedStatement() {
			return failedStatement;
		}

		public Phase getPhase() {
			return phase;
		}

		public List<String> getVerifiedGeneratedStatements() {
			return verifiedGeneratedStatements;
		}
	}

	/** Optional offline preview; performs the same preflight used by restore. */
	public List<String> preview(Schema schema) {
		return VerticaSchemaRestorePlan.statements(Objects.requireNonNull(schema, "schema"));
	}

	public Result restore(Connection connection, Schema schema) throws SQLException {
		return restoreWithDetails(connection, schema).result();
	}

	/**
	 * Adds generated-object verification details without changing the original
	 * Result record.
	 */
	public DetailedResult restoreWithDetails(Connection connection, Schema schema) throws SQLException {
		Objects.requireNonNull(connection, "connection");
		var plan = VerticaSchemaRestorePlan.create(Objects.requireNonNull(schema, "schema"));
		if (connection.getMetaData().getDatabaseMajorVersion() < 25)
			throw new SQLException("Schema restoration is qualified for Vertica 25 and later");
		VerticaSchedulingStateRestorer.requireStoppedScheduler(connection, schema);
		try (var statement = connection
				.prepareStatement("SELECT schema_name FROM v_catalog.schemata WHERE LOWER(schema_name)=LOWER(?)")) {
			statement.setString(1, schema.getName());
			try (var rows = statement.executeQuery()) {
				if (rows.next())
					throw new SQLException("Restore target schema already exists; no DDL was executed");
			}
		}
		var completed = new ArrayList<String>();
		var verified = new ArrayList<String>();
		Set<String> generated = Set.of();
		for (int i = 0; i < plan.statements().size(); i++) {
			if (plan.generatedCheckpoint() > 0 && i == plan.generatedCheckpoint()) {
				try {
					generated = VerticaFlexGeneratedObjects.verify(connection, schema, plan);
				} catch (SQLException failure) {
					throw new RestorationException(failure, completed, null, Phase.GENERATED_OBJECTS, verified);
				}
			}
			String sql = plan.statements().get(i);
			if (generated.contains(sql)) {
				verified.add(sql);
				continue;
			}
			try (var statement = connection.createStatement()) {
				statement.execute(sql);
				completed.add(sql);
			} catch (SQLException failure) {
				throw new RestorationException(failure, completed, sql, Phase.DDL, verified);
			}
		}
		if (plan.generatedCheckpoint() > 0 && plan.generatedCheckpoint() == plan.statements().size()) {
			try {
				VerticaFlexGeneratedObjects.verify(connection, schema, plan);
			} catch (SQLException failure) {
				throw new RestorationException(failure, completed, null, Phase.GENERATED_OBJECTS, verified);
			}
		}
		try {
			return new DetailedResult(
					new Result(completed, new VerticaSchedulingStateRestorer().restore(connection, schema)), verified);
		} catch (SQLException failure) {
			throw new RestorationException(failure, completed, null, Phase.SCHEDULING_STATES, verified);
		}
	}
}
