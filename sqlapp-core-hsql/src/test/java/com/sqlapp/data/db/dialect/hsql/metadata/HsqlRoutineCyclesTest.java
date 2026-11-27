/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;

class HsqlRoutineCyclesTest {
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void mutualFunctionsAndFunctionViewCycleSurviveXmlAndRecreation(boolean crossSchema) throws Exception {
		String second = crossSchema ? "OTHER" : "APP";
		try (var source = DriverManager.getConnection("jdbc:hsqldb:mem:routine-cycles-source-" + crossSchema, "SA", "");
				var sql = source.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			if (crossSchema)
				sql.execute("CREATE SCHEMA OTHER AUTHORIZATION DBA");
			sql.execute(
					"CREATE FUNCTION APP.A(V INTEGER) RETURNS INTEGER SPECIFIC A_SPEC LANGUAGE SQL READS SQL DATA RETURN 0");
			sql.execute("CREATE FUNCTION " + second
					+ ".B(V INTEGER) RETURNS INTEGER SPECIFIC B_SPEC LANGUAGE SQL READS SQL DATA RETURN CASE WHEN V <= 0 THEN 0 ELSE APP.A(V-1)+1 END");
			sql.execute("ALTER SPECIFIC ROUTINE APP.A_SPEC BODY RETURN CASE WHEN V <= 0 THEN 0 ELSE " + second
					+ ".B(V-1)+1 END");
			sql.execute(
					"CREATE FUNCTION APP.C(V INTEGER) RETURNS INTEGER SPECIFIC C_SPEC LANGUAGE SQL READS SQL DATA RETURN 0");
			sql.execute("CREATE VIEW APP.VIEW_C AS SELECT APP.C(1) AS N FROM (VALUES(0))");
			sql.execute(
					"ALTER SPECIFIC ROUTINE APP.C_SPEC BODY BEGIN ATOMIC IF V < 0 THEN RETURN (SELECT MAX(N) FROM APP.VIEW_C); ELSE RETURN V; END IF; END");
			sql.execute("CREATE TABLE APP.LOG(V INTEGER)");
			sql.execute(
					"CREATE PROCEDURE APP.P(IN V INTEGER) SPECIFIC P_SPEC MODIFIES SQL DATA INSERT INTO APP.LOG VALUES(V)");
			sql.execute("CREATE PROCEDURE " + second
					+ ".Q(IN V INTEGER) SPECIFIC Q_SPEC MODIFIES SQL DATA BEGIN ATOMIC IF V>0 THEN CALL APP.P(V-1); ELSE INSERT INTO APP.LOG VALUES(V); END IF; END");
			sql.execute("ALTER SPECIFIC ROUTINE APP.P_SPEC BODY BEGIN ATOMIC IF V>0 THEN CALL " + second
					+ ".Q(V-1); ELSE INSERT INTO APP.LOG VALUES(V); END IF; END");
			var dialect = DialectResolver.getInstance().getDialect(source);
			var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("APP");
			Schema schema = SchemaUtils.readXml(new java.io.StringReader(reader.getAllFull(source).get(0).asXml()));
			var catalog = new com.sqlapp.data.schemas.Catalog("PUBLIC");
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
			assertTrue(
					operations.stream().anyMatch(op -> op.getSqlText().contains("Routine initialization incomplete")));
			var script = com.sqlapp.data.db.sql.SqlOperation.toText(operations);
			var split = dialect.createSqlSplitter().parse(script);
			assertEquals(operations.size(), split.size());
			assertEquals(operations.size(),
					dialect.createSqlSplitter().parse(dialect.toTextFromSqlOperation(operations)).size());
			try (var target = DriverManager.getConnection("jdbc:hsqldb:mem:routine-cycles-target-" + crossSchema, "SA",
					""); var statement = target.createStatement()) {
				boolean checked = false;
				for (var operation : split) {
					statement.execute(operation.getText());
					if (!checked && operation.getText().contains("Routine initialization incomplete")) {
						checked = true;
						var failure = assertThrows(java.sql.SQLException.class,
								() -> statement.executeQuery("VALUES APP.A(0)"));
						assertEquals("45000", failure.getSQLState());
					}
				}
				try (var rs = statement.executeQuery("VALUES (APP.A(4), " + second + ".B(5), APP.C(-1))")) {
					assertTrue(rs.next());
					assertEquals(4, rs.getInt(1));
					assertEquals(5, rs.getInt(2));
					assertEquals(1, rs.getInt(3));
				}
				try (var rs = statement.executeQuery("SELECT N FROM APP.VIEW_C")) {
					assertTrue(rs.next());
					assertEquals(1, rs.getInt(1));
				}
				statement.execute("CALL APP.P(3)");
				try (var rs = statement.executeQuery("SELECT COUNT(*),SUM(V) FROM APP.LOG")) {
					assertTrue(rs.next());
					assertEquals(1, rs.getInt(1));
					assertEquals(0, rs.getInt(2));
				}
			}
		}
	}
}
