package com.sqlapp.data.db.dialect.oracle.util;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.Event;

class OracleDisabledSchedulerJobRestorerTest {
	private Event job() {
		Event event = new Event("J\"1").setSchemaName("APP").setEnable(false);
		event.setStatement("BEGIN NULL; END;");
		String p = "oracle.scheduler.";
		for (String[] entry : new String[][] { { "kind", "JOB" }, { "argumentCount", "0" },
				{ "NUMBER_OF_ARGUMENTS", "0" }, { "JOB_TYPE", "PLSQL_BLOCK" }, { "RESTARTABLE", "FALSE" },
				{ "JOB_PRIORITY", "3" }, { "AUTO_DROP", "FALSE" }, { "NLS_ENV", "NLS_LANGUAGE='AMERICAN'" },
				{ "definitionStatus", "AVAILABLE" }, { "definitionContext", "SOURCE_OWNER" },
				{ "definitionOwner", "APP" }, { "definitionName", "J\"1" } })
			event.getSpecifics().put(p + entry[0], entry[1]);
		event.getSpecifics().put(p + "definition", "BEGIN\n dbms_scheduler.create_job('\"J\"\"1\"',"
				+ "job_type=>'PLSQL_BLOCK',job_action=>'BEGIN NULL; END;',number_of_arguments=>0,"
				+ "start_date=>TO_TIMESTAMP_TZ('2050-01-01 10:00:00 Asia/Tokyo','YYYY-MM-DD HH24:MI:SS TZR'),"
				+ "repeat_interval=>'FREQ=DAILY',end_date=>NULL,job_class=>'\"DEFAULT_JOB_CLASS\"',"
				+ "enabled=>FALSE,auto_drop=>FALSE,comments=>NULL);"
				+ "sys.dbms_scheduler.set_attribute('\"J\"\"1\"','NLS_ENV','NLS_LANGUAGE=''AMERICAN'''); COMMIT; END;");
		seal(event);
		return event;
	}

	private String script(Event event) {
		return event.getSpecifics().get("oracle.scheduler.definition");
	}

	private void script(Event event, String value) {
		event.getSpecifics().put("oracle.scheduler.definition", value);
		seal(event);
	}

	private void seal(Event event) {
		event.getSpecifics().put(OracleSchedulerDefinition.FINGERPRINT, OracleSchedulerDefinition.fingerprint(event));
	}

	private Event namedJob() {
		return namedJob("PROGRAM", "SCHEDULE");
	}

	private Event namedJob(String... kinds) {
		Event event = job();
		var specifics = event.getSpecifics();
		for (String kind : kinds) {
			String prefix = "oracle.scheduler." + kind.toLowerCase(java.util.Locale.ROOT) + ".";
			specifics.put("oracle.scheduler." + kind + "_OWNER", "APP");
			specifics.put("oracle.scheduler." + kind + "_NAME", kind);
			specifics.put(prefix + "status", "AVAILABLE");
			specifics.put(prefix + "definitionStatus", "AVAILABLE");
			specifics.put(prefix + "definitionContext", "SOURCE_OWNER");
			specifics.put(prefix + "definitionOwner", "APP");
			specifics.put(prefix + "definitionName", kind);
			specifics.put(prefix + "definition", kind + " captured DDL");
		}
		specifics.put("oracle.scheduler.program.ENABLED", "FALSE");
		specifics.put("oracle.scheduler.program.PROGRAM_TYPE", "STORED_PROCEDURE");
		specifics.put("oracle.scheduler.program.NUMBER_OF_ARGUMENTS", "1");
		specifics.put("oracle.scheduler.program.argumentCount", "1");
		specifics.put("oracle.scheduler.program.argument.1.name", "ARG");
		specifics.put("oracle.scheduler.program.argument.1.type", "VARCHAR2");
		specifics.put("oracle.scheduler.program.argument.1.defaultValue", "default");
		specifics.put("oracle.scheduler.program.argument.1.hasDefaultAnydataValue", "FALSE");
		String original = script(event);
		if (List.of(kinds).contains("PROGRAM"))
			original = original.substring(0, original.indexOf("job_type")) + "program_name=>'\"PROGRAM\"',"
					+ original.substring(original.indexOf("start_date"));
		if (List.of(kinds).contains("SCHEDULE"))
			original = original.substring(0, original.indexOf("start_date")) + "schedule_name=>'\"SCHEDULE\"',"
					+ original.substring(original.indexOf("job_class"));
		script(event, original);
		return event;
	}

