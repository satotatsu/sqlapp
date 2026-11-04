package com.sqlapp.data.db.dialect.oracle.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.schemas.Event;

/** Restores the qualified argument-free inline job subset, always disabled. */
public final class OracleDisabledSchedulerJobRestorer {
	private OracleDisabledSchedulerJobRestorer() {
	}

	public record Result(String owner, String name, List<String> completedOperations) {
		public Result {
			completedOperations = List.copyOf(completedOperations);
		}
	}

	/**
	 * Scheduler calls are not atomic; no automatic DROP or rollback is attempted.
	 */
	public static final class RestorationException extends SQLException {
		private static final long serialVersionUID = 1L;
		private final Result partialResult;
		private final String failedOperation;

		private RestorationException(SQLException cause, Result partialResult, String failedOperation) {
			super("Oracle Scheduler restoration failed during " + failedOperation
					+ "; inspect the target before retrying (a failed call may have taken effect)", cause.getSQLState(),
					cause.getErrorCode(), cause);
			this.partialResult = partialResult;
			this.failedOperation = failedOperation;
		}

		public Result getPartialResult() {
			return partialResult;
		}

		public String getFailedOperation() {
			return failedOperation;
		}
	}

	/**
	 * Restores one absent job in its original owner using an Oracle 23ai/26ai
	 * connection with auto-commit enabled. Session user, current user and current
	 * schema must all equal that owner. The source must have current full metadata,
	 * no arguments or Program/Schedule references, and a supported native base
	 * script. Unsupported scripts are rejected before any SQL is executed.
	 *
	 * Only CREATE_JOB(enabled=FALSE) and setting the captured NLS environment are
	 * executed, with values bound as data. Original scripts, COMMIT, ENABLE and job
	 * actions are never executed. Requires an exclusive maintenance window: no
	 * concurrent actor may create, alter, enable or drop the target job.
	 *
	 * Scheduler changes may commit independently; failures retain completed
	 * operation names and do not delete partial objects. No grants are issued.
	 */
	public static Result restore(Connection connection, Event source) throws SQLException {
		if (connection == null || source == null)
			throw new IllegalArgumentException("Connection and source Event are required");
		Event event = source.clone();
		var calls = OracleSchedulerJobScript.parse(event);
		if (!connection.getAutoCommit())
			throw new IllegalArgumentException("Use a dedicated auto-commit connection for Scheduler restoration");
		var metadata = connection.getMetaData();
		if (!metadata.getDatabaseProductName().contains("Oracle") || metadata.getDatabaseMajorVersion() != 23)
			throw new IllegalArgumentException(
					"Scheduler restoration is qualified only for Oracle 23ai/26ai (database major 23)");
		try (var statement = connection.prepareStatement("SELECT SYS_CONTEXT('USERENV','SESSION_USER'), "
				+ "SYS_CONTEXT('USERENV','CURRENT_USER'), SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM DUAL");
				var result = statement.executeQuery()) {
			if (!result.next() || !event.getSchemaName().equals(result.getString(1))
					|| !event.getSchemaName().equals(result.getString(2))
					|| !event.getSchemaName().equals(result.getString(3)))
				throw new IllegalArgumentException(
						"Connect as the source owner with the original current schema: " + event.getSchemaName());
		}
		if (enabled(connection, event) != null)
			throw new IllegalArgumentException("Scheduler target already exists; no replacement is performed: "
					+ event.getSchemaName() + "." + event.getName());
		List<String> completed = new ArrayList<>();
		String operation = "CREATE_JOB";
		try {
			for (var call : calls) {
				operation = call.operation();
				try (var statement = connection.prepareCall(call.sql())) {
					for (int index = 0; index < call.bindings().size(); index++)
						statement.setString(index + 1, call.bindings().get(index));
					statement.execute();
				}
				completed.add(operation);
			}
			operation = "VERIFY_DISABLED";
			if (!"FALSE".equals(enabled(connection, event)))
				throw new SQLException("Restored Scheduler job is missing or not disabled");
			completed.add(operation);
			return new Result(event.getSchemaName(), event.getName(), completed);
		} catch (SQLException failure) {
			throw new RestorationException(failure, new Result(event.getSchemaName(), event.getName(), completed),
					operation);
		}
	}

	private static String enabled(Connection connection, Event event) throws SQLException {
		try (var statement = connection
				.prepareStatement("SELECT ENABLED FROM ALL_SCHEDULER_JOBS WHERE OWNER=? AND JOB_NAME=?")) {
			statement.setString(1, event.getSchemaName());
			statement.setString(2, event.getName());
			try (var result = statement.executeQuery()) {
				if (!result.next())
					return null;
				String enabled = result.getString(1);
				if (enabled == null || result.next())
					throw new SQLException("Invalid Scheduler target identity/state");
				return enabled;
			}
		}
	}
}
