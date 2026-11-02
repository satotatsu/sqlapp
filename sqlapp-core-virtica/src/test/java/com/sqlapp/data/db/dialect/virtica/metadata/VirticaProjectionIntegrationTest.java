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

	private String required(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("Required integration environment variable: " + name);
		return value;
	}
}
