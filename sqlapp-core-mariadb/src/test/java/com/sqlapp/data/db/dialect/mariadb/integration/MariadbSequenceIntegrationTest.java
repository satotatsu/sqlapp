/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.integration;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Sequence;

/**
 * Real-server test. Excluded from test; only run against an authorized
 * disposable target.
 */
@Tag("mariadb-integration")
class MariadbSequenceIntegrationTest {
	private static String required(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("Missing integration setting: " + name);
		return value;
	}

	static void execute(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	static class TemporarySchema implements AutoCloseable {
		final Connection connection;
		final String name = "sqlapp_mariadb_it_" + UUID.randomUUID().toString().replace("-", "");

		TemporarySchema(Connection connection) throws SQLException {
			this.connection = connection;
			execute(connection, "CREATE DATABASE `" + name + "`");
		}

		@Override
		public void close() throws SQLException {
			execute(connection, "DROP DATABASE `" + name + "`");
		}
	}

	private Sequence read(Connection connection, String schema, String name) {
		var dialect = DialectResolver.getInstance().getDialect(connection);
		var reader = dialect.getCatalogReader().getSchemaReader().getSequenceReader();
		reader.setSchemaName(schema);
		reader.setObjectName(name);
		var sequences = reader.getAllFull(connection);
		assertEquals(1, sequences.size());
		return sequences.get(0);
	}

	@Test
	void unsignedRangeBeyondSignedBigintRoundTrips() throws Exception {
		try (var connection = connect()) {
			assertTrue(connection.getMetaData().getDatabaseProductName().contains("MariaDB"));
			var dialect = DialectResolver.getInstance().getDialect(connection);
			org.junit.jupiter.api.Assumptions.assumeTrue(
					dialect instanceof com.sqlapp.data.db.dialect.mariadb.Mariadb11_50,
					"Typed unsigned sequence scenario requires MariaDB 11.5+");
			try (var schema = new TemporarySchema(connection)) {
				execute(connection, "CREATE SEQUENCE `" + schema.name + "`.source_seq AS BIGINT UNSIGNED"
						+ " MINVALUE 1 MAXVALUE 18446744073709551614 START WITH 9223372036854775808 NOCACHE");
				Sequence original = read(connection, schema.name, "source_seq");
				assertEquals(com.sqlapp.data.db.datatype.DataType.UBIGINT, original.getDataType(), original.asXml());
				assertEquals(new java.math.BigInteger("9223372036854775808"), original.getStartValue(),
						original.asXml());
				assertEquals(new java.math.BigInteger("18446744073709551614"), original.getMaxValue(),
						original.asXml());
				Sequence restored = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
				assertEquals(com.sqlapp.data.db.datatype.DataType.UBIGINT, restored.getDataType(), restored.asXml());
				restored.setName("recreated_seq");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var operation : registry.createSql(restored, SqlType.CREATE)) {
					assertTrue(operation.getSqlText().contains("AS BIGINT UNSIGNED"), operation.getSqlText());
					execute(connection, operation.getSqlText());
				}
				Sequence recreated = read(connection, schema.name, "recreated_seq");
				assertEquals(com.sqlapp.data.db.datatype.DataType.UBIGINT, recreated.getDataType());
				assertEquals(new java.math.BigInteger("9223372036854775808"), recreated.getStartValue());
				assertEquals(new java.math.BigInteger("18446744073709551614"), recreated.getMaxValue());
				assertEquals(0, recreated.getCacheSize());
			}
		}
	}

	static Connection connect() throws SQLException {
		if (!"true".equals(System.getenv("SQLAPP_MARIADB_ALLOW_SCHEMA_CREATE"))) {
			throw new IllegalStateException("Explicit authorization required: SQLAPP_MARIADB_ALLOW_SCHEMA_CREATE=true");
		}
		String url = required("SQLAPP_MARIADB_INTEGRATION_URL");
		if (!url.startsWith("jdbc:mariadb:"))
			throw new IllegalArgumentException("Expected a MariaDB JDBC URL");
		return DriverManager.getConnection(url, required("SQLAPP_MARIADB_INTEGRATION_USER"),
				System.getenv().getOrDefault("SQLAPP_MARIADB_INTEGRATION_PASSWORD", ""));
	}

	@Test
	void metadataXmlAndRecreatedSequenceRetainSettings() throws Exception {
		try (var connection = connect()) {
			assertTrue(connection.getMetaData().getDatabaseProductName().contains("MariaDB"),
					"Expected MariaDB server");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			assertTrue(dialect.supportsSequence(), "This integration test requires MariaDB 10.3+");
			try (var schema = new TemporarySchema(connection)) {
				String source = "`" + schema.name + "`.`source_seq`";
				execute(connection, "CREATE SEQUENCE " + source
						+ " START WITH 10 INCREMENT BY 2 MINVALUE 2 MAXVALUE 9998 CACHE 50 CYCLE ENGINE=InnoDB COMMENT='owner''s sequence'");
				Sequence original = read(connection, schema.name, "source_seq");
				Sequence restored = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
				restored.setName("recreated_seq");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var operation : registry.createSql(restored, SqlType.CREATE))
					execute(connection, operation.getSqlText());
				Sequence recreated = read(connection, schema.name, "recreated_seq");
				assertEquals(original.getStartValue(), recreated.getStartValue());
				assertEquals(original.getIncrementBy(), recreated.getIncrementBy());
				assertEquals(original.getMinValue(), recreated.getMinValue());
				assertEquals(original.getMaxValue(), recreated.getMaxValue());
				assertEquals(original.isCycle(), recreated.isCycle());
				assertEquals(original.getDataType(), recreated.getDataType());
				assertEquals(original.getSpecifics().get("ENGINE"), recreated.getSpecifics().get("ENGINE"));
				assertEquals("owner's sequence", recreated.getRemarks());
				assertEquals(50, original.getCacheSize());
				assertEquals(original.getCacheSize(), recreated.getCacheSize());
			}
		}
	}
}
