package com.sqlapp.data.db.dialect.vertica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.vertica.metadata.VerticaSchemaRestorer;
import com.sqlapp.data.schemas.*;

class VerticaSchemaRestorePlanTest {
	private Schema schema() {
		Schema schema = new Schema("app");
		schema.setDefinition("CREATE SCHEMA app; CREATE TABLE app.data(id INT); "
				+ "CREATE PROCEDURE app.p(x VARCHAR) LANGUAGE PLvSQL AS $$ BEGIN RAISE NOTICE 'a;b'; END; $$; "
				+ "CREATE SCHEDULE app.s USING DATETIMES('2099-01-01'); "
				+ "CREATE TRIGGER app.t ON SCHEDULE s EXECUTE PROCEDURE p('literal; . p()') AS DEFINER;");
		schema.getSpecifics().put("VERTICA_SCHEDULING_STATES",
				"TRIGGER \"app\".\"t\" ENABLED=false; SCHEDULE \"app\".\"s\" ENABLED=false;");
		schema.getEvents().add(new Event("t"));
		schema.getEvents().get("t").getSpecifics().put("PROCEDURE_SCHEMA_NAME", "app");
		return schema;
	}

	@Test
	void previewComposesFactoriesAndResolvesExplicitReferencesWithoutChangingBodyOrModel() throws Exception {
		Schema schema = schema();
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		String before = restored.asXml();
		var statements = new VerticaSchemaRestorer().preview(restored);
		assertEquals(5, statements.size());
		assertTrue(statements.get(2).contains("RAISE NOTICE 'a;b'"));
		String trigger = statements.get(4);
		assertTrue(trigger.contains("ON SCHEDULE \"app\".\"s\" EXECUTE"), trigger);
		assertTrue(trigger.contains("PROCEDURE \"app\".\"p\" ('literal; . p()')"), trigger);
		assertEquals(before, restored.asXml());
	}

	@Test
	void missingResolutionAndStateMismatchFailOffline() {
		Schema schema = schema();
		schema.getEvents().clear();
		assertThrows(IllegalArgumentException.class, () -> new VerticaSchemaRestorer().preview(schema));
		Schema missingState = schema();
		missingState.getSpecifics().put("VERTICA_SCHEDULING_STATES", "");
		assertThrows(IllegalArgumentException.class, () -> new VerticaSchemaRestorer().preview(missingState));
		Schema missingSchedule = schema();
		missingSchedule.setDefinition(
				String.join("\n", missingSchedule.getDefinition()).replace("ON SCHEDULE s", "ON SCHEDULE missing"));
		assertThrows(IllegalArgumentException.class, () -> new VerticaSchemaRestorer().preview(missingSchedule));
	}

	@Test
	void freshSchemaBoundaryRejectsAdditionalSchemasOutsideObjectsFlexAndArbitrarySql() {
		for (String script : new String[] { "CREATE SCHEMA IF NOT EXISTS app;",
				"CREATE SCHEMA app; CREATE SCHEMA other;", "CREATE SCHEMA app; CREATE TABLE other.data(id INT);",
				"CREATE SCHEMA app; DROP TABLE public.data;",
				"CREATE SCHEMA app; CREATE FLEX TABLE app.f(); CREATE TABLE app.t(id INT); CREATE FLEX TABLE app.g();",
				"CREATE SCHEMA app; SELECT 1;" }) {
			Schema schema = new Schema("app");
			schema.setDefinition(script);
			schema.getSpecifics().put("VERTICA_SCHEDULING_STATES", "");
			assertThrows(IllegalArgumentException.class, () -> new VerticaSchemaRestorer().preview(schema), script);
		}
	}

	@Test
	void qualifiedProcedureNeedsNoExtraResolutionAndQuotedOverridesAreEscaped() {
		Schema schema = schema();
		schema.setDefinition(
				String.join("\n", schema.getDefinition()).replace("PROCEDURE p(", "PROCEDURE external.p("));
		schema.getEvents().clear();
		assertTrue(new VerticaSchemaRestorer().preview(schema).get(4).contains("external.p("));
		Schema quoted = schema();
		quoted.getEvents().get("t").getSpecifics().put("PROCEDURE_SCHEMA_NAME", "external\"schema");
		assertTrue(new VerticaSchemaRestorer().preview(quoted).get(4).contains("\"external\"\"schema\".\"p\""));
	}
}
