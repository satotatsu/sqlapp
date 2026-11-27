/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransferNameMappingTest {
	@TempDir
	Path directory;
	private final TransferNameMappingIO io = new TransferNameMappingIO();
	private static final String MAPPING = """
			formatVersion: 1
			tables:
			  PUBLIC.PARENTS:
			    targetTable: "親テーブル"
			    columns: {ID: "親番号"}
			  PUBLIC.CHILDREN:
			    targetTable: "子テーブル"
			    columns: {ID: "子番号", PARENT_ID: "親番号"}
			""";

	@Test
	void strictYamlAndPinnedAtomicWrites() throws Exception {
		var saved = io.write(directory.resolve("mapping.yaml"), MAPPING, null);
		assertEquals(saved, io.read(saved.file()));
		assertEquals(saved.configuration(), io.validate(io.format(saved.configuration())));
		assertThrows(java.io.IOException.class, () -> io.write(saved.file(), MAPPING, null));
		var updated = io.write(saved.file(), MAPPING + "# changed\n", saved.fingerprint());
		assertNotEquals(saved.fingerprint(), updated.fingerprint());
		assertThrows(java.io.IOException.class, () -> io.write(saved.file(), MAPPING, saved.fingerprint()));
		for (String invalid : List.of(MAPPING + "unknown: true\n",
				MAPPING.replace("formatVersion: 1", "formatVersion: '1'"),
				MAPPING.replace("columns: {ID: \"親番号\"}", "columns: {ID: a, ID: b}"),
				MAPPING.replace("targetTable: \"親テーブル\"", "targetTable: other.table"),
				MAPPING.replace("targetTable: \"親テーブル\"", "targetTable: 123")))
			assertThrows(IllegalArgumentException.class, () -> io.validate(invalid), invalid);
	}

	@Test
	void schemaOnlyMapsKeysIndexesAndForeignKeysWithoutChangingSource() throws Exception {
		var source = database();
		var target = database();
		try (var c = source.getConnection(); var s = c.createStatement()) {
			s.execute("CREATE TABLE PARENTS(ID INTEGER PRIMARY KEY)");
			s.execute(
					"CREATE TABLE CHILDREN(ID INTEGER PRIMARY KEY, PARENT_ID INTEGER, CONSTRAINT FK_PARENT FOREIGN KEY(PARENT_ID) REFERENCES PARENTS(ID))");
			s.execute("CREATE UNIQUE INDEX IX_PARENT ON CHILDREN(PARENT_ID DESC)");
			s.execute("INSERT INTO PARENTS VALUES(7)");
		}
		io.write(directory.resolve("mapping.yaml"), MAPPING, null);
		var service = new TransferJobExecutionService();
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job(""), bindings, directory);
		assertTrue(review.prepared().warnings().stream().anyMatch(w -> w.contains("SCHEMA_MAPPING_EXPRESSIONS")));
		assertEquals(MAPPING,
				Files.readString(review.prepared().directory().resolve(TransferNameMappingIO.CAPTURED_FILE)));
		assertTrue(Files.readString(review.prepared().sourceSchemaFile()).contains("PARENT_ID"));
		var command = new TransferDatabaseCommand();
		command.setConfigurationFile(review.definitionFile().toFile());
		command.setWorkspaceDirectory(directory.toFile());
		command.setSourceDataSource(source);
		command.setDataSource(target);
		command.setCloseDataSource(false);
		command.setAllowExecution(true);
		command.setExpectedConfigurationFingerprint(review.prepared().job().fingerprint());
		command.run();
		assertNotNull(command.getResultFile());
		try (var c = target.getConnection()) {
			try (var r = c.getMetaData().getImportedKeys(null, "PUBLIC", "子テーブル")) {
				assertTrue(r.next());
				assertEquals("親番号", r.getString("FKCOLUMN_NAME"));
				assertEquals("親テーブル", r.getString("PKTABLE_NAME"));
				assertEquals("親番号", r.getString("PKCOLUMN_NAME"));
			}
			try (var r = c.getMetaData().getPrimaryKeys(null, "PUBLIC", "子テーブル")) {
				assertTrue(r.next());
				assertEquals("子番号", r.getString("COLUMN_NAME"));
			}
			try (var r = c.getMetaData().getIndexInfo(null, "PUBLIC", "子テーブル", true, false)) {
				boolean found = false;
				while (r.next())
					if ("IX_PARENT".equals(r.getString("INDEX_NAME"))) {
						found = true;
						assertEquals("親番号", r.getString("COLUMN_NAME"));
						assertFalse(r.getBoolean("NON_UNIQUE"));
					}
				assertTrue(found);
			}
			try (var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM \"親テーブル\"")) {
				r.next();
				assertEquals(0, r.getInt(1));
			}
		}
		try (var c = source.getConnection();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT ID FROM PARENTS")) {
			assertTrue(r.next());
			assertEquals(7, r.getInt(1));
		}
	}

	@Test
	void changedOriginalOrCapturedMappingPreventsAnyDdl() throws Exception {
		for (boolean captured : List.of(false, true)) {
			var source = database();
			var target = database();
			try (var c = source.getConnection(); var s = c.createStatement()) {
				s.execute("CREATE TABLE PARENTS(ID INTEGER)");
			}
			Files.writeString(directory.resolve("mapping.yaml"),
					"formatVersion: 1\ntables: {PARENTS: {targetTable: NEW_PARENT}}\n");
			var file = job("");
			Files.writeString(file, Files.readString(file).replace("[PARENTS, CHILDREN]", "[PARENTS]"));
			var service = new TransferJobExecutionService();
			var bindings = Map.of("from", source, "to", target);
			var review = service.review(file, bindings, directory);
			Path changed = captured ? review.prepared().directory().resolve(TransferNameMappingIO.CAPTURED_FILE)
					: directory.resolve("mapping.yaml");
			Files.writeString(changed, Files.readString(changed) + "# changed\n");
			assertThrows(TransferJobExecutionService.ExecutionException.class, () -> service.execute(review, bindings));
			try (var c = target.getConnection();
					var r = c.getMetaData().getTables(null, "PUBLIC", "NEW_PARENT", null)) {
				assertFalse(r.next());
			}
		}
	}

	@Test
	void invalidMappingsAndStrictPolicyFailBeforeDdl() throws Exception {
		var source = database();
		var target = database();
		try (var c = source.getConnection(); var s = c.createStatement()) {
			s.execute("CREATE TABLE PARENTS(ID INTEGER PRIMARY KEY)");
			s.execute(
					"CREATE TABLE CHILDREN(ID INTEGER, PARENT_ID INTEGER, FOREIGN KEY(PARENT_ID) REFERENCES PARENTS(ID))");
		}
		var service = new TransferJobExecutionService();
		var bindings = Map.of("from", source, "to", target);
		for (String text : List.of(MAPPING.replace("子テーブル", "親テーブル"), MAPPING.replace("子番号", "親番号"),
				MAPPING.replace("PARENT_ID:", "MISSING:"))) {
			Files.writeString(directory.resolve("mapping.yaml"), text);
			assertThrows(TransferSchemaPreparation.ValidationException.class,
					() -> service.review(job(""), bindings, directory));
		}
		Files.writeString(directory.resolve("mapping.yaml"), MAPPING);
		assertTrue(assertThrows(TransferSchemaPreparation.ValidationException.class,
				() -> service.review(job("compatibilityPolicy: ERROR\n"), bindings, directory)).getMessage()
				.startsWith(TransferSchemaChangeChecks.MAPPING_EXPRESSIONS));
		var file = job("");
		Files.writeString(file, Files.readString(file).replace("[PARENTS, CHILDREN]", "[CHILDREN]"));
		Files.writeString(directory.resolve("mapping.yaml"),
				"formatVersion: 1\ntables: {CHILDREN: {targetTable: COPY}}\n");
		assertThrows(TransferSchemaPreparation.ValidationException.class,
				() -> service.review(file, bindings, directory));
	}

	private Path job(String extra) throws Exception {
		return Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: [PARENTS, CHILDREN]\nschemaMode: CREATE_MISSING\ndataMode: SCHEMA_ONLY\nmappingFile: mapping.yaml\n"
						+ extra);
	}

	@Test
	void swapsRetainCompositeKeyOrderAndOriginalModel() throws Exception {
		var source = database();
		try (var c = source.getConnection(); var s = c.createStatement()) {
			s.execute("CREATE TABLE PARENTS(A INTEGER, B INTEGER, PRIMARY KEY(A,B))");
			s.execute("CREATE TABLE CHILDREN(A INTEGER, B INTEGER, FOREIGN KEY(A,B) REFERENCES PARENTS(A,B))");
		}
		try (var c = source.getConnection()) {
			var schema = TransferJobPreparationService.readSchema(c, TransferJobPreparationService.dialect(c),
					"PUBLIC");
			var original = schema.clone();
			var configuration = io.validate("""
					formatVersion: 1
					tables:
					  PARENTS: {targetTable: CHILDREN, columns: {A: B, B: A}}
					  CHILDREN: {targetTable: PARENTS, columns: {A: B, B: A}}
					""");
			var projected = TransferNameMappingProjection.project(schema,
					TransferSchemaOnlyPreparation.select(schema, List.of("PARENTS", "CHILDREN")), configuration);
			assertEquals(original, schema);
			var parent = projected.schema().getTables().get("CHILDREN");
			assertEquals(List.of("B", "A"), parent.getPrimaryKeyConstraint().getColumns().stream()
					.map(com.sqlapp.data.schemas.ReferenceColumn::getName).toList());
			var fk = projected.schema().getTables().get("PARENTS").getConstraints().stream()
					.filter(com.sqlapp.data.schemas.ForeignKeyConstraint.class::isInstance)
					.map(com.sqlapp.data.schemas.ForeignKeyConstraint.class::cast).findFirst().orElseThrow();
			assertEquals("CHILDREN", fk.getRelatedTableName());
			assertEquals(List.of("B", "A"),
					fk.getRelatedColumns().stream().map(com.sqlapp.data.schemas.ReferenceColumn::getName).toList());
		}
	}

	@Test
	void differencesUseExplicitMappedNamesAndRemainIdempotent() throws Exception {
		var source = database();
		var target = database();
		try (var c = source.getConnection(); var s = c.createStatement()) {
			s.execute("CREATE TABLE PARENTS(ID INTEGER, EXTRA INTEGER)");
		}
		try (var c = target.getConnection(); var s = c.createStatement()) {
			s.execute("CREATE TABLE COPY(ID INTEGER)");
		}
		Files.writeString(directory.resolve("mapping.yaml"),
				"formatVersion: 1\ntables: {PARENTS: {targetTable: COPY, columns: {EXTRA: ADDED}}}\n");
		var file = job("");
		Files.writeString(file, Files.readString(file).replace("[PARENTS, CHILDREN]", "[PARENTS]")
				.replace("CREATE_MISSING", "APPLY_DIFFERENCES"));
		var service = new TransferJobExecutionService();
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(file, bindings, directory);
		assertFalse(review.prepared().schemaPreparation().statements().isEmpty());
		service.execute(review, bindings);
		try (var c = target.getConnection(); var r = c.getMetaData().getColumns(null, "PUBLIC", "COPY", "ADDED")) {
			assertTrue(r.next());
		}
		assertTrue(service.review(file, bindings, directory).prepared().schemaPreparation().statements().isEmpty());
	}

	private static JDBCDataSource database() {
		var ds = new JDBCDataSource();
		ds.setUrl("jdbc:hsqldb:mem:mapping_" + UUID.randomUUID());
		ds.setUser("SA");
		ds.setPassword("");
		return ds;
	}
}
