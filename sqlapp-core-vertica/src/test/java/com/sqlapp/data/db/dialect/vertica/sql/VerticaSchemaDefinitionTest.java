package com.sqlapp.data.db.dialect.vertica.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.vertica.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class VerticaSchemaDefinitionTest {
	@Test
	void scheduledSchemaFailsBeforeAnyReplayOperationsAreReturned() {
		var registry = DialectHolder.defaultDialect11_1_1.createSqlFactoryRegistry();
		for (String ddl : new String[] { "CREATE SCHEDULE app.s USING CRON '0 0 * * *'",
				"CREATE TRIGGER app.t ON SCHEDULE s EXECUTE PROCEDURE p() AS DEFINER" }) {
			Schema schema = new Schema("app");
			schema.setDefinition("CREATE SCHEMA app;" + ddl);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(schema, SqlType.CREATE));
		}
	}

	@Test
	void completeScriptKeepsOrderAndSuppressesSupplementalChildrenAfterXml() throws Exception {
		Schema schema = new Schema("app");
		schema.setDefinition("CREATE SCHEMA app; CREATE TABLE app.f(id INT);"
				+ "CREATE PROCEDURE app.p() LANGUAGE PLvSQL AS $$ BEGIN RAISE NOTICE 'a;b'; END; $$;");
		schema.getTables().add(new Table("must_not_be_generated"));
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		var operations = DialectHolder.defaultDialect11_1_1.createSqlFactoryRegistry().createSql(restored,
				SqlType.CREATE);
		assertEquals(3, operations.size());
		assertTrue(operations.get(0).getSqlText().startsWith("CREATE SCHEMA"));
		assertTrue(operations.get(1).getSqlText().contains("CREATE TABLE"));
		assertTrue(operations.get(2).getSqlText().contains("RAISE NOTICE 'a;b'"));
		assertTrue(operations.stream().noneMatch(o -> o.getSqlText().contains("must_not_be_generated")));
	}

	@Test
	void flexProjectionCollisionFailsBeforeReturningAnyOperations() {
		Schema schema = new Schema("app");
		schema.setDefinition(
				"CREATE SCHEMA app; CREATE FLEX TABLE app.f(id INT); CREATE PROJECTION app.f_super AS SELECT id FROM app.f;");
		var registry = DialectHolder.defaultDialect11_1_1.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(schema, SqlType.CREATE));
	}

	@Test
	void blankOrCommentOnlyDefinitionsFailAndNoDefinitionRetainsNormalGeneration() {
		var registry = DialectHolder.defaultDialect11_1_1.createSqlFactoryRegistry();
		Schema schema = new Schema("app");
		assertEquals(1, registry.createSql(schema, SqlType.CREATE).size());
		for (String script : new String[] { " ", "-- comment only" }) {
			schema.setDefinition(script);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(schema, SqlType.CREATE));
		}
	}
}
