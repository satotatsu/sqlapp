/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.db2;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Executed only against explicitly authorized disposable Db2 containers. */
final class Db2TypeGraphAssertions {
	private Db2TypeGraphAssertions() {
	}

	static void verify(Connection connection) throws Exception {
		verify(connection, false);
	}

	static void verifyCrossSchema(Connection connection) throws Exception {
		verify(connection, true);
	}

	private static void verify(Connection connection, boolean crossSchema) throws Exception {
		String schema = crossSchema ? "SQLAPP_TYPE_USE" : "SQLAPP_TYPE_GRAPH";
		String baseSchema = crossSchema ? "SQLAPP_TYPE_BASE" : schema;
		List<String> drops = new ArrayList<>(List.of("DROP FUNCTION " + schema + ".F", "DROP TYPE " + schema + ".C",
				"DROP TYPE " + schema + ".MC", "DROP TYPE " + schema + ".N", "DROP TYPE " + schema + ".A",
				"DROP TYPE " + baseSchema + ".BASE", "DROP TYPE " + baseSchema + ".D",
				"DROP MODULE " + baseSchema + ".M", "DROP SCHEMA " + schema + " RESTRICT"));
		if (crossSchema)
			drops.add("DROP SCHEMA " + baseSchema + " RESTRICT");
		Throwable originalFailure = null;
		try (var statement = connection.createStatement()) {
			try {
				statement.execute("CREATE SCHEMA " + baseSchema);
				if (crossSchema)
					statement.execute("CREATE SCHEMA " + schema);
				statement.execute("CREATE MODULE " + baseSchema + ".M");
				statement.execute("ALTER MODULE " + baseSchema + ".M PUBLISH TYPE R AS ROW (ID INTEGER)");
				statement.execute("CREATE TYPE " + baseSchema + ".D AS INTEGER WITH STRONG TYPE RULES");
				statement.execute(
						"CREATE TYPE " + baseSchema + ".BASE AS ROW (ID " + baseSchema + ".D, LABEL VARCHAR(10))");
				statement.execute("CREATE TYPE " + schema + ".A AS " + baseSchema + ".BASE ARRAY[4]");
				statement.execute("CREATE TYPE " + schema + ".N AS ROW (ITEMS " + schema + ".A)");
				statement.execute("CREATE TYPE " + schema + ".C AS " + baseSchema + ".BASE CURSOR");
				statement.execute("CREATE TYPE " + schema + ".MC AS " + baseSchema + ".M.R CURSOR");
				statement.execute("CREATE FUNCTION " + schema + ".F() RETURNS INTEGER LANGUAGE SQL BEGIN DECLARE X "
						+ baseSchema + ".BASE; SET X.ID=" + baseSchema + ".D(7); RETURN " + baseSchema
						+ ".INTEGER(X.ID); END");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName(schema);
				Schema original = reader.getAllFull(connection).stream().filter(value -> schema.equals(value.getName()))
						.findFirst().orElseThrow();
				Schema restored = SchemaUtils.readXml(new StringReader(original.asXml()));
				Schema restoredBase = restored;
				if (crossSchema) {
					reader.setSchemaName(baseSchema);
					Schema base = reader.getAllFull(connection).stream()
							.filter(value -> baseSchema.equals(value.getName())).findFirst().orElseThrow();
					restoredBase = SchemaUtils.readXml(new StringReader(base.asXml()));
					reader.setSchemaName(schema);
				}
				assertEquals("D",
						restoredBase.getTypes().get("BASE").getColumns().get("ID").getSpecifics().get("TYPENAME"));
				assertEquals("BASE", restored.getDomains().get("A").getSpecifics().get("SOURCENAME"));
				assertEquals("M", restored.getTypes().get("MC").getSpecifics().get("DB2_CURSOR_ROW_MODULE"));
				var types = new ArrayList<>(restored.getTypes());
				Collections.reverse(types);
				restored.getTypes().clear();
				restored.getTypes().addAll(types);
				var domains = new ArrayList<>(restored.getDomains());
				Collections.reverse(domains);
				restored.getDomains().clear();
				restored.getDomains().addAll(domains);
				var catalog = new Catalog();
				catalog.getSchemas().add(restored);
				if (crossSchema)
					catalog.getSchemas().add(restoredBase);
				SqlFactory<Catalog> factory = dialect.createSqlFactoryRegistry().getSqlFactory(catalog, SqlType.CREATE);
				var operations = factory.createSql(catalog);
				if (crossSchema) {
					assertEquals(schema, catalog.getSchemas().getFirst().getName());
					assertTrue(operations.stream().filter(value -> value.getSqlText().startsWith("CREATE TYPE"))
							.findFirst().orElseThrow().getSqlText().contains(baseSchema));
				}
				for (String sql : drops)
					statement.execute(sql);
				for (var operation : operations)
					statement.execute(operation.getSqlText());
				Schema reread = reader.getAllFull(connection).stream().filter(value -> schema.equals(value.getName()))
						.findFirst().orElseThrow();
				assertEquals(original.getTypes().size(), reread.getTypes().size());
				assertEquals(original.getDomains().size(), reread.getDomains().size());
				assertEquals("M", reread.getTypes().get("MC").getSpecifics().get("DB2_CURSOR_ROW_MODULE"));
				assertEquals(baseSchema, reread.getTypes().get("MC").getSpecifics().get("DB2_CURSOR_ROW_SCHEMA"));
				assertEquals("A", reread.getTypes().get("N").getColumns().get("ITEMS").getSpecifics().get("TYPENAME"));
				try (var rows = statement.executeQuery("VALUES " + schema + ".F()")) {
					assertTrue(rows.next());
					assertEquals(7, rows.getInt(1));
				}
			} catch (Exception | AssertionError failure) {
				originalFailure = failure;
				throw failure;
			} finally {
				SQLException cleanupFailure = null;
				for (String sql : drops)
					try {
						statement.execute(sql);
					} catch (SQLException failure) {
						if (originalFailure != null)
							originalFailure.addSuppressed(failure);
						else if (cleanupFailure == null)
							cleanupFailure = failure;
						else
							cleanupFailure.addSuppressed(failure);
					}
				if (cleanupFailure != null)
					throw cleanupFailure;
			}
		}
	}
}
