/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class VirticaStoredObjectSqlTest {
	@Test
	void udxKindsUseTheirOwnDropSyntaxAndRequireCompleteCreate() {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		registry.getOptions().setDropIfExists(false);
		for (String kind : new String[] { "Transform Function", "Aggregate Function", "Analytic Function", "Parser",
				"Filter", "Source" }) {
			Function function = new Function("udx");
			function.getSpecifics().put("PROCEDURE_TYPE", "User Defined " + kind);
			String drop = registry.createSql(function, SqlType.DROP).get(0).getSqlText();
			assertTrue(drop.contains("DROP " + kind.toUpperCase(java.util.Locale.ROOT)), drop);
			assertTrue(drop.contains("\"udx\"()"), drop);
			function.setStatement("RETURN 1");
			function.getReturning().setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
			function.setDefinition("CREATE " + kind + " udx AS LANGUAGE 'C++' NAME 'Factory' LIBRARY lib");
			assertEquals(String.join("\n", function.getDefinition()),
					registry.createSql(function, SqlType.CREATE).get(0).getSqlText());
		}
		Function parser = new Function("p");
		parser.getSpecifics().put("PROCEDURE_TYPE", "User Defined Parser");
		registry.getOptions().setDropIfExists(true);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(parser, SqlType.DROP));
	}

	@Test
	void dropUsesBaseTypesAndPolymorphicTransformHasEmptySignature() {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		Function function = new Function("f");
		function.getArguments()
				.add(new NamedArgument().setDataType(com.sqlapp.data.db.datatype.DataType.VARCHAR).setLength(123L));
		String sql = registry.createSql(function, SqlType.DROP).get(0).getSqlText();
		assertTrue(sql.contains("(VARCHAR)"), sql);
		function.getArguments().clear();
		function.getSpecifics().put("PROCEDURE_TYPE", "User Defined Transform");
		function.getSpecifics().put("FUNCTION_ARGUMENT_TYPE", "Any");
		assertTrue(registry.createSql(function, SqlType.DROP).get(0).getSqlText().contains("\"f\"()"));
		function.getArguments().add(new NamedArgument().setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.DROP));
		function.getSpecifics().put("PROCEDURE_TYPE", "Unknown kind");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.DROP));
	}

	@Test
	void dropFunctionUsesExactTypesWithoutNamesAndRejectsIncompleteSignatures() {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setDropIfExists(true);
		Function function = new Function("quoted function");
		function.setSchemaName("my schema");
		String zero = registry.createSql(function, SqlType.DROP).get(0).getSqlText();
		assertTrue(zero.contains("IF EXISTS"), zero);
		assertTrue(zero.contains("\"my schema\".\"quoted function\"()"), zero);
		function.getArguments()
				.add(new NamedArgument("ignored argument").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT));
		String typed = registry.createSql(function, SqlType.DROP).get(0).getSqlText();
		assertTrue(typed.contains("(BIGINT)"), typed);
		assertFalse(typed.contains("ignored argument"), typed);
		function.getArguments().add(new NamedArgument("missing"));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.DROP));
	}

	@Test
	void functionsAndProceduresRequireRecreatableRepresentation() {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		Function function = new Function("f");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
		function.setStatement("RETURN 1");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
		Procedure procedure = new Procedure("p");
		procedure.setStatement("BEGIN NULL; END;");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(procedure, SqlType.CREATE));
		String complete = "CREATE PROCEDURE p() LANGUAGE PLvSQL AS $$ BEGIN NULL; END; $$";
		procedure.setDefinition(complete);
		assertEquals(complete, registry.createSql(procedure, SqlType.CREATE).get(0).getSqlText());
	}

	@Test
	void functionCompleteDefinitionIsAuthoritativeAndZeroArgsHaveParentheses() {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		Function function = new Function("f");
		function.setStatement("RETURN 1;");
		function.getReturning().setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
		String sql = registry.createSql(function, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("\"f\"()"), sql);
		assertFalse(sql.contains(";;"), sql);
		String complete = "CREATE FUNCTION f() RETURN INT AS BEGIN RETURN 1; END;";
		function.setDefinition(complete);
		assertEquals(complete, registry.createSql(function, SqlType.CREATE).get(0).getSqlText());
	}

	@Test
	void blankDefinitionsAndUnsupportedFunctionShapesFailClearly() {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		Function function = new Function("f");
		function.setDefinition("  ");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
		Procedure procedure = new Procedure("p");
		procedure.setDefinition("  ");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(procedure, SqlType.CREATE));
		View view = new View("v");
		view.setStatement("  ");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(view, SqlType.CREATE));
		function.setDefinition((String) null);
		function.getReturning().setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
		function.setStatement("external library body");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
		function.setStatement("RETURN 1");
		function.getArguments().add(new NamedArgument("missing_type"));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
	}

	@Test
	void temporaryViewRetainsOutputNamesThroughXmlAndMissingQueryFails() throws Exception {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		View view = new View("order view");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(view, SqlType.CREATE));
		view.setStatement("SELECT 1");
		view.getColumns().add("order id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT));
		view.getSpecifics().put("IS_LOCAL_TEMP_VIEW", true);
		View restored = SchemaUtils.readXml(new java.io.StringReader(view.asXml()));
		String sql = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("CREATE OR REPLACE LOCAL TEMPORARY VIEW"), sql);
		assertTrue(sql.contains("\"order id\""), sql);
		assertTrue(sql.contains("SELECT 1"), sql);
	}
}
