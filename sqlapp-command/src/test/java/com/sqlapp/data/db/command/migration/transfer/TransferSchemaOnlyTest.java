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

class TransferSchemaOnlyTest {
	@TempDir
	Path directory;
	private final TransferJobExecutionService service = new TransferJobExecutionService();

	@Test
	void repeatedTableLocalIndexNamesRetainBothDefinitionsAndUniqueness() throws Exception {
		var schema = new com.sqlapp.data.schemas.Schema("").setProductName("Microsoft Access");
		var selections = new ArrayList<TransferSchemaPreparation.Selection>();
		for (String name : List.of("CUSTOMERS", "SUPPLIERS")) {
			var table = new com.sqlapp.data.schemas.Table(name);
			table.getColumns().add("CODE",
					column -> column.setDataType(com.sqlapp.data.db.datatype.DataType.INT).setNotNull(true));
			var index = new com.sqlapp.data.schemas.Index("IX_CODE").setUnique(true);
			index.getColumns().add(table.getColumns().get("CODE"), com.sqlapp.data.schemas.Order.Desc);
			table.getIndexes().add(index);
			schema.getTables().add(table);
			selections.add(new TransferSchemaPreparation.Selection(name, table, table.clone().setSchemaName("PUBLIC")));
		}
		try (var connection = database().getConnection()) {
			var plan = TransferSchemaPreparation.schemaOnly(connection, schema, selections, "fixture",
					TransferJobConfiguration.CompatibilityPolicy.WARN);
			assertEquals(plan, TransferSchemaPreparation.schemaOnly(connection, schema, selections, "fixture",
					TransferJobConfiguration.CompatibilityPolicy.WARN));
			assertEquals(2, plan.statements().stream()
					.filter(statement -> statement.sql().toUpperCase(Locale.ROOT).contains(" DESC")).count());
			TransferSchemaPreparation.execute(connection, plan, progress -> {
			});
			var names = new HashSet<String>();
			for (var table : schema.getTables()) {
				assertEquals("IX_CODE", table.getIndexes().getFirst().getName());
				try (var indexes = connection.getMetaData().getIndexInfo(null, "PUBLIC", table.getName(), true,
						false)) {
					assertTrue(indexes.next());
					assertFalse(indexes.getBoolean("NON_UNIQUE"));
					assertEquals("CODE", indexes.getString("COLUMN_NAME"));
					names.add(indexes.getString("INDEX_NAME"));
				}
			}
			assertEquals(2, names.size());
			assertEquals(2,
					plan.warnings().stream().filter(warning -> warning.contains("Repeated source index name")).count());
			assertEquals(2, plan.warnings().stream()
					.filter(warning -> warning.startsWith(TransferSchemaChangeChecks.NAME_ADJUSTMENT)).count());
		}
	}

	@Test
	void repeatedTableLocalConstraintNamesReceiveStableNamesOnlyInTargetCreatePlan() throws Exception {
		var schema = new com.sqlapp.data.schemas.Schema("").setProductName("Microsoft Access");
		var selections = new ArrayList<TransferSchemaPreparation.Selection>();
		for (String name : List.of("PARENTS", "CHILDREN")) {
			var table = new com.sqlapp.data.schemas.Table(name);
			table.getColumns().add("ID",
					column -> column.setDataType(com.sqlapp.data.db.datatype.DataType.INT).setNotNull(true));
			table.setPrimaryKey("PrimaryKey", table.getColumns().get("ID"));
			schema.getTables().add(table);
			selections.add(new TransferSchemaPreparation.Selection(name, table, table.clone().setSchemaName("PUBLIC")));
		}
		var target = database();
		try (var connection = target.getConnection()) {
			var plan = TransferSchemaPreparation.schemaOnly(connection, schema, selections, "fixture",
					TransferJobConfiguration.CompatibilityPolicy.WARN);
			assertEquals(2, plan.warnings().stream()
					.filter(warning -> warning.contains("Repeated source constraint name")).count());
			assertEquals(2, plan.warnings().stream()
					.filter(warning -> warning.startsWith(TransferSchemaChangeChecks.NAME_ADJUSTMENT)).count());
			assertTrue(plan.statements().stream().noneMatch(statement -> statement.sql().contains("PrimaryKey")));
			assertEquals(plan, TransferSchemaPreparation.schemaOnly(connection, schema, selections, "fixture",
					TransferJobConfiguration.CompatibilityPolicy.WARN));
			assertEquals(2, TransferSchemaPreparation.execute(connection, plan, progress -> {
			}).completedStatements());
			for (var table : schema.getTables()) {
				assertEquals("PrimaryKey", table.getPrimaryKeyConstraint().getName());
				try (var keys = connection.getMetaData().getPrimaryKeys(null, "PUBLIC", table.getName())) {
					assertTrue(keys.next());
					assertEquals("ID", keys.getString("COLUMN_NAME"));
				}
			}
		}
	}

