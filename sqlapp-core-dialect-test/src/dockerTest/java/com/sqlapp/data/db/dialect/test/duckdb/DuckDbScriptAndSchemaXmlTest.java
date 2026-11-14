/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.duckdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.ExportSchemaXmlCommand;
import com.sqlapp.data.db.command.SqlExecuteCommand;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.duckdb.DuckDb;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Host DuckDB file integration through the command layer; no Docker container
 * or external database is needed.
 */
class DuckDbScriptAndSchemaXmlTest {

	private static final String SCRIPT = """
			/* DuckDB schema; with /* nested */ comments */
			CREATE SCHEMA s1;
			CREATE TYPE s1.mood AS ENUM ('sad', 'o''k');
			CREATE SEQUENCE s1.seq1 START WITH 5;
			CREATE TABLE s1.parent (
			  id INTEGER PRIMARY KEY
			, code VARCHAR NOT NULL DEFAULT 'x;y'
			, mood s1.mood
			, twice INTEGER GENERATED ALWAYS AS (id * 2) VIRTUAL
			);
			CREATE TABLE s1.child (
			  id BIGINT DEFAULT nextval('s1.seq1') PRIMARY KEY
			, parent_id INTEGER REFERENCES s1.parent (id)
			);
			CREATE INDEX ix_child_parent ON s1.child (parent_id);
			CREATE VIEW s1.v_parent AS SELECT id, code FROM s1.parent;
			CREATE MACRO s1.label(v) AS v || $$;$$;
			CREATE MACRO s1.parents(n) AS TABLE SELECT * FROM s1.parent WHERE id <= n;
			COMMENT ON TABLE s1.parent IS 'parent; table';
			INSERT INTO s1.parent (id, code, mood) VALUES (1, E'a\\';b', 'o''k');
			""";

	@TempDir
	Path directory;

	private HikariDataSource dataSource(final String name) {
		final HikariConfig config = new HikariConfig();
		config.setJdbcUrl("jdbc:duckdb:" + directory.resolve(name));
		config.setDriverClassName("org.duckdb.DuckDBDriver");
		config.setMaximumPoolSize(1);
		return new HikariDataSource(config);
	}

	private void run(final HikariDataSource source, final String file, final String sql) throws Exception {
		final Path script = directory.resolve(file);
		Files.writeString(script, sql, StandardCharsets.UTF_8);
		final SqlExecuteCommand command = new SqlExecuteCommand();
		command.setCloseDataSource(false);
		command.setDataSource(source);
		command.setFiles(List.of(script.toFile()));
		command.run();
	}

	@Test
	void scriptExportedXmlAndGeneratedDdlRoundTrip() throws Exception {
		final String xml;
		try (HikariDataSource source = dataSource("source.duckdb")) {
			run(source, "schema.sql", SCRIPT);
			try (var connection = source.getConnection();
					var statement = connection.createStatement();
					var rs = statement.executeQuery("SELECT s1.label(code), CAST(mood AS VARCHAR) FROM s1.parent")) {
				assertTrue(rs.next());
				assertEquals("a';b;", rs.getString(1));
				assertEquals("o'k", rs.getString(2));
			}

			final ExportSchemaXmlCommand export = new ExportSchemaXmlCommand();
			export.setCloseDataSource(false);
			export.setDataSource(source);
			export.setOutputDirectory(directory.resolve("export").toFile());
			export.setIncludeSchemas("s1");
			export.run();
			final String exported = Files.readString(new File(export.getOutputFileFullPath()).toPath(),
					StandardCharsets.UTF_8);
			assertTrue(exported.contains("parents") && exported.contains("mood"), exported);

			try (var connection = source.getConnection()) {
				final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
				assertInstanceOf(DuckDb.class, dialect);
				final var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName("s1");
				xml = reader.getAllFull(connection).get(0).asXml();
			}
		}

		final Schema schema = SchemaUtils.readXml(new StringReader(xml));
		assertEquals(DataType.ENUM, schema.getDomains().get("mood").getDataType());
		assertEquals(2, schema.getFunctions().size());
		try (HikariDataSource target = dataSource("target.duckdb")) {
			final Dialect dialect;
			try (var connection = target.getConnection()) {
				dialect = DialectResolver.getInstance().getDialect(connection);
			}
			final SqlFactoryRegistry registry = dialect.createSqlFactoryRegistry();
			final List<SqlOperation> operations = new ArrayList<>();
			operations.add(new SqlOperation("CREATE SCHEMA s1", SqlType.CREATE));
			schema.getDomains().forEach(d -> operations.addAll(registry.createSql(d, SqlType.CREATE)));
			schema.getSequences().forEach(s -> operations.addAll(registry.createSql(s, SqlType.CREATE)));
			for (final String table : List.of("parent", "child")) {
				operations.addAll(registry.createSql(schema.getTables().get(table), SqlType.CREATE));
			}
			schema.getViews().forEach(v -> operations.addAll(registry.createSql(v, SqlType.CREATE)));
			schema.getFunctions().forEach(f -> operations.addAll(registry.createSql(f, SqlType.CREATE)));
			final StringBuilder script = new StringBuilder();
			for (final SqlOperation operation : operations) {
				script.append(operation.getSqlText()).append(";\n");
			}
			run(target, "generated.sql", script.toString());
			try (var connection = target.getConnection();
					var statement = connection.createStatement();
					var rs = statement.executeQuery("""
							SELECT s1.label('z'), (SELECT count(*) FROM s1.parents(10)),
							  (SELECT comment FROM duckdb_tables() WHERE table_name = 'parent'),
							  (SELECT count(*) FROM duckdb_indexes() WHERE index_name = 'ix_child_parent'),
							  (SELECT array_to_string(labels, ',') FROM duckdb_types() WHERE type_name = 'mood')
							""")) {
				assertTrue(rs.next(), script.toString());
				assertEquals("z;", rs.getString(1));
				assertEquals(0, rs.getInt(2));
				assertEquals("parent; table", rs.getString(3));
				assertEquals(1, rs.getInt(4));
				assertEquals("sad,o'k", rs.getString(5));
			}
		}
	}
}
