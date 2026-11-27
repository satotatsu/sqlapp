/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;

class HsqlCyclicForeignKeysTest {
	@Test
	void schemaRecreationDefersMutualAndSelfReferencesWithoutChangingModel() throws Exception {
		verify(false);
	}

	@Test
	void catalogRecreationDefersCrossSchemaReferences() throws Exception {
		verify(true);
	}

	private void verify(boolean crossSchema) throws Exception {
		String second = crossSchema ? "OTHER" : "APP";
		try (var source = DriverManager.getConnection("jdbc:hsqldb:mem:fk-source-" + crossSchema, "SA", "");
				var sql = source.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			if (crossSchema)
				sql.execute("CREATE SCHEMA OTHER AUTHORIZATION DBA");
			sql.execute("CREATE TABLE APP.A (ID INTEGER PRIMARY KEY, B_ID INTEGER, PARENT_ID INTEGER)");
			sql.execute("CREATE TABLE " + second + ".B (ID INTEGER PRIMARY KEY, A_ID INTEGER)");
			sql.execute("ALTER TABLE APP.A ADD CONSTRAINT A_B FOREIGN KEY(B_ID) REFERENCES " + second + ".B(ID)");
			sql.execute("ALTER TABLE " + second + ".B ADD CONSTRAINT B_A FOREIGN KEY(A_ID) REFERENCES APP.A(ID)");
			sql.execute("ALTER TABLE APP.A ADD CONSTRAINT A_SELF FOREIGN KEY(PARENT_ID) REFERENCES APP.A(ID)");
			var dialect = DialectResolver.getInstance().getDialect(source);
			var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("APP");
			var read = reader.getAllFull(source).get(0);
			Schema schema = SchemaUtils.readXml(new java.io.StringReader(read.asXml()));
			var catalog = new Catalog("PUBLIC");
			catalog.getSchemas().add(schema);
			if (crossSchema) {
				reader.setSchemaName(second);
				Schema other = SchemaUtils.readXml(new java.io.StringReader(reader.getAllFull(source).get(0).asXml()));
				catalog.getSchemas().add(other);
			}
			String before = catalog.asXml();
			var registry = dialect.createSqlFactoryRegistry();
			var operations = crossSchema ? registry.createSql(catalog, SqlType.CREATE)
					: registry.createSql(schema, SqlType.CREATE);
			assertEquals(before, catalog.asXml());
			try (var target = DriverManager.getConnection("jdbc:hsqldb:mem:fk-target-" + crossSchema, "SA", "");
					var statement = target.createStatement()) {
				for (var operation : operations)
					statement.execute(operation.getSqlText());
				statement.execute("INSERT INTO APP.A VALUES (1,NULL,NULL)");
				statement.execute("INSERT INTO " + second + ".B VALUES (2,1)");
				statement.execute("UPDATE APP.A SET B_ID=2, PARENT_ID=1 WHERE ID=1");
				assertThrows(java.sql.SQLException.class,
						() -> statement.execute("DELETE FROM " + second + ".B WHERE ID=2"));
				assertThrows(java.sql.SQLException.class,
						() -> statement.execute("INSERT INTO APP.A VALUES (3,NULL,999)"));
			}
		}
	}
}
