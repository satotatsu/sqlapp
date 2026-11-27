package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Oracle10gEventReaderTest {
	private ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			@Override
			public String getString(String name) {
				return values.get(name.toUpperCase(java.util.Locale.ROOT));
			}
		};
	}

	@Test
	void jobsAreReadInOneQueryAndRetainCalendarAndZonesThroughSchemaXml() throws Exception {
		int[] queries = { 0 };
		var reader = new Oracle10gEventReader(DialectHolder.oracle10gDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				queries[0]++;
				try {
					for (String name : List.of("job.one", "job two"))
						h.handleResultSetNext(row(Map.of("OWNER", "APP", "JOB_NAME", name, "JOB_TYPE", "PLSQL_BLOCK",
								"JOB_ACTION", "BEGIN NULL; END;", "ENABLED", "FALSE", "AUTO_DROP", "TRUE",
								"START_DATE_TEXT", "2026-11-01T01:30:00.123456 America/New_York -04:00",
								"REPEAT_INTERVAL", "FREQ=DAILY;BYHOUR=1;BYMINUTE=30", "NUMBER_OF_ARGUMENTS", "0")));
				} catch (SQLException error) {
					throw new RuntimeException(error);
				}
				return null;
			}
		};
		Schema schema = new Schema("APP");
		schema.getEvents().addAll(reader.doGetAll(null, new ParametersContext(), null));
		assertEquals(1, queries[0]);
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		assertEquals(2, restored.getEvents().size());
		Event event = restored.getEvents().get("job.one");
		assertFalse(event.isEnable());
		assertEquals(List.of("BEGIN NULL; END;"), event.getStatement());
		assertEquals("JOB", event.getSpecifics().get("oracle.scheduler.kind"));
		assertEquals("2026-11-01T01:30:00.123456 America/New_York -04:00",
				event.getSpecifics().get("oracle.scheduler.START_DATE_TEXT"));
		assertEquals("FREQ=DAILY;BYHOUR=1;BYMINUTE=30", event.getSpecifics().get("oracle.scheduler.REPEAT_INTERVAL"));
		assertNull(event.getIntervalValue());
		assertNull(event.getStarts());
		assertTrue(event.getDefinition().isEmpty());
	}

	@Test
	void namedProgramAndScheduleReferencesRemainInventoryWithNoInventedBody() throws Exception {
		Event event = new Oracle10gEventReader(DialectHolder.oracle26aiDialect).createEvent(row(Map.of("OWNER", "APP",
				"JOB_NAME", "JOB", "ENABLED", "TRUE", "PROGRAM_OWNER", "SHARED", "PROGRAM_NAME", "PROGRAM",
				"SCHEDULE_OWNER", "SHARED", "SCHEDULE_NAME", "SCHEDULE", "NUMBER_OF_ARGUMENTS", "2")));
		assertTrue(event.isEnable());
		assertNull(event.getDefiner());
		assertEquals("SHARED", event.getSpecifics().get("oracle.scheduler.PROGRAM_OWNER"));
		assertEquals("SCHEDULE", event.getSpecifics().get("oracle.scheduler.SCHEDULE_NAME"));
		assertTrue(event.getStatement().isEmpty());
	}

	@Test
	void invalidEnableStateIsNotSilentlyTreatedAsDisabled() {
		var reader = new Oracle10gEventReader(DialectHolder.oracle10gDialect);
		for (String state : List.of("", "Y", "UNKNOWN"))
			assertThrows(SQLException.class,
					() -> reader.createEvent(row(Map.of("OWNER", "APP", "JOB_NAME", "JOB", "ENABLED", state))));
		assertThrows(SQLException.class, () -> reader.createEvent(row(Map.of("OWNER", "APP", "ENABLED", "FALSE"))));
	}

	@Test
	void creatorIsPreservedWithoutInferringExecutionIdentity() throws Exception {
		Event event = new Oracle10gEventReader(DialectHolder.oracle10gDialect).createEvent(
				row(Map.of("OWNER", "APP", "JOB_NAME", "JOB", "JOB_CREATOR", "ADMIN", "ENABLED", "FALSE")));
		Event restored = SchemaUtils.readXml(new java.io.StringReader(event.asXml()));
		assertEquals("APP", restored.getSchemaName());
		assertEquals("ADMIN", restored.getSpecifics().get("oracle.scheduler.JOB_CREATOR"));
		assertNull(restored.getDefiner());
	}

	@Test
	void resolverWiresOnlyTenGAndLaterAndDdlFailsExplicitly() throws Exception {
		assertNull(DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getEventReader());
		for (var dialect : List.of(DialectHolder.oracle10gDialect, DialectHolder.oracle12cDialect,
				DialectHolder.oracle26aiDialect)) {
			assertInstanceOf(Oracle10gEventReader.class, dialect.getCatalogReader().getSchemaReader().getEventReader());
			Event event = new Event("JOB").setSchemaName("APP");
			event.setStatement("BEGIN NULL; END;");
			var registry = dialect.createSqlFactoryRegistry();
			for (SqlType type : List.of(SqlType.CREATE, SqlType.ALTER, SqlType.DROP)) {
				var failure = assertThrows(IllegalArgumentException.class, () -> registry.createSql(event, type));
				assertTrue(failure.getMessage().contains("read-only"));
			}
			assertTrue(registry.getSqlFactory(event, SqlType.ALTER).createDiffSql(event.diff(event.clone())).isEmpty());
			Event changed = event.clone().setEnable(false);
			assertThrows(IllegalArgumentException.class,
					() -> registry.getSqlFactory(event, SqlType.ALTER).createDiffSql(event.diff(changed)));
			Schema schema = new Schema("APP");
			schema.getEvents().add(event);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(schema, SqlType.CREATE));
		}
	}

	private Oracle10gEventReader argumentReader(int[] queries, List<Map<String, String>> rows) {
		return new Oracle10gEventReader(DialectHolder.oracle10gDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				queries[0]++;
				try {
					for (var values : rows) {
						var combined = new java.util.HashMap<String, String>(
								Map.of("OWNER", "APP", "JOB_NAME", "JOB", "ENABLED", "FALSE"));
						combined.putAll(values);
						h.handleResultSetNext(row(combined));
					}
				} catch (SQLException error) {
					throw new RuntimeException(error);
				}
				return null;
			}
		};
	}

	@Test
	void argumentsKeepPositionsValuesAndAnydataPresenceWithOneQueryThroughXml() throws Exception {
		String value = "  first\n'quoted'; second  ";
		int[] queries = { 0 };
		var rows = List.of(
				Map.of("ARGUMENT_POSITION", "1", "ARGUMENT_NAME", "input value", "ARGUMENT_TYPE", "VARCHAR2",
						"ARGUMENT_VALUE", value, "HAS_ANYDATA_VALUE", "TRUE"),
				Map.of("ARGUMENT_POSITION", "2", "ARGUMENT_TYPE", "APP.CUSTOM_TYPE", "HAS_ANYDATA_VALUE", "TRUE",
						"OUT_ARGUMENT", "FALSE"),
				Map.of("OWNER", "OTHER", "ARGUMENT_POSITION", "1", "ARGUMENT_VALUE", "other owner", "HAS_ANYDATA_VALUE",
						"FALSE"));
		var events = argumentReader(queries, rows).doGetAll(null, new ParametersContext(), null);
		assertEquals(1, queries[0]);
		assertEquals(2, events.size());
		Event restored = SchemaUtils.readXml(new java.io.StringReader(events.get(0).asXml()));
		assertEquals("2", restored.getSpecifics().get("oracle.scheduler.argumentCount"));
		assertEquals(value, restored.getSpecifics().get("oracle.scheduler.argument.1.value"));
		assertEquals("TRUE", restored.getSpecifics().get("oracle.scheduler.argument.1.hasAnydataValue"));
		assertEquals("input value", restored.getSpecifics().get("oracle.scheduler.argument.1.name"));
		assertEquals("APP.CUSTOM_TYPE", restored.getSpecifics().get("oracle.scheduler.argument.2.type"));
		assertEquals("TRUE", restored.getSpecifics().get("oracle.scheduler.argument.2.hasAnydataValue"));
		assertFalse(restored.getSpecifics().containsKey("oracle.scheduler.argument.2.value"));
		assertEquals("1", events.get(1).getSpecifics().get("oracle.scheduler.argumentCount"));
	}

	@Test
	void invalidAndDuplicateArgumentPositionsFailInsteadOfOverwriting() {
		for (String position : List.of("0", "-1", "1.5", "bad")) {
			var reader = argumentReader(new int[] { 0 },
					List.of(Map.of("ARGUMENT_POSITION", position, "HAS_ANYDATA_VALUE", "FALSE")));
			assertThrows(RuntimeException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		}
		var duplicate = Map.of("ARGUMENT_POSITION", "1", "HAS_ANYDATA_VALUE", "FALSE");
		assertThrows(RuntimeException.class, () -> argumentReader(new int[] { 0 }, List.of(duplicate, duplicate))
				.doGetAll(null, new ParametersContext(), null));
		assertThrows(RuntimeException.class,
				() -> argumentReader(new int[] { 0 }, List.of(Map.of("ARGUMENT_POSITION", "1"))).doGetAll(null,
						new ParametersContext(), null));
	}

	private Oracle10gEventReader dependencyReader(int[] queries, List<Map<String, String>> jobs,
			List<Map<String, String>> programs, List<Map<String, String>> schedules, boolean denied) {
		return new Oracle10gEventReader(DialectHolder.oracle10gDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				int kind = p.get("programName") != null ? 1 : p.get("scheduleName") != null ? 2 : 0;
				queries[kind]++;
				if (denied && kind != 0)
					throw new RuntimeException(new SQLException("dependency denied", "", 1031));
				try {
					for (var values : kind == 0 ? jobs : kind == 1 ? programs : schedules)
						h.handleResultSetNext(row(values));
				} catch (SQLException error) {
					throw new RuntimeException(error);
				}
				return null;
			}
		};
	}

	private Map<String, String> dependentJob(String name) {
		return Map.of("OWNER", "APP", "JOB_NAME", name, "ENABLED", "FALSE", "PROGRAM_OWNER", "SHARED", "PROGRAM_NAME",
				"PROGRAM", "SCHEDULE_OWNER", "SHARED", "SCHEDULE_NAME", "SCHEDULE");
	}

	@Test
	void sharedDependenciesAndDefaultsStaySeparateFromJobOverridesInThreeQueries() throws Exception {
		var first = new java.util.HashMap<>(dependentJob("J1"));
		first.putAll(Map.of("ARGUMENT_POSITION", "1", "ARGUMENT_VALUE", " override ", "HAS_ANYDATA_VALUE", "TRUE"));
		var program = new java.util.HashMap<String, String>(
				Map.of("OWNER", "SHARED", "PROGRAM_NAME", "PROGRAM", "ENABLED", "FALSE", "PROGRAM_TYPE",
						"STORED_PROCEDURE", "PROGRAM_ACTION", "SHARED.DO_WORK", "NUMBER_OF_ARGUMENTS", "3"));
		program.putAll(Map.of("ARGUMENT_POSITION", "1", "ARGUMENT_NAME", "TEXT_ARG", "ARGUMENT_TYPE", "VARCHAR2",
				"DEFAULT_VALUE", " default\n'quoted' ", "HAS_DEFAULT_ANYDATA_VALUE", "TRUE"));
		var typed = new java.util.HashMap<>(program);
		typed.remove("DEFAULT_VALUE");
		typed.putAll(Map.of("ARGUMENT_POSITION", "2", "ARGUMENT_NAME", "CUSTOM_ARG", "ARGUMENT_TYPE", "SHARED.CUSTOM"));
		var metadata = new java.util.HashMap<>(typed);
		metadata.putAll(Map.of("ARGUMENT_POSITION", "3", "ARGUMENT_NAME", "JOB_ID", "ARGUMENT_TYPE", "VARCHAR2",
				"METADATA_ATTRIBUTE", "JOB_NAME", "HAS_DEFAULT_ANYDATA_VALUE", "FALSE"));
		int[] queries = { 0, 0, 0 };
		var reader = dependencyReader(queries, List.of(first, dependentJob("J2")), List.of(program, typed, metadata),
				List.of(Map.of("OWNER", "SHARED", "SCHEDULE_NAME", "SCHEDULE", "REPEAT_INTERVAL", "FREQ=DAILY",
						"START_DATE_TEXT", "2050-01-01T10:00:00.000000 Europe/Berlin +01:00")),
				false);
		Schema schema = new Schema("APP");
		schema.getEvents().addAll(reader.doGetAll(null, new ParametersContext(), null));
		assertArrayEquals(new int[] { 1, 1, 1 }, queries);
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		Event job = restored.getEvents().get("J1"), shared = restored.getEvents().get("J2");
		assertEquals(" override ", job.getSpecifics().get("oracle.scheduler.argument.1.value"));
		assertEquals(" default\n'quoted' ", job.getSpecifics().get("oracle.scheduler.program.argument.1.defaultValue"));
		assertEquals("AVAILABLE", job.getSpecifics().get("oracle.scheduler.program.status"));
		assertEquals("3", job.getSpecifics().get("oracle.scheduler.program.argumentCount"));
		assertEquals("JOB_NAME", job.getSpecifics().get("oracle.scheduler.program.argument.3.metadataAttribute"));
		assertEquals("SHARED.DO_WORK", job.getSpecifics().get("oracle.scheduler.program.PROGRAM_ACTION"));
		assertEquals("TRUE", job.getSpecifics().get("oracle.scheduler.program.argument.2.hasDefaultAnydataValue"));
		assertFalse(job.getSpecifics().containsKey("oracle.scheduler.program.argument.2.defaultValue"));
		assertEquals("FREQ=DAILY", job.getSpecifics().get("oracle.scheduler.schedule.REPEAT_INTERVAL"));
		assertTrue(
				job.getSpecifics().get("oracle.scheduler.schedule.START_DATE_TEXT").contains("Europe/Berlin +01:00"));
		assertTrue(job.getStatement().isEmpty());
		job.getSpecifics().put("oracle.scheduler.program.PROGRAM_ACTION", "changed");
		assertEquals("SHARED.DO_WORK", shared.getSpecifics().get("oracle.scheduler.program.PROGRAM_ACTION"));
	}

	@Test
	void missingAndUnresolvedDependenciesAreExplicitAndErrorsPropagate() {
		int[] queries = { 0, 0, 0 };
		var reader = dependencyReader(queries, List.of(dependentJob("J")), List.of(), List.of(), false);
		Event event = reader.doGetAll(null, new ParametersContext(), null).get(0);
		assertArrayEquals(new int[] { 1, 1, 1 }, queries);
		assertEquals("UNAVAILABLE", event.getSpecifics().get("oracle.scheduler.program.status"));
		assertEquals("UNAVAILABLE", event.getSpecifics().get("oracle.scheduler.schedule.status"));
		var unresolved = new java.util.HashMap<>(dependentJob("J"));
		unresolved.remove("PROGRAM_OWNER");
		unresolved.remove("SCHEDULE_OWNER");
		int[] noLookup = { 0, 0, 0 };
		Event missingOwner = dependencyReader(noLookup, List.of(unresolved), List.of(), List.of(), false)
				.doGetAll(null, new ParametersContext(), null).get(0);
		assertArrayEquals(new int[] { 1, 0, 0 }, noLookup);
		assertEquals("UNRESOLVED", missingOwner.getSpecifics().get("oracle.scheduler.program.status"));
		assertThrows(RuntimeException.class,
				() -> dependencyReader(new int[3], List.of(dependentJob("J")), List.of(), List.of(), true)
						.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void duplicateProgramArgumentsAndSchedulesAreRejected() {
		var program = Map.of("OWNER", "SHARED", "PROGRAM_NAME", "PROGRAM", "ENABLED", "FALSE", "ARGUMENT_POSITION", "1",
				"HAS_DEFAULT_ANYDATA_VALUE", "FALSE");
		assertThrows(RuntimeException.class, () -> dependencyReader(new int[3], List.of(dependentJob("J")),
				List.of(program, program), List.of(), false).doGetAll(null, new ParametersContext(), null));
		var schedule = Map.of("OWNER", "SHARED", "SCHEDULE_NAME", "SCHEDULE");
		assertThrows(RuntimeException.class, () -> dependencyReader(new int[3], List.of(dependentJob("J")), List.of(),
				List.of(schedule, schedule), false).doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void dependencyResolutionUsesOwnerAndNameTogether() {
		var first = new java.util.HashMap<>(dependentJob("J1"));
		first.putAll(Map.of("PROGRAM_OWNER", "A", "PROGRAM_NAME", "P"));
		var second = new java.util.HashMap<>(dependentJob("J2"));
		second.putAll(Map.of("PROGRAM_OWNER", "B", "PROGRAM_NAME", "Q"));
		var programs = List.of(Map.of("OWNER", "A", "PROGRAM_NAME", "Q", "ENABLED", "FALSE", "PROGRAM_ACTION", "WRONG"),
				Map.of("OWNER", "B", "PROGRAM_NAME", "Q", "ENABLED", "FALSE", "PROGRAM_ACTION", "RIGHT"));
		var jobs = dependencyReader(new int[3], List.of(first, second), programs, List.of(), false).doGetAll(null,
				new ParametersContext(), null);
		assertEquals("UNAVAILABLE", jobs.get(0).getSpecifics().get("oracle.scheduler.program.status"));
		assertEquals("RIGHT", jobs.get(1).getSpecifics().get("oracle.scheduler.program.PROGRAM_ACTION"));
	}
}
