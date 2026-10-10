/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.alloydb;

import java.sql.Connection;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Runs the official AlloyDB Omni engine, not a PostgreSQL substitute. */
class AlloyDBOmniCompatibilityTest extends AlloyDBAssertions {
	private static final PostgreSQLContainer OMNI = new PostgreSQLContainer(
			DockerImageName.parse(System.getProperty("sqlapp.test.alloydb.image", "google/alloydbomni:16.8.0"))
					.asCompatibleSubstituteFor("postgres"))
			.withSharedMemorySize(256L * 1024 * 1024).withStartupTimeout(Duration.ofMinutes(3));

	@BeforeAll
	static void start() throws Exception {
		OMNI.start();
		try (var connection = OMNI.createConnection("")) {
			int expected = Integer.parseInt(System.getProperty("sqlapp.test.alloydb.expectedEngineMajor", "16"));
			org.junit.jupiter.api.Assertions.assertEquals(expected, connection.getMetaData().getDatabaseMajorVersion());
		}
	}

	@AfterAll
	static void stop() {
		OMNI.stop();
	}

	@Override
	Connection connect() throws Exception {
		return OMNI.createConnection("");
	}

	@Override
	boolean alloyDbTarget() {
		return true;
	}

	@org.junit.jupiter.api.Test
	void identifiesWithColumnarDisabledAndWithoutSuperuser() throws Exception {
		String role = "sqlapp_role_" + java.util.UUID.randomUUID().toString().replace("-", "");
		String password = java.util.UUID.randomUUID().toString();
		try (var admin = connect(); var statement = admin.createStatement()) {
			try (var rows = statement.executeQuery(
					"SELECT setting FROM pg_catalog.pg_settings WHERE name = 'google_columnar_engine.enabled'")) {
				org.junit.jupiter.api.Assertions.assertTrue(rows.next());
				org.junit.jupiter.api.Assertions.assertEquals("off", rows.getString(1));
			}
			statement.execute("CREATE ROLE " + role + " LOGIN PASSWORD '" + password + "'");
			try (var connection = java.sql.DriverManager.getConnection(OMNI.getJdbcUrl(), role, password)) {
				connection.setAutoCommit(false);
				org.junit.jupiter.api.Assertions.assertInstanceOf(com.sqlapp.data.db.dialect.alloydb.AlloyDB.class,
						com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(connection));
				try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT 42")) {
					org.junit.jupiter.api.Assertions.assertTrue(rows.next());
					org.junit.jupiter.api.Assertions.assertEquals(42, rows.getInt(1));
				}
				connection.rollback();
			} finally {
				statement.execute("DROP ROLE " + role);
			}
		}
	}

	@org.junit.jupiter.api.Test
	void scannMetadataXmlAndDdlRecreateDistanceIndexes() throws Exception {
		String schema = "sqlapp_scann_" + java.util.UUID.randomUUID().toString().replace("-", "");
		try (var connection = connect(); var statement = connection.createStatement()) {
			statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
			statement.execute("CREATE EXTENSION IF NOT EXISTS alloydb_scann");
			try {
				statement.execute("CREATE SCHEMA " + schema);
				statement.execute("CREATE TABLE " + schema + ".\"Vectors\" (id integer, \"Embedding\" vector(3))");
				statement.execute("INSERT INTO " + schema + ".\"Vectors\" SELECT x, ('['||x||',1,2]')::vector FROM generate_series(1,10000) x");
				statement.execute("CREATE INDEX regular_id ON " + schema + ".\"Vectors\" (id)");
				var original = new java.util.LinkedHashMap<String, String>();
				for (String distance : new String[] { "cosine", "l2", "dot_product" }) {
					String name = "Scann " + distance;
					statement.execute("CREATE INDEX \"" + name + "\" ON " + schema
							+ ".\"Vectors\" USING scann (\"Embedding\" " + distance + ") WITH (num_leaves=1)");
					statement.execute("COMMENT ON INDEX " + schema + ".\"" + name + "\" IS 'ScaNN comment'");
					try (var rows = statement.executeQuery("SELECT pg_get_indexdef('" + schema + ".\"" + name + "\"'::regclass)")) {
						org.junit.jupiter.api.Assertions.assertTrue(rows.next());
						original.put(name, rows.getString(1));
					}
				}
				var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(connection);
				statement.execute("CREATE INDEX \"Scann expression\" ON " + schema
						+ ".\"Vectors\" USING scann (((\"Embedding\" + '[1,0,0]'::vector)::vector(3)) l2)"
						+ " WITH (num_leaves=1, quantizer='FLAT') WHERE id > 0");
				var modelTable = new com.sqlapp.data.schemas.Table("Vectors").setSchemaName(schema);
				modelTable.getColumns().add("Embedding", c -> c.setDataTypeName("vector(3)"));
				var newIndex = new com.sqlapp.data.schemas.Index("Scann model");
				newIndex.getColumns().add("Embedding");
				newIndex.setIndexType(com.sqlapp.data.schemas.IndexType.Vector);
				newIndex.setVectorDistanceType(com.sqlapp.data.schemas.VectorDistanceType.Euclidean);
				modelTable.getIndexes().add(newIndex);
				com.sqlapp.data.db.sql.SqlFactory<com.sqlapp.data.schemas.Index> modelFactory = dialect.createSqlFactoryRegistry()
						.getSqlFactory(newIndex, com.sqlapp.data.db.sql.SqlType.CREATE);
				for (var sql : modelFactory.createSql(newIndex)) statement.execute(sql.getSqlText());
				for (String name : new String[] { "Scann expression", "Scann model" }) {
					statement.execute("COMMENT ON INDEX " + schema + ".\"" + name + "\" IS 'ScaNN comment'");
					try (var rows = statement.executeQuery("SELECT pg_get_indexdef('" + schema + ".\"" + name + "\"'::regclass)")) {
						org.junit.jupiter.api.Assertions.assertTrue(rows.next());
						original.put(name, rows.getString(1));
					}
				}
				var indexQueries = new java.util.concurrent.atomic.AtomicInteger();
				var counted = (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
						new Class<?>[] { Connection.class }, (proxy, method, args) -> {
					if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
							&& sql.matches("(?is).*FROM\\s+pg_catalog\\.pg_index\\b.*")) indexQueries.incrementAndGet();
					try { return method.invoke(connection, args); }
					catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
				});
				var indexReader = new com.sqlapp.data.db.dialect.alloydb.metadata.AlloyDBIndexReader(dialect);
				indexReader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				indexReader.setSchemaName(schema);
				indexReader.setObjectName("Vectors");
				indexReader.setIndexName("Scann cosine");
				org.junit.jupiter.api.Assertions.assertEquals(1, indexReader.getAll(counted).size());
				org.junit.jupiter.api.Assertions.assertEquals(2, indexQueries.get(), "One base + one ScaNN query");
				indexQueries.set(0);
				indexReader.setIndexName(null);
				org.junit.jupiter.api.Assertions.assertEquals(6, indexReader.getAll(counted).size());
				org.junit.jupiter.api.Assertions.assertEquals(2, indexQueries.get(), "Query count must not grow with index count");
				indexQueries.set(0);
				indexReader.setIndexName("regular_id");
				org.junit.jupiter.api.Assertions.assertEquals(1, indexReader.getAll(counted).size());
				org.junit.jupiter.api.Assertions.assertEquals(1, indexQueries.get(), "Ordinary indexes need no ScaNN query");
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(schema);
				reader.setObjectName("Vectors");
				var table = reader.getAllFull(connection).get(0);
				com.sqlapp.data.schemas.Table restored = com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(table.asXml()));
				org.junit.jupiter.api.Assertions.assertNull(restored.getIndexes().get("regular_id").getSpecifics()
						.get(com.sqlapp.data.db.dialect.alloydb.sql.AlloyDBCreateIndexFactory.METHOD));
				for (var entry : original.entrySet()) {
					var index = restored.getIndexes().get(entry.getKey());
					org.junit.jupiter.api.Assertions.assertNotNull(index);
					org.junit.jupiter.api.Assertions.assertEquals(com.sqlapp.data.schemas.IndexType.Vector, index.getIndexType());
					org.junit.jupiter.api.Assertions.assertEquals("ScaNN comment", index.getRemarks());
					org.junit.jupiter.api.Assertions.assertNotNull(index.getVectorDistanceType());
					com.sqlapp.data.db.sql.SqlFactory<com.sqlapp.data.schemas.Index> drop = dialect.createSqlFactoryRegistry()
							.getSqlFactory(index, com.sqlapp.data.db.sql.SqlType.DROP);
					for (var sql : drop.createSql(index)) statement.execute(sql.getSqlText());
					com.sqlapp.data.db.sql.SqlFactory<com.sqlapp.data.schemas.Index> create = dialect.createSqlFactoryRegistry()
							.getSqlFactory(index, com.sqlapp.data.db.sql.SqlType.CREATE);
					for (var sql : create.createSql(index)) statement.execute(sql.getSqlText());
					try (var rows = statement.executeQuery("SELECT pg_get_indexdef('" + schema + ".\"" + entry.getKey() + "\"'::regclass)")) {
						org.junit.jupiter.api.Assertions.assertTrue(rows.next());
						org.junit.jupiter.api.Assertions.assertEquals(entry.getValue(), rows.getString(1));
					}
				}
				// Approximate ScaNN results do not promise the exact nearest row.
				// Keep this operator/result control deterministic; index recreation is checked above.
				statement.execute("SET enable_indexscan=off");
				statement.execute("SET enable_bitmapscan=off");
				try (var rows = statement.executeQuery("SELECT id FROM " + schema
						+ ".\"Vectors\" ORDER BY \"Embedding\" <-> '[1,1,2]'::vector LIMIT 1")) {
					org.junit.jupiter.api.Assertions.assertTrue(rows.next());
					org.junit.jupiter.api.Assertions.assertEquals(1, rows.getInt(1));
				}
			} finally {
				statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
			}
		}
	}

	@org.junit.jupiter.api.Test
	void persistentColumnarSelectionSurvivesXmlAndRestart() throws Exception {
		try (var columnar = new PostgreSQLContainer(DockerImageName.parse(
				System.getProperty("sqlapp.test.alloydb.image", "google/alloydbomni:16.8.0"))
				.asCompatibleSubstituteFor("postgres")) {
			@Override
			public com.github.dockerjava.api.command.InspectContainerResponse getContainerInfo() {
				return getContainerId() == null ? super.getContainerInfo() : getCurrentContainerInfo();
			}
		}
				.withSharedMemorySize(256L * 1024 * 1024).withStartupTimeout(Duration.ofMinutes(3))
				.withCommand("postgres", "-c", "google_columnar_engine.enabled=on", "-c",
						"google_columnar_engine.memory_size_in_mb=128", "-c",
						"google_columnar_engine.enable_auto_columnarization=off")) {
			columnar.start();
			String schema = "Columnar_" + java.util.UUID.randomUUID().toString().replace("-", "");
			String prefix = columnar.getDatabaseName() + "." + schema + ".";
			String baseline = prefix + "One(Id)," + prefix + "Keep(id)";
			try (var connection = columnar.createConnection(""); var statement = connection.createStatement()) {
				statement.execute("CREATE SCHEMA \"" + schema + "\"");
				statement.execute("CREATE TABLE \"" + schema + "\".\"One\" (\"Id\" integer, value text)");
				statement.execute("CREATE TABLE \"" + schema + "\".\"Keep\" (id integer)");
				statement.execute("INSERT INTO \"" + schema + "\".\"One\" SELECT x, 'value' FROM generate_series(1,1000) x");
				statement.execute("INSERT INTO \"" + schema + "\".\"Keep\" SELECT generate_series(1,1000)");
				statement.execute(new com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder(
						new com.sqlapp.data.db.dialect.alloydb.AlloyDB16())
						._add("ALTER SYSTEM SET google_columnar_engine.relations = ").sqlChar(baseline).toString());
				statement.execute("SELECT pg_catalog.pg_reload_conf()");
			}
			awaitColumnarSetting(columnar, baseline);
			com.sqlapp.data.schemas.Catalog restored;
			com.sqlapp.data.db.dialect.alloydb.AlloyDBColumnarConfiguration.Plan plan;
			try (var connection = columnar.createConnection("")) {
				var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader();
				reader.setCatalogName(connection.getCatalog());
				reader.setReadDbObjectPredicate((object, childReader) -> {
					if (object instanceof com.sqlapp.data.schemas.Schema value) return schema.equals(value.getName());
					if (object instanceof com.sqlapp.data.schemas.Table value) return "One".equals(value.getName());
					return true;
				});
				var catalog = reader.getAllFull(connection).get(0);
				org.junit.jupiter.api.Assertions.assertEquals("on", catalog.getSettings().get("google_columnar_engine.enabled").getValue());
				var table = catalog.getSchemas().get(schema).getTables().get("One");
				org.junit.jupiter.api.Assertions.assertEquals("Id", table.getSpecifics().get(
						com.sqlapp.data.db.dialect.alloydb.AlloyDBColumnarConfiguration.COLUMNS));
				org.junit.jupiter.api.Assertions.assertNull(catalog.getSchemas().get(schema).getTables().get("Keep"));
				restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(catalog.asXml()));
				com.sqlapp.data.db.dialect.alloydb.AlloyDBColumnarConfiguration.setColumns(
						restored.getSchemas().get(schema).getTables().get("One"), "Id", "value");
				plan = com.sqlapp.data.db.dialect.alloydb.AlloyDBColumnarConfiguration.plan(restored);
				org.junit.jupiter.api.Assertions.assertEquals(prefix + "One(Id,value)," + prefix + "Keep(id)", plan.relations());
				org.junit.jupiter.api.Assertions.assertTrue(plan.requiresAutoCommit());
				try (var statement = connection.createStatement()) {
					for (var sql : plan.sqlOperations()) statement.execute(sql.getSqlText());
				}
			}
			awaitColumnarSetting(columnar, plan.relations());
			org.testcontainers.DockerClientFactory.instance().client().restartContainerCmd(columnar.getContainerId()).exec();
			awaitColumnarSetting(columnar, plan.relations());
			long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(45);
			boolean populated = false;
			while (System.nanoTime() < deadline) {
				try (var connection = columnar.createConnection(""); var statement = connection.prepareStatement(
						"SELECT count(*) FROM public.g_columnar_columns WHERE schema_name=? AND relation_name IN ('One','Keep')")) {
					statement.setString(1, schema);
					try (var rows = statement.executeQuery()) { rows.next(); populated = rows.getInt(1) == 3; }
				}
				if (populated) break;
				Thread.sleep(200);
			}
			org.junit.jupiter.api.Assertions.assertTrue(populated, "Persistent selected columns were not populated after restart");
			// A fresh settings baseline is required for subsequent edits.
			restored.getSettings().get(com.sqlapp.data.db.dialect.alloydb.AlloyDBColumnarConfiguration.RELATIONS)
					.setValue(plan.relations());
			com.sqlapp.data.db.dialect.alloydb.AlloyDBColumnarConfiguration.read(restored);
			org.junit.jupiter.api.Assertions.assertTrue(com.sqlapp.data.db.dialect.alloydb.AlloyDBColumnarConfiguration
					.plan(restored).sqlOperations().isEmpty());
		}
	}

	private static void awaitColumnarSetting(PostgreSQLContainer container, String expected) throws Exception {
		long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(45);
		java.sql.SQLException lastFailure = null;
		while (System.nanoTime() < deadline) {
			try (var connection = container.createConnection(""); var statement = connection.createStatement();
					var rows = statement.executeQuery("SELECT setting FROM pg_catalog.pg_settings WHERE name='google_columnar_engine.relations'")) {
				if (rows.next() && expected.equals(rows.getString(1))) return;
			} catch (java.sql.SQLException e) { lastFailure = e; }
			Thread.sleep(200);
		}
		throw new AssertionError("Columnar relations setting did not become active: " + expected, lastFailure);
	}


    @org.junit.jupiter.api.Test
    void preservesSequenceAndAdvancedIndexMetadata() throws Exception {
        try (var c = connect()) {
            com.sqlapp.data.db.dialect.test.postgres.PostgresMetadataRegressionAssertions.verify(c, com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(c));
        }
    }
}
