/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransferSchemaDifferenceTest {
	@TempDir
	Path directory;
	private final TransferJobExecutionService service = new TransferJobExecutionService();

	@Test
	void hsqlDefinitionsProducePostgresAlterUsingTargetDialectWithoutExternalDatabase() throws Exception {
		var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("PostgreSQL", 16, 0, null);
		var actual = new com.sqlapp.data.schemas.Schema("public").setProductName("PostgreSQL");
		var old = new com.sqlapp.data.schemas.Table("DOCUMENTS");
		old.getColumns().add("ID", column -> column.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		actual.getTables().add(old);
		var source = old.clone();
		source.getColumns().add("CONTENT", column -> column.setDataType(com.sqlapp.data.db.datatype.DataType.BLOB));
		var target = source.clone().setSchemaName("public");
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var plan = TransferSchemaDifferencePreparation.compare(actual,
				List.of(new TransferSchemaPreparation.Selection("documents", source, target)),
				Map.of("DOCUMENTS", target), registry, "fixture", List.of("Source and target products differ"),
				TransferJobConfiguration.CompatibilityPolicy.WARN);
		var ddl = plan.statements().stream().map(TransferSchemaPreparation.Statement::sql)
				.collect(java.util.stream.Collectors.joining("\n")).toUpperCase(Locale.ROOT);
		assertTrue(ddl.contains("ALTER TABLE"), ddl);
		assertTrue(ddl.contains("BYTEA"), ddl);
		assertFalse(ddl.contains("BLOB"), ddl);
		assertTrue(plan.warnings().contains("Source and target products differ"));
	}

	@Test
	void addsColumnAndMissingTableWithoutCopyingRowsOrChangingUnselectedTables() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER, NAME VARCHAR(40))", "INSERT INTO ITEMS VALUES(99,'source')",
				"CREATE TABLE NEW_TABLE(ID INTEGER)", "CREATE TABLE UNSELECTED(ID INTEGER)");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER)", "INSERT INTO ITEMS VALUES(5)",
				"CREATE TABLE UNSELECTED(KEEP_VALUE INTEGER)", "INSERT INTO UNSELECTED VALUES(7)");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[ITEMS, NEW_TABLE]", ""), bindings, directory);
		var sql = ddl(review);
		assertTrue(sql.contains("ALTER TABLE"), sql);
		assertTrue(sql.contains("ADD COLUMN"), sql);
		assertFalse(sql.contains("UNSELECTED"), sql);
		assertFalse(TransferSchemaChangeChecks.executionBlocked(review.prepared().job().configuration(),
				review.prepared().schemaPreparation()));
		assertFalse(column(target, "ITEMS", "NAME"));
		var result = service.execute(review, bindings);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
		assertTrue(column(target, "ITEMS", "NAME"));
		assertTrue(column(target, "NEW_TABLE", "ID"));
		assertTrue(column(target, "UNSELECTED", "KEEP_VALUE"));
		try (var c = target.getConnection();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT ID,NAME FROM ITEMS")) {
			assertTrue(r.next());
			assertEquals(5, r.getInt(1));
			assertNull(r.getString(2));
			assertFalse(r.next());
		}
		assertNull(result.copyResult());
		assertTrue(result.committedProgress().isEmpty());
	}

	@Test
	void addsForeignKeyAndIndexToExistingTablesWithTheirRowsPreserved() throws Exception {
		var source = database();
		var target = database();
		for (var ds : List.of(source, target))
			sql(ds, "CREATE TABLE PARENT(ID INTEGER, CONSTRAINT PK_PARENT PRIMARY KEY(ID))",
					"CREATE TABLE CHILD(ID INTEGER, PARENT_ID INTEGER)");
		sql(source, "ALTER TABLE CHILD ADD CONSTRAINT FK_PARENT FOREIGN KEY(PARENT_ID) REFERENCES PARENT(ID)",
				"CREATE INDEX CHILD_LOOKUP ON CHILD(ID)");
		sql(target, "INSERT INTO PARENT VALUES(42)", "INSERT INTO CHILD VALUES(7,42)");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[CHILD,PARENT]", "allowDestructiveSchemaChanges: true\n"), bindings,
				directory);
		assertTrue(ddl(review).contains("FK_PARENT"));
		assertTrue(ddl(review).contains("CHILD_LOOKUP"));
		service.execute(review, bindings);
		assertThrows(SQLException.class, () -> sql(target, "INSERT INTO CHILD VALUES(8,999)"));
		try (var c = target.getConnection();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT ID FROM CHILD")) {
			assertTrue(r.next());
			assertEquals(7, r.getInt(1));
			assertFalse(r.next());
		}
	}

	@Test
	void altersExistingParentKeyBeforeCreatingItsMissingChildTable() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE PARENT(ID INTEGER, CONSTRAINT PK_PARENT PRIMARY KEY(ID))",
				"CREATE TABLE CHILD(PARENT_ID INTEGER, CONSTRAINT FK_PARENT FOREIGN KEY(PARENT_ID) REFERENCES PARENT(ID))");
		sql(target, "CREATE TABLE PARENT(ID INTEGER)", "INSERT INTO PARENT VALUES(42)");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[CHILD,PARENT]", "allowDestructiveSchemaChanges: true\n"), bindings,
				directory);
		var result = service.execute(review, bindings);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
		assertTrue(column(target, "CHILD", "PARENT_ID"));
		sql(target, "INSERT INTO CHILD VALUES(42)");
		assertThrows(SQLException.class, () -> sql(target, "INSERT INTO CHILD VALUES(999)"));
	}

	@Test
	void failedAlterRetainsPriorConstraintDropAndUncertainAttemptWithoutResettingRows() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER, CONSTRAINT CK_ID CHECK(ID>0))");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER, CONSTRAINT CK_ID CHECK(ID>=0))", "INSERT INTO ITEMS VALUES(0)");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[ITEMS]", "allowDestructiveSchemaChanges: true\n"), bindings, directory);
		var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.SCHEMA_PREPARATION, failure.outcome().phase());
		assertTrue(failure.outcome().schemaPreparation().completedStatements() > 0);
		assertNotNull(failure.outcome().schemaPreparation().attemptedStatement());
		assertNull(failure.outcome().copyResult());
		try (var c = target.getConnection();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT ID FROM ITEMS")) {
			assertTrue(r.next());
			assertEquals(0, r.getInt(1));
			assertFalse(r.next());
		}
	}

	@Test
	void postgresDefaultBackfillSqlIsVisibleAndRequiresTargetDataChangePermission() throws Exception {
		var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("PostgreSQL", 16, 0, null);
		var actual = new com.sqlapp.data.schemas.Schema("public").setProductName("PostgreSQL");
		var old = new com.sqlapp.data.schemas.Table("ITEMS");
		old.getColumns().add("ID", column -> column.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		actual.getTables().add(old);
		var desired = old.clone().setSchemaName("public");
		desired.getColumns().add("STATUS", column -> column.setDataType(com.sqlapp.data.db.datatype.DataType.INT)
				.setNotNull(true).setDefaultValue("0"));
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var plan = TransferSchemaDifferencePreparation.compare(actual,
				List.of(new TransferSchemaPreparation.Selection("items", desired, desired)), Map.of("ITEMS", desired),
				registry, "fixture", List.of(), TransferJobConfiguration.CompatibilityPolicy.WARN);
		assertTrue(plan.statements().stream()
				.anyMatch(statement -> statement.sql().stripLeading().toUpperCase(Locale.ROOT).startsWith("UPDATE")));
		assertTrue(
				plan.warnings().stream().anyMatch(warning -> warning.startsWith(TransferSchemaChangeChecks.DATA_IMPACT)
						&& warning.contains("UPDATE_TABLE")));
		var configuration = new TransferJobConfigurationIO().read(job("[ITEMS]", "")).configuration();
		assertTrue(TransferSchemaChangeChecks.executionBlocked(configuration, plan));
	}

	@Test
	void destructiveSqlIsReviewableButCannotExecuteWithoutSeparatePermission() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER)");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER, EXTRA VARCHAR(40))", "INSERT INTO ITEMS VALUES(5,'keep')");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[ITEMS]", ""), bindings, directory);
		assertTrue(ddl(review).contains("DROP COLUMN"));
		assertTrue(TransferSchemaChangeChecks.executionBlocked(review.prepared().job().configuration(),
				review.prepared().schemaPreparation()));
		var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.REVALIDATION, failure.outcome().phase());
		assertEquals(0, failure.outcome().schemaPreparation().completedStatements());
		assertTrue(column(target, "ITEMS", "EXTRA"));
		assertTrue(Files.readString(new TransferJobReportIO().writeReview(review)).contains("SCHEMA_DATA_IMPACT"));
	}

	@Test
	void explicitPermissionAllowsSelectedColumnRemovalWhileKeepingRows() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER)");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER, EXTRA VARCHAR(40))", "INSERT INTO ITEMS VALUES(5,'remove')");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[ITEMS]", "allowDestructiveSchemaChanges: true\n"), bindings, directory);
		assertFalse(TransferSchemaChangeChecks.executionBlocked(review.prepared().job().configuration(),
				review.prepared().schemaPreparation()));
		service.execute(review, bindings);
		assertFalse(column(target, "ITEMS", "EXTRA"));
		try (var c = target.getConnection();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT ID FROM ITEMS")) {
			assertTrue(r.next());
			assertEquals(5, r.getInt(1));
			assertFalse(r.next());
		}
	}

	@Test
	void replacesDifferentColumnAtSamePositionWithoutGuessingRenameOrDuplicatingAlter() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER, NAME VARCHAR(80))");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER, EXTRA VARCHAR(40))", "INSERT INTO ITEMS VALUES(5,'old value')");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[ITEMS]", "allowDestructiveSchemaChanges: true\n"), bindings, directory);
		assertEquals(2, review.prepared().schemaPreparation().statements().size(), ddl(review));
		assertFalse(ddl(review).contains("RENAME"), ddl(review));
		var result = service.execute(review, bindings);
		assertEquals(2, result.schemaPreparation().completedStatements());
		try (var c = target.getConnection();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT ID,NAME FROM ITEMS")) {
			assertTrue(r.next());
			assertEquals(5, r.getInt(1));
			assertNull(r.getString(2));
			assertFalse(r.next());
		}
	}

	@Test
	void replacesDifferentlyNamedConstraintWithoutDuplicateDropOrGuessedRename() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER, CONSTRAINT NEW_CHECK CHECK(ID>0))");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER, CONSTRAINT OLD_CHECK CHECK(ID>0))", "INSERT INTO ITEMS VALUES(5)");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[ITEMS]", "allowDestructiveSchemaChanges: true\n"), bindings, directory);
		assertEquals(2, review.prepared().schemaPreparation().statements().size(), ddl(review));
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, service.execute(review, bindings).phase());
		assertThrows(SQLException.class, () -> sql(target, "INSERT INTO ITEMS VALUES(0)"));
	}

	@Test
	void narrowingRequiresPermissionAndExpandingDoesNot() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(NAME VARCHAR(20))");
		sql(target, "CREATE TABLE ITEMS(NAME VARCHAR(100))");
		try (var from = source.getConnection(); var to = target.getConnection()) {
			var schema = TransferJobPreparationService.readSchema(from, TransferJobPreparationService.dialect(from),
					"PUBLIC");
			var table = schema.getTables().getFirst();
			TransferSchemaPreparation.schemaOnly(to, schema,
					List.of(new TransferSchemaPreparation.Selection("items", table,
							table.clone().setSchemaName("PUBLIC").setCatalogName(to.getCatalog()))),
					"fixture", TransferJobConfiguration.CompatibilityPolicy.WARN,
					TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES);
		}
		var review = service.review(job("[ITEMS]", ""), Map.of("from", source, "to", target), directory);
		assertTrue(TransferSchemaChangeChecks.executionBlocked(review.prepared().job().configuration(),
				review.prepared().schemaPreparation()));
		var reverse = service.review(job("[ITEMS]", ""), Map.of("from", target, "to", source), directory);
		assertFalse(TransferSchemaChangeChecks.executionBlocked(reverse.prepared().job().configuration(),
				reverse.prepared().schemaPreparation()));
	}

	@Test
	void targetMetadataChangedAfterReviewStopsBeforeAnyAlter() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER, NAME VARCHAR(40))");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER)");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[ITEMS]", ""), bindings, directory);
		sql(target, "ALTER TABLE ITEMS ADD LOCAL_VALUE INTEGER");
		var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.REVALIDATION, failure.outcome().phase());
		assertFalse(column(target, "ITEMS", "NAME"));
		assertTrue(column(target, "ITEMS", "LOCAL_VALUE"));
	}

	@Test
	void explicitTargetSchemaKeepsSameNamedPublicTableAndIndexUntouched() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER, NAME VARCHAR(40))");
		sql(target, "CREATE SCHEMA ARCHIVE AUTHORIZATION DBA",
				"CREATE TABLE PUBLIC.ITEMS(ID INTEGER, EXTRA VARCHAR(40))",
				"CREATE INDEX PUBLIC.OLD_LOOKUP ON PUBLIC.ITEMS(ID)",
				"INSERT INTO PUBLIC.ITEMS VALUES(9,'public keep')",
				"CREATE TABLE ARCHIVE.ITEMS(ID INTEGER, EXTRA VARCHAR(40))",
				"CREATE INDEX ARCHIVE.OLD_LOOKUP ON ARCHIVE.ITEMS(ID)",
				"INSERT INTO ARCHIVE.ITEMS VALUES(5,'archive old')");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[ITEMS]", "targetSchema: ARCHIVE\nallowDestructiveSchemaChanges: true\n"),
				bindings, directory);
		assertFalse(ddl(review).contains("PUBLIC.ITEMS"), ddl(review));
		service.execute(review, bindings);
		try (var c = target.getConnection();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT EXTRA FROM PUBLIC.ITEMS")) {
			assertTrue(r.next());
			assertEquals("public keep", r.getString(1));
		}
		try (var c = target.getConnection();
				var r = c.getMetaData().getIndexInfo(null, "PUBLIC", "ITEMS", false, false)) {
			boolean found = false;
			while (r.next())
				if ("OLD_LOOKUP".equals(r.getString("INDEX_NAME")))
					found = true;
			assertTrue(found);
		}
		try (var c = target.getConnection();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT ID,NAME FROM ARCHIVE.ITEMS")) {
			assertTrue(r.next());
			assertEquals(5, r.getInt(1));
			assertNull(r.getString(2));
		}
	}

	@Test
	void equivalentSelectedTablesProduceNoExecutableDdl() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER)");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER)");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[ITEMS]", ""), bindings, directory);
		assertTrue(review.prepared().schemaPreparation().statements().isEmpty(), ddl(review));
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, service.execute(review, bindings).phase());
	}

	@Test
	void invalidModesAndPermissionsCannotBeSilentlyIgnoredAndDefaultSerializationStaysConcise() throws Exception {
		var io = new TransferJobConfigurationIO();
		var yaml = Files.readString(job("[ITEMS]", ""));
		assertFalse(io.format(io.validate(yaml)).contains("allowDestructiveSchemaChanges"));
		assertThrows(IllegalArgumentException.class, () -> io.validate(yaml.replace("SCHEMA_ONLY", "JDBC_DIRECT")));
		assertThrows(IllegalArgumentException.class, () -> io.validate(
				yaml.replace("APPLY_DIFFERENCES", "CREATE_MISSING") + "allowDestructiveSchemaChanges: true\n"));
		assertTrue(io.validate(yaml + "allowDestructiveSchemaChanges: true\n").allowDestructiveSchemaChanges());
		assertThrows(IllegalArgumentException.class,
				() -> io.validate(yaml + "allowDestructiveSchemaChanges: 'true'\n"));
	}

	@Test
	void unsupportedGeneratedExpressionIsWarnedWhileOtherTableChangesRemainExecutable() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE GENERATED_ITEM(A INTEGER, B INTEGER GENERATED ALWAYS AS(A+1))",
				"CREATE TABLE ITEMS(ID INTEGER, NAME VARCHAR(40))");
		sql(target, "CREATE TABLE GENERATED_ITEM(A INTEGER, B INTEGER GENERATED ALWAYS AS(A+2))",
				"CREATE TABLE ITEMS(ID INTEGER)");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[GENERATED_ITEM,ITEMS]", "allowDestructiveSchemaChanges: true\n"), bindings,
				directory);
		assertTrue(review.prepared().warnings().stream()
				.anyMatch(w -> w.contains("SCHEMA_DIFF_UNRESOLVED") && w.contains("GENERATED_ITEM")));
		assertTrue(ddl(review).contains("ADD COLUMN"));
		assertFalse(ddl(review).contains("GENERATED_ITEM"));
		var result = service.execute(review, bindings);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
		assertTrue(column(target, "ITEMS", "NAME"));
		assertTrue(result.prepared().warnings().stream().anyMatch(w -> w.contains("SCHEMA_DIFF_UNRESOLVED")));
	}

	private Path job(String tables, String extra) throws Exception {
		return Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: " + tables
						+ "\nschemaMode: APPLY_DIFFERENCES\ndataMode: SCHEMA_ONLY\n" + extra);
	}

	private static String ddl(TransferJobExecutionService.Review review) {
		return review.prepared().schemaPreparation().statements().stream().map(TransferSchemaPreparation.Statement::sql)
				.collect(java.util.stream.Collectors.joining("\n")).toUpperCase(Locale.ROOT);
	}

	private static JDBCDataSource database() {
		var ds = new JDBCDataSource();
		ds.setUrl("jdbc:hsqldb:mem:schema_diff_" + UUID.randomUUID());
		ds.setUser("SA");
		ds.setPassword("");
		return ds;
	}

	private static void sql(DataSource ds, String... statements) throws Exception {
		try (var c = ds.getConnection(); var s = c.createStatement()) {
			for (var text : statements)
				s.execute(text);
		}
	}

	private static boolean column(DataSource ds, String table, String column) throws Exception {
		try (var c = ds.getConnection(); var r = c.getMetaData().getColumns(null, "PUBLIC", table, column)) {
			return r.next();
		}
	}
}