	@Test
	void independentlyValidatesNamedProgramAndScheduleOverloads() {
		var program = OracleSchedulerJobScript.parse(namedJob("PROGRAM"));
		assertTrue(program.arguments().isEmpty());
		assertEquals("PROGRAM", program.dependencies().getFirst().name());
		String programSql = program.calls().getFirst().sql();
		assertTrue(programSql.contains("PROGRAM_NAME=>?") && programSql.contains("START_DATE=>TO_TIMESTAMP_TZ"));
		assertFalse(programSql.contains("JOB_ACTION") || programSql.contains("SCHEDULE_NAME"));

		Event scheduleJob = namedJob("SCHEDULE");
		var schedule = OracleSchedulerJobScript.parse(scheduleJob);
		assertEquals("SCHEDULE", schedule.dependencies().getFirst().name());
		String scheduleSql = schedule.calls().getFirst().sql();
		assertTrue(scheduleSql.contains("SCHEDULE_NAME=>?") && scheduleSql.contains("JOB_ACTION=>?"));
		assertFalse(scheduleSql.contains("START_DATE") || scheduleSql.contains("PROGRAM_NAME"));
		script(scheduleJob, script(scheduleJob).replace("number_of_arguments=>0", "number_of_arguments=>1"));
		assertThrows(IllegalArgumentException.class, () -> OracleSchedulerJobScript.parse(scheduleJob));
	}

	@Test
	void checksNamedDependenciesAndDefaultsBeforeCreatingTheJob() throws Exception {
		Event event = namedJob();
		event.getSpecifics().put("oracle.scheduler.program.argument.1.hasDefaultAnydataValue", "TRUE");
		event.getSpecifics().put("oracle.scheduler.program.argument.1.defaultRepresentation", "TEXT");
		seal(event);
		State state = new State();
		state.programArgumentRows = List
				.<String[]>of(new String[] { "1", "ARG", "VARCHAR2", "default", null, null, "TRUE", "default" });
		var result = OracleDisabledSchedulerJobRestorer.restore(state.connection(), event);
		assertEquals(List.of("CREATE_JOB", "SET_NLS_ENV", "VERIFY_ARGUMENTS", "VERIFY_DISABLED"),
				result.completedOperations());
		assertTrue(state.bound.getFirst().containsAll(List.of("\"PROGRAM\"", "\"SCHEDULE\"")));
		assertEquals(7, state.queries);

		State drift = new State();
		drift.programArgumentRows = List
				.<String[]>of(new String[] { "1", "ARG", "VARCHAR2", "default", null, null, "TRUE", "different" });
		assertThrows(IllegalArgumentException.class,
				() -> OracleDisabledSchedulerJobRestorer.restore(drift.connection(), event));
		assertTrue(drift.executed.isEmpty());

		State missing = new State();
		missing.programArgumentRows = state.programArgumentRows;
		missing.scheduleDefinition = null;
		assertThrows(IllegalArgumentException.class,
				() -> OracleDisabledSchedulerJobRestorer.restore(missing.connection(), event));
		assertTrue(missing.executed.isEmpty());
	}

