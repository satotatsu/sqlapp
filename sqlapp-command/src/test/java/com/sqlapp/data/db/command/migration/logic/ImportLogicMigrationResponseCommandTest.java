/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Eligibility;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.ObjectEntry;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationResponseModel.ImportStatus;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationResponseModel.ImportedObject;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.exceptions.CommandException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Imports responses into request work directories produced by the real M1
 * command, including objects that were not requested.
 */
class ImportLogicMigrationResponseCommandTest {
	private static final String FUNCTION_SQL = """
			CREATE FUNCTION app.orders_audit_fn()
			RETURNS trigger
			LANGUAGE plpgsql
			AS $$
			BEGIN
			  INSERT INTO app.order_log(id) VALUES (NEW.id); -- DROP TABLE app.orders;
			  RETURN NEW;
			END;
			$$;
			""";
	private static final String TRIGGER_SQL = """
			CREATE TRIGGER orders_audit
			AFTER INSERT OR UPDATE ON app.orders
			FOR EACH ROW
			EXECUTE FUNCTION app.orders_audit_fn();
			""";

	@TempDir
	Path directory;

	private final JsonMapper json = JsonMapper.builder().build();
	private Path work;
	private String requestId;
	private Map<String, ObjectEntry> objects;

	private static Table table(final String name, final String... columns) {
		final Table table = new Table(name);
		for (final String column : columns) {
			table.getColumns().add(new Column(column).setDataType(DataType.NUMERIC).setLength(10L).setScale(0));
		}
		return table;
	}

	private static Trigger trigger(final String name, final String table, final String timing, final String events,
			final String definition) {
		final Trigger trigger = new Trigger(name);
		if (table != null) {
			trigger.setTableSchemaName("APP");
			trigger.setTableName(table);
		}
		trigger.setActionTiming(timing);
		trigger.setActionOrientation(table == null ? "EVENT" : "ROW");
		trigger.addEventManipulation(events);
		trigger.setDefinition(definition);
		return trigger;
	}

	/**
	 * Runs the real M1 command: REQUESTED, TARGET_TABLE_UNRESOLVED and a trigger
	 * without table.
	 */
	@BeforeEach
	void generateRequest() throws Exception {
		final Schema source = new Schema("APP").setProductName("Oracle").setProductMajorVersion(19);
		source.getTables().add(table("ORDERS", "ID", "AMOUNT"));
		source.getTables().add(table("ITEMS", "ID"));
		source.getTables().add(table("LEGACY", "ID"));
		source.getTriggers()
				.add(trigger("ORDERS_AUDIT", "ORDERS", "AFTER", "INSERT OR UPDATE",
						"CREATE OR REPLACE TRIGGER APP.ORDERS_AUDIT AFTER INSERT OR UPDATE ON APP.ORDERS FOR EACH ROW\n"
								+ "BEGIN INSERT INTO APP.ORDER_LOG(ID) VALUES (:NEW.ID); END;"));
		source.getTriggers()
				.add(trigger("ITEMS_ID", "ITEMS", "BEFORE", "INSERT",
						"CREATE OR REPLACE TRIGGER APP.ITEMS_ID BEFORE INSERT ON APP.ITEMS FOR EACH ROW\n"
								+ "BEGIN :NEW.ID := ITEMS_SEQ.NEXTVAL; END;"));
		source.getTriggers().add(trigger("LEGACY_TRG", "LEGACY", "BEFORE", "INSERT",
				"CREATE OR REPLACE TRIGGER APP.LEGACY_TRG BEFORE INSERT ON APP.LEGACY FOR EACH ROW BEGIN NULL; END;"));
		source.getTriggers().add(trigger("LOGON_TRG", null, "AFTER", "LOGON",
				"CREATE OR REPLACE TRIGGER APP.LOGON_TRG AFTER LOGON ON SCHEMA BEGIN NULL; END;"));
		final File sourceFile = directory.resolve("oracle.xml").toFile();
		source.writeXml(sourceFile);
		final Schema target = new Schema("app").setProductName("PostgreSQL").setProductMajorVersion(16);
		target.getTables().add(table("orders", "id", "amount"));
		target.getTables().add(table("items", "id"));
		final File targetFile = directory.resolve("postgres.xml").toFile();
		target.writeXml(targetFile);
		final var request = new GenerateLogicMigrationRequestCommand();
		request.setSourceSchemaFile(sourceFile);
		request.setTargetSchemaFile(targetFile);
		work = directory.resolve("work");
		request.setWorkDir(work.toFile());
		request.run();
		requestId = request.getResult().requestId();
		objects = request.getResult().manifest().objects().stream()
				.collect(Collectors.toMap(o -> o.identity().name(), Function.identity()));
		assertEquals(Eligibility.REQUESTED, objects.get("ORDERS_AUDIT").eligibility());
		assertEquals(Eligibility.REQUESTED, objects.get("ITEMS_ID").eligibility());
		assertEquals(Eligibility.TARGET_TABLE_UNRESOLVED, objects.get("LEGACY_TRG").eligibility());
		assertEquals(Eligibility.UNSUPPORTED_FORM, objects.get("LOGON_TRG").eligibility());
		assertNull(objects.get("LOGON_TRG").identity().table());
	}

