/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class Db2RoutineAlterTest {
	private Function definedFunction() {
		var function = new Function("F").setSchemaName("APP").setSpecificNameLiteral("F_S").setLanguage("SQL")
				.setDefinition("CREATE FUNCTION APP.F(N INTEGER) RETURNS INTEGER SPECIFIC F_S LANGUAGE SQL RETURN N+1");
		context(function);
		return function;
	}

	private void context(Routine<?> routine) {
		routine.getSpecifics().put("QUALIFIER", routine.getSchemaName());
		routine.getSpecifics().put("FUNC_PATH", "\"SYSIBM\",\"APP\"");
	}

	@Test
	void unqualifiedDefinitionNamesAreResolvedFromMatchingCreationSchema() {
		var before = definedFunction()
				.setDefinition("CREATE FUNCTION F(N INTEGER) RETURNS INTEGER SPECIFIC F_S LANGUAGE SQL RETURN N+1");
		var after = before.clone()
				.setDefinition("CREATE FUNCTION F(N INTEGER) RETURNS INTEGER SPECIFIC F_S LANGUAGE SQL RETURN N+2");
		assertTrue(alter(before, after).stream().anyMatch(sql -> sql.equals(
				"CREATE OR REPLACE FUNCTION \"APP\".\"F\"(N INTEGER) RETURNS INTEGER SPECIFIC F_S LANGUAGE SQL RETURN N+2")));
		var qualified = after.clone()
				.setDefinition(String.join("\n", after.getDefinition()).replace("FUNCTION F(", "FUNCTION APP.F("));
		assertTrue(alter(before, qualified).stream().anyMatch(sql -> sql.endsWith("RETURN N+2")));
	}

	@Test
	void unqualifiedQuotedFunctionAndProcedureNamesAreEscapedWithoutRewritingBodies() {
		Function before = new Function("F\".Q").setSchemaName("A.Q").setSpecificNameLiteral("F_S").setLanguage("SQL")
				.setDefinition(
						"CREATE FUNCTION \"F\"\".Q\"() RETURNS VARCHAR(20) SPECIFIC F_S LANGUAGE SQL RETURN 'F.Q' || 'old'");
		context(before);
		var after = before.clone().setDefinition(String.join("\n", before.getDefinition()).replace("'old'", "'new'"));
		assertTrue(alter(before, after).stream()
				.anyMatch(sql -> sql.startsWith("CREATE OR REPLACE FUNCTION \"A.Q\".\"F\"\".Q\"(")
						&& sql.endsWith("RETURN 'F.Q' || 'new'")));
		Procedure procedure = new Procedure("P").setSchemaName("APP").setSpecificNameLiteral("P_S").setLanguage("SQL")
				.setDefinition("CREATE PROCEDURE P(OUT N INTEGER) SPECIFIC P_S LANGUAGE SQL BEGIN SET N=1; END");
		context(procedure);
		assertTrue(alter(procedure,
				procedure.clone().setDefinition(String.join("\n", procedure.getDefinition()).replace("N=1", "N=2")))
				.stream().anyMatch(sql -> sql.startsWith("CREATE OR REPLACE PROCEDURE \"APP\".\"P\"(")));
	}

	@Test
	void unqualifiedStatementTemplateIsQualifiedAfterXmlRoundTrip() throws Exception {
		var before = definedFunction();
		String ddl = String.join("\n", before.getDefinition()).replace("FUNCTION APP.F(", "FUNCTION F(");
		before.getDefinition().clear();
		before.setStatement("RETURN N+1");
		before.getSpecifics().put("DB2_ROUTINE_DDL_BASE64",
				java.util.Base64.getEncoder().encodeToString(ddl.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		Function restored = SchemaUtils.readXml(new java.io.StringReader(before.asXml()));
		assertTrue(alter(restored, restored.clone().setStatement("RETURN N+2")).stream().anyMatch(
				sql -> sql.startsWith("CREATE OR REPLACE FUNCTION \"APP\".\"F\"(") && sql.endsWith("RETURN N+2")));
		assertEquals("APP", restored.getSpecifics().get("QUALIFIER"));
	}

	@Test
	void unqualifiedNamesRejectMissingMismatchedOrUnprovenCreationSchema() {
		String ddl = "CREATE FUNCTION F(N INTEGER) RETURNS INTEGER SPECIFIC F_S LANGUAGE SQL RETURN N+1";
		for (String qualifier : List.of("OTHER", "app", "")) {
			var before = definedFunction().setDefinition(ddl);
			before.getSpecifics().put("QUALIFIER", qualifier);
			assertThrows(IllegalArgumentException.class,
					() -> alter(before, before.clone().setDefinition(ddl.replace("N+1", "N+2"))));
		}
		var wrongName = definedFunction().setDefinition(ddl.replace("FUNCTION F(", "FUNCTION OTHER("));
		assertThrows(IllegalArgumentException.class, () -> alter(wrongName,
				wrongName.clone().setDefinition(String.join("\n", wrongName.getDefinition()).replace("N+1", "N+2"))));
	}

	@Test
	void statementOnlyReplacementUsesVerifiedCatalogHeaderAndPreservesSource() throws Exception {
		var before = definedFunction();
		String ddl = String.join("\n", before.getDefinition());
		before.getSpecifics().put("DB2_ROUTINE_DDL_BASE64",
				java.util.Base64.getEncoder().encodeToString(ddl.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		before.getDefinition().clear();
		before.setStatement("RETURN N+1");
		Function restored = SchemaUtils.readXml(new java.io.StringReader(before.asXml()));
		var after = restored.clone().setStatement("RETURN N+2").setRemarks("new");
		var sql = alter(restored, after);
		assertTrue(sql.stream().anyMatch(
				value -> value.startsWith("CREATE OR REPLACE FUNCTION APP.F(") && value.endsWith("RETURN N+2")));
		assertTrue(sql.getLast().endsWith("IS 'new'"));
		assertEquals(List.of("RETURN N+1"), restored.getStatement());
		assertTrue(after.getDefinition().isEmpty());
	}

	@Test
	void independentlyReadBodySnapshotsValidateBothHeadersBeforeReplacement() {
		var before = definedFunction().setStatement("RETURN N+1");
		String ddl = String.join("\n", before.getDefinition());
		before.getSpecifics().put("DB2_ROUTINE_DDL_BASE64",
				java.util.Base64.getEncoder().encodeToString(ddl.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		var after = before.clone().setStatement("RETURN N+2").setDefinition(ddl.replace("N+1", "N+2"));
		after.getSpecifics().put("DB2_ROUTINE_DDL_BASE64", java.util.Base64.getEncoder().encodeToString(
				String.join("\n", after.getDefinition()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertTrue(alter(before, after).stream().anyMatch(value -> value.endsWith("RETURN N+2")));
		var mismatch = after.clone().setDefinition(ddl.replace("N INTEGER", "N BIGINT").replace("N+1", "N+2"));
		mismatch.getSpecifics().put("DB2_ROUTINE_DDL_BASE64", java.util.Base64.getEncoder().encodeToString(
				String.join("\n", mismatch.getDefinition()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertThrows(IllegalArgumentException.class, () -> alter(before, mismatch));
		var withoutDefinition = before.clone();
		withoutDefinition.getDefinition().clear();
		var cacheOnly = withoutDefinition.clone();
		cacheOnly.getSpecifics().put("DB2_ROUTINE_DDL_BASE64",
				java.util.Base64.getEncoder()
						.encodeToString(ddl.replace("CREATE FUNCTION", "CREATE /* refreshed */ FUNCTION")
								.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertEquals(List.of(), alter(withoutDefinition, cacheOnly));
		cacheOnly.getSpecifics().remove("DB2_ROUTINE_DDL_BASE64");
		assertThrows(IllegalArgumentException.class, () -> alter(withoutDefinition, cacheOnly));
	}

	@Test
	void statementReplacementRejectsStaleCorruptOrConflictingCatalogTemplates() {
		var before = definedFunction();
		String ddl = String.join("\n", before.getDefinition());
		before.getSpecifics().put("DB2_ROUTINE_DDL_BASE64",
				java.util.Base64.getEncoder().encodeToString(ddl.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		before.setStatement("RETURN N+1");
		assertTrue(alter(before, before.clone().setStatement("RETURN N+2")).stream()
				.anyMatch(value -> value.endsWith("RETURN N+2")));
		for (String encoded : List.of("!corrupt",
				java.util.Base64.getEncoder().encodeToString(new byte[] { (byte) 0xff }),
				java.util.Base64.getEncoder().encodeToString(
						ddl.replace("F_S", "OTHER").getBytes(java.nio.charset.StandardCharsets.UTF_8)))) {
			var invalid = before.clone();
			invalid.getSpecifics().put("DB2_ROUTINE_DDL_BASE64", encoded);
			assertThrows(IllegalArgumentException.class,
					() -> alter(invalid, invalid.clone().setStatement("RETURN 2")));
		}
		var stale = before.clone().setStatement("RETURN 99");
		assertThrows(IllegalArgumentException.class, () -> alter(stale, stale.clone().setStatement("RETURN 2")));
		assertThrows(IllegalArgumentException.class,
				() -> alter(before, before.clone().setStatement("DROP TABLE APP.T")));
		var changedIdentity = before.clone().setStatement("RETURN N+2").setSpecificNameLiteral("OTHER");
		assertThrows(IllegalArgumentException.class, () -> alter(before, changedIdentity));
	}

	@Test
	void missingContextScriptsAndMixedModelChangesRequireExplicitPlans() {
		var before = definedFunction();
		String ddl = String.join("\n", before.getDefinition());
		var after = before.clone().setDefinition(ddl.replace("N+1", "N+2"));
		for (String key : List.of("QUALIFIER", "FUNC_PATH")) {
			var missingBefore = before.clone();
			missingBefore.getSpecifics().remove(key);
			var missingAfter = missingBefore.clone().setDefinition(String.join("\n", after.getDefinition()));
			assertThrows(IllegalArgumentException.class, () -> alter(missingBefore, missingAfter));
		}
		assertThrows(IllegalArgumentException.class,
				() -> alter(before, after.clone().setDefinition("--#SET TERMINATOR @\n" + ddl + "@")));
		assertThrows(IllegalArgumentException.class, () -> alter(before, after.clone().setStatement("RETURN N+2")));
		before.setStatement("RETURN N+1");
		var supplemental = before.clone().setDefinition(ddl.replace("N+1", "N+2"));
		assertTrue(alter(before, supplemental).stream().anyMatch(sql -> sql.endsWith("RETURN N+2")));
	}

	@Test
	void completeSqlDefinitionsReplaceOnlyBodiesAndRestoreCommentsAndContext() {
		Function before = definedFunction().setRemarks("kept");
		before.getSpecifics().put("QUALIFIER", "APP");
		before.getSpecifics().put("FUNC_PATH", "\"SYSIBM\",\"APP\"");
		Function after = before.clone().setDefinition(String.join("\n", before.getDefinition()).replace("N+1", "N+2"));
		var sql = alter(before, after);
		assertEquals("SET CURRENT SCHEMA = 'APP'", sql.getFirst());
		assertEquals("SET CURRENT PATH = \"SYSIBM\", \"APP\"", sql.get(1));
		assertTrue(sql.get(2).startsWith("CREATE OR REPLACE FUNCTION APP.F("));
		assertTrue(sql.get(2).endsWith("RETURN N+2"));
		assertEquals("COMMENT ON SPECIFIC FUNCTION \"APP\".\"F_S\" IS 'kept'", sql.getLast());
		assertTrue(String.join("\n", before.getDefinition()).endsWith("N+1"));
	}

	@Test
	void procedureCommitOnReturnIsAHeaderOptionAndCompoundBodyStaysIntact() {
		Procedure before = new Procedure("P").setSchemaName("APP").setSpecificNameLiteral("P_S").setLanguage("SQL")
				.setDefinition(
						"CREATE PROCEDURE APP.P(OUT N INTEGER) SPECIFIC P_S LANGUAGE SQL COMMIT ON RETURN NO BEGIN SET N=1; END");
		context(before);
		var after = before.clone().setDefinition(String.join("\n", before.getDefinition()).replace("N=1", "N=2"));
		assertTrue(alter(before, after).stream().anyMatch(sql -> sql.endsWith("BEGIN SET N=2; END")));
		assertTrue(alter(before, after).getLast().endsWith("IS ''"));
	}

	@Test
	void replacementRejectsIdentitySignatureOptionsAndUnverifiedDdl() {
		var before = definedFunction();
		String ddl = String.join("\n", before.getDefinition());
		for (String target : List.of(ddl.replace("APP.F", "OTHER.F"), ddl.replace("F_S", "OTHER"),
				ddl.replace("N INTEGER", "N BIGINT"), ddl.replace("RETURNS INTEGER", "RETURNS BIGINT"),
				ddl.replace("LANGUAGE SQL", "LANGUAGE JAVA"), ddl.replace("LANGUAGE SQL", "LANGUAGE SQL DETERMINISTIC"),
				ddl.replace("SPECIFIC F_S", ""), ddl.replace("RETURN N+1", "WRAPPED 'opaque'"),
				ddl.replace("RETURN N+1", "/* unclosed"))) {
			assertThrows(IllegalArgumentException.class, () -> alter(before, before.clone().setDefinition(target)),
					target);
		}
	}

	@Test
	void quotedNamesNestedCommentsAndLiteralKeywordsDoNotConfuseHeaderProof() {
		Function before = new Function("F\".Q").setSchemaName("A.Q").setSpecificNameLiteral("S\"Q").setLanguage("SQL")
				.setDefinition(
						"/* BEGIN /* RETURN */ */ CREATE FUNCTION \"A.Q\".\"F\"\".Q\"(N VARCHAR(20) DEFAULT 'BEGIN RETURN') RETURNS INTEGER SPECIFIC \"S\"\"Q\" LANGUAGE SQL RETURN 1");
		context(before);
		var after = before.clone()
				.setDefinition(String.join("\n", before.getDefinition()).replace("RETURN 1", "RETURN 2"));
		assertTrue(alter(before, after).stream()
				.anyMatch(sql -> sql.contains("CREATE OR REPLACE FUNCTION \"A.Q\".\"F\"\".Q\"")));
		assertThrows(IllegalArgumentException.class, () -> alter(before, before.clone()
				.setDefinition(String.join("\n", after.getDefinition()).replace("'BEGIN RETURN'", "'changed'"))));
	}

	private <T extends Routine<T>> List<String> alter(T before, T after) {
		SqlFactory<T> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(after,
				SqlType.ALTER);
		return factory.createDiffSql(before.diff(after)).stream().map(SqlOperation::getSqlText).toList();
	}

	@Test
	void specificFunctionAndProcedureCommentsPreserveQuotedOverloadIdentity() {
		Function function = new Function("F").setSpecificNameLiteral("F\".S").setSchemaName("A.Q");
		Function changed = function.clone().setRemarks("owner's note");
		assertEquals(List.of("COMMENT ON SPECIFIC FUNCTION \"A.Q\".\"F\"\".S\" IS 'owner''s note'"),
				alter(function, changed));
		assertTrue(alter(changed, function).getFirst().endsWith("IS ''"));
		Procedure procedure = new Procedure("P").setSpecificNameLiteral("P.S").setSchemaName("APP");
		assertEquals(List.of("COMMENT ON SPECIFIC PROCEDURE \"APP\".\"P.S\" IS 'note'"),
				alter(procedure, procedure.clone().setRemarks("note")));
		assertEquals(List.of(), alter(function, function.clone()));
	}

	@Test
	void schemaAndCatalogStateRoutingRetainBothKinds() {
		Catalog before = new Catalog();
		Schema schema = new Schema("APP");
		schema.getFunctions().add(new Function("F").setSpecificNameLiteral("F_S"));
		schema.getProcedures().add(new Procedure("P").setSpecificNameLiteral("P_S"));
		before.getSchemas().add(schema);
		Catalog after = before.clone();
		after.getSchemas().getFirst().getFunctions().getFirst().setRemarks("F");
		after.getSchemas().getFirst().getProcedures().getFirst().setRemarks("P");
		var registry = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry();
		SqlFactory<Catalog> catalogFactory = registry.getSqlFactory(after, SqlType.ALTER);
		SqlFactory<Schema> schemaFactory = registry.getSqlFactory(after.getSchemas().getFirst(), SqlType.ALTER);
		var expected = catalogFactory.createDiffSql(before.diff(after)).stream().map(SqlOperation::getSqlText).sorted()
				.toList();
		assertEquals(2, expected.size());
		assertEquals(expected, schemaFactory.createDiffSql(schema.diff(after.getSchemas().getFirst())).stream()
				.map(SqlOperation::getSqlText).sorted().toList());
		assertNull(schema.getFunctions().getFirst().getRemarks());
	}

	@Test
	void bodyIdentityAndOptionsFailWithActionableReplacementError() {
		Function before = new Function("F").setSpecificNameLiteral("F_S").setSchemaName("APP").setStatement("RETURN 1");
		for (Function after : List.of(before.clone().setStatement("RETURN 2"), before.clone().setSpecificName("OTHER"),
				before.clone().setLanguage("JAVA"), before.clone().setDefinition("CREATE FUNCTION ..."))) {
			var error = assertThrows(IllegalArgumentException.class, () -> alter(before, after));
			assertTrue(error.getMessage().contains("replacement plan"));
		}
		Procedure procedure = new Procedure("P").setSpecificNameLiteral("P_S").setSchemaName("APP")
				.setStatement("BEGIN END");
		assertThrows(IllegalArgumentException.class,
				() -> alter(procedure, procedure.clone().setStatement("BEGIN RETURN; END")));
		SqlFactory<Function> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(before,
				SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(before));
	}

	@Test
	void commentsRequireUnambiguousSpecificIdentity() {
		Function before = new Function("F").setSpecificNameLiteral(" ").setSchemaName("APP");
		assertThrows(IllegalArgumentException.class, () -> alter(before, before.clone().setRemarks("note")));
	}

	@Test
	void unsupportedNestedChangeCannotDisappearBesideAValidComment() {
		Schema before = new Schema("APP");
		before.getFunctions().add(new Function("F").setSpecificNameLiteral("F_S").setStatement("RETURN 1"));
		before.getProcedures().add(new Procedure("P").setSpecificNameLiteral("P_S").setStatement("BEGIN END"));
		Schema after = before.clone();
		after.getFunctions().getFirst().setRemarks("valid");
		after.getProcedures().getFirst().setStatement("BEGIN RETURN; END");
		SqlFactory<Schema> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(after,
				SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(before.diff(after)));
	}
}
