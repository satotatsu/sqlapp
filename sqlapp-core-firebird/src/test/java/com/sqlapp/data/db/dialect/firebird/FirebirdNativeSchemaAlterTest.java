/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.*;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class FirebirdNativeSchemaAlterTest {
	private FirebirdSchemaObject object(FirebirdSchemaObject.Kind kind, String body, String comment) {
		String type = kind == FirebirdSchemaObject.Kind.BLOB_FILTER ? "FILTER" : kind.name();
		String start = kind == FirebirdSchemaObject.Kind.BLOB_FILTER ? "DECLARE " : "CREATE ";
		String name = "\"native object\"";
		var definitions = new java.util.ArrayList<String>();
		definitions.add(start + type + " " + name + " " + body);
		if (comment != null)
			definitions.add("COMMENT ON " + type + " " + name + " IS '" + comment + "'");
		return new FirebirdSchemaObject(kind, "native object", definitions, "DROP " + type + " " + name);
	}

	private Schema schema(FirebirdSchemaObject... objects) {
		var schema = new Schema();
		FirebirdSchemaObjects.set(schema, List.of(objects));
		FirebirdSchemaDependencies.set(schema, List.of());
		return schema;
	}

	private Dependency edge(Kind dependent, String name, Kind provider) {
		return new Dependency(new ObjectId(dependent, name), new ObjectId(provider, "native object"));
	}

	private List<String> sql(Schema before, Schema after) {
		return DialectHolder.defaultDialect25.createSqlFactoryRegistry().getSqlFactory(before, SqlType.ALTER)
				.createDiffSql(before.diff(after)).stream().map(operation -> operation.getSqlText()).toList();
	}

	@Test
	void exceptionMessageAndCommentChangesPreserveIdentityAndSourceXml() throws Exception {
		var before = schema(object(FirebirdSchemaObject.Kind.EXCEPTION, "'old message'", "old comment"));
		before.getProcedures().add(new Procedure("P").setStatement("BEGIN EXCEPTION \"native object\"; END"));
		FirebirdSchemaDependencies.set(before, List.of(edge(Kind.PROCEDURE, "P", Kind.EXCEPTION)));
		var after = before.clone();
		FirebirdSchemaObjects.set(after,
				List.of(object(FirebirdSchemaObject.Kind.EXCEPTION, "'new ''message'''", null)));
		String original = before.asXml(), target = after.asXml();
		assertEquals(List.of("ALTER EXCEPTION \"native object\" 'new ''message'''",
				"COMMENT ON EXCEPTION \"native object\" IS NULL"), sql(before, after));
		assertEquals(original, before.asXml());
		assertEquals(target, after.asXml());
	}

	@Test
	void commentOnlyChangesNeedNoDestructiveReplacementOrDependencyGraph() {
		for (var kind : FirebirdSchemaObject.Kind.values()) {
			var before = schema(object(kind, "body", "old"));
			var after = schema(object(kind, "body", "new"));
			before.getSpecifics().remove(FirebirdSchemaDependencies.SPECIFICS_KEY);
			after.getSpecifics().remove(FirebirdSchemaDependencies.SPECIFICS_KEY);
			assertEquals(1, sql(before, after).size());
			assertTrue(sql(before, after).getFirst().startsWith("COMMENT ON "));
		}
	}

	@Test
	void unreferencedFilterReplacementUsesDropThenDeclareAndRequiresBothGraphs() {
		var old = object(FirebirdSchemaObject.Kind.BLOB_FILTER,
				"INPUT_TYPE 0 OUTPUT_TYPE -1000 ENTRY_POINT 'old' MODULE_NAME 'library'", "old");
		var next = object(FirebirdSchemaObject.Kind.BLOB_FILTER,
				"INPUT_TYPE 0 OUTPUT_TYPE -1000 ENTRY_POINT 'new' MODULE_NAME 'library'", "new");
		var before = schema(old);
		var after = schema(next);
		assertEquals(List.of(old.dropDefinition(), next.definition().get(0), next.definition().get(1)),
				sql(before, after));
		after.getSpecifics().remove(FirebirdSchemaDependencies.SPECIFICS_KEY);
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
	}

	@Test
	void dependentProcedureIsRemovedBeforeItsException() {
		var before = schema(object(FirebirdSchemaObject.Kind.EXCEPTION, "'old'", null));
		before.getProcedures().add(new Procedure("P").setStatement("BEGIN EXCEPTION \"native object\"; END"));
		FirebirdSchemaDependencies.set(before, List.of(edge(Kind.PROCEDURE, "P", Kind.EXCEPTION)));
		assertEquals(List.of("DROP PROCEDURE P", "DROP EXCEPTION \"native object\""), sql(before, schema()));
	}

	@Test
	void retainedOutsideOrUnreleasedDependentsAreRejectedBeforeReturningSql() {
		var before = schema(object(FirebirdSchemaObject.Kind.EXCEPTION, "'old'", null));
		before.getProcedures().add(new Procedure("P").setStatement("BEGIN EXCEPTION \"native object\"; END"));
		FirebirdSchemaDependencies.set(before, List.of(edge(Kind.PROCEDURE, "P", Kind.EXCEPTION)));
		var after = before.clone();
		FirebirdSchemaObjects.set(after, List.of());
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
		FirebirdSchemaDependencies.set(after, List.of());
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
		after.getProcedures().get(0).setRemarks("comment cannot release a reference");
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
		before.getProcedures().clear();
		assertThrows(UnsupportedOperationException.class, () -> sql(before, schema()));
	}

	@Test
	void changedProcedureCanReleaseItsExceptionReference() {
		var before = schema(object(FirebirdSchemaObject.Kind.EXCEPTION, "'old'", null));
		before.getProcedures().add(new Procedure("P").setStatement("BEGIN EXCEPTION \"native object\"; END"));
		FirebirdSchemaDependencies.set(before, List.of(edge(Kind.PROCEDURE, "P", Kind.EXCEPTION)));
		var after = before.clone();
		FirebirdSchemaObjects.set(after, List.of());
		FirebirdSchemaDependencies.set(after, List.of());
		after.getProcedures().get(0).setStatement("BEGIN END");
		var plan = sql(before, after);
		assertTrue(plan.getFirst().startsWith("ALTER PROCEDURE P"));
		assertEquals("DROP EXCEPTION \"native object\"", plan.getLast());
	}

	@Test
	void modeledCollationUsesProtectAgainstIncompleteGraphsAndOrderDomainDrop() {
		var before = schema(object(FirebirdSchemaObject.Kind.COLLATION, "FOR UTF8 FROM UNICODE", null));
		before.getDomains().add(new Domain("D").setDataType(com.sqlapp.data.db.datatype.DataType.VARCHAR).setLength(20)
				.setCharacterSet("UTF8").setCollation("native object"));
		var after = before.clone();
		FirebirdSchemaObjects.set(after, List.of());
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
		assertEquals(List.of("DROP DOMAIN D", "DROP COLLATION \"native object\""), sql(before, schema()));
		var replacement = object(FirebirdSchemaObject.Kind.COLLATION, "FOR UTF8 FROM UNICODE CASE INSENSITIVE", null);
		FirebirdSchemaObjects.set(after, List.of(replacement));
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
		after.getDomains().clear();
		assertEquals(List.of("DROP DOMAIN D", "DROP COLLATION \"native object\"", replacement.definition().getFirst()),
				sql(before, after));
	}

	@Test
	void nonCanonicalDefinitionsCannotBeGuessedIntoAnAlter() {
		var before = schema(object(FirebirdSchemaObject.Kind.EXCEPTION, "'old'", null));
		var after = schema(new FirebirdSchemaObject(FirebirdSchemaObject.Kind.EXCEPTION, "native object",
				List.of("CREATE EXCEPTION OTHER 'message'"), "DROP EXCEPTION \"native object\""));
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
	}
}