	private String id(final String name) {
		return objects.get(name).sourceId();
	}

	private void write(final String file, final String content) throws Exception {
		Files.writeString(work.resolve("response").resolve(file), content, StandardCharsets.UTF_8);
	}

	private String artifacts(final String functionFile, final String triggerFile, final String triggerDepends) {
		return """
				"artifacts": [
				  {"artifactId": "a1", "kind": "FUNCTION", "schema": "app", "name": "orders_audit_fn",
				   "argumentTypes": [], "file": "%s", "dependsOn": []},
				  {"artifactId": "a2", "kind": "TRIGGER", "schema": "app", "name": "orders_audit",
				   "table": {"catalog": null, "schema": "app", "name": "orders"},
				   "file": "%s", "dependsOn": [%s]}
				]""".formatted(functionFile, triggerFile, triggerDepends);
	}

	private String results(final String auditStatus) {
		return """
				"results": [
				  {"sourceId": "%s", "status": "%s", "artifactIds": ["a1", "a2"], "notes": []},
				  {"sourceId": "%s", "status": "UNRESOLVED", "artifactIds": [],
				   "notes": ["Numbering should move to IDENTITY"],
				   "suggestion": {"type": "REPLACE_WITH_SCHEMA", "reason": "Sequence-only trigger",
				    "targetColumn": {"catalog": null, "schema": "app", "table": "items", "name": "id"},
				    "checks": ["Next value after loading"]}}
				],""".formatted(id("ORDERS_AUDIT"), auditStatus, id("ITEMS_ID"));
	}

	private void writeResponse(final String body) throws Exception {
		write("response.json", """
				{
				  "format": "sqlapp-logic-response",
				  "formatVersion": 1,
				  "requestId": "%s",
				%s
				}
				""".formatted(requestId, body));
	}

	private void writeValidResponse() throws Exception {
		write("a1.sql", FUNCTION_SQL);
		write("a2.sql", TRIGGER_SQL);
		writeResponse(results("CONVERTED") + "\n" + artifacts("a1.sql", "a2.sql", "\"a1\""));
	}

	private ImportLogicMigrationResponseCommand command() {
		final var command = new ImportLogicMigrationResponseCommand();
		command.setWorkDir(work.toFile());
		return command;
	}

	private Map<String, ImportedObject> run() {
		final var command = command();
		command.run();
		return command.getReport().objects().stream()
				.collect(Collectors.toMap(o -> o.identity().name(), Function.identity()));
	}

	private String failure() {
		final String message = assertThrows(CommandException.class, command()::run).getMessage();
		assertFalse(Files.exists(work.resolve("import")), "a failed import must not publish import/");
		return message;
	}

