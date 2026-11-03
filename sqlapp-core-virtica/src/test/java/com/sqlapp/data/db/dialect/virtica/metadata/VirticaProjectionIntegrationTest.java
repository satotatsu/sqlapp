package com.sqlapp.data.db.dialect.virtica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/**
 * Real database test: opt-in only after explicit target/credentials/cleanup
 * authorization.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "SQLAPP_VERTICA_PROJECTION_INTEGRATION", matches = "true")
class VirticaProjectionIntegrationTest {
	@Test
	void readXmlDropAndRecreateProjectionFromBulkVendorDefinition() throws Exception {
		String url = required("SQLAPP_VERTICA_URL");
		if (!url.matches("jdbc:vertica://127\\.0\\.0\\.1:[0-9]+/[A-Za-z0-9_]+"))
			throw new IllegalArgumentException(
					"Integration URL must name an explicit loopback port and disposable database");
		Properties credentials = new Properties();
		credentials.setProperty("user", required("SQLAPP_VERTICA_USER"));
		credentials.setProperty("password", required("SQLAPP_VERTICA_PASSWORD"));
		String schemaName = "sqlapp_projection_it_" + UUID.randomUUID().toString().replace("-", "");
		try (var connection = DriverManager.getConnection(url, credentials)) {
			var metadata = connection.getMetaData();
			assertTrue(metadata.getDatabaseProductName().toLowerCase(java.util.Locale.ROOT).contains("vertica"));
			var dialect = DialectResolver.getInstance().getDialect(metadata.getDatabaseProductName(),
					metadata.getDatabaseMajorVersion(), metadata.getDatabaseMinorVersion(), null);
			boolean created = false;
			Throwable primaryFailure = null;
			try {
				try (var statement = connection.createStatement()) {
					statement.execute("CREATE SCHEMA " + schemaName);
					created = true;
					statement.execute("CREATE TABLE " + schemaName + ".source_table(id INT, body VARCHAR(80))");
					statement.execute("INSERT INTO " + schemaName + ".source_table VALUES (1, 'test')");
					statement.execute("CREATE PROJECTION " + schemaName + ".custom_projection AS SELECT id FROM "
							+ schemaName + ".source_table ORDER BY id UNSEGMENTED ALL NODES");
				}
				var reader = dialect.getCatalogReader().getSchemaReader().getProjectionReader();
				assertNotNull(reader);
				reader.setSchemaName(schemaName);
				var candidates = reader.getAll(connection);
				Projection candidate = candidates.stream()
						.filter(p -> "custom_projection".equals(p.getName())
								|| "custom_projection".equals(p.getSpecifics().get("PROJECTION_BASENAME")))
						.findFirst().orElseThrow();
				reader.setObjectName(candidate.getName());
				var full = reader.getAllFull(connection);
				assertEquals(1, full.size());
				Projection projection = full.get(0);
				assertEquals("source_table", projection.getTableName());
				assertEquals(1, projection.getColumns().size());
				assertEquals("id", projection.getSortColumns().get(0).getName());
				assertFalse(String.join("\n", projection.getDefinition()).contains("CREATE TABLE"));
				Projection restored = SchemaUtils.readXml(new java.io.StringReader(projection.asXml()));
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				try (var statement = connection.createStatement()) {
					statement.execute(registry.createSql(restored, SqlType.DROP).get(0).getSqlText());
					statement.execute(registry.createSql(restored, SqlType.CREATE).get(0).getSqlText());
				}
				var recreated = reader.getAllFull(connection);
				assertEquals(1, recreated.size());
				assertEquals(projection.getColumns().get(0).getName(), recreated.get(0).getColumns().get(0).getName());

				// Exercise exported quoted identifiers and the complete schema set,
				// including the server's automatic source-table projection.
				String quotedName = "projection.with \"quote\"";
				String quotedIdentifier = "\"projection.with \"\"quote\"\"\"";
				try (var statement = connection.createStatement()) {
					statement.execute("CREATE PROJECTION " + schemaName + "." + quotedIdentifier + " AS SELECT id FROM "
							+ schemaName + ".source_table ORDER BY id UNSEGMENTED ALL NODES");
				}
				reader.setObjectName(null);
				var all = reader.getAllFull(connection);
				assertTrue(all.size() >= 3);
				Projection quoted = all.stream().filter(p -> quotedName.equals(p.getName())).findFirst().orElseThrow();
				Projection quotedXml = SchemaUtils.readXml(new java.io.StringReader(quoted.asXml()));
				try (var statement = connection.createStatement()) {
					statement.execute(registry.createSql(quotedXml, SqlType.DROP).get(0).getSqlText());
					statement.execute(registry.createSql(quotedXml, SqlType.CREATE).get(0).getSqlText());
				}
				assertTrue(reader.getAll(connection).stream().anyMatch(p -> quotedName.equals(p.getName())));
				Projection renamed = quotedXml.clone();
				renamed.setName("renamed_projection");
				com.sqlapp.data.db.sql.SqlFactory<Projection> alter = registry.getSqlFactory(quotedXml, SqlType.ALTER);
				try (var statement = connection.createStatement()) {
					statement.execute(alter.createDiffSql(quotedXml.diff(renamed)).get(0).getSqlText());
				}
				assertTrue(reader.getAll(connection).stream().anyMatch(p -> "renamed_projection".equals(p.getName())));
				try (var statement = connection.createStatement()) {
					statement.execute("CREATE PROJECTION " + schemaName
							+ ".segmented_projection (id ENCODING RLE, body ENCODING AUTO) AS SELECT id, body FROM "
							+ schemaName + ".source_table ORDER BY body, id SEGMENTED BY HASH(id) ALL NODES KSAFE 0");
				}
				var segmented = reader.getAll(connection).stream()
						.filter(p -> "segmented_projection".equals(p.getName())
								|| "segmented_projection".equals(p.getSpecifics().get("PROJECTION_BASENAME")))
						.findFirst().orElseThrow();
				reader.setObjectName(segmented.getName());
				segmented = reader.getAllFull(connection).get(0);
				assertEquals(Boolean.TRUE, segmented.getSpecifics().get("IS_SEGMENTED", Boolean.class));
				assertNotNull(segmented.getSpecifics().get("SEGMENT_EXPRESSION"));
				assertEquals(java.util.List.of("body", "id"),
						segmented.getSortColumns().stream().map(c -> c.getName()).toList());
				assertEquals("RLE", segmented.getColumns().get("id").getSpecifics().get("ENCODING_TYPE"));
				Projection segmentedXml = SchemaUtils.readXml(new java.io.StringReader(segmented.asXml()));
				try (var statement = connection.createStatement()) {
					statement.execute(registry.createSql(segmentedXml, SqlType.DROP).get(0).getSqlText());
					statement.execute(registry.createSql(segmentedXml, SqlType.CREATE).get(0).getSqlText());
				}
				assertEquals(segmented.getSortColumns(), reader.getAllFull(connection).get(0).getSortColumns());
				verifyRelatedObjects(connection, dialect, schemaName);
				verifyTables(connection, dialect, schemaName);
				verifyProcedures(connection, dialect, schemaName);
				verifyDefinitionTables(connection, dialect, schemaName);
				verifyTextIndex(connection, dialect, schemaName);
				createScheduledObjects(connection, schemaName);
				verifyScheduledObjects(connection, dialect, schemaName);
				verifySchedulingStateRestorer(connection, dialect, schemaName);
				verifySchemaDefinition(connection, dialect, schemaName);
				verifyFlexSchemaRestore(connection, dialect);
				verifyUdxParser(connection, dialect, schemaName);
			} catch (Exception | AssertionError failure) {
				primaryFailure = failure;
				throw failure;
			} finally {
				if (created)
					try (var statement = connection.createStatement()) {
						statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
					} catch (java.sql.SQLException cleanupFailure) {
						if (primaryFailure == null)
							throw cleanupFailure;
						primaryFailure.addSuppressed(cleanupFailure);
					}
			}
		}
	}

	private void verifyUdxParser(java.sql.Connection connection, com.sqlapp.data.db.dialect.Dialect dialect,
			String schemaName) throws Exception {
		String ddl = "CREATE PARSER " + schemaName
				+ ".test_json_parser AS LANGUAGE 'C++' NAME 'FJsonParserFactory' LIBRARY public.FlexTableLib NOT FENCED";
		try (var statement = connection.createStatement()) {
			statement.execute(ddl);
		}
		var reader = dialect.getCatalogReader().getSchemaReader().getFunctionReader();
		reader.setSchemaName(schemaName);
		Function function = reader.getAllFull(connection).stream().filter(f -> "test_json_parser".equals(f.getName()))
				.findFirst().orElseThrow();
		assertEquals("User Defined Parser", function.getSpecifics().get("PROCEDURE_TYPE"));
		assertTrue(function.getArguments().isEmpty());
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setDropIfExists(false);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
		function.setDefinition(ddl);
		Function restored = SchemaUtils.readXml(new java.io.StringReader(function.asXml()));
		try (var statement = connection.createStatement()) {
			statement.execute(registry.createSql(restored, SqlType.DROP).get(0).getSqlText());
			statement.execute(registry.createSql(restored, SqlType.CREATE).get(0).getSqlText());
		}
		assertTrue(reader.getAll(connection).stream().anyMatch(f -> "test_json_parser".equals(f.getName())));
	}

	private void verifyRelatedObjects(java.sql.Connection connection, com.sqlapp.data.db.dialect.Dialect dialect,
			String schemaName) throws Exception {
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE SEQUENCE " + schemaName + ".test_sequence INCREMENT BY 3 CACHE 20");
			statement.execute(
					"CREATE VIEW " + schemaName + ".test_view AS SELECT id, body FROM " + schemaName + ".source_table");
			statement.execute("CREATE FUNCTION " + schemaName
					+ ".test_function(x INT) RETURN INT AS BEGIN RETURN CASE WHEN x > 0 THEN x + 1 ELSE 0 END; END;");
			statement.execute("CREATE FUNCTION " + schemaName
					+ ".test_function(x VARCHAR) RETURN VARCHAR AS BEGIN RETURN x || '!'; END;");
		}
		var schemaReader = dialect.getCatalogReader().getSchemaReader();
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var sequences = schemaReader.getSequenceReader();
		sequences.setSchemaName(schemaName);
		Sequence sequence = SchemaUtils.readXml(new java.io.StringReader(sequences.getAll(connection).stream()
				.filter(s -> "test_sequence".equals(s.getName())).findFirst().orElseThrow().asXml()));
		assertEquals(3L, sequence.getIncrementBy().longValue());
		assertEquals(20, sequence.getCacheSize());
		assertNull(sequence.getLastValue());
		try (var statement = connection.createStatement()) {
			statement.execute(registry.createSql(sequence, SqlType.DROP).get(0).getSqlText());
			statement.execute(registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText());
		}
		var views = schemaReader.getViewReader();
		views.setSchemaName(schemaName);
		View view = SchemaUtils.readXml(new java.io.StringReader(views.getAllFull(connection).get(0).asXml()));
		assertEquals(2, view.getColumns().size());
		try (var statement = connection.createStatement()) {
			statement.execute(registry.createSql(view, SqlType.DROP).get(0).getSqlText());
			statement.execute(registry.createSql(view, SqlType.CREATE).get(0).getSqlText());
			try (var result = statement.executeQuery("SELECT count(*) FROM " + schemaName + ".test_view")) {
				assertTrue(result.next());
				assertEquals(1, result.getInt(1));
			}
		}
		var functions = schemaReader.getFunctionReader();
		functions.setSchemaName(schemaName);
		var overloads = functions.getAllFull(connection);
		assertEquals(2, overloads.size());
		Function function = SchemaUtils.readXml(new java.io.StringReader(overloads.stream()
				.filter(f -> f.getArguments().get(0).getDataType() == com.sqlapp.data.db.datatype.DataType.BIGINT)
				.findFirst().orElseThrow().asXml()));
		assertEquals(1, function.getArguments().size());
		try (var statement = connection.createStatement()) {
			statement.execute(registry.createSql(function, SqlType.DROP).get(0).getSqlText());
			statement.execute(registry.createSql(function, SqlType.CREATE).get(0).getSqlText());
			try (var result = statement.executeQuery("SELECT " + schemaName + ".test_function(2)")) {
				assertTrue(result.next());
				assertEquals(3, result.getInt(1));
			}
			try (var result = statement.executeQuery("SELECT " + schemaName + ".test_function('ok'::VARCHAR)")) {
				assertTrue(result.next());
				assertEquals("ok!", result.getString(1));
			}
		}
	}

	private void verifyTables(java.sql.Connection connection, com.sqlapp.data.db.dialect.Dialect dialect,
			String schemaName) throws Exception {
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE " + schemaName + ".typed_table (id IDENTITY(10, 2, 20), "
					+ "amount NUMERIC(18,4), description LONG VARCHAR(1024), payload LONG VARBINARY(1024), "
					+ "occurred TIMESTAMPTZ, CONSTRAINT positive_amount CHECK (amount >= 0) ENABLED) PARTITION BY id");
			statement.execute("CREATE TABLE " + schemaName + ".parent_table (a INT, b INT, PRIMARY KEY(a,b) DISABLED)");
			statement.execute("CREATE TABLE " + schemaName + ".child_table (a INT, b INT, "
					+ "CONSTRAINT child_fk FOREIGN KEY(a,b) REFERENCES " + schemaName + ".parent_table(a,b))");
		}
		var tables = dialect.getCatalogReader().getSchemaReader().getTableReader();
		tables.setSchemaName(schemaName);
		tables.setObjectName("typed_table");
		Table table = tables.getAllFull(connection).get(0);
		assertEquals(5, table.getColumns().size());
		assertTrue(table.getColumns().get("id").isIdentity());
		assertEquals(2L, table.getColumns().get("id").getIdentityStep().longValue());
		assertEquals(20, table.getColumns().get("id").getIdentityCacheSize());
		assertEquals(18L, table.getColumns().get("amount").getLength().longValue());
		assertEquals(4, table.getColumns().get("amount").getScale());
		assertNull(table.getColumns().get("occurred").getScale());
		assertEquals(1, table.getConstraints().size());
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		try (var statement = connection.createStatement()) {
			statement.execute(registry.createSql(restored, SqlType.DROP).get(0).getSqlText());
			for (var sql : registry.createSql(restored, SqlType.CREATE))
				assertDoesNotThrow(() -> statement.execute(sql.getSqlText()), sql.getSqlText());
		}
		Table recreated = tables.getAllFull(connection).get(0);
		try (var statement = connection.createStatement()) {
			statement.execute("INSERT INTO " + schemaName
					+ ".typed_table(amount,occurred) VALUES (1.2345,'2026-01-02 03:04:05.123456+00')");
			try (var result = statement.executeQuery("SELECT occurred FROM " + schemaName + ".typed_table")) {
				assertTrue(result.next());
				assertEquals(123456000, result.getTimestamp(1).getNanos());
			}
		}
		assertEquals(table.getColumns().get("amount").getDataType(),
				recreated.getColumns().get("amount").getDataType());
		assertEquals(table.getSpecifics().get("PARTITION_EXPRESSION"),
				recreated.getSpecifics().get("PARTITION_EXPRESSION"));
		tables.setObjectName("child_table");
		Table child = tables.getAllFull(connection).get(0);
		var foreignKey = (ForeignKeyConstraint) child.getConstraints().get("child_fk");
		assertEquals(2, foreignKey.getColumns().size());
		assertEquals("a", foreignKey.getColumns().get(0).getName());
		assertEquals("b", foreignKey.getColumns().get(1).getName());
	}

	private void verifyProcedures(java.sql.Connection connection, com.sqlapp.data.db.dialect.Dialect dialect,
			String schemaName) throws Exception {
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE PROCEDURE " + schemaName
					+ ".test_procedure(x INT) LANGUAGE PLvSQL AS $$ BEGIN PERFORM INSERT INTO " + schemaName
					+ ".source_table VALUES (x, 'procedure; body'); END; $$;");
			statement.execute("CREATE PROCEDURE " + schemaName
					+ ".test_procedure(x VARCHAR) LANGUAGE PLvSQL AS $$ BEGIN PERFORM INSERT INTO " + schemaName
					+ ".source_table VALUES (99, x); END; $$;");
		}
		var reader = dialect.getCatalogReader().getSchemaReader().getProcedureReader();
		reader.setSchemaName(schemaName);
		var procedures = reader.getAllFull(connection);
		assertEquals(2, procedures.size());
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		for (var procedure : procedures) {
			assertFalse(procedure.getDefinition().isEmpty());
			Procedure restored = SchemaUtils.readXml(new java.io.StringReader(procedure.asXml()));
			try (var statement = connection.createStatement()) {
				statement.execute(registry.createSql(restored, SqlType.DROP).get(0).getSqlText());
				statement.execute(registry.createSql(restored, SqlType.CREATE).get(0).getSqlText());
			}
		}
		try (var statement = connection.createStatement()) {
			statement.execute("CALL " + schemaName + ".test_procedure(42)");
			statement.execute("CALL " + schemaName + ".test_procedure('varchar overload'::VARCHAR)");
			try (var result = statement
					.executeQuery("SELECT count(*) FROM " + schemaName + ".source_table WHERE id IN (42,99)")) {
				assertTrue(result.next());
				assertEquals(2, result.getInt(1));
			}
		}
	}

	private void verifyDefinitionTables(java.sql.Connection connection, com.sqlapp.data.db.dialect.Dialect dialect,
			String schemaName) throws Exception {
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		for (String name : java.util.List.of("flex_table", "external_table")) {
			String ddl = name.equals("flex_table") ? "CREATE FLEX TABLE " + schemaName + "." + name + "(id INT)"
					: "CREATE EXTERNAL TABLE " + schemaName + "." + name + "(id INT) AS COPY FROM '/dev/null'";
			try (var statement = connection.createStatement()) {
				statement.execute(ddl);
			}
			var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName(schemaName);
			reader.setObjectName(name);
			Table table = reader.getAllFull(connection).get(0);
			// Explicit complete CREATE, never the dependency export assigned wholesale.
			table.setDefinition(ddl);
			Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			try (var statement = connection.createStatement()) {
				statement.execute(registry.createSql(restored, SqlType.DROP).get(0).getSqlText());
				statement.execute(registry.createSql(restored, SqlType.CREATE).get(0).getSqlText());
				try (var result = statement.executeQuery("SELECT count(*) FROM " + schemaName + "." + name)) {
					assertTrue(result.next());
					assertEquals(0, result.getInt(1));
				}
			}
		}
	}

	private void verifyTextIndex(java.sql.Connection connection, com.sqlapp.data.db.dialect.Dialect dialect,
			String schemaName) throws Exception {
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE " + schemaName
					+ ".text_source(id INT PRIMARY KEY, body LONG VARCHAR, payload VARCHAR(20))");
			statement.execute("INSERT INTO " + schemaName + ".text_source VALUES(1, 'hello world', 'payload value')");
		}
		String ddl = "CREATE TEXT INDEX " + schemaName + ".\"text.index\" ON " + schemaName
				+ ".text_source(id,body,payload) TOKENIZER v_txtindex.StringTokenizer(LONG VARCHAR) STEMMER NONE";
		try (var statement = connection.createStatement()) {
			statement.execute(ddl);
		}
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(schemaName);
		reader.setObjectName("text_source");
		Index index = reader.getAllFull(connection).get(0).getIndexes().get(0);
		assertEquals("text.index", index.getName());
		assertFalse(index.getDefinition().isEmpty());
		Index restored = SchemaUtils.readXml(new java.io.StringReader(index.asXml()));
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		try (var statement = connection.createStatement()) {
			statement.execute(registry.createSql(restored, SqlType.DROP).get(0).getSqlText());
			statement.execute(registry.createSql(restored, SqlType.CREATE).get(0).getSqlText());
			try (var result = statement
					.executeQuery("SELECT doc_id,payload FROM " + schemaName + ".\"text.index\" WHERE token='hello'")) {
				assertTrue(result.next());
				assertEquals(1, result.getInt(1));
				assertEquals("payload value", result.getString(2));
			}
		}
	}

	private void verifySchemaDefinition(java.sql.Connection connection, com.sqlapp.data.db.dialect.Dialect dialect,
			String schemaName) throws Exception {
		Schema schema = new Schema(schemaName);
		String script = new VirticaDdlExporter().exportSchemaDefinition(connection, schema);
		assertTrue(script.contains("flex_table"));
		var registry = dialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(schema, SqlType.CREATE));
		// Qualify ordinary schema replay separately, without hiding the Flex blocker.
		try (var statement = connection.createStatement()) {
			statement.execute("DROP TABLE " + schemaName + ".flex_table");
		}
		new VirticaDdlExporter().exportSchemaDefinition(connection, schema);
		assertTrue(schema.getSpecifics().get("VERTICA_SCHEDULING_STATES").contains("ENABLED=false"));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(schema, SqlType.CREATE));
		verifyScheduledSchemaRestore(connection, dialect, schema);
		try (var statement = connection.createStatement()) {
			statement.execute("DROP TRIGGER " + schemaName + ".trigger_one");
			statement.execute("DROP TRIGGER " + schemaName + ".trigger_two");
			for (String schedule : java.util.List.of("schedule_one", "schedule_two", "unattached_schedule"))
				statement.execute("DROP SCHEDULE " + schemaName + "." + schedule);
		}
		new VirticaDdlExporter().exportSchemaDefinition(connection, schema);
		// Supplemental objects must not duplicate the dependency script's CREATEs.
		schema.getTables().add(new Table("supplemental_not_created"));
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		try (var statement = connection.createStatement()) {
			statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
			for (var operation : registry.createSql(restored, SqlType.CREATE))
				assertDoesNotThrow(() -> statement.execute(operation.getSqlText()), operation.getSqlText());
			try (var result = statement.executeQuery("SELECT count(*) FROM v_catalog.tables WHERE table_schema='"
					+ schemaName + "' AND table_name IN ('flex_table_keys','supplemental_not_created')")) {
				assertTrue(result.next());
				assertEquals(0, result.getInt(1));
			}
			try (var result = statement.executeQuery("SELECT count(*) FROM v_catalog.views WHERE table_schema='"
					+ schemaName + "' AND table_name='flex_table_view'")) {
				assertTrue(result.next());
				assertEquals(0, result.getInt(1));
			}
		}
	}

	private void createScheduledObjects(java.sql.Connection connection, String schemaName) throws Exception {
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEDULE " + schemaName + ".schedule_one USING DATETIMES('2099-01-01 00:00:00')");
			statement.execute("CREATE SCHEDULE " + schemaName
					+ ".schedule_two USING DATETIMES('2099-02-01 00:00:00','2099-03-01 00:00:00')");
			statement.execute("CREATE SCHEDULE " + schemaName + ".unattached_schedule USING CRON '0 0 1 1 *'");
			statement.execute("CREATE TRIGGER " + schemaName + ".trigger_one ON SCHEDULE " + schemaName
					+ ".schedule_one EXECUTE PROCEDURE " + schemaName + ".test_procedure(10) AS DEFINER");
			statement.execute("CREATE TRIGGER " + schemaName + ".trigger_two ON SCHEDULE " + schemaName
					+ ".schedule_two EXECUTE PROCEDURE " + schemaName + ".test_procedure(20) AS DEFINER");
			statement.execute("SELECT ENABLE_TRIGGER('" + schemaName + ".trigger_one',FALSE)");
			statement.execute("SELECT ENABLE_SCHEDULE('" + schemaName + ".schedule_two',FALSE)");
		}
	}

	private void verifyScheduledObjects(java.sql.Connection connection, com.sqlapp.data.db.dialect.Dialect dialect,
			String schemaName) throws Exception {
		var reader = dialect.getCatalogReader().getSchemaReader().getEventReader();
		reader.setSchemaName(schemaName);
		var events = reader.getAll(connection);
		assertEquals(2, events.size());
		Event first = SchemaUtils.readXml(new java.io.StringReader(
				events.stream().filter(e -> "trigger_one".equals(e.getName())).findFirst().orElseThrow().asXml()));
		Event second = SchemaUtils.readXml(new java.io.StringReader(
				events.stream().filter(e -> "trigger_two".equals(e.getName())).findFirst().orElseThrow().asXml()));
		assertEquals(Boolean.FALSE, first.getSpecifics().get("TRIGGER_ENABLED", Boolean.class));
		assertEquals(Boolean.TRUE, second.getSpecifics().get("TRIGGER_ENABLED", Boolean.class));
		assertEquals(Boolean.FALSE, second.getSpecifics().get("SCHEDULE_ENABLED", Boolean.class));
		assertTrue(second.getSpecifics().get("DATE_TIME_STRING").contains("2099"));
		try (var statement = connection.createStatement();
				var result = statement.executeQuery("SELECT count(*) FROM v_catalog.user_schedules WHERE schema_name='"
						+ schemaName + "' AND schedule_name='unattached_schedule'")) {
			assertTrue(result.next());
			assertEquals(1, result.getInt(1));
		}
	}

	private void verifySchedulingStateRestorer(java.sql.Connection connection,
			com.sqlapp.data.db.dialect.Dialect dialect, String schemaName) throws Exception {
		Schema snapshot = new Schema(schemaName);
		new VirticaDdlExporter().exportSchemaDefinition(connection, snapshot);
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(snapshot.asXml()));
		var restorer = new VirticaSchedulingStateRestorer();
		try (var statement = connection.createStatement()) {
			statement.execute("SELECT ENABLE_TRIGGER('" + schemaName + ".trigger_one',TRUE)");
			statement.execute("SELECT ENABLE_SCHEDULE('" + schemaName + ".schedule_two',TRUE)");
		}
		assertThrows(java.sql.SQLException.class, () -> restorer.restore(connection, restored));
		// This test owns the entire disposable VMart container. Production API only
		// checks this prerequisite; it never changes scheduler-global configuration.
		try (var statement = connection.createStatement()) {
			statement.execute("SELECT SET_CONFIG_PARAMETER('EnableStoredProcedureScheduler',0)");
			try {
				var result = restorer.restore(connection, restored);
				assertTrue(result.applied().size() >= 2);
				verifyScheduledObjects(connection, dialect, schemaName);
				assertEquals(0, restorer.restore(connection, restored).applied().size());
			} finally {
				statement.execute("SELECT SET_CONFIG_PARAMETER('EnableStoredProcedureScheduler',1)");
			}
		}
	}

	private void verifyScheduledSchemaRestore(java.sql.Connection connection,
			com.sqlapp.data.db.dialect.Dialect dialect, Schema schema) throws Exception {
		var restorer = new VirticaSchemaRestorer();
		assertThrows(IllegalArgumentException.class, () -> restorer.preview(schema));
		var events = dialect.getCatalogReader().getSchemaReader().getEventReader();
		events.setSchemaName(schema.getName());
		for (Event event : events.getAll(connection)) {
			// The fixture explicitly created both references to this schema. Catalog
			// export omits that qualification; production code must not guess it.
			event.getSpecifics().put("PROCEDURE_SCHEMA_NAME", schema.getName());
			schema.getEvents().add(event);
		}
		Schema xml = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		assertTrue(restorer.preview(xml).stream()
				.anyMatch(sql -> sql.contains("EXECUTE PROCEDURE \"" + schema.getName() + "\".")));
		assertThrows(java.sql.SQLException.class, () -> restorer.restore(connection, xml));
		try (var statement = connection.createStatement()) {
			statement.execute("SELECT SET_CONFIG_PARAMETER('EnableStoredProcedureScheduler',0)");
			try {
				assertThrows(java.sql.SQLException.class, () -> restorer.restore(connection, xml));
				statement.execute("DROP SCHEMA " + schema.getName() + " CASCADE");
				var result = restorer.restore(connection, xml);
				assertTrue(result.completedStatements().size() > 5);
				verifyScheduledObjects(connection, dialect, schema.getName());
				try (var rows = statement.executeQuery("SELECT count(*) FROM " + schema.getName() + ".source_table")) {
					assertTrue(rows.next());
					assertEquals(0, rows.getInt(1));
				}
				statement.execute("CALL " + schema.getName() + ".test_procedure(42)");
				try (var rows = statement
						.executeQuery("SELECT count(*) FROM " + schema.getName() + ".source_table WHERE id=42")) {
					assertTrue(rows.next());
					assertEquals(1, rows.getInt(1));
				}
			} finally {
				statement.execute("SELECT SET_CONFIG_PARAMETER('EnableStoredProcedureScheduler',1)");
			}
		}
	}

	private void verifyFlexSchemaRestore(java.sql.Connection connection, com.sqlapp.data.db.dialect.Dialect dialect)
			throws Exception {
		String name = "sqlapp_projection_it_" + java.util.UUID.randomUUID().toString().replace("-", "");
		boolean created = false;
		Throwable primaryFailure = null;
		try {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE SCHEMA " + name);
				created = true;
				statement.execute("CREATE FLEX TABLE " + name + ".f(id INT)");
				statement.execute("CREATE FLEX TABLE " + name + ".g()");
				statement.execute("CREATE PROJECTION " + name + ".custom_projection AS SELECT id FROM " + name
						+ ".f ORDER BY id UNSEGMENTED ALL NODES");
			}
			Schema source = new Schema(name);
			new VirticaDdlExporter().exportSchemaDefinition(connection, source);
			Schema xml = SchemaUtils.readXml(new java.io.StringReader(source.asXml()));
			var projections = dialect.getCatalogReader().getSchemaReader().getProjectionReader();
			projections.setSchemaName(name);
			var expectedNames = projections.getAll(connection).stream().map(p -> p.getName())
					.collect(java.util.stream.Collectors.toSet());
			var restorer = new VirticaSchemaRestorer();
			assertTrue(restorer.preview(xml).stream()
					.noneMatch(sql -> sql.contains("MAKE_AHM") || sql.startsWith("DROP")));
			try (var statement = connection.createStatement()) {
				statement.execute("SELECT SET_CONFIG_PARAMETER('EnableStoredProcedureScheduler',0)");
				try {
					statement.execute("DROP SCHEMA " + name + " CASCADE");
					Schema differentWidth = xml.clone();
					var raw = differentWidth.getTables().get("f").getColumns().get("__raw__");
					raw.setLength(raw.getLength() + 1);
					var mismatch = assertThrows(VirticaSchemaRestorer.RestorationException.class,
							() -> restorer.restoreWithDetails(connection, differentWidth));
					assertEquals(VirticaSchemaRestorer.Phase.GENERATED_OBJECTS, mismatch.getPhase());
					assertTrue(mismatch.getCompletedStatements().stream()
							.noneMatch(sql -> sql.contains("custom_projection")));
					statement.execute("DROP SCHEMA " + name + " CASCADE");
					var result = restorer.restoreWithDetails(connection, xml);
					assertFalse(result.verifiedGeneratedStatements().isEmpty());
					assertTrue(result.result().completedStatements().stream()
							.anyMatch(sql -> sql.contains("custom_projection")));
					assertEquals(expectedNames, projections.getAll(connection).stream().map(p -> p.getName())
							.collect(java.util.stream.Collectors.toSet()));
					try (var rows = statement
							.executeQuery("SELECT RESTORE_FLEXTABLE_DEFAULT_KEYS_TABLE_AND_VIEW('" + name + ".f')")) {
						assertTrue(rows.next());
						String status = rows.getString(1);
						assertTrue(status.contains("already exists and is linked"), status);
						assertFalse(status.contains("restored successfully"), status);
					}
					statement.execute("INSERT INTO " + name + ".f(id,payload) VALUES(7,'after restore')");
					statement.execute("SELECT COMPUTE_FLEXTABLE_KEYS_AND_BUILD_VIEW('" + name + ".f')");
					try (var rows = statement.executeQuery("SELECT payload FROM " + name + ".f_view WHERE id=7")) {
						assertTrue(rows.next());
						assertEquals("after restore", rows.getString(1));
					}
					statement.execute("INSERT INTO " + name + ".g(payload) VALUES('implicit identity')");
					try (var rows = statement
							.executeQuery("SELECT __identity__,payload::VARCHAR FROM " + name + ".g")) {
						assertTrue(rows.next());
						assertTrue(rows.getLong(1) > 0);
						assertEquals("implicit identity", rows.getString(2));
					}
					statement.execute("DROP TABLE " + name + ".f CASCADE");
					try (var rows = statement
							.executeQuery("SELECT (SELECT count(*) FROM v_catalog.tables WHERE table_schema='" + name
									+ "' AND table_name='f_keys') + (SELECT count(*) FROM v_catalog.views WHERE table_schema='"
									+ name + "' AND table_name='f_view')")) {
						assertTrue(rows.next());
						assertEquals(0, rows.getInt(1));
					}
				} finally {
					statement.execute("SELECT SET_CONFIG_PARAMETER('EnableStoredProcedureScheduler',1)");
				}
			}
		} catch (Exception | AssertionError failure) {
			primaryFailure = failure;
			throw failure;
		} finally {
			if (created)
				try (var statement = connection.createStatement()) {
					statement.execute("DROP SCHEMA " + name + " CASCADE");
				} catch (java.sql.SQLException cleanupFailure) {
					if (primaryFailure == null)
						throw cleanupFailure;
					primaryFailure.addSuppressed(cleanupFailure);
				}
		}
	}

	private String required(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("Required integration environment variable: " + name);
		return value;
	}
}
