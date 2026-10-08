/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

/** Opt-in real YSQL test: creates and drops only its own randomly named schema. */
@EnabledIfEnvironmentVariable(named = "SQLAPP_YSQL_JDBC_URL", matches = ".+")
class YugabyteMetadataRoundTripTest {
	@Test
	void readsAndRecreatesTableFromSchemaModel() throws Exception {
		String schemaName = "sqlapp_ysql_" + UUID.randomUUID().toString().replace("-", "");
		String user = System.getenv("SQLAPP_YSQL_USER");
		String password = System.getenv("SQLAPP_YSQL_PASSWORD");
		assertNotNull(user, "Set SQLAPP_YSQL_USER for an authorized disposable YSQL database");
		assertNotNull(password, "Set SQLAPP_YSQL_PASSWORD (empty is allowed)");
		try (var connection = DriverManager.getConnection(System.getenv("SQLAPP_YSQL_JDBC_URL"), user, password);
				var statement = connection.createStatement()) {
			var dialect = DialectResolver.getInstance().getDialect(connection);
			assertEquals("YugabyteDB", dialect.getProductName());
			statement.execute("CREATE SCHEMA " + schemaName);
			try {
				statement.execute("CREATE TABLE " + schemaName
						+ ".source_table (id integer PRIMARY KEY, label varchar(40) NOT NULL)");
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(schemaName);
				reader.setObjectName("source_table");
				Table table = reader.getAllFull(connection).stream()
						.filter(t -> "source_table".equals(t.getName())).findFirst().orElseThrow();
				assertNotNull(table.getColumns().get("id"));
				assertTrue(table.getColumns().get("label").isNotNull());
				assertNotNull(table.getConstraints().getPrimaryKeyConstraint());
				statement.execute("DROP TABLE " + schemaName + ".source_table");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				SqlFactory<Table> factory = registry.getSqlFactory(table, SqlType.CREATE);
				for (var operation : factory.createSql(table)) {
					statement.execute(operation.getSqlText());
				}
				statement.execute("INSERT INTO " + schemaName + ".source_table VALUES (1, 'roundtrip')");
				try (var rows = statement.executeQuery("SELECT label FROM " + schemaName + ".source_table WHERE id=1")) {
					assertTrue(rows.next());
					assertEquals("roundtrip", rows.getString(1));
				}
			} finally {
				statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
			}
		}
	}
}
