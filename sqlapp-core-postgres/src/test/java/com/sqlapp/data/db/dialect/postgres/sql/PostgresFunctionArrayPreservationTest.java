package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.SchemaUtils;

class PostgresFunctionArrayPreservationTest {
	@Test
	void preservesExplicitIdentityWhenArgumentsAreNotModeled() throws Exception {
		for (int major : new int[] { 8, 11, 15, 18 }) {
			var registry = DialectResolver.getInstance().getDialect("postgres", major, major == 8 ? 3 : 0, null)
					.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			Function function = new Function("f.name").setSchemaName("Other Schema")
					.setSpecificName("f.name(uuid[], \"Types\".\"State\"[])");
			Function restored = SchemaUtils.readXml(new java.io.StringReader(function.asXml()));
			assertEquals("DROP FUNCTION \"Other Schema\".\"f.name\"(uuid[], \"Types\".\"State\"[])",
					registry.createSql(restored, SqlType.DROP).get(0).getSqlText());
			restored.getArguments().add(new NamedArgument("value").setDataType(DataType.INT));
			assertTrue(registry.createSql(restored, SqlType.DROP).get(0).getSqlText().endsWith("(INT)"));
		}
	}

	@Test
	void omitsCatalogUnnamedArgumentNamesAfterXmlRoundTrip() throws Exception {
		Function function = new Function("unnamed").setLanguage("sql").setStatement("SELECT $1");
		function.getReturning().setDataType(DataType.INT);
		NamedArgument argument = new NamedArgument().setDataType(DataType.INT).setDefaultValue("2");
		argument.getSpecifics().put("UNNAMED_ARGUMENT", true);
		function.getArguments().add(argument);
		Function restored = SchemaUtils.readXml(new java.io.StringReader(function.asXml()));
		assertEquals("$1", restored.getArguments().get(0).getName());
		var registry = DialectResolver.getInstance().getDialect("postgres", 15, 0, null).createSqlFactoryRegistry();
		String sql = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
		assertFalse(sql.substring(0, sql.indexOf("RETURNS")).contains("$1"), sql);
		assertTrue(sql.contains("DEFAULT 2"), sql);
	}

	@Test
	void generatesInputOnlyQualifiedSignaturesForDropAndComments() {
		var dialect = DialectResolver.getInstance().getDialect("postgres", 15, 0, null);
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		Function function = new Function("f.name").setSchemaName("Other Schema").setRemarks("comment");
		function.getArguments().add(new NamedArgument("value").setDataType(DataType.UUID).setArrayDimension(1));
		function.getArguments().add(new NamedArgument("result").setDataType(DataType.INT)
				.setDirection(com.sqlapp.jdbc.sql.ParameterDirection.Output));
		String sql = registry.createSql(function, SqlType.DROP).get(0).getSqlText();
		assertEquals("DROP FUNCTION \"Other Schema\".\"f.name\"(UUID[])", sql);
		function.getSpecifics().put("ROUTINE_KIND", "PROCEDURE");
		assertTrue(registry.createSql(function, SqlType.DROP).get(0).getSqlText().startsWith("DROP PROCEDURE "));
	}

	@Test
	void preservesExecutableDefinitionAndChoosesValidBodyDelimiter() {
		var dialect = DialectResolver.getInstance().getDialect("postgres", 15, 0, null);
		var registry = dialect.createSqlFactoryRegistry();
		Function ddl = new Function("p").setDefinition("CREATE PROCEDURE p() LANGUAGE SQL AS 'SELECT 1'");
		assertEquals("CREATE PROCEDURE p() LANGUAGE SQL AS 'SELECT 1'",
				registry.createSql(ddl, SqlType.CREATE).get(0).getSqlText());
		Function function = new Function("bad name").setLanguage("sql").setStatement("SELECT '$$ $sqlapp$ $sqlapp_0$'");
		function.getReturning().setDataType(DataType.VARCHAR);
		String sql = registry.createSql(function, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("AS $sqlapp_1$"), sql);
	}

	@Test
	void generatesSchemaQualifiedFunctionWithArrayArgumentsAndReturns() throws Exception {
		for (int major : new int[] { 8, 11, 15, 18 }) {
			var dialect = DialectResolver.getInstance().getDialect("postgres", major, major == 8 ? 3 : 0, null);
			Function function = new Function("f").setSchemaName("Other Schema");
			function.getArguments().add(new NamedArgument("items").setDataType(DataType.UUID).setArrayDimension(1));
			function.getReturning().setDataType(DataType.UUID).setArrayDimension(2);
			function.setLanguage("sql").setStatement("SELECT ARRAY[items]");
			Function restored = SchemaUtils.readXml(new java.io.StringReader(function.asXml()));
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			String sql = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("\"Other Schema\".f"), sql);
			assertTrue(sql.contains("items UUID[]"), sql);
			assertTrue(sql.contains("RETURNS UUID[][]"), sql);
		}
	}
}
