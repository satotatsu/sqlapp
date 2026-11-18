/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.duckdb;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.rowiterator.JdbcDynamicRowIteratorHandler;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

/** Opt-in local extension tests: never INSTALL or download from a test. */
class DuckDbExtensionRoundTripTest {
	@Test
	void spatialAndInetSurviveSchemaXmlBatchAndUpsert() throws Exception {
		final String directory = System.getenv("SQLAPP_DUCKDB_EXTENSION_DIRECTORY");
		assumeTrue(directory != null && !directory.isBlank(),
				"Set SQLAPP_DUCKDB_EXTENSION_DIRECTORY to preinstalled official extensions");
		try (var source = DriverManager.getConnection("jdbc:duckdb:");
				var target = DriverManager.getConnection("jdbc:duckdb:")) {
			final var dialect = DialectResolver.getInstance().getDialect(source);
			assumeTrue(dialect instanceof DuckDbVersions.DuckDb20);
			load(source, directory);
			load(target, directory);
			execute(source, "CREATE TABLE t(id INTEGER PRIMARY KEY, g GEOMETRY, ip INET)");
			execute(source, """
					INSERT INTO t VALUES
					(1, ST_GeomFromText('POINT Z (1.1234567890123457 -2.5 3.25)'), '192.168.1.2/24'),
					(2, ST_GeomFromText('LINESTRING (0 0, 1.25 2.5)'), '2001:db8::1/64'),
					(3, ST_GeomFromText('POLYGON EMPTY'), '::ffff:192.0.2.128/128'),
					(4, NULL, NULL)
					""");
			final var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("main");
			final Schema schema = SchemaUtils.readXml(new StringReader(reader.getAllFull(source).getFirst().asXml()));
			schema.setDialect(dialect);
			final var table = schema.getTables().get("t");
			for (var operation : dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE))
				execute(target, operation.getSqlText());
			table.setRowIteratorHandler(new JdbcDynamicRowIteratorHandler(source));
			assertEquals(4, BulkInsertResolver.execute(target, table, BulkOption.defaults()));
			final String query = "SELECT id, ST_AsWKB(g)::VARCHAR, ip::VARCHAR FROM t ORDER BY id";
			assertEquals(values(source, query), values(target, query));
			assertEquals(4, BulkUpsertResolver.execute(target, table, BulkUpsertOption.defaults()));
			assertEquals(values(source, query), values(target, query));
			assertEquals(4, BulkUpsertResolver.execute(target, table,
					BulkUpsertOption.builder().insertWhenNotMatched(false).build()));
			assertEquals(values(source, query), values(target, query));
		}
	}

	private static void load(Connection connection, String directory) throws Exception {
		execute(connection, "SET autoinstall_known_extensions=false");
		execute(connection, "SET autoload_known_extensions=false");
		execute(connection, "SET extension_directories=['" + directory.replace('\\', '/').replace("'", "''") + "']");
		execute(connection, "LOAD spatial");
		execute(connection, "LOAD inet");
	}

	private static void execute(Connection connection, String sql) throws Exception {
		try (var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static List<String> values(Connection connection, String sql) throws Exception {
		final List<String> result = new ArrayList<>();
		try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
			while (rows.next())
				for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++)
					result.add(rows.getString(i));
		}
		return result;
	}
}