	@Test
	void hsqlModelGeneratesPostgresqlDdlUsingTargetDialectWithoutExternalDatabase() throws Exception {
		var target = database();
		var schema = new com.sqlapp.data.schemas.Schema("PUBLIC");
		schema.setProductName("HSQL Database Engine");
		var table = new com.sqlapp.data.schemas.Table("DOCUMENTS");
		table.getColumns().add("CONTENT", column -> column.setDataType(com.sqlapp.data.db.datatype.DataType.BLOB));
		schema.getTables().add(table);
		try (var real = target.getConnection()) {
			var metadata = (DatabaseMetaData) java.lang.reflect.Proxy.newProxyInstance(
					DatabaseMetaData.class.getClassLoader(), new Class<?>[] { DatabaseMetaData.class },
					(proxy, method, args) -> switch (method.getName()) {
					case "getDatabaseProductName" -> "PostgreSQL";
					case "getDatabaseMajorVersion" -> 16;
					case "getDatabaseMinorVersion" -> 2;
					case "getDatabaseProductVersion" -> "16.2";
					default -> invoke(real.getMetaData(), method, args);
					});
			var connection = (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
					new Class<?>[] { Connection.class }, (proxy, method,
							args) -> method.getName().equals("getMetaData") ? metadata : invoke(real, method, args));
			var plan = TransferSchemaPreparation.schemaOnly(connection, schema,
					List.of(new TransferSchemaPreparation.Selection("documents", table,
							table.clone().setSchemaName("PUBLIC"))),
					"fixture", TransferJobConfiguration.CompatibilityPolicy.WARN);
			assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("products differ")));
			var ddl = plan.statements().stream().map(TransferSchemaPreparation.Statement::sql)
					.collect(java.util.stream.Collectors.joining("\n")).toUpperCase(Locale.ROOT);
			assertTrue(ddl.contains("BYTEA"), ddl);
			assertFalse(ddl.contains("BLOB"), ddl);
			assertFalse(exists(target, "DOCUMENTS"));
		}
	}

	@Test
	void ddlFailureRetainsCompletedAndUncertainStatementsWithoutCopying() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER)", "CREATE TABLE OTHER(ID INTEGER)");
		var attempts = new java.util.concurrent.atomic.AtomicInteger();
		var faulty = new JDBCDataSource() {
			@Override
			public Connection getConnection() throws SQLException {
				var real = target.getConnection();
				return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
						new Class<?>[] { Connection.class }, (proxy, method, args) -> {
							var result = invoke(real, method, args);
							if (!method.getName().equals("createStatement"))
								return result;
							return java.lang.reflect.Proxy.newProxyInstance(Statement.class.getClassLoader(),
									new Class<?>[] { Statement.class }, (p, m, a) -> {
										if (m.getName().equals("execute")
												&& ((String) a[0]).stripLeading().startsWith("CREATE")
												&& attempts.incrementAndGet() == 2)
											throw new SQLException("secret driver message", "42000");
										return invoke(result, m, a);
									});
						});
			}
		};
		var bindings = Map.of("from", source, "to", faulty);
		var review = service.review(job("[ITEMS, OTHER]", ""), bindings, directory);
		var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.SCHEMA_PREPARATION, failure.outcome().phase());
		assertEquals(1, failure.outcome().schemaPreparation().completedStatements());
		assertEquals(2, failure.outcome().schemaPreparation().attemptedStatement());
		assertNull(failure.outcome().copyResult());
		assertTrue(failure.outcome().committedProgress().isEmpty());
		assertNull(failure.getCause());
		assertFalse(failure.getMessage().contains("secret"));
		assertTrue(Files.readString(new TransferJobReportIO().writeOutcome(review, failure.outcome()))
				.contains("SCHEMA_PREPARATION"));
	}

	private static Object invoke(Object object, java.lang.reflect.Method method, Object[] args) throws Throwable {
		try {
			return method.invoke(object, args);
		} catch (java.lang.reflect.InvocationTargetException failure) {
			throw failure.getCause();
		}
	}

	@Test
	void createsKeylessTablesRelationsAndIndexesWithoutCopyingRowsOrGeneratingBulkJob() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE PARENT(ID INTEGER PRIMARY KEY)", "INSERT INTO PARENT VALUES(42)",
				"CREATE TABLE CHILD(ID INTEGER, PARENT_ID INTEGER REFERENCES PARENT(ID))",
				"CREATE INDEX CHILD_PARENT ON CHILD(PARENT_ID)", "INSERT INTO CHILD VALUES(NULL,42)");
		var bindings = Map.of("from", source, "to", target);
		var captured = service.reviewWithSqlDraft(job("[CHILD, PARENT]", ""), bindings, directory);
		var review = captured.review();
		assertNull(review.prepared().dataJob());
		assertTrue(captured.schemaSqlDraft()
				.contains("-- Schema plan: " + review.prepared().schemaPreparation().fingerprint()));
		assertTrue(captured.schemaSqlDraft().contains(";\n"));
		assertFalse(Files.exists(review.prepared().directory().resolve("data-job.yaml")));
		assertFalse(exists(target, "PARENT"));
		assertTrue(review.prepared().warnings().stream().anyMatch(w -> w.contains("Views, independent sequences")));
		var result = service.execute(review, bindings);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
		assertNull(result.copyResult());
		assertNull(result.verificationResult());
		assertTrue(result.committedProgress().isEmpty());
		assertEquals(0, count(target, "CHILD"));
		assertEquals(0, count(target, "PARENT"));
		assertEquals(1, count(source, "CHILD"));
		assertThrows(SQLException.class, () -> sql(target, "INSERT INTO CHILD VALUES(1,999)"));
		try (var c = target.getConnection();
				var indexes = c.getMetaData().getIndexInfo(null, "PUBLIC", "CHILD", false, false)) {
			boolean found = false;
			while (indexes.next())
				if ("CHILD_PARENT".equals(indexes.getString("INDEX_NAME")))
					found = true;
			assertTrue(found);
		}
		var report = new TransferJobReportIO().writeOutcome(review, result);
		assertTrue(Files.readString(report).contains("SCHEMA_ONLY"));
		assertFalse(Files.readString(report).contains("schemaSqlDraft"));
	}

	@Test
	void skipsExistingNonemptyDifferentTableWithoutComparingOrChangingItsRows() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER)", "CREATE TABLE OTHER(ID INTEGER)");
		sql(target, "CREATE TABLE ITEMS(OLD_VALUE VARCHAR(10))", "INSERT INTO ITEMS VALUES('keep')");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[ITEMS, OTHER]", ""), bindings, directory);
		assertTrue(review.prepared().warnings().stream().anyMatch(w -> w.contains("skipped without comparison")));
		service.execute(review, bindings);
		assertEquals(1, count(target, "ITEMS"));
		assertEquals(0, count(target, "OTHER"));
	}

	@Test
	void changedTargetRequiresNewReviewAndDoesNotCreateRemainingTable() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER)", "CREATE TABLE OTHER(ID INTEGER)");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[ITEMS, OTHER]", ""), bindings, directory);
		sql(target, "CREATE TABLE ITEMS(ID INTEGER)");
		var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.REVALIDATION, failure.outcome().phase());
		assertFalse(exists(target, "OTHER"));
	}

	@Test
	void tamperedSchemaCannotExecute() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER)");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[ITEMS]", ""), bindings, directory);
		Files.writeString(review.prepared().sourceSchemaFile(), "changed");
		assertThrows(TransferJobExecutionService.ExecutionException.class, () -> service.execute(review, bindings));
		assertFalse(exists(target, "ITEMS"));
	}

	@Test
	void changedLiveSourceRequiresNewReviewBeforeAnyDdl() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER)");
		var bindings = Map.of("from", source, "to", target);
		var review = service.review(job("[ITEMS]", ""), bindings, directory);
		sql(source, "ALTER TABLE ITEMS ADD NAME VARCHAR(40)");
		var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.REVALIDATION, failure.outcome().phase());
		assertFalse(exists(target, "ITEMS"));
	}

	@Test
	void strictPolicyStopsBeforeDdlForUncopiedObjectScope() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER)");
		assertThrows(TransferSchemaPreparation.ValidationException.class,
				() -> service.review(job("[ITEMS]", "compatibilityPolicy: ERROR\n"),
						Map.of("from", source, "to", target), directory));
		assertFalse(exists(target, "ITEMS"));
	}

	@Test
	void invalidSchemaOnlyOptionsAreRejectedAndOldDefaultsRemain() throws Exception {
		var io = new TransferJobConfigurationIO();
		var yaml = Files.readString(job("[ITEMS]", ""));
		assertFalse(io.validate(yaml).verify());
		assertThrows(IllegalArgumentException.class, () -> io.validate(yaml + "verify: true\n"));
		assertThrows(IllegalArgumentException.class,
				() -> io.validate(yaml.replace("CREATE_MISSING", "REQUIRE_EXISTING")));
		assertThrows(IllegalArgumentException.class,
				() -> io.validate(yaml + "files: {directory: packages, nullValue: NULL}\n"));
		assertTrue(io.validate(yaml.replace("dataMode: SCHEMA_ONLY\n", "")).verify());
	}

	@Test
	void sharedCommandReviewsThenExecutesSameSchemaOnlyJob() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER)");
		var file = job("[ITEMS]", "");
		var command = new TransferDatabaseCommand();
		command.setConfigurationFile(file.toFile());
		command.setWorkspaceDirectory(directory.toFile());
		command.setSourceDataSource(source);
		command.setDataSource(target);
		command.setCloseDataSource(false);
		command.run();
		assertFalse(exists(target, "ITEMS"));
		command.setAllowExecution(true);
		command.setExpectedConfigurationFingerprint(new TransferJobConfigurationIO().read(file).fingerprint());
		command.run();
		assertEquals(0, count(target, "ITEMS"));
		assertNotNull(command.getResultFile());
	}

	@Test
	void offlineAuthoringAcceptsKeylessSelectionAndPinsTheReviewedSourceSnapshot() throws Exception {
		var source = database();
		var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER)");
		var review = service.review(job("[ITEMS]", ""), Map.of("from", source, "to", target), directory);
		var inspected = new com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder()
				.inspect(review.prepared().sourceSchemaFile());
		assertFalse(inspected.tables().getFirst().selectable());
		var builder = new TransferJobDraftBuilder();
		var saved = builder.saveSchemaOnly(inspected, List.of("ITEMS"),
				TransferJobConfiguration.CompatibilityPolicy.WARN, "ARCHIVE", directory.resolve("schema-draft.yaml"));
		assertEquals(TransferJobConfiguration.DataMode.SCHEMA_ONLY, saved.configuration().dataMode());
		assertEquals("ARCHIVE", saved.configuration().targetSchema());
		assertFalse(saved.configuration().verify());
		assertEquals(1, saved.configuration().tables().size());
		Files.writeString(inspected.file(), Files.readString(inspected.file()) + "\n");
		assertThrows(IllegalArgumentException.class, () -> builder.saveSchemaOnly(inspected, List.of("ITEMS"),
				TransferJobConfiguration.CompatibilityPolicy.WARN, null, directory.resolve("stale.yaml")));
		assertFalse(Files.exists(directory.resolve("stale.yaml")));
	}

	private Path job(String tables, String extra) throws Exception {
		return Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: " + tables
						+ "\nschemaMode: CREATE_MISSING\ndataMode: SCHEMA_ONLY\n" + extra);
	}

	private static JDBCDataSource database() {
		var ds = new JDBCDataSource();
		ds.setUrl("jdbc:hsqldb:mem:schema_only_" + UUID.randomUUID());
		ds.setUser("SA");
		ds.setPassword("");
		return ds;
	}

	private static void sql(DataSource ds, String... queries) throws Exception {
		try (var c = ds.getConnection(); var s = c.createStatement()) {
			for (var query : queries)
				s.execute(query);
		}
	}

	private static int count(DataSource ds, String table) throws Exception {
		try (var c = ds.getConnection();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
			r.next();
			return r.getInt(1);
		}
	}

	private static boolean exists(DataSource ds, String table) throws Exception {
		try (var c = ds.getConnection(); var r = c.getMetaData().getTables(null, "PUBLIC", table, null)) {
			return r.next();
		}
	}
}
