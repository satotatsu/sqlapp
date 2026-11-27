/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Actual copy and verification between disposable embedded databases. */
class TransferDirectNameMappingTest {
	@TempDir
	Path directory;
	private static final String MAPPING = """
			formatVersion: 1
			tables:
			  PARENTS: {targetTable: "親", columns: {A: B, B: A}}
			  CHILDREN: {targetTable: "子", columns: {ID: "行ID", A: B, B: A, NOTE: "備考"}}
			""";

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void copiesAndVerifiesMappedCompositeKeysSwappedColumnsAndForeignKeys(boolean createMissing) throws Exception {
		var source = database();
		var target = database();
		initialize(source);
		String targetSchema = createMissing ? "ARCHIVE" : "PUBLIC";
		if (createMissing)
			sql(target, "CREATE SCHEMA ARCHIVE AUTHORIZATION DBA");
		if (!createMissing) {
			sql(target, "CREATE TABLE \"親\"(A INTEGER NOT NULL,B INTEGER NOT NULL,PRIMARY KEY(B,A))",
					"CREATE TABLE \"子\"(\"行ID\" INTEGER NOT NULL PRIMARY KEY,A INTEGER,B INTEGER,\"備考\" VARCHAR(40),FOREIGN KEY(B,A) REFERENCES \"親\"(B,A))");
		}
		var file = job(createMissing);
		Files.writeString(file, Files.readString(file) + "targetSchema: " + targetSchema + "\n");
		var service = new TransferJobExecutionService();
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(file, bindings, directory);
		assertTrue(Files.readString(review.prepared().dataJob().file()).startsWith("# transfer-name-mapping: sha256:"));
		assertEquals(MAPPING,
				Files.readString(review.prepared().directory().resolve(TransferNameMappingIO.CAPTURED_FILE)));
		var result = service.execute(review, bindings);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
		assertEquals(4, result.copyResult().getProcessedRows());
		assertTrue(result.verificationResult().isMatch());
		try (var c = target.getConnection();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT \"行ID\",B,A,\"備考\" FROM " + targetSchema + ".\"子\" ORDER BY \"行ID\"")) {
			assertTrue(r.next());
			assertEquals(100, r.getInt(1));
			assertEquals(1, r.getInt(2));
			assertEquals(10, r.getInt(3));
			assertEquals("日本語", r.getString(4));
			assertTrue(r.next());
			assertEquals(101, r.getInt(1));
			assertEquals(2, r.getInt(2));
			assertEquals(20, r.getInt(3));
			assertNull(r.getString(4));
			assertFalse(r.next());
		}
		try (var c = target.getConnection(); var r = c.getMetaData().getImportedKeys(null, targetSchema, "子")) {
			assertTrue(r.next());
			assertEquals("B", r.getString("FKCOLUMN_NAME"));
			assertEquals("親", r.getString("PKTABLE_NAME"));
			assertEquals("B", r.getString("PKCOLUMN_NAME"));
			assertTrue(r.next());
			assertEquals("A", r.getString("FKCOLUMN_NAME"));
			assertEquals("A", r.getString("PKCOLUMN_NAME"));
		}
		assertThrows(TransferJobExecutionService.ExecutionException.class, () -> service.execute(review, bindings));
		try (var c = source.getConnection();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT NOTE FROM CHILDREN WHERE ID=100")) {
			assertTrue(r.next());
			assertEquals("日本語", r.getString(1));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "original", "captured", "both", "bulk" })
	void editedMappingOrGeneratedJobCannotExecuteReviewedPlan(String change) throws Exception {
		var source = database();
		var target = database();
		initialize(source);
		var service = new TransferJobExecutionService();
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job(true), bindings, directory);
		var captured = review.prepared().directory().resolve(TransferNameMappingIO.CAPTURED_FILE);
		if (change.equals("original") || change.equals("both"))
			Files.writeString(directory.resolve("mapping.yaml"), MAPPING + "# changed\n");
		if (change.equals("captured") || change.equals("both"))
			Files.writeString(captured, MAPPING + "# changed\n");
		if (change.equals("bulk"))
			Files.writeString(review.prepared().dataJob().file(),
					Files.readString(review.prepared().dataJob().file()) + "# changed\n");
		var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.REVALIDATION, failure.outcome().phase());
		try (var c = target.getConnection(); var r = c.getMetaData().getTables(null, "PUBLIC", "親", null)) {
			assertFalse(r.next());
		}
	}

	private Path job(boolean createMissing) throws Exception {
		Files.writeString(directory.resolve("mapping.yaml"), MAPPING);
		return Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: [CHILDREN, PARENTS]\nschemaMode: "
						+ (createMissing ? "CREATE_MISSING" : "REQUIRE_EXISTING")
						+ "\nmappingFile: mapping.yaml\nchunkSize: 1\n");
	}

	private static void initialize(JDBCDataSource source) throws Exception {
		sql(source, "CREATE TABLE PARENTS(A INTEGER NOT NULL,B INTEGER NOT NULL,PRIMARY KEY(A,B))",
				"CREATE TABLE CHILDREN(ID INTEGER NOT NULL PRIMARY KEY,A INTEGER,B INTEGER,NOTE VARCHAR(40),FOREIGN KEY(A,B) REFERENCES PARENTS(A,B))",
				"INSERT INTO PARENTS VALUES(1,10),(2,20)",
				"INSERT INTO CHILDREN VALUES(100,1,10,'日本語'),(101,2,20,NULL)");
	}

	private static void sql(JDBCDataSource ds, String... commands) throws Exception {
		try (var c = ds.getConnection(); var s = c.createStatement()) {
			for (String command : commands)
				s.execute(command);
		}
	}

	private static JDBCDataSource database() {
		var ds = new JDBCDataSource();
		ds.setUrl("jdbc:hsqldb:mem:direct_names_" + UUID.randomUUID());
		ds.setUser("SA");
		ds.setPassword("");
		return ds;
	}
}