	@Test
	void createsAnAbsentScheduleThroughValidatedCallsAndReportsReadbackFailure() throws Exception {
		Event event = namedJob();
		String definition = "BEGIN DBMS_SCHEDULER.DISABLE1_CALENDAR_CHECK(); "
				+ "DBMS_SCHEDULER.CREATE_SCHEDULE('\"SCHEDULE\"',NULL,'FREQ=DAILY',NULL,NULL); COMMIT; END;";
		event.getSpecifics().put("oracle.scheduler.schedule.definition", definition);
		event.getSpecifics().put("oracle.scheduler.schedule.REPEAT_INTERVAL", "FREQ=DAILY");
		seal(event);
		State state = new State();
		state.programArgumentRows = List
				.<String[]>of(new String[] { "1", "ARG", "VARCHAR2", "default", null, null, "FALSE" });
		state.scheduleDefinition = null;
		state.createdScheduleDefinition = "different definition";
		var failure = assertThrows(OracleDisabledSchedulerJobRestorer.RestorationException.class,
				() -> OracleDisabledSchedulerJobRestorer.restoreWithDependencies(state.connection(), event));
		assertEquals("VERIFY_SCHEDULE", failure.getFailedOperation());
		assertEquals(List.of("CREATE_SCHEDULE"), failure.getPartialResult().completedOperations());
		assertEquals(List.of("BEGIN SYS.DBMS_SCHEDULER.CREATE_SCHEDULE(?,NULL,?,NULL,NULL); END;"), state.executed);

		state.executed.clear();
		state.scheduleDefinition = null;
		event.getSpecifics().put("oracle.scheduler.schedule.definition",
				definition.replace("COMMIT;", "DBMS_SCHEDULER.ENABLE('\"J\"'); COMMIT;"));
		seal(event);
		assertThrows(IllegalArgumentException.class,
				() -> OracleDisabledSchedulerJobRestorer.restoreWithDependencies(state.connection(), event));
		assertTrue(state.executed.isEmpty());

		String combined = "FREQ=DAILY;INCLUDE=HOLIDAY";
		event.getSpecifics().put("oracle.scheduler.schedule.definition", definition.replace("FREQ=DAILY", combined));
		event.getSpecifics().put("oracle.scheduler.schedule.REPEAT_INTERVAL", combined);
		seal(event);
		assertThrows(IllegalArgumentException.class,
				() -> OracleDisabledSchedulerJobRestorer.restoreWithDependencies(state.connection(), event));
		assertTrue(state.executed.isEmpty());
	}

	@Test
	void rejectsDifferentNativeReferenceAndOpaqueProgramDefaultBeforeTargetAccess() {
		Event different = namedJob();
		script(different, script(different).replace("schedule_name=>'\"SCHEDULE\"'", "schedule_name=>'\"OTHER\"'"));
		Event opaque = namedJob();
		opaque.getSpecifics().put("oracle.scheduler.program.argument.1.hasDefaultAnydataValue", "TRUE");
		seal(opaque);
		for (Event event : List.of(different, opaque)) {
			State state = new State();
			assertThrows(IllegalArgumentException.class,
					() -> OracleDisabledSchedulerJobRestorer.restore(state.connection(), event));
			assertEquals(0, state.accesses);
		}
	}

	private Event argumentsJob() {
		Event event = job();
		event.setStatement("APP.PROC");
		event.getSpecifics().put("oracle.scheduler.JOB_TYPE", "STORED_PROCEDURE");
		event.getSpecifics().put("oracle.scheduler.NUMBER_OF_ARGUMENTS", "2");
		event.getSpecifics().put("oracle.scheduler.argumentCount", "2");
		for (int index = 1; index <= 2; index++) {
			String prefix = "oracle.scheduler.argument." + index + ".";
			event.getSpecifics().put(prefix + "value", index == 1 ? "  first\n'quoted'; second  " : "42");
			event.getSpecifics().put(prefix + "representation", "TEXT");
			event.getSpecifics().put(prefix + "hasAnydataValue", "TRUE");
			event.getSpecifics().put(prefix + "type", index == 1 ? "UNDEFINED" : "VARCHAR2");
		}
		script(event,
				script(event).replace("job_type=>'PLSQL_BLOCK'", "job_type=>'STORED_PROCEDURE'")
						.replace("job_action=>'BEGIN NULL; END;'", "job_action=>'APP.PROC'")
						.replace("number_of_arguments=>0", "number_of_arguments=>2"));
		return event;
	}

	@Test
	void restoresVerifiedTextArgumentsInPositionOrderAndChecksThemInOneQuery() throws Exception {
		State state = new State();
		state.argumentRows = List.of(new String[] { "1", "  first\n'quoted'; second  " }, new String[] { "2", "42" });
		var result = OracleDisabledSchedulerJobRestorer.restore(state.connection(), argumentsJob());
		assertEquals(List.of("CREATE_JOB", "SET_NLS_ENV", "SET_ARGUMENT_1", "SET_ARGUMENT_2", "VERIFY_ARGUMENTS",
				"VERIFY_DISABLED"), result.completedOperations());
		assertEquals(4, state.queries);
		assertEquals(List.of("\"J\"\"1\"", "  first\n'quoted'; second  "), state.bound.get(2));
		assertFalse(state.executed.get(2).contains("quoted"));
	}

