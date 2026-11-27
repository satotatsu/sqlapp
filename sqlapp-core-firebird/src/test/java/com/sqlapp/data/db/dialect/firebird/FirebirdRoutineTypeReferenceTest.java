/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.ParameterDirection;

class FirebirdRoutineTypeReferenceTest {
	private NamedArgument argument(FirebirdRoutineTypeReference reference) {
		var argument = new NamedArgument("A").setDataType(DataType.INT).setDirection(ParameterDirection.Input);
		FirebirdRoutineTypeReference.set(argument.getSpecifics(), reference);
		return argument;
	}

	private Procedure procedure(FirebirdRoutineTypeReference reference) {
		var procedure = new Procedure("P").setStatement("BEGIN END");
		procedure.getArguments().add(argument(reference));
		return procedure;
	}

	@Test
	void allReferenceFormsQuoteIdentifiersAndSuppressInheritedCharacterSet() {
		var references = List.of(FirebirdRoutineTypeReference.domain("quoted \" domain"),
				FirebirdRoutineTypeReference.typeOfDomain("quoted \" domain"),
				FirebirdRoutineTypeReference.typeOfColumn("quoted table", "quoted column"));
		var expected = List.of("\"quoted \"\" domain\"", "TYPE OF \"quoted \"\" domain\"",
				"TYPE OF COLUMN \"quoted table\".\"quoted column\"");
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50))
			for (int i = 0; i < references.size(); i++) {
				var object = procedure(references.get(i));
				object.getArguments().get(0).setDataType(DataType.VARCHAR).setLength(20).setCharacterSet("UTF8")
						.setCollation("local collation").setNotNull(true).setDefaultValue("1");
				String sql = dialect.createSqlFactoryRegistry().createSql(object, SqlType.CREATE).get(0).getSqlText();
				assertTrue(sql.contains("A " + expected.get(i) + " NOT NULL COLLATE \"local collation\" DEFAULT 1"),
						sql);
				assertFalse(sql.contains("CHARACTER SET"), sql);
			}
	}

	@Test
	void referencesAndResolvedTypesSurviveSchemaXmlAndCloning() throws Exception {
		var schema = new Schema();
		schema.getProcedures().add(procedure(FirebirdRoutineTypeReference.typeOfColumn("Table", "Column")));
		Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
		for (var item : List.of(schema.clone(), restored)) {
			var argument = item.getProcedures().get(0).getArguments().get(0);
			assertEquals(DataType.INT, argument.getDataType());
			assertEquals(FirebirdRoutineTypeReference.typeOfColumn("Table", "Column"),
					FirebirdRoutineTypeReference.get(argument.getSpecifics()));
		}
	}

	@Test
	void functionReturnsRenderReferencesAndAllowInPlaceReferenceChanges() {
		var function = new Function("F").setStatement("BEGIN RETURN 1; END");
		function.setDialect(DialectHolder.defaultDialect30);
		function.getReturning().setDataType(DataType.INT).setCharacterSet("UTF8");
		FirebirdRoutineTypeReference.set(function.getReturning().getSpecifics(),
				FirebirdRoutineTypeReference.domain("D"));
		var registry = DialectHolder.defaultDialect30.createSqlFactoryRegistry();
		assertTrue(registry.createSql(function, SqlType.CREATE).get(0).getSqlText().contains("RETURNS D"));
		var after = function.clone();
		FirebirdRoutineTypeReference.set(after.getReturning().getSpecifics(),
				FirebirdRoutineTypeReference.typeOfColumn("T", "ID"));
		var plan = registry.createSql(function.diff(after), SqlType.ALTER);
		assertTrue(plan.get(0).getSqlText().contains("RETURNS TYPE OF COLUMN T.ID"));
		assertFalse(plan.get(0).getSqlText().contains("CHARACTER SET"));
	}

	@Test
	void argumentReferenceChangesUseAlterAndUnknownSpecificsStillFail() {
		var before = procedure(FirebirdRoutineTypeReference.domain("D"));
		before.setDialect(DialectHolder.defaultDialect25);
		var after = before.clone();
		FirebirdRoutineTypeReference.set(after.getArguments().get(0).getSpecifics(),
				FirebirdRoutineTypeReference.typeOfDomain("D"));
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		var plan = registry.createSql(before.diff(after), SqlType.ALTER);
		assertTrue(plan.get(0).getSqlText().startsWith("ALTER PROCEDURE"));
		assertTrue(plan.get(0).getSqlText().contains("A TYPE OF D"));
		after.getArguments().get(0).getSpecifics().put("unhandled", "value");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(before.diff(after), SqlType.ALTER));
	}

	@Test
	void schemaCreateAndAlterInferDomainTableAndViewSignatureDependencies() {
		var schema = new Schema();
		schema.setDialect(DialectHolder.defaultDialect30);
		schema.getDomains().add(new Domain("D").setDataType(DataType.INT));
		var table = new Table("T");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		schema.getTables().add(table);
		schema.getViews().add(new View("V").setStatement("SELECT ID FROM T"));
		var procedure = procedure(FirebirdRoutineTypeReference.typeOfColumn("V", "ID"));
		var domainArgument = argument(FirebirdRoutineTypeReference.typeOfDomain("D")).setName("B");
		procedure.getArguments().add(domainArgument);
		schema.getProcedures().add(procedure);
		var function = new Function("F").setStatement("BEGIN RETURN 1; END");
		FirebirdRoutineTypeReference.set(function.getReturning().getSpecifics(),
				FirebirdRoutineTypeReference.typeOfColumn("T", "ID"));
		schema.getFunctions().add(function);
		FirebirdSchemaDependencies.set(schema,
				List.of(new FirebirdSchemaDependencies.Dependency(
						new FirebirdSchemaDependencies.ObjectId(FirebirdSchemaDependencies.Kind.VIEW, "V"),
						new FirebirdSchemaDependencies.ObjectId(FirebirdSchemaDependencies.Kind.TABLE, "T"))));
		var empty = new Schema();
		FirebirdSchemaDependencies.set(empty, List.of());
		var registry = DialectHolder.defaultDialect30.createSqlFactoryRegistry();
		var create = registry.createSql(schema, SqlType.CREATE).stream().map(operation -> operation.getSqlText())
				.toList();
		assertTrue(position(create, "CREATE TABLE T") < position(create, "CREATE FUNCTION F"), create.toString());
		assertTrue(position(create, "CREATE VIEW V") < position(create, "CREATE PROCEDURE P"), create.toString());
		var drop = registry.getSqlFactory(schema, SqlType.ALTER).createDiffSql(schema.diff(empty)).stream()
				.map(operation -> operation.getSqlText()).toList();
		assertTrue(position(drop, "DROP PROCEDURE P") < position(drop, "DROP DOMAIN D"), drop.toString());
		assertTrue(position(drop, "DROP FUNCTION F") < position(drop, "DROP TABLE T"), drop.toString());
		var retained = schema.clone();
		retained.getDomains().clear();
		assertThrows(UnsupportedOperationException.class,
				() -> registry.getSqlFactory(schema, SqlType.ALTER).createDiffSql(schema.diff(retained)));
	}

	private int position(List<String> sql, String prefix) {
		for (int i = 0; i < sql.size(); i++)
			if (sql.get(i).startsWith(prefix))
				return i;
		throw new AssertionError("Missing " + prefix + ": " + sql);
	}

	@Test
	void resolvedTypeEditsCannotSilentlyOverrideAnUnchangedReference() {
		var before = procedure(FirebirdRoutineTypeReference.domain("D"));
		before.setDialect(DialectHolder.defaultDialect25);
		var after = before.clone();
		after.getArguments().get(0).setDataType(DataType.BIGINT);
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect25.createSqlFactoryRegistry()
				.createSql(before.diff(after), SqlType.ALTER));
	}

	@Test
	void malformedPayloadsAndPre25GenerationFailClearly() {
		assertThrows(IllegalArgumentException.class, () -> FirebirdRoutineTypeReference.typeOfColumn("T", null));
		assertThrows(IllegalArgumentException.class,
				() -> new FirebirdRoutineTypeReference(FirebirdRoutineTypeReference.Kind.DOMAIN, "D", "T", null));
		var values = new HashMap<String, String>();
		values.put(FirebirdRoutineTypeReference.SPECIFICS_KEY, "{\"format\":2,\"reference\":null}");
		assertThrows(RuntimeException.class, () -> FirebirdRoutineTypeReference.get(values));
		values.put(FirebirdRoutineTypeReference.SPECIFICS_KEY,
				"{\"format\":1,\"reference\":{\"kind\":\"DOMAIN\",\"domain\":\"D\",\"unexpected\":true}}");
		assertThrows(RuntimeException.class, () -> FirebirdRoutineTypeReference.get(values));
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect20.createSqlFactoryRegistry()
				.createSql(procedure(FirebirdRoutineTypeReference.domain("D")), SqlType.CREATE));
	}
}