	@Test
	void importsResponseIntoTheRequestWrittenByM1() throws Exception {
		writeValidResponse();
		final byte[] responseBefore = Files.readAllBytes(work.resolve("response/response.json"));
		final byte[] targetBefore = Files.readAllBytes(work.resolve("request/target.xml"));
		final var command = command();
		command.run();
		final var report = command.getReport();
		final Map<String, ImportedObject> imported = report.objects().stream()
				.collect(Collectors.toMap(o -> o.identity().name(), Function.identity()));

		assertEquals(4, imported.size(), "the whole inventory is kept");
		final ImportedObject audit = imported.get("ORDERS_AUDIT");
		assertEquals(ImportStatus.ACCEPTED_CANDIDATE, audit.status(), String.valueOf(audit));
		assertEquals(2, audit.artifacts().size());
		assertTrue(audit.artifacts().stream().allMatch(a -> a.problems().isEmpty()));
		assertTrue(audit.artifacts().stream().allMatch(a -> a.generatedFingerprint().matches("sha256:[0-9a-f]{64}")));
		assertEquals("orders_audit_fn", audit.artifacts().get(1).declaration().calledFunctionName());
		final ImportedObject items = imported.get("ITEMS_ID");
		assertEquals(ImportStatus.UNRESOLVED, items.status());
		assertEquals("REPLACE_WITH_SCHEMA", items.suggestion().type().name());
		assertEquals(ImportStatus.NOT_REQUESTED, imported.get("LEGACY_TRG").status());
		assertEquals(Eligibility.TARGET_TABLE_UNRESOLVED, imported.get("LEGACY_TRG").eligibility());
		assertEquals(ImportStatus.NOT_REQUESTED, imported.get("LOGON_TRG").status());
		assertNull(imported.get("LOGON_TRG").identity().table());
		assertEquals(List.of("a1", "a2"), report.applyOrder());

		final Path importDir = work.resolve("import");
		for (final String name : List.of("import.json", "target-with-logic.xml", "logic.sql", "response/response.json",
				"response/a1.sql", "response/a2.sql")) {
			assertTrue(Files.isRegularFile(importDir.resolve(name)), name);
		}
		assertArrayEquals(responseBefore, Files.readAllBytes(importDir.resolve("response/response.json")));
		assertArrayEquals(responseBefore, Files.readAllBytes(work.resolve("response/response.json")));
		assertArrayEquals(targetBefore, Files.readAllBytes(work.resolve("request/target.xml")));
		final JsonNode evidence = json.readTree(importDir.resolve("import.json").toFile());
		assertEquals("sqlapp-logic-import", evidence.get("format").asString());
		assertEquals(ImportLogicMigrationResponseCommand.fingerprint(importDir.resolve("logic.sql")),
				evidence.get("outputs").get("sql").get("fingerprint").asString());
		assertEquals(ImportLogicMigrationResponseCommand.fingerprint(work.resolve("request/manifest.json")),
				evidence.get("requestManifest").get("fingerprint").asString());

		final Schema merged = SchemaUtils.readXml(importDir.resolve("target-with-logic.xml").toFile());
		assertEquals(1, merged.getFunctions().size());
		assertEquals(1, merged.getTriggers().size());
		assertEquals("orders", merged.getTriggers().get(0).getTableName());
		final String sql = Files.readString(importDir.resolve("logic.sql"));
		assertTrue(sql.indexOf("CREATE FUNCTION app.orders_audit_fn") < sql.indexOf("CREATE TRIGGER orders_audit"),
				sql);
		assertTrue(sql.contains("-- DROP TABLE app.orders;"), "the body is kept as returned");

		final byte[] evidenceBefore = Files.readAllBytes(importDir.resolve("import.json"));
		assertTrue(assertThrows(CommandException.class, command()::run).getMessage().contains("already imported"));
		assertArrayEquals(evidenceBefore, Files.readAllBytes(importDir.resolve("import.json")));
	}

	@Test
	void reportsTriggerFormChangesAndMissingResults() throws Exception {
		write("a1.sql", FUNCTION_SQL);
		write("a2.sql", TRIGGER_SQL.replace("AFTER INSERT OR UPDATE", "BEFORE INSERT"));
		writeResponse("""
				  "results": [
				    {"sourceId": "%s", "status": "CONVERTED", "artifactIds": ["a1", "a2"], "notes": []}
				  ],
				""".formatted(id("ORDERS_AUDIT")) + artifacts("a1.sql", "a2.sql", "\"a1\""));
		final var command = command();
		command.run();
		final var imported = command.getReport().objects().stream()
				.collect(Collectors.toMap(o -> o.identity().name(), Function.identity()));
		assertEquals(ImportStatus.ACCEPTED_CANDIDATE, imported.get("ORDERS_AUDIT").status());
		assertEquals(ImportStatus.MISSING, imported.get("ITEMS_ID").status());
		assertTrue(command.getReport().diagnostics().stream().anyMatch(d -> d.code().equals("TRIGGER_FORM_CHANGED")));
	}

	@Test
	void rejectsOneObjectAndKeepsTheOthers() throws Exception {
		write("a1.sql", FUNCTION_SQL.replace("LANGUAGE plpgsql", "LANGUAGE plpgsql SECURITY DEFINER"));
		write("a2.sql", TRIGGER_SQL.replace("ON app.orders", "ON app.items"));
		writeResponse(results("CONVERTED") + "\n" + artifacts("a1.sql", "a2.sql", "\"a1\""));
		final var imported = run();
		final ImportedObject audit = imported.get("ORDERS_AUDIT");
		assertEquals(ImportStatus.REJECTED, audit.status());
		final String problems = audit.artifacts().stream().flatMap(a -> a.problems().stream())
				.collect(Collectors.joining("\n"));
		assertTrue(problems.contains("SECURITY DEFINER"), problems);
		assertTrue(problems.contains("app.items"), problems);
		assertEquals(ImportStatus.UNRESOLVED, imported.get("ITEMS_ID").status());
		final Schema merged = SchemaUtils.readXml(work.resolve("import/target-with-logic.xml").toFile());
		assertTrue(merged.getFunctions().isEmpty(), "rejected objects are not added to the model");
		assertTrue(merged.getTriggers().isEmpty());
		assertTrue(Files.readString(work.resolve("import/logic.sql")).isBlank());
	}