	@Test
	void rejectsMissingAmbiguousAndNonTextArgumentsBeforeConnectionAccess() {
		for (String[] change : new String[][] { { "argument.1.representation", "UNSUPPORTED" },
				{ "argument.1.representation", "" }, { "argument.1.value", "" }, { "argument.1.type", "NUMBER" },
				{ "argument.1.hasAnydataValue", "unknown" }, { "argument.1.outArgument", "TRUE" },
				{ "argument.1.name", "NAME" }, { "argument.3.value", "extra" }, { "argument.01.value", "noncanonical" },
				{ "argumentCount", "1" }, { "NUMBER_OF_ARGUMENTS", "256" }, { "argument.1.payload", "unknown" } }) {
			Event event = argumentsJob();
			event.getSpecifics().put("oracle.scheduler." + change[0], change[1]);
			seal(event);
			State state = new State();
			assertThrows(IllegalArgumentException.class,
					() -> OracleDisabledSchedulerJobRestorer.restore(state.connection(), event));
			assertEquals(0, state.accesses);
		}
	}

	@Test
	void reportsArgumentVerificationAndSetterFailuresWithoutCleanup() {
		State state = new State();
		var verification = assertThrows(OracleDisabledSchedulerJobRestorer.RestorationException.class,
				() -> OracleDisabledSchedulerJobRestorer.restore(state.connection(), argumentsJob()));
		assertEquals("VERIFY_ARGUMENTS", verification.getFailedOperation());
		assertEquals(4, verification.getPartialResult().completedOperations().size());
		State setterState = new State();
		setterState.failAtCall = 3;
		var setter = assertThrows(OracleDisabledSchedulerJobRestorer.RestorationException.class,
				() -> OracleDisabledSchedulerJobRestorer.restore(setterState.connection(), argumentsJob()));
		assertEquals("SET_ARGUMENT_1", setter.getFailedOperation());
		assertEquals(List.of("CREATE_JOB", "SET_NLS_ENV"), setter.getPartialResult().completedOperations());
	}

	@Test
	void restoresOnlyBoundDisabledCallsAndVerifiesState() throws Exception {
		State state = new State();
		var result = OracleDisabledSchedulerJobRestorer.restore(state.connection(), job());
		assertEquals(List.of("CREATE_JOB", "SET_NLS_ENV", "VERIFY_DISABLED"), result.completedOperations());
		assertEquals(3, state.queries);
		assertEquals(2, state.executed.size());
		assertTrue(state.executed.getFirst().contains("ENABLED=>FALSE"));
		assertFalse(state.executed.getFirst().contains("Asia/Tokyo"));
		assertTrue(state.bound.getFirst().contains("2050-01-01 10:00:00 Asia/Tokyo"));
		assertTrue(state.bound.getFirst().contains("\"J\"\"1\""));
		assertThrows(UnsupportedOperationException.class, () -> result.completedOperations().clear());
	}

	@Test
	void rejectsUnsafeGrammarBeforeTouchingConnection() {
		for (String[] replacement : new String[][] { { "enabled=>FALSE", "enabled=>TRUE" },
				{ "enabled=>FALSE", "enabled=>'FALSE'" }, { "COMMIT;", "dbms_scheduler.enable('\"J\"\"1\"'); COMMIT;" },
				{ "COMMIT;", "EXECUTE IMMEDIATE 'DELETE FROM T'; COMMIT;" }, { "NLS_ENV", "ENABLED" },
				{ "number_of_arguments=>0", "number_of_arguments=>1" }, { "END;", "END; / BEGIN NULL; END;" },
				{ "dbms_scheduler.create_job", "other.dbms_scheduler.create_job" },
				{ "start_date=>TO_TIMESTAMP_TZ", "start_date=>evil" }, { "job_type=>", "job_type=>/* comment */" },
				{ "'FREQ=DAILY'", "'x'||evil()" }, { "enabled=>FALSE", "enabled=>FALSE,enabled=>FALSE" } }) {
			Event event = job();
			script(event, script(event).replace(replacement[0], replacement[1]));
			State state = new State();
			assertThrows(IllegalArgumentException.class,
					() -> OracleDisabledSchedulerJobRestorer.restore(state.connection(), event),
					Arrays.toString(replacement));
			assertEquals(0, state.accesses);
		}
	}

