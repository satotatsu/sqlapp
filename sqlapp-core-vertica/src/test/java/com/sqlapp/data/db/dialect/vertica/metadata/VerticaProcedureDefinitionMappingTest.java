package com.sqlapp.data.db.dialect.vertica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.vertica.DialectHolder;
import com.sqlapp.data.db.dialect.vertica.metadata.Vertica11_1_1ProcedureReader;
import com.sqlapp.data.db.dialect.vertica.metadata.VerticaDdlExporter;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class VerticaProcedureDefinitionMappingTest {
	@Test
	void zeroArgumentProceduresKeepParenthesesAndMissingTypesFailBeforeQuery() throws Exception {
		Procedure p = new Procedure("empty");
		p.setSchemaName("app");
		new VerticaDdlExporter().applyProcedureDefinitions(
				"CREATE PROCEDURE app.empty() LANGUAGE 'PL/vSQL' AS 'BEGIN NULL; END;';", List.of(p));
		var registry = DialectHolder.defaultDialect11_1_1.createSqlFactoryRegistry();
		assertTrue(registry.createSql(p, SqlType.DROP).get(0).getSqlText().contains("\"empty\"()"));
		p.getArguments().add(new NamedArgument("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> new VerticaDdlExporter().exportProcedureDefinitions(null, List.of(p)));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(p, SqlType.DROP));
	}

	private Procedure procedure(String type) {
		Procedure p = new Procedure("p.with quote\"");
		p.setSchemaName("my schema");
		p.getArguments().add(
				new Vertica11_1_1ProcedureReader(DialectHolder.defaultDialect11_1_1).createArgument("INOUT x " + type));
		return p;
	}

	private String ddl(String type) {
		return "CREATE PROCEDURE \"my schema\".\"p.with quote\"\"\"(x " + type
				+ ") LANGUAGE 'PL/vSQL' SECURITY INVOKER AS $$ BEGIN RAISE NOTICE 'a;b'; END; $$;";
	}

	@Test
	void mapsOverloadsAndPreservesOpaqueBodyThroughXmlAndFactories() throws Exception {
		var integer = procedure("INT");
		var string = procedure("VARCHAR(100)");
		new VerticaDdlExporter().applyProcedureDefinitions(ddl("INT") + ddl("VARCHAR(100)"), List.of(integer, string));
		Procedure restored = SchemaUtils.readXml(new java.io.StringReader(integer.asXml()));
		var registry = DialectHolder.defaultDialect11_1_1.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		assertTrue(registry.createSql(restored, SqlType.CREATE).get(0).getSqlText().contains("RAISE NOTICE 'a;b'"));
		String drop = registry.createSql(restored, SqlType.DROP).get(0).getSqlText();
		assertTrue(drop.contains("\"my schema\".\"p.with quote\"\"\"(BIGINT)"), drop);
		assertTrue(String.join("\n", string.getDefinition()).contains("VARCHAR(100)"));
	}

	@Test
	void failuresDoNotPartiallyAssignDefinitions() {
		var first = procedure("INT");
		var second = procedure("VARCHAR");
		first.setDefinition("old first");
		second.setDefinition("old second");
		for (String script : List.of(ddl("INT"), ddl("INT") + ddl("INT"), "SET search_path TO public;" + ddl("INT"),
				ddl("INT") + "ALTER PROCEDURE public.p() RENAME TO other;")) {
			assertThrows(IllegalArgumentException.class,
					() -> new VerticaDdlExporter().applyProcedureDefinitions(script, List.of(first, second)));
			assertEquals(List.of("old first"), first.getDefinition());
			assertEquals(List.of("old second"), second.getDefinition());
		}
		assertThrows(IllegalArgumentException.class,
				() -> new VerticaDdlExporter().applyProcedureDefinitions(ddl("INT"), List.of(first, procedure("INT"))));
	}
}