	@Test
	void rejectsUnqualifiedOrUnrelatedFunctionsAndExtraStatements() throws Exception {
		write("a1.sql", FUNCTION_SQL + "\nDROP TABLE app.orders;");
		write("a2.sql", TRIGGER_SQL.replace("app.orders_audit_fn()", "orders_audit_fn()"));
		writeResponse(results("CONVERTED") + "\n" + artifacts("a1.sql", "a2.sql", "\"a1\""));
		final ImportedObject audit = run().get("ORDERS_AUDIT");
		assertEquals(ImportStatus.REJECTED, audit.status());
		assertTrue(audit.artifacts().get(0).problems().getFirst().contains("exactly one"));
		assertTrue(audit.artifacts().get(1).problems().stream().anyMatch(p -> p.contains("schema-qualified")));
	}

	@Test
	void requiresTheHelperFunctionInDependsOn() throws Exception {
		write("a1.sql", FUNCTION_SQL);
		write("a2.sql", TRIGGER_SQL);
		writeResponse(results("CONVERTED") + "\n" + artifacts("a1.sql", "a2.sql", ""));
		final ImportedObject audit = run().get("ORDERS_AUDIT");
		assertEquals(ImportStatus.REJECTED, audit.status());
		assertTrue(audit.artifacts().get(1).problems().getFirst().contains("dependsOn"));
	}

	@Test
	void failsForResultsOfObjectsThatWereNotRequested() throws Exception {
		writeValidResponse();
		final String text = Files.readString(work.resolve("response/response.json"));
		write("response.json", text.replace("\"results\": [", """
				"results": [
				    {"sourceId": "%s", "status": "UNRESOLVED", "artifactIds": [], "notes": ["n"]},"""
				.formatted(id("LEGACY_TRG"))));
		assertTrue(failure().contains("was not requested (TARGET_TABLE_UNRESOLVED)"));
	}

	@Test
	void failsForAnotherRequestUnknownFieldsAndDuplicateKeys() throws Exception {
		writeValidResponse();
		final String text = Files.readString(work.resolve("response/response.json"));
		write("response.json", text.replace(requestId, "0000000000000000"));
		assertTrue(failure().contains("belongs to request"));
		write("response.json", text.replace("\"formatVersion\": 1,", "\"formatVersion\": 1, \"verified\": true,"));
		assertTrue(failure().contains("not a valid Response"));
		write("response.json", text.replace("\"formatVersion\": 1,", "\"formatVersion\": 1, \"formatVersion\": 1,"));
		assertTrue(failure().contains("not a valid Response"));
	}

	@Test
	void failsWhenTheRequestChangedAfterItWasWritten() throws Exception {
		writeValidResponse();
		Files.writeString(work.resolve("request/target.xml"), "\n", StandardCharsets.UTF_8,
				java.nio.file.StandardOpenOption.APPEND);
		assertTrue(failure().contains("request/target.xml does not match"));
	}

	@Test
	void failsForSharedArtifactsCyclesAndFilesOutsideTheResponse() throws Exception {
		write("a1.sql", FUNCTION_SQL);
		write("a2.sql", TRIGGER_SQL);
		writeResponse("""
				  "results": [
				    {"sourceId": "%s", "status": "CONVERTED", "artifactIds": ["a1", "a2"], "notes": []},
				    {"sourceId": "%s", "status": "UNRESOLVED", "artifactIds": ["a1"], "notes": ["n"]}
				  ],
				""".formatted(id("ORDERS_AUDIT"), id("ITEMS_ID")) + artifacts("a1.sql", "a2.sql", "\"a1\""));
		assertTrue(failure().contains("helper objects cannot be shared"));

		writeResponse(results("CONVERTED") + "\n" + artifacts("a1.sql", "a2.sql", "\"a2\""));
		assertTrue(failure().contains("Circular dependsOn"));

		writeResponse(results("CONVERTED") + "\n" + artifacts("../a1.sql", "a2.sql", "\"a1\""));
		assertTrue(failure().contains("plain .sql file name"));

		writeValidResponse();
		write("notes.txt", "extra");
		assertTrue(failure().contains("Unexpected entry in response/: notes.txt"));
	}

	@Test
	void failsWhenTheRequestStateIsMissing() throws Exception {
		writeValidResponse();
		Files.delete(work.resolve("request-state.json"));
		assertTrue(failure().contains("request-state.json is missing"));
	}
}