	@Test
	void treatsQuotesUnicodeAndEnableLookalikesInsideActionsAsData() {
		Event event = job();
		String action = "BEGIN -- 日本語\n dbms_scheduler.enable('other'); END;";
		event.setStatement(action);
		script(event, script(event).replace("'BEGIN NULL; END;'", "'" + action.replace("'", "''") + "'"));
		var calls = OracleSchedulerJobScript.parse(event).calls();
		assertTrue(calls.getFirst().bindings().contains(action));
		assertFalse(calls.getFirst().sql().contains("enable('other')"));
	}

	@Test
	void rejectsArgumentPayloadsNonDefaultAttributesAndStaleActions() {
		for (String[] field : new String[][] { { "NUMBER_OF_ARGUMENTS", "1" }, { "argumentCount", "1" },
				{ "argument.1.value", "text" }, { "argument.1.hasAnydataValue", "TRUE" }, { "DESTINATION", "REMOTE" },
				{ "RESTARTABLE", "TRUE" }, { "JOB_PRIORITY", "2" }, { "MAX_RUNS", "5" }, { "MAX_FAILURES", "2" },
				{ "JOB_TYPE", "EXECUTABLE" } }) {
			Event event = job();
			event.getSpecifics().put("oracle.scheduler." + field[0], field[1]);
			seal(event);
			assertThrows(IllegalArgumentException.class, () -> OracleSchedulerJobScript.parse(event));
		}
		Event event = job();
		event.setStatement("changed");
		assertThrows(IllegalArgumentException.class, () -> OracleSchedulerJobScript.parse(event));
	}

