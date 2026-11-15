/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.saphana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.saphana.SapHana;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.PublicSynonym;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcHandler;

/**
 * SAP HANA 2.0 checks for dialect gaps found in review: generic MERGE, generated
 * columns, SELECT_FOR_APP paging, public synonyms, parameter limit and spatial
 * JDBC representation.
 */
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class SapHanaDialectGapTest {
	private static final String SCHEMA = "HANA_GAP_TEST";
	private static final String PASSWORD = "HxeTest9xA";
	private static final Path PASSWORD_DIRECTORY = createPasswordDirectory();
	private static final GenericContainer<?> HANA = ReusableTestcontainers
			.configure(new GenericContainer<>(DockerImageName.parse("saplabs/hanaexpress:2.00.088.00.20251110.1"))
					.withFileSystemBind(PASSWORD_DIRECTORY.toString(), "/hana/mounts", BindMode.READ_WRITE)
					.withCommand("--passwords-url", "file:///hana/mounts/password.json", "--agree-to-sap-license",
							"--dont-check-system")
					.withExposedPorts(39041)
					.withCreateContainerCmdModifier(
							command -> command.withHostName("hxehost").getHostConfig().withShmSize(1L << 30))
					.waitingFor(
							Wait.forLogMessage(".*Startup finished.*", 1).withStartupTimeout(Duration.ofMinutes(30))));

	@BeforeAll
	static void startContainer() throws Exception {
		DialectResolver.getInstance();
		ReusableTestcontainers.start(HANA);
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA " + SCHEMA + " CASCADE");
			statement.execute("CREATE SCHEMA " + SCHEMA);
		}
	}

	@AfterAll
	static void stopContainer() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP PUBLIC SYNONYM HANA_GAP_PUBLIC_ALIAS");
			drop(statement, "DROP SCHEMA " + SCHEMA + " CASCADE");
		} finally {
			ReusableTestcontainers.stop(HANA);
			try {
				Files.deleteIfExists(PASSWORD_DIRECTORY.resolve("password.json"));
				Files.deleteIfExists(PASSWORD_DIRECTORY);
			} catch (Exception e) {
				// HANA normally removes password.json itself; cleanup is best effort.
			}
		}
	}

	/** The shared MERGE factory output (subquery source with derived column list) runs on HANA. */
	@Test
	void executesGeneratedMergeIncludingGeneratedColumn() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			statement.execute("CREATE COLUMN TABLE " + SCHEMA + ".MERGE_ITEMS (ID INTEGER PRIMARY KEY, "
					+ "NAME NVARCHAR(20), TOTAL INTEGER GENERATED ALWAYS AS (ID * 2))");
			Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(SapHana.class, dialect);
			Table table = readTable(connection, dialect, "MERGE_ITEMS");
			assertEquals("ID*2", table.getColumns().get("TOTAL").getFormula().replaceAll("[\\s()\"]", ""));
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			var nodes = registry.createSqlNodes(table, SqlType.MERGE);
			System.out.println("HANA MERGE SQL:\n" + registry.createSql(table, SqlType.MERGE).getFirst().getSqlText());
			ParametersContext context = new ParametersContext();
			context.put("ID", 7);
			context.put("NAME", "first");
			new JdbcHandler(nodes.getFirst()).execute(connection, context);
			context.put("NAME", "second");
			new JdbcHandler(nodes.getFirst()).execute(connection, context);
			assertEquals("1:second:14", single(connection,
					"SELECT COUNT(*) OVER () || ':' || NAME || ':' || TOTAL FROM " + SCHEMA + ".MERGE_ITEMS"));
		}
	}

	/** Generated INSERT and UPDATE skip GENERATED ALWAYS AS columns. */
	@Test
	void executesGeneratedInsertAndUpdateWithGeneratedColumn() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			statement.execute("CREATE COLUMN TABLE " + SCHEMA + ".FORMULA_ITEMS (ID INTEGER PRIMARY KEY, "
					+ "PRICE INTEGER, TOTAL INTEGER GENERATED ALWAYS AS (PRICE * 2))");
			Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			Table table = readTable(connection, dialect, "FORMULA_ITEMS");
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			ParametersContext context = new ParametersContext();
			context.put("ID", 1);
			context.put("PRICE", 5);
			new JdbcHandler(registry.createSqlNodes(table, SqlType.INSERT).getFirst()).execute(connection, context);
			context.put("PRICE", 6);
			new JdbcHandler(registry.createSqlNodes(table, SqlType.UPDATE).getFirst()).execute(connection, context);
			assertEquals("12", single(connection, "SELECT TOTAL FROM " + SCHEMA + ".FORMULA_ITEMS WHERE ID = 1"));
		}
	}

	/** SELECT_FOR_APP paging, including the offset-only LIMIT form. */
	@Test
	void pagesSelectForApp() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			statement.execute("CREATE COLUMN TABLE " + SCHEMA + ".PAGED_ITEMS (ID INTEGER PRIMARY KEY)");
			for (int i = 1; i <= 10; i++) {
				statement.execute("INSERT INTO " + SCHEMA + ".PAGED_ITEMS VALUES (" + i + ")");
			}
			Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			Table table = readTable(connection, dialect, "PAGED_ITEMS");
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			var node = registry.createSqlNodes(table, SqlType.SELECT_FOR_APP).getFirst();
			assertEquals(List.of(4, 5, 6), ids(connection, node, 3, 3));
			assertEquals(List.of(1, 2), ids(connection, node, 2, null));
			assertEquals(List.of(9, 10), ids(connection, node, null, 8));
			assertEquals(10, ids(connection, node, null, null).size());
		}
	}

	private List<Integer> ids(Connection connection, com.sqlapp.jdbc.sql.node.SqlNode node, Integer rows,
			Integer offset) throws SQLException {
		ParametersContext context = new ParametersContext();
		context.put(ParameterDefinition.COUNTSQL_KEY_PARANETER_NAME, false);
		if (rows != null) {
			context.put(ParameterDefinition.ROW_KEY_PARANETER_NAME, rows);
		}
		if (offset != null) {
			context.put(ParameterDefinition.OFFSET_KEY_PARANETER_NAME, offset);
		}
		List<Integer> result = new ArrayList<>();
		new JdbcHandler(node, rs -> result.add(rs.getInt("ID"))).execute(connection, context);
		return result;
	}

	/** PUBLIC synonyms are read at catalog level and recreated with PUBLIC DDL. */
	@Test
	void readsAndRecreatesPublicSynonyms() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			statement.execute("CREATE COLUMN TABLE " + SCHEMA + ".SYNONYM_TARGET (ID INTEGER)");
			statement.execute("CREATE PUBLIC SYNONYM HANA_GAP_PUBLIC_ALIAS FOR " + SCHEMA + ".SYNONYM_TARGET");
			Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			var publicReader = dialect.getCatalogReader().getPublicSynonymReader();
			ParametersContext context = new ParametersContext();
			context.put("schemaName", SCHEMA);
			List<PublicSynonym> synonyms = publicReader.getAll(connection, context);
			assertEquals(1, synonyms.size(), synonyms.toString());
			PublicSynonym synonym = synonyms.getFirst();
			assertEquals("HANA_GAP_PUBLIC_ALIAS", synonym.getName());
			assertEquals(SCHEMA, synonym.getObjectSchemaName());
			assertEquals("SYNONYM_TARGET", synonym.getObjectName());
			assertEquals(null, synonym.getDbLinkName());
			List<PublicSynonym> userSynonyms = publicReader.getAll(connection);
			assertTrue(userSynonyms.stream().anyMatch(s -> s.getName().equals("HANA_GAP_PUBLIC_ALIAS")));
			assertFalse(userSynonyms.stream().anyMatch(s -> "SYS".equals(s.getObjectSchemaName())
					|| s.getObjectSchemaName().startsWith("_SYS_")), "system public synonyms must be excluded");
			var schemaReader = dialect.getCatalogReader().getSchemaReader().getSynonymReader();
			schemaReader.setSchemaName("PUBLIC");
			assertTrue(schemaReader.getAll(connection).isEmpty(), "PUBLIC synonyms must not be schema synonyms");
			var registry = dialect.createSqlFactoryRegistry();
			for (var op : registry.createSql(synonym, SqlType.DROP)) {
				statement.execute(op.getSqlText());
			}
			assertTrue(publicReader.getAll(connection, context).isEmpty());
			for (var op : registry.createSql(synonym, SqlType.CREATE)) {
				statement.execute(op.getSqlText());
			}
			assertEquals("0", single(connection, "SELECT COUNT(*) FROM PUBLIC.HANA_GAP_PUBLIC_ALIAS"));
			assertEquals(1, publicReader.getAll(connection, context).size());
		}
	}

	/**
	 * The dialect limit is accepted; HANA express 2.00.088 rejects 65536
	 * parameters (SQL error 307).
	 */
	@Test
	void acceptsDialectParameterLimit() throws Exception {
		try (Connection connection = createConnection()) {
			Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			assertEquals(32767, dialect.getMaxStatementParameterCount());
			assertEquals("32767", countParameters(connection, dialect.getMaxStatementParameterCount()));
			SQLException e = assertThrows(SQLException.class, () -> countParameters(connection, 65536));
			assertEquals(307, e.getErrorCode(), e.getMessage());
		}
	}

	private String countParameters(Connection connection, int count) throws SQLException {
		StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM DUMMY WHERE 1 IN (");
		for (int i = 0; i < count; i++) {
			sql.append(i == 0 ? "?" : ",?");
		}
		sql.append(')');
		try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
			for (int i = 1; i <= count; i++) {
				statement.setInt(i, 1);
			}
			try (var rs = statement.executeQuery()) {
				rs.next();
				return "" + count;
			}
		}
	}

	/**
	 * Documents the ngdbc spatial representation that a future ST_GEOMETRY type
	 * mapping can rely on: values are read as little-endian WKB without SRID, and
	 * WKB bytes or (E)WKT strings can be bound directly.
	 */
	@Test
	void documentsSpatialJdbcRepresentation() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			statement.execute("CREATE COLUMN TABLE " + SCHEMA + ".SPATIAL_ITEMS (ID INTEGER PRIMARY KEY, "
					+ "G ST_GEOMETRY(4326), P ST_POINT(4326))");
			statement.execute("INSERT INTO " + SCHEMA
					+ ".SPATIAL_ITEMS VALUES (1, NEW ST_POINT('POINT(1 2)', 4326), NEW ST_POINT('POINT(1 2)', 4326))");
			try (var rs = statement.executeQuery("SELECT G, P FROM " + SCHEMA + ".SPATIAL_ITEMS")) {
				var meta = rs.getMetaData();
				assertEquals(java.sql.Types.OTHER, meta.getColumnType(1));
				assertEquals("ST_GEOMETRY", meta.getColumnTypeName(1));
				assertEquals("ST_POINT", meta.getColumnTypeName(2));
				rs.next();
				assertInstanceOf(byte[].class, rs.getObject(1));
				assertEquals("0101000000000000000000f03f0000000000000040", HexFormat.of().formatHex(rs.getBytes(1)));
				assertThrows(SQLException.class, () -> rs.getString(1));
			}
			assertEquals("G:ST_GEOMETRY:4326,P:ST_POINT:4326", single(connection,
					"SELECT STRING_AGG(C.COLUMN_NAME || ':' || C.DATA_TYPE_NAME || ':' || G.SRS_ID, ',' ORDER BY C.COLUMN_NAME) "
							+ "FROM TABLE_COLUMNS C JOIN ST_GEOMETRY_COLUMNS G ON G.SCHEMA_NAME = C.SCHEMA_NAME "
							+ "AND G.TABLE_NAME = C.TABLE_NAME AND G.COLUMN_NAME = C.COLUMN_NAME WHERE C.SCHEMA_NAME = '"
							+ SCHEMA + "' AND C.TABLE_NAME = 'SPATIAL_ITEMS'"));
			byte[] wkb = HexFormat.of().parseHex("010100000000000000000008400000000000001040");
			insertSpatial(connection, 2, ps -> ps.setBytes(1, wkb));
			insertSpatial(connection, 3, ps -> ps.setString(1, "POINT(3 4)"));
			insertSpatial(connection, 4, ps -> ps.setString(1, "SRID=4326;POINT(3 4)"));
			assertEquals("1:POINT (1 2):4326,2:POINT (3 4):4326,3:POINT (3 4):4326,4:POINT (3 4):4326",
					single(connection, "SELECT STRING_AGG(ID || ':' || G.ST_AsText() || ':' || G.ST_SRID(), ',' ORDER BY ID) FROM "
							+ SCHEMA + ".SPATIAL_ITEMS"));
		}
	}

	private interface Binder {
		void bind(PreparedStatement statement) throws SQLException;
	}

	private void insertSpatial(Connection connection, int id, Binder binder) throws SQLException {
		try (PreparedStatement ps = connection
				.prepareStatement("INSERT INTO " + SCHEMA + ".SPATIAL_ITEMS (ID, G) VALUES (" + id + ", ?)")) {
			binder.bind(ps);
			ps.executeUpdate();
		}
	}

	private static Table readTable(Connection connection, Dialect dialect, String name) {
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(SCHEMA);
		reader.setObjectName(name);
		return reader.getAllFull(connection).stream().filter(t -> name.equals(t.getName())).findFirst()
				.orElseThrow();
	}

	private static String single(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement(); var rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

	private static Connection createConnection() throws SQLException {
		String url = "jdbc:sap://localhost:" + HANA.getMappedPort(39041) + "/?databaseName=HXE";
		return DriverManager.getConnection(url, "SYSTEM", PASSWORD);
	}

	private static void drop(final Statement statement, final String sql) {
		try {
			statement.execute(sql);
		} catch (SQLException e) {
			// The disposable test database may not contain the object yet.
		}
	}

	private static Path createPasswordDirectory() {
		try {
			Path directory = Files.createTempDirectory("sqlapp-hana-gap-");
			Path file = directory.resolve("password.json");
			Files.writeString(file, "{\"master_password\":\"" + PASSWORD + "\"}", StandardCharsets.UTF_8);
			// The container user differs from the host user on Linux Docker hosts.
			try {
				Files.setPosixFilePermissions(directory, java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxrwx"));
				Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-rw-rw-"));
			} catch (UnsupportedOperationException e) {
				// Non-POSIX file systems (Windows) do not need this.
			}
			return directory;
		} catch (Exception e) {
			throw new ExceptionInInitializerError(e);
		}
	}
}
