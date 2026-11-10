package com.sqlapp.data.db.dialect.oracle.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.data.db.dialect.oracle.util.OracleSchedulerScriptReview.Kind;
import com.sqlapp.data.db.dialect.oracle.util.OracleSchedulerScriptReview.Script;

/** Restores supported inline or named-dependency jobs in a disabled state. */
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
	 * verified text arguments and a supported native base script. Named jobs
	 * require each referenced Program or Schedule to exist and match in the same
	 * owner.
	 *
	 * Only CREATE_JOB(enabled=FALSE), the captured NLS environment and verified
	 * text arguments are executed, with values bound as data. Original scripts,
	 * COMMIT, ENABLE and job actions are never executed. Requires an exclusive
	 * maintenance window: no concurrent actor may create, alter, enable or drop the
	 * target job or its dependencies.
	 *
	 * Scheduler changes may commit independently; failures retain completed
	 * operation names and do not delete partial objects. No grants are issued.
	 */
	public static Result restore(Connection connection, Event source) throws SQLException {
		return restore(connection, source, false);
	}

	/**
	 * Restores the job and creates absent same-owner dependencies after checking
	 * all existing dependencies. Newly created Programs and the job are disabled.
	 * The connection and maintenance requirements of {@link #restore} apply.
	 */
	public static Result restoreWithDependencies(Connection connection, Event source) throws SQLException {
		return restore(connection, source, true);
	}

	private static Result restore(Connection connection, Event source, boolean createDependencies) throws SQLException {
		if (connection == null || source == null)
			throw new IllegalArgumentException("Connection and source Event are required");
		Event event = source.clone();
		var plan = OracleSchedulerJobScript.parse(event);
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
		var missing = new LinkedHashMap<Script, List<OracleSchedulerJobScript.Call>>();
		for (Script dependency : plan.dependencies()) {
			if (!verifyDependency(connection, event, dependency)) {
				if (!createDependencies)
					throw dependencyMismatch(dependency);
				missing.put(dependency, OracleSchedulerJobScript.createDependency(event, dependency));
			}
		}
		List<String> completed = new ArrayList<>();
		String operation = "CREATE_JOB";
		try {
			for (var dependency : missing.entrySet()) {
				for (var call : dependency.getValue()) {
					operation = call.operation();
					execute(connection, call);
					completed.add(operation);
				}
				operation = "VERIFY_" + dependency.getKey().kind();
				if (!verifyDependency(connection, event, dependency.getKey()))
					throw dependencyMismatch(dependency.getKey());
				completed.add(operation);
			}
			for (var call : plan.calls()) {
				operation = call.operation();
				execute(connection, call);
				completed.add(operation);
			}
			if (!plan.arguments().isEmpty() || !plan.dependencies().isEmpty()) {
				operation = "VERIFY_ARGUMENTS";
				verifyArguments(connection, event, plan.arguments());
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
		} catch (IllegalArgumentException failure) {
			throw new RestorationException(new SQLException(failure.getMessage(), failure),
					new Result(event.getSchemaName(), event.getName(), completed), operation);
		}
	}

	private static void execute(Connection connection, OracleSchedulerJobScript.Call call) throws SQLException {
		try (var statement = connection.prepareCall(call.sql())) {
			for (int index = 0; index < call.bindings().size(); index++)
				statement.setString(index + 1, call.bindings().get(index));
			statement.execute();
		}
	}

	private static boolean verifyDependency(Connection connection, Event event, Script dependency) throws SQLException {
		boolean program = dependency.kind() == Kind.PROGRAM;
		String catalog = program ? "SYS.ALL_SCHEDULER_PROGRAMS WHERE OWNER=? AND PROGRAM_NAME=?"
				: "SYS.ALL_SCHEDULER_SCHEDULES WHERE OWNER=? AND SCHEDULE_NAME=?";
		String ddl = "SYS.DBMS_METADATA.GET_DDL('PROCOBJ', ?, OWNER)";
		if (program)
			ddl = "CASE WHEN ENABLED='FALSE' THEN " + ddl + " END";
		try (var statement = connection.prepareStatement("SELECT " + ddl + " FROM " + catalog)) {
			statement.setString(1, dependency.name());
			statement.setString(2, dependency.owner());
			statement.setString(3, dependency.name());
			try (var result = statement.executeQuery()) {
				if (!result.next())
					return false;
				if (!dependency.definition().equals(result.getString(1)) || result.next())
					throw dependencyMismatch(dependency);
			}
		}
		if (program)
			verifyProgramArguments(connection, event, dependency);
		return true;
	}

	private static IllegalArgumentException dependencyMismatch(Script dependency) {
		return new IllegalArgumentException("Missing or different Scheduler " + dependency.kind() + ": "
				+ dependency.owner() + "." + dependency.name());
	}

	private static void verifyProgramArguments(Connection connection, Event event, Script dependency)
			throws SQLException {
		String prefix = "oracle.scheduler.program.";
		int count = Integer.parseInt(event.getSpecifics().get(prefix + "argumentCount"));
		try (var statement = connection.prepareStatement("""
				SELECT ARGUMENT_POSITION, ARGUMENT_NAME, ARGUMENT_TYPE, DEFAULT_VALUE,
				       METADATA_ATTRIBUTE, OUT_ARGUMENT,
				       CASE WHEN DEFAULT_ANYDATA_VALUE IS NULL THEN 'FALSE' ELSE 'TRUE' END,
				       CASE WHEN DEFAULT_ANYDATA_VALUE IS NOT NULL THEN
				         CASE WHEN A.DEFAULT_ANYDATA_VALUE.GetTypeName()='SYS.VARCHAR2'
				              THEN A.DEFAULT_ANYDATA_VALUE.AccessVarchar2() END
				       END
				FROM SYS.ALL_SCHEDULER_PROGRAM_ARGS A
				WHERE OWNER=? AND PROGRAM_NAME=? ORDER BY ARGUMENT_POSITION
				""")) {
			statement.setString(1, dependency.owner());
			statement.setString(2, dependency.name());
			try (var result = statement.executeQuery()) {
				var fields = List.of("name", "type", "defaultValue", "metadataAttribute", "outArgument",
						"hasDefaultAnydataValue");
				for (int index = 1; index <= count; index++) {
					if (!result.next() || !Integer.toString(index).equals(result.getString(1)))
						throw new IllegalArgumentException("Missing Scheduler Program argument at position " + index);
					for (int column = 0; column < fields.size(); column++) {
						String key = prefix + "argument." + index + "." + fields.get(column);
						if (!Objects.equals(event.getSpecifics().get(key), result.getString(column + 2)))
							throw new IllegalArgumentException("Different Scheduler Program argument at position "
									+ index + ": " + fields.get(column));
					}
					if ("TRUE".equals(result.getString(7)) && !event.getSpecifics()
							.get(prefix + "argument." + index + ".defaultValue").equals(result.getString(8)))
						throw new IllegalArgumentException(
								"Different Scheduler Program default payload at position " + index);
				}
				if (result.next())
					throw new IllegalArgumentException("Unexpected target Scheduler Program arguments");
			}
		}
	}

	private static void verifyArguments(Connection connection, Event event, Map<Integer, String> arguments)
			throws SQLException {
		try (var statement = connection.prepareStatement("SELECT ARGUMENT_POSITION, VALUE FROM ALL_SCHEDULER_JOB_ARGS "
				+ "WHERE OWNER=? AND JOB_NAME=? ORDER BY ARGUMENT_POSITION")) {
			statement.setString(1, event.getSchemaName());
			statement.setString(2, event.getName());
			try (var result = statement.executeQuery()) {
				for (var argument : arguments.entrySet()) {
					if (!result.next() || !argument.getKey().toString().equals(result.getString(1))
							|| !argument.getValue().equals(result.getString(2)))
						throw new SQLException(
								"Restored Scheduler argument value/position mismatch: " + argument.getKey());
				}
				if (result.next())
					throw new SQLException("Unexpected restored Scheduler arguments");
			}
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