	@Test
	void fingerprintSurvivesXmlAndRejectsChangedDatesScriptsAndMissingProvenance() throws Exception {
		Event event = job();
		Event restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(event.asXml()));
		assertEquals(OracleSchedulerDefinition.fingerprint(event), OracleSchedulerDefinition.fingerprint(restored));
		assertEquals(2, OracleSchedulerJobScript.parse(restored).calls().size());
		var schema = new com.sqlapp.data.schemas.Schema("APP");
		schema.getEvents().add(event);
		com.sqlapp.data.schemas.Schema importedSchema = com.sqlapp.data.schemas.SchemaUtils
				.readXml(new java.io.StringReader(schema.asXml()));
		assertEquals(2, OracleSchedulerJobScript.parse(importedSchema.getEvents().getFirst()).calls().size());
		for (String[] change : new String[][] { { "START_DATE_TEXT", "changed" }, { "REPEAT_INTERVAL", "changed" },
				{ "definition", "changed" }, { "definitionFingerprint", "" } }) {
			Event changed = event.clone();
			changed.getSpecifics().put("oracle.scheduler." + change[0], change[1]);
			State state = new State();
			assertThrows(IllegalArgumentException.class,
					() -> OracleDisabledSchedulerJobRestorer.restore(state.connection(), changed));
			assertEquals(0, state.accesses);
		}
	}

	@Test
	void refusesExistingTargetsWrongContextsTransactionsAndVersionsWithoutWrites() {
		for (int mode = 0; mode < 6; mode++) {
			State state = new State();
			switch (mode) {
			case 0 -> state.present = true;
			case 1 -> state.schema = "OTHER";
			case 2 -> state.sessionUser = "OTHER";
			case 3 -> state.autoCommit = false;
			case 4 -> state.major = 19;
			case 5 -> state.product = "Other";
			}
			assertThrows(IllegalArgumentException.class,
					() -> OracleDisabledSchedulerJobRestorer.restore(state.connection(), job()));
			assertTrue(state.executed.isEmpty());
		}
	}

	@Test
	void reportsPartialCallsWithoutAutomaticCleanupOrRetry() {
		State state = new State();
		state.failSecondCall = true;
		var failure = assertThrows(OracleDisabledSchedulerJobRestorer.RestorationException.class,
				() -> OracleDisabledSchedulerJobRestorer.restore(state.connection(), job()));
		assertEquals(List.of("CREATE_JOB"), failure.getPartialResult().completedOperations());
		assertEquals("SET_NLS_ENV", failure.getFailedOperation());
		assertTrue(state.present);
		assertEquals(2, state.executed.size());
		assertEquals(1031, failure.getErrorCode());
	}

	@Test
	void reportsFailedDisabledVerification() {
		State state = new State();
		state.enabled = "TRUE";
		var failure = assertThrows(OracleDisabledSchedulerJobRestorer.RestorationException.class,
				() -> OracleDisabledSchedulerJobRestorer.restore(state.connection(), job()));
		assertEquals("VERIFY_DISABLED", failure.getFailedOperation());
		assertEquals(List.of("CREATE_JOB", "SET_NLS_ENV"), failure.getPartialResult().completedOperations());
	}

	private interface Handler {
		Object invoke(String method, Object[] arguments) throws Throwable;
	}

	private static <T> T proxy(Class<T> type, Handler handler) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
				(object, method, args) -> handler.invoke(method.getName(), args)));
	}

	private static final class State {
		boolean autoCommit = true, present, failSecondCall;
		String sessionUser = "APP", schema = "APP", product = "Oracle", enabled = "FALSE";
		int major = 23, accesses, queries, failAtCall;
		List<String[]> argumentRows = List.of();
		List<String[]> programArgumentRows = List.of();
		String scheduleDefinition = "SCHEDULE captured DDL";
		String createdScheduleDefinition;
		List<String> executed = new ArrayList<>();
		List<List<String>> bound = new ArrayList<>();

		Connection connection() {
			return proxy(Connection.class, (method, args) -> {
				accesses++;
				return switch (method) {
				case "getAutoCommit" -> autoCommit;
				case "getMetaData" -> proxy(DatabaseMetaData.class, (name, ignored) -> switch (name) {
				case "getDatabaseProductName" -> product;
				case "getDatabaseMajorVersion" -> major;
				default -> throw new UnsupportedOperationException(name);
				});
				case "prepareStatement" -> statement(PreparedStatement.class, (String) args[0]);
				case "prepareCall" -> statement(CallableStatement.class, (String) args[0]);
				default -> throw new UnsupportedOperationException(method);
				};
			});
		}

		<T> T statement(Class<T> type, String sql) {
			List<String> bindings = new ArrayList<>();
			return proxy(type, (method, args) -> switch (method) {
			case "setString" -> {
				bindings.add((String) args[1]);
				yield null;
			}
			case "execute" -> {
				executed.add(sql);
				bound.add(List.copyOf(bindings));
				if (failSecondCall && executed.size() == 2 || executed.size() == failAtCall)
					throw new SQLException("denied", "", 1031);
				if (sql.contains("CREATE_SCHEDULE"))
					scheduleDefinition = createdScheduleDefinition;
				else
					present = true;
				yield false;
			}
			case "executeQuery" -> {
				queries++;
				if (sql.contains("ALL_SCHEDULER_PROGRAM_ARGS"))
					yield rows(programArgumentRows);
				if (sql.contains("ALL_SCHEDULER_PROGRAMS"))
					yield rows(List.<String[]>of(new String[] { "PROGRAM captured DDL" }));
				if (sql.contains("ALL_SCHEDULER_SCHEDULES"))
					yield rows(scheduleDefinition == null ? List.of()
							: List.<String[]>of(new String[] { scheduleDefinition }));
				yield rows(sql.contains("SYS_CONTEXT")
						? List.<String[]>of(new String[] { sessionUser, sessionUser, schema })
						: sql.contains("ALL_SCHEDULER_JOB_ARGS") ? argumentRows
								: present ? List.<String[]>of(new String[] { enabled }) : List.of());
			}
			case "close" -> null;
			default -> throw new UnsupportedOperationException(method);
			});
		}

		ResultSet rows(List<String[]> rows) {
			int[] index = { -1 };
			return proxy(ResultSet.class, (method, args) -> switch (method) {
			case "next" -> ++index[0] < rows.size();
			case "getString" -> rows.get(index[0])[(int) args[0] - 1];
			case "close" -> null;
			default -> throw new UnsupportedOperationException(method);
			});
		}
	}
}
