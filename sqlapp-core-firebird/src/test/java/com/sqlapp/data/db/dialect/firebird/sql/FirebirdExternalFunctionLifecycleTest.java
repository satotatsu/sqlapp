/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.firebird.*;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class FirebirdExternalFunctionLifecycleTest {
	@Test
	void legacyTextReturnDeclarationsRetainTheirCharacterSet() {
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50)) {
			for (String type : List.of("CSTRING", "VARCHAR", "CHAR")) {
				var function = legacy();
				function.getReturning().setDataType("CHAR".equals(type) ? DataType.CHAR : DataType.VARCHAR)
						.setDataTypeName(type).setLength(12).setCharacterSet("UTF8");
				function.getSpecifics().put("firebird.udf.returnMechanism", "1");
				String sql = dialect.createSqlFactoryRegistry().createSql(function, SqlType.CREATE).getFirst()
						.getSqlText();
				assertTrue(sql.contains("RETURNS " + type + "(12) CHARACTER SET UTF8"), sql);
			}
		}
	}

	@Test
	void modeledDeclarationsRejectInvalidReturnPositionsInsteadOfChangingTheirMeaning() {
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50)) {
			var registry = dialect.createSqlFactoryRegistry();
			for (String position : List.of("-1", "2", "not-a-number", "2147483648")) {
				var function = legacy();
				function.getSpecifics().put("firebird.udf.returnPosition", position);
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
			}
			var function = legacy();
			function.getSpecifics().put("firebird.udf.returnPosition", "1");
			assertTrue(registry.createSql(function, SqlType.CREATE).getFirst().getSqlText()
					.contains("RETURNS PARAMETER 1"));
		}
	}

	@Test
	void modeledDeclarationsRejectNulIdentitiesAndLibraryLiterals() {
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50)) {
			var registry = dialect.createSqlFactoryRegistry();
			for (String name : List.of("", " ", "bad\0name")) {
				var function = legacy().setName(name);
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
			}
			for (String key : List.of("firebird.udf.module", "firebird.udf.entrypoint")) {
				var function = legacy();
				function.getSpecifics().put(key, "bad\0value");
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
			}
		}
	}

	@Test
	void modeledCStringArgumentsAndReturnsRequirePositiveByteLengths() {
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50)) {
			var registry = dialect.createSqlFactoryRegistry();
			for (Long length : Arrays.asList(null, 0L, -1L)) {
				var input = legacy();
				input.getArguments().getFirst().setDataTypeName("CSTRING").setLength(length);
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(input, SqlType.CREATE));
				var output = legacy();
				output.getReturning().setDataTypeName("CSTRING").setLength(length);
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(output, SqlType.CREATE));
			}
			var function = legacy();
			function.getArguments().getFirst().setDataTypeName("CSTRING").setLength(8);
			function.getReturning().setDataTypeName("CSTRING").setLength(16);
			function.getSpecifics().put("firebird.udf.returnMechanism", "1");
			String sql = registry.createSql(function, SqlType.CREATE).getFirst().getSqlText();
			assertTrue(sql.contains("CSTRING(8)"), sql);
			assertTrue(sql.contains("RETURNS CSTRING(16)"), sql);
		}
	}

	@Test
	void definitionOnlyLegacyDeclarationsDoNotEmitModernParameterComments() {
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50)) {
			var function = new Function("F").setRemarks("legacy comment");
			function.setDefinition(
					"declare external function F integer returns integer by value entry_point 'x' module_name 'x'");
			function.getArguments()
					.add(new NamedArgument("A").setDataType(DataType.INT).setRemarks("supplemental metadata"));
			var sql = dialect.createSqlFactoryRegistry().createSql(function, SqlType.CREATE);
			assertEquals(2, sql.size());
			assertEquals(String.join("\n", function.getDefinition()), sql.getFirst().getSqlText());
			assertEquals("COMMENT ON EXTERNAL FUNCTION F IS 'legacy comment'", sql.getLast().getSqlText());
		}
	}

	private Function legacy() {
		var function = new Function("legacy function");
		function.getSpecifics().put("firebird.udf.module", "oldlib");
		function.getSpecifics().put("firebird.udf.entrypoint", "old_entry");
		function.getReturning().setDataType(DataType.DOUBLE);
		function.getArguments().add(new NamedArgument("A").setDataType(DataType.DOUBLE));
		return function;
	}

	@Test
	void dropSelectsLegacyOrModernSyntaxAndIgnoresLogicalSchemaLabels() {
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50)) {
			var registry = dialect.createSqlFactoryRegistry();
			var function = legacy().setName("quoted \" function").setSchemaName("logical schema");
			assertEquals("DROP EXTERNAL FUNCTION \"quoted \"\" function\"",
					registry.createSql(function, SqlType.DROP).getFirst().getSqlText());
			function.getSpecifics().remove("firebird.udf.module");
			function.setDefinition(
					"declare external function X returns integer by value entry_point 'x' module_name 'x'");
			assertTrue(registry.createSql(function, SqlType.DROP).getFirst().getSqlText()
					.startsWith("DROP EXTERNAL FUNCTION"));
		}
		var function = new Function("F");
		assertEquals("DROP EXTERNAL FUNCTION F", DialectHolder.defaultDialect25.createSqlFactoryRegistry()
				.createSql(function, SqlType.DROP).getFirst().getSqlText());
		assertEquals("DROP FUNCTION F", DialectHolder.defaultDialect30.createSqlFactoryRegistry()
				.createSql(function, SqlType.DROP).getFirst().getSqlText());
		function.getSpecifics().put("firebird.external.engine", "UDR");
		assertEquals("DROP FUNCTION F", DialectHolder.defaultDialect50.createSqlFactoryRegistry()
				.createSql(function, SqlType.DROP).getFirst().getSqlText());
	}

	@Test
	void changesEntryModuleAndCommentsInPlaceAcrossVersions() throws Exception {
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50)) {
			var original = legacy();
			original.setDialect(dialect);
			var target = original.clone().setRemarks("new comment");
			target.getSpecifics().put("firebird.udf.entrypoint", "new'entry");
			target.getSpecifics().put("firebird.udf.module", "newlib");
			String before = original.asXml(), after = target.asXml();
			var plan = dialect.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER);
			assertEquals(
					List.of("ALTER EXTERNAL FUNCTION \"legacy function\" ENTRY_POINT 'new''entry' MODULE_NAME 'newlib'",
							"COMMENT ON EXTERNAL FUNCTION \"legacy function\" IS 'new comment'"),
					plan.stream().map(operation -> operation.getSqlText()).toList());
			assertEquals(before, original.asXml());
			assertEquals(after, target.asXml());
		}
	}

	@Test
	void onlyChangedAttributesAreRequiredAndMissingNewValuesFail() {
		var before = new Function("F");
		before.setDialect(DialectHolder.defaultDialect25);
		before.getSpecifics().put("firebird.udf.module", "oldlib");
		var after = before.clone();
		after.getSpecifics().put("firebird.udf.module", "newlib");
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		assertEquals("ALTER EXTERNAL FUNCTION F MODULE_NAME 'newlib'",
				registry.createSql(before.diff(after), SqlType.ALTER).getFirst().getSqlText());
		after.getSpecifics().remove("firebird.udf.module");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(before.diff(after), SqlType.ALTER));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(before, SqlType.ALTER));
	}

	@Test
	void canonicalChangedDefinitionsAreValidatedAndUnchangedCachesDoNotShadowEdits() {
		var registry = DialectHolder.defaultDialect30.createSqlFactoryRegistry();
		var before = legacy();
		before.setDialect(DialectHolder.defaultDialect30);
		before.setDefinition(registry.createSql(before, SqlType.CREATE).getFirst().getSqlText());
		var after = before.clone();
		after.getSpecifics().put("firebird.udf.entrypoint", "new_entry");
		assertEquals(1, registry.createSql(before.diff(after), SqlType.ALTER).size());
		var modeled = after.clone();
		modeled.setDefinition(List.of());
		after.setDefinition(registry.createSql(modeled, SqlType.CREATE).getFirst().getSqlText());
		assertEquals(1, registry.createSql(before.diff(after), SqlType.ALTER).size());
		after.setDefinition(
				"DECLARE EXTERNAL FUNCTION DIFFERENT RETURNS INTEGER BY VALUE ENTRY_POINT 'x' MODULE_NAME 'x'");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(before.diff(after), SqlType.ALTER));
	}

	@Test
	void signatureChangesUnknownSpecificsAndGenreConversionsFailExplicitly() {
		var before = legacy();
		before.setDialect(DialectHolder.defaultDialect30);
		var registry = DialectHolder.defaultDialect30.createSqlFactoryRegistry();
		var signature = before.clone();
		signature.getReturning().setDataType(DataType.INT);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(before.diff(signature), SqlType.ALTER));
		var unknown = before.clone();
		unknown.getSpecifics().put("unhandled", "x");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(before.diff(unknown), SqlType.ALTER));
		var renamed = before.clone().setName("LEGACY FUNCTION");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(before.diff(renamed), SqlType.ALTER));
		var modern = new Function("legacy function").setStatement("BEGIN RETURN 1; END");
		modern.setDialect(DialectHolder.defaultDialect30);
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(before.diff(modern), SqlType.ALTER));
	}

	@Test
	void schemaAlterKeepsCallersForEntryChangesAndDropsThemBeforeTheUdf() {
		var before = new Schema();
		before.setDialect(DialectHolder.defaultDialect25);
		before.getFunctions().add(legacy());
		before.getProcedures().add(new Procedure("P").setStatement("BEGIN END"));
		FirebirdSchemaDependencies.set(before, List.of(new FirebirdSchemaDependencies.Dependency(
				new FirebirdSchemaDependencies.ObjectId(FirebirdSchemaDependencies.Kind.PROCEDURE, "P"),
				new FirebirdSchemaDependencies.ObjectId(FirebirdSchemaDependencies.Kind.FUNCTION, "legacy function"))));
		var after = before.clone();
		after.getFunctions().get(0).getSpecifics().put("firebird.udf.entrypoint", "new_entry");
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		var plan = registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(before.diff(after));
		assertEquals(1, plan.size());
		assertTrue(plan.getFirst().getSqlText().startsWith("ALTER EXTERNAL FUNCTION"));
		var empty = new Schema();
		FirebirdSchemaDependencies.set(empty, List.of());
		assertEquals(List.of("DROP PROCEDURE P", "DROP EXTERNAL FUNCTION \"legacy function\""),
				registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(before.diff(empty)).stream()
						.map(operation -> operation.getSqlText()).toList());
	}
}
