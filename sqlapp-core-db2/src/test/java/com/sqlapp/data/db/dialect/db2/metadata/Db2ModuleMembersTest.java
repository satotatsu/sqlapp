/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.db.dialect.db2.Db2ModuleMembers.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Package;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2ModuleMembersTest {
	private Package module() {
		Package module = new Package("M\".Q").setSchemaName("APP");
		module.getSpecifics().putAll(Map.of("DB2_OBJECT_TYPE", "MODULE", "MODULETYPE", "M", "MEMBER_COUNT", "0"));
		return module;
	}

	private Member condition() {
		return new Member(Kind.CONDITION, "C\".Q", null, true, true, Map.of("CONDITION_SQLSTATE", "75001"));
	}

	private Member generatedCast(Package module, boolean into) {
		var fields = new HashMap<String, String>();
		fields.put("ROUTINE_ORIGIN", "S");
		fields.put("ROUTINE_PARAMETER_COUNT", "1");
		String prefix = into ? "ROUTINE_RETURN_" : "ROUTINE_PARAMETER_";
		fields.put(prefix + "SCHEMA", module.getSchemaName());
		fields.put(prefix + "MODULE", module.getName());
		fields.put(prefix + "NAME", "R");
		prefix = into ? "ROUTINE_PARAMETER_" : "ROUTINE_RETURN_";
		fields.put(prefix + "SCHEMA", "SYSIBM");
		fields.put(prefix + "NAME", "ROW");
		return new Member(Kind.FUNCTION, into ? "R" : "ROW", into ? "CAST_IN" : "CAST_OUT", true, false, fields);
	}

	@Test
	void typeOwnedGeneratedCastPairsAreRetainedButNotReplayed() {
		Package module = module();
		var into = generatedCast(module, true);
		var out = generatedCast(module, false);
		Db2ModuleMembers.set(module, List.of(into, out, rowMember(rowType("R", "APP"), true)));
		var snapshot = Db2ModuleMembers.get(module);
		assertEquals(Optional.of("R"), Db2ModuleMembers.generatedTypeOwner(snapshot, into));
		assertEquals(Optional.of("R"), Db2ModuleMembers.generatedTypeOwner(snapshot, out));
		assertEquals(2, sql(module).size());
		for (var entry : Map.of("ROUTINE_ORIGIN", "Q", "ROUTINE_PARAMETER_COUNT", "2", "ROUTINE_RETURN_MODULE", "OTHER",
				"UNKNOWN", "x").entrySet()) {
			var fields = new HashMap<>(into.attributes());
			fields.put(entry.getKey(), entry.getValue());
			Db2ModuleMembers.set(module, List.of(new Member(Kind.FUNCTION, "R", "CAST_IN", true, false, fields),
					rowMember(rowType("R", "APP"), true)));
			assertThrows(IllegalArgumentException.class, () -> sql(module));
		}
	}

	@Test
	void generatedCastTimestampTiesAndPaddedBuiltinSchemasDoNotHideUserOrder() {
		var module = new Package("M").setSchemaName("APP");
		var type = row("R", "TYPE");
		type.putAll(Map.of("TYPE_METATYPE", "F", "TYPE_NULLS", "Y", "MEMBER_CREATE_TIME", "2026-01-01 00:00:00"));
		var cast = row("R", "FUNCTION");
		cast.putAll(generatedCast(module, true).attributes());
		cast.putAll(Map.of("USERDEFINED", "N", "SPECIFICNAME", "CAST_IN", "ROUTINE_PARAMETER_SCHEMA", "SYSIBM  ",
				"MEMBER_CREATE_TIME", "2026-01-01 00:00:00"));
		var variable = row("V", "VARIABLE");
		variable.putAll(typedVariable(module, "R").attributes());
		variable.put("MEMBER_CREATE_TIME", "2026-01-01 00:00:01");
		var result = read(List.of(header("3")), List.of(cast, type, variable), List.of(fieldRow("M", "R", "ID", 0)),
				new AtomicInteger()).getFirst();
		assertTrue(Db2ModuleMembers.get(result).orderKnown());
		assertEquals(3, sql(result).size());
	}

	private Member variable() {
		return new Member(Kind.VARIABLE, "V\".Q", null, false, true,
				Map.of("TYPESCHEMA", "SYSIBM", "TYPENAME", "DECIMAL", "LENGTH", "12", "SCALE", "2", "SCOPE", "S",
						"READONLY", "C", "DEFAULT_EXPRESSION", "(1 +\n 2)", "QUALIFIER", "Base.Q", "FUNC_PATH",
						"\"SYSIBM\",\"Fns.Q\""));
	}

	private List<String> sql(Package module) {
		SqlFactory<Package> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(module,
				SqlType.CREATE);
		return factory.createSql(module).stream().map(SqlOperation::getSqlText).toList();
	}

	private Member type(String name, boolean published, Map<String, String> extra) {
		var fields = new HashMap<>(Map.of("TYPE_METATYPE", "A", "SOURCESCHEMA", "SYSIBM", "SOURCENAME", "INTEGER",
				"TYPE_NULLS", "Y", "TYPE_ARRAY_LENGTH", "10"));
		fields.putAll(extra);
		return new Member(Kind.TYPE, name, null, published, true, fields);
	}

	private Member typedVariable(Package module, String typeName) {
		return new Member(Kind.VARIABLE, "V", null, true, true, Map.of("TYPESCHEMA", module.getSchemaName(),
				"TYPEMODULENAME", module.getName(), "TYPENAME", typeName, "SCOPE", "S", "READONLY", "N"));
	}

	@Test
	void publishedRowArrayAndNestedRowDependenciesRecreateInOrder() throws Exception {
		Package module = module();
		var base = rowMember(rowType("R", "APP"), true);
		var array = type("A", true,
				Map.of("SOURCESCHEMA", "APP", "SOURCEMODULENAME", module.getName(), "SOURCENAME", "R"));
		var nested = new Type("N").setSchemaName("APP").setMetaType(Type.MetaType.Row);
		var field = new TypeColumn("ITEMS");
		field.getSpecifics()
				.putAll(Map.of("DB2_TYPE_SCHEMA", "APP", "DB2_TYPE_MODULE", module.getName(), "TYPENAME", "A"));
		nested.getColumns().add(field);
		Db2ModuleMembers.set(module, List.of(base, array, rowMember(nested, true), typedVariable(module, "N")));
		Package restored = SchemaUtils.readXml(new StringReader(module.asXml()));
		var generated = sql(restored);
		assertTrue(generated.get(2).endsWith("PUBLISH TYPE \"A\" AS \"R\" ARRAY[10]"));
		assertTrue(generated.get(3).endsWith("PUBLISH TYPE \"N\" AS ROW (\"ITEMS\" \"A\")"));
		assertTrue(generated.getLast().endsWith("PUBLISH VARIABLE \"V\" \"N\""));
		assertEquals(module, restored);
	}

	@Test
	void arrayAndRowTypeSourcesRejectForwardPrivateAndExternalReferences() {
		Package module = module();
		var base = rowMember(rowType("R", "APP"), true);
		var array = type("A", true,
				Map.of("SOURCESCHEMA", "APP", "SOURCEMODULENAME", module.getName(), "SOURCENAME", "R"));
		for (var members : List.of(List.of(array, base), List.of(rowMember(rowType("R", "APP"), false), array))) {
			Db2ModuleMembers.set(module, members);
			assertThrows(IllegalArgumentException.class, () -> sql(module));
		}
		for (String key : List.of("SOURCESCHEMA", "SOURCEMODULENAME")) {
			var fields = new HashMap<>(array.attributes());
			fields.put(key, "OTHER");
			Db2ModuleMembers.set(module, List.of(base, new Member(Kind.TYPE, "A", null, true, true, fields)));
			assertThrows(IllegalArgumentException.class, () -> sql(module));
		}
		var nested = new Type("N").setSchemaName("APP").setMetaType(Type.MetaType.Row);
		var field = new TypeColumn("F");
		field.getSpecifics()
				.putAll(Map.of("DB2_TYPE_SCHEMA", "APP", "DB2_TYPE_MODULE", module.getName(), "TYPENAME", "N"));
		nested.getColumns().add(field);
		Db2ModuleMembers.set(module, List.of(rowMember(nested, true)));
		assertThrows(IllegalArgumentException.class, () -> sql(module));
	}

	@Test
	void moduleTypesStillRequireExplicitGlobalPrerequisiteOrder() {
		Package module = module();
		var array = type("A", true, Map.of("SOURCESCHEMA", "APP", "SOURCENAME", "GLOBAL_TYPE"));
		Db2ModuleMembers.set(module, List.of(array));
		assertThrows(IllegalArgumentException.class, () -> sql(module));
		var row = rowType("R", "APP");
		row.getColumns().getFirst().getSpecifics().putAll(Map.of("DB2_TYPE_SCHEMA", "APP", "TYPENAME", "GLOBAL_TYPE"));
		Db2ModuleMembers.set(module, List.of(rowMember(row, true)));
		assertThrows(IllegalArgumentException.class, () -> sql(module));
	}

	@Test
	void bulkReaderMarksTypeDependenciesUnknownWithoutUniqueCreationTimes() {
		var base = row("R", "TYPE");
		base.putAll(Map.of("TYPE_METATYPE", "F", "TYPE_NULLS", "Y"));
		var array = row("A", "TYPE");
		array.putAll(type("A", true, Map.of("SOURCESCHEMA", "APP", "SOURCEMODULENAME", "M", "SOURCENAME", "R"))
				.attributes());
		var fields = List.of(fieldRow("M", "R", "ID", 0));
		Package unknown = read(List.of(header("2")), List.of(base, array), fields, new AtomicInteger()).getFirst();
		assertFalse(Db2ModuleMembers.get(unknown).orderKnown());
		base.put("MEMBER_CREATE_TIME", "2026-01-01 00:00:00");
		array.put("MEMBER_CREATE_TIME", "2026-01-01 00:00:01");
		Package ordered = read(List.of(header("2")), List.of(base, array), fields, new AtomicInteger()).getFirst();
		assertTrue(sql(ordered).getLast().endsWith("AS \"R\" ARRAY[10]"));
	}

	private Member routine(Kind kind, String name, String specific, boolean published, String text) {
		return new Member(kind, name, specific, published, true,
				Map.of("ROUTINE_TEXT", text, "ROUTINE_LANGUAGE", "SQL", "MODULEROUTINEIMPLEMENTED", "Y",
						"ROUTINE_QUALIFIER", "APP", "ROUTINE_FUNC_PATH", "\"SYSIBM\",\"APP\""));
	}

	@Test
	void completeModuleRoutineDefinitionsPreserveBodiesSpecificNamesAndCreationContext() throws Exception {
		Package module = module();
		String function = "ALTER MODULE \"APP\".\"M\"\".Q\" PUBLISH FUNCTION \"F\"\".X\"(P INTEGER) RETURNS INTEGER SPECIFIC \"F.S\" LANGUAGE SQL RETURN P + 1";
		String procedure = "-- native source\nALTER /* module */ MODULE \"APP\".\"M\"\".Q\" ADD PROCEDURE P(IN V VARCHAR(10) DEFAULT 'SPECIFIC X LANGUAGE SQL')\nSPECIFIC P_S LANGUAGE SQL\nBEGIN\n VALUES 'a;b';\n VALUES 2;\nEND";
		Db2ModuleMembers.set(module, List.of(routine(Kind.FUNCTION, "F\".X", "F.S", true, function),
				routine(Kind.PROCEDURE, "P", "P_S", false, procedure)));
		Package restored = SchemaUtils.readXml(new StringReader(module.asXml()));
		var generated = sql(restored);
		assertEquals(List.of("CREATE MODULE \"APP\".\"M\"\".Q\"", "SET CURRENT SCHEMA = 'APP'",
				"SET CURRENT PATH = \"SYSIBM\", \"APP\"", function.substring(0, function.lastIndexOf(" RETURN ")),
				"SET CURRENT SCHEMA = 'APP'", "SET CURRENT PATH = \"SYSIBM\", \"APP\"",
				function.replace("PUBLISH FUNCTION", "ADD FUNCTION"), "SET CURRENT SCHEMA = 'APP'",
				"SET CURRENT PATH = \"SYSIBM\", \"APP\"", procedure), generated);
		SqlFactory<Package> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(restored,
				SqlType.CREATE);
		assertEquals(3, factory.createSql(restored).stream()
				.filter(operation -> operation.getSqlType() == SqlType.ALTER).count());
		assertEquals(module, restored);
		assertEquals(module, module.clone());
	}

	@Test
	void unqualifiedRoutineModuleRequiresMatchingCreationSchema() {
		Package module = new Package("M").setSchemaName("APP");
		String text = "alter module m publish procedure p() specific p_s language sql begin end";
		var routine = routine(Kind.PROCEDURE, "P", "P_S", true, text);
		Db2ModuleMembers.set(module, List.of(routine));
		assertEquals(text.replace("publish procedure", "ADD procedure"), sql(module).getLast());
		var fields = new HashMap<>(routine.attributes());
		fields.put("ROUTINE_QUALIFIER", "OTHER");
		Db2ModuleMembers.set(module, List.of(new Member(Kind.PROCEDURE, "P", "P_S", true, true, fields)));
		assertThrows(IllegalArgumentException.class, () -> sql(module));
	}

	@Test
	void overloadedRoutineSpecificIdentityAndNestedHeaderCommentsSurviveSchemaCreate() {
		Package module = new Package("M").setSchemaName("APP");
		String first = "/* one /* nested */ end */ ALTER MODULE APP.M PUBLISH FUNCTION F(P INTEGER) RETURNS INTEGER SPECIFIC F_INT LANGUAGE SQL RETURN P";
		String second = "ALTER MODULE APP.M PUBLISH FUNCTION F(P BIGINT) RETURNS BIGINT SPECIFIC \"F.Big\" LANGUAGE SQL RETURN P";
		Db2ModuleMembers.set(module, List.of(routine(Kind.FUNCTION, "F", "F_INT", true, first),
				routine(Kind.FUNCTION, "F", "F.Big", true, second)));
		Schema schema = new Schema("APP");
		schema.getPackages().add(module);
		SqlFactory<Schema> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(schema,
				SqlType.CREATE);
		var generated = factory.createSql(schema).stream().map(SqlOperation::getSqlText).toList();
		String firstBody = first.replace("PUBLISH FUNCTION", "ADD FUNCTION"),
				secondBody = second.replace("PUBLISH FUNCTION", "ADD FUNCTION");
		assertTrue(generated.containsAll(List.of(firstBody, secondBody)));
		assertTrue(generated.indexOf(firstBody) < generated.indexOf(secondBody));
		assertTrue(
				generated.indexOf(second.substring(0, second.lastIndexOf(" RETURN"))) < generated.indexOf(firstBody));
		String invalid = second.replace("SPECIFIC \"F.Big\"", "\"SPECIFIC\" \"F.Big\"");
		Db2ModuleMembers.set(module, List.of(routine(Kind.FUNCTION, "F", "F.Big", true, invalid)));
		assertThrows(IllegalArgumentException.class, () -> sql(module));
	}

	@Test
	void moduleAssociativeArraysResolveEarlierPublishedRowTypesAndRetainIndexValidation() {
		Package module = module();
		var base = rowMember(rowType("R", "APP"), true);
		var fields = new HashMap<>(type("A", true, Map.of("TYPE_METATYPE", "L", "SOURCESCHEMA", "APP",
				"SOURCEMODULENAME", module.getName(), "SOURCENAME", "R")).attributes());
		fields.putAll(Map.of("TYPE_ARRAYINDEXTYPESCHEMA", "SYSIBM", "TYPE_ARRAYINDEXTYPENAME", "INTEGER"));
		Db2ModuleMembers.set(module, List.of(base, new Member(Kind.TYPE, "A", null, true, true, fields)));
		assertTrue(sql(module).getLast().endsWith("AS \"R\" ARRAY[INTEGER]"));
		fields.put("TYPE_ARRAYINDEXTYPESCHEMA", "CUSTOM");
		Db2ModuleMembers.set(module, List.of(base, new Member(Kind.TYPE, "A", null, true, true, fields)));
		assertThrows(IllegalArgumentException.class, () -> sql(module));
	}

	@Test
	void routineReplayRejectsMismatchedHeadersPrototypesAndUnknownComponents() {
		Package module = new Package("M").setSchemaName("APP");
		String text = "ALTER MODULE APP.M PUBLISH PROCEDURE P() SPECIFIC P_S LANGUAGE SQL BEGIN END";
		for (String invalid : List.of(text.replace("APP.M", "OTHER.M"), text.replace("APP.M", "APP.OTHER"),
				text.replace("PUBLISH", "ADD"), text.replace("PROCEDURE", "FUNCTION"), text.replace("P()", "OTHER()"),
				text.replace("P_S", "OTHER"), text.replace("SPECIFIC P_S ", ""),
				text.replace("SPECIFIC P_S", "SPECIFIC APP.P_S"), text.replace("LANGUAGE SQL", "LANGUAGE JAVA"),
				"CREATE PROCEDURE P() SPECIFIC P_S LANGUAGE SQL BEGIN END",
				"ALTER MODULE APP.M PUBLISH PROCEDURE P() /* unterminated")) {
			Db2ModuleMembers.set(module, List.of(routine(Kind.PROCEDURE, "P", "P_S", true, invalid)));
			assertThrows(IllegalArgumentException.class, () -> sql(module), invalid);
		}
		for (var entry : Map.of("ROUTINE_LANGUAGE", "JAVA", "MODULEROUTINEIMPLEMENTED", "N", "ROUTINE_REMARKS",
				"comment", "UNKNOWN", "x").entrySet()) {
			var fields = new HashMap<>(routine(Kind.PROCEDURE, "P", "P_S", true, text).attributes());
			fields.put(entry.getKey(), entry.getValue());
			Db2ModuleMembers.set(module, List.of(new Member(Kind.PROCEDURE, "P", "P_S", true, true, fields)));
			assertThrows(IllegalArgumentException.class, () -> sql(module));
		}
	}

	@Test
	void publishedSqlPrototypesSurviveXmlWithAndWithoutLanguage() throws Exception {
		Package module = new Package("M").setSchemaName("APP");
		for (Kind kind : List.of(Kind.FUNCTION, Kind.PROCEDURE)) {
			for (String suffix : List.of("", " LANGUAGE SQL", " LANGUAGE SQL; -- declaration")) {
				String text = "ALTER MODULE APP.M PUBLISH " + kind + " P(V VARCHAR(20) DEFAULT 'BEGIN; RETURN')"
						+ (kind == Kind.FUNCTION ? " RETURNS INTEGER" : "") + " SPECIFIC P_S" + suffix;
				var fields = new HashMap<>(routine(kind, "P", "P_S", true, text).attributes());
				fields.put("MODULEROUTINEIMPLEMENTED", "N");
				Db2ModuleMembers.set(module, List.of(new Member(kind, "P", "P_S", true, true, fields)));
				Package restored = SchemaUtils.readXml(new StringReader(module.asXml()));
				assertEquals(text, sql(restored).getLast());
				assertEquals(module, restored);
			}
		}
	}

	@Test
	void cyclicPublishedBodiesFollowAllDeclarationsAndKeepPrivateVisibility() throws Exception {
		Package module = new Package("M").setSchemaName("APP");
		String f = "ALTER MODULE APP.M PUBLISH FUNCTION F(N INTEGER DEFAULT LENGTH('LANGUAGE SQL')) RETURNS INTEGER SPECIFIC F_S LANGUAGE SQL BEGIN RETURN G(N); END";
		String g = "ALTER MODULE APP.M PUBLISH FUNCTION G(N INTEGER) RETURNS INTEGER SPECIFIC G_S LANGUAGE SQL DETERMINISTIC NO EXTERNAL ACTION READS SQL DATA BEGIN RETURN F(N); END";
		String p = "ALTER MODULE APP.M ADD PROCEDURE P() SPECIFIC P_S LANGUAGE SQL BEGIN VALUES 'PUBLISH; LANGUAGE SQL'; END";
		Db2ModuleMembers.set(module, List.of(routine(Kind.PROCEDURE, "P", "P_S", false, p),
				routine(Kind.FUNCTION, "F", "F_S", true, f), routine(Kind.FUNCTION, "G", "G_S", true, g)));
		Package restored = SchemaUtils.readXml(new StringReader(module.asXml()));
		var operations = sql(restored);
		String fDeclaration = f.substring(0, f.indexOf(" BEGIN")), gDeclaration = g.substring(0, g.indexOf(" BEGIN"));
		assertTrue(operations.indexOf(gDeclaration) < operations.indexOf(p));
		assertTrue(operations.indexOf(fDeclaration) < operations.indexOf(gDeclaration));
		assertEquals(f.replace("PUBLISH FUNCTION", "ADD FUNCTION"), operations.get(operations.size() - 4));
		assertEquals(g.replace("PUBLISH FUNCTION", "ADD FUNCTION"), operations.getLast());
		assertEquals("SET CURRENT SCHEMA = 'APP'", operations.get(operations.size() - 3));
		assertEquals("SET CURRENT PATH = \"SYSIBM\", \"APP\"", operations.get(operations.size() - 2));
		assertTrue(operations.stream().noneMatch(value -> value.contains("PUBLISH PROCEDURE")));
	}

	@Test
	void defaultsBeforeImplementedRoutineTimestampsRequireExplicitDependencyDefinition() {
		Package module = module();
		String text = "ALTER MODULE \"APP\".\"M\"\".Q\" PUBLISH FUNCTION F() RETURNS INTEGER SPECIFIC F_S LANGUAGE SQL RETURN 1";
		var function = routine(Kind.FUNCTION, "F", "F_S", true, text);
		var fields = new HashMap<>(variable().attributes());
		fields.put("DEFAULT_EXPRESSION", "F()");
		var dependent = new Member(Kind.VARIABLE, "V", null, true, true, fields);
		Db2ModuleMembers.set(module, List.of(dependent, function));
		assertTrue(
				assertThrows(IllegalArgumentException.class, () -> sql(module)).getMessage().contains("CREATE_TIME"));
		Db2ModuleMembers.set(module, List.of(function, dependent));
		assertFalse(sql(module).isEmpty());
		Db2ModuleMembers.set(module, List.of(variable(), function));
		assertFalse(sql(module).isEmpty());
	}

	@Test
	void bulkBuiltinSignatureProofMovesDeclarationsBeforeRoutineDefaultsAfterXml() throws Exception {
		String text = "ALTER MODULE APP.M PUBLISH FUNCTION F() RETURNS INTEGER SPECIFIC F_S LANGUAGE SQL DETERMINISTIC NO EXTERNAL ACTION CONTAINS SQL RETURN 4";
		var variable = row("V", "VARIABLE");
		variable.putAll(Map.of("TYPESCHEMA", "SYSIBM", "TYPENAME", "INTEGER", "SCOPE", "S", "READONLY", "N",
				"DEFAULT_EXPRESSION", "F()", "MEMBER_CREATE_TIME", "2026-01-01 00:00:00"));
		var function = row("F", "FUNCTION");
		function.put("SPECIFICNAME", "F_S");
		function.putAll(routine(Kind.FUNCTION, "F", "F_S", true, text).attributes());
		function.putAll(Map.of("ROUTINE_DECLARATION_INDEPENDENT", "Y", "MEMBER_CREATE_TIME", "2026-01-01 00:00:01"));
		AtomicInteger queries = new AtomicInteger();
		Package module = read(List.of(header("2")), List.of(variable, function), queries).getFirst();
		assertEquals(2, queries.get());
		Package restored = SchemaUtils.readXml(new StringReader(module.asXml()));
		assertEquals(module, restored);
		assertEquals("V", Db2ModuleMembers.get(restored).members().getFirst().name());
		var generated = sql(restored);
		int declaration = generated.indexOf(text.substring(0, text.lastIndexOf(" RETURN ")));
		int variableSql = java.util.stream.IntStream.range(0, generated.size())
				.filter(index -> generated.get(index).contains("VARIABLE \"V\" INTEGER DEFAULT (F())")).findFirst()
				.orElseThrow();
		assertTrue(declaration > 0 && declaration < variableSql);
		assertEquals(text.replace("PUBLISH FUNCTION", "ADD FUNCTION"), generated.getLast());
		function.put("ROUTINE_DECLARATION_INDEPENDENT", "N");
		Package dependent = read(List.of(header("2")), List.of(variable, function), new AtomicInteger()).getFirst();
		assertThrows(IllegalArgumentException.class, () -> sql(dependent));
		function.put("ROUTINE_DECLARATION_INDEPENDENT", "?");
		Package invalid = read(List.of(header("1")), List.of(function), new AtomicInteger()).getFirst();
		assertThrows(IllegalArgumentException.class, () -> sql(invalid));
	}

	@Test
	void resolvedBuiltinTypesDoNotProveAnchoredOrDefaultedDeclarationsIndependent() {
		Package module = module();
		var fields = new HashMap<>(variable().attributes());
		fields.put("DEFAULT_EXPRESSION", "F()");
		var variable = new Member(Kind.VARIABLE, "V", null, true, true, fields);
		String prefix = "ALTER MODULE \"APP\".\"M\"\".Q\" PUBLISH FUNCTION F";
		for (String signature : List.of("(P ANCHOR DATA TYPE TO V) RETURNS INTEGER",
				"(P INTEGER DEFAULT 1) RETURNS INTEGER", "() RETURNS ANCHOR DATA TYPE TO V")) {
			var attributes = new HashMap<>(
					routine(Kind.FUNCTION, "F", "F_S", true, prefix + signature + " SPECIFIC F_S LANGUAGE SQL RETURN 1")
							.attributes());
			attributes.put("ROUTINE_DECLARATION_INDEPENDENT", "Y");
			var function = new Member(Kind.FUNCTION, "F", "F_S", true, true, attributes);
			Db2ModuleMembers.set(module, List.of(variable, function));
			assertThrows(IllegalArgumentException.class, () -> sql(module), signature);
		}
	}

	@Test
	void moduleProcedureDeclarationsRetainResultSetsNullInputAndCommitOptions() {
		Package module = new Package("M").setSchemaName("APP");
		String prefix = "ALTER MODULE APP.M PUBLISH PROCEDURE P() SPECIFIC P_S LANGUAGE SQL ";
		for (String options : List.of("DYNAMIC RESULT SETS 0 CALLED ON NULL INPUT COMMIT ON RETURN NO",
				"DYNAMIC RESULT SETS 32767 COMMIT ON RETURN YES CALLED ON NULL INPUT")) {
			String text = prefix + options + " BEGIN VALUES 'COMMIT ON RETURN'; END";
			Db2ModuleMembers.set(module, List.of(routine(Kind.PROCEDURE, "P", "P_S", true, text)));
			assertTrue(sql(module).contains(prefix + options));
			assertEquals(text.replace("PUBLISH PROCEDURE", "ADD PROCEDURE"), sql(module).getLast());
		}
		for (String options : List.of("DYNAMIC RESULT SETS -1", "DYNAMIC RESULT SETS 32768",
				"DYNAMIC RESULT SETS 99999999999999", "DYNAMIC RESULT SETS 1.5", "DYNAMIC RESULT SETS '1'",
				"CALLED ON NULL VALUE", "COMMIT ON RETURN MAYBE")) {
			Db2ModuleMembers.set(module,
					List.of(routine(Kind.PROCEDURE, "P", "P_S", true, prefix + options + " BEGIN END")));
			assertThrows(IllegalArgumentException.class, () -> sql(module), options);
		}
		for (String options : List.of("DYNAMIC RESULT SETS 1", "COMMIT ON RETURN NO")) {
			String function = "ALTER MODULE APP.M PUBLISH FUNCTION F() RETURNS INTEGER SPECIFIC F_S LANGUAGE SQL "
					+ options + " RETURN 1";
			Db2ModuleMembers.set(module, List.of(routine(Kind.FUNCTION, "F", "F_S", true, function)));
			assertThrows(IllegalArgumentException.class, () -> sql(module));
		}
	}

	@Test
	void prototypeFlagsCannotHideBodiesOrAdditionalStatements() {
		Package module = new Package("M").setSchemaName("APP");
		String prefix = "ALTER MODULE APP.M PUBLISH PROCEDURE P() SPECIFIC P_S";
		for (String suffix : List.of(" LANGUAGE SQL BEGIN END", " LANGUAGE SQL RETURN 1",
				" LANGUAGE SQL EXTERNAL NAME 'x'", " LANGUAGE SQL WRAPPED xyz", " LANGUAGE SQL; DROP MODULE APP.M",
				" LANGUAGE SQL UPDATE T SET V=1", " LANGUAGE SQL LANGUAGE SQL")) {
			var fields = new HashMap<>(routine(Kind.PROCEDURE, "P", "P_S", true, prefix + suffix).attributes());
			fields.put("MODULEROUTINEIMPLEMENTED", "N");
			Db2ModuleMembers.set(module, List.of(new Member(Kind.PROCEDURE, "P", "P_S", true, true, fields)));
			assertThrows(IllegalArgumentException.class, () -> sql(module), suffix);
		}
		var fields = new HashMap<>(
				routine(Kind.PROCEDURE, "P", "P_S", false, prefix.replace("PUBLISH", "ADD")).attributes());
		fields.put("MODULEROUTINEIMPLEMENTED", "N");
		Db2ModuleMembers.set(module, List.of(new Member(Kind.PROCEDURE, "P", "P_S", false, true, fields)));
		assertThrows(IllegalArgumentException.class, () -> sql(module));
	}

	@Test
	void bulkRoutineReplayKeepsTwoQueriesAndRequiresKnownOrder() {
		var p = row("P", "PROCEDURE");
		p.put("SPECIFICNAME", "P_S");
		p.putAll(routine(Kind.PROCEDURE, "P", "P_S", true,
				"ALTER MODULE APP.M PUBLISH PROCEDURE P() SPECIFIC P_S LANGUAGE SQL BEGIN END").attributes());
		p.put("ROUTINE_LANGUAGE", "SQL     "); // CHAR catalog values may retain right padding.
		var c = row("C", "CONDITION");
		Package unknown = read(List.of(header("2")), List.of(c, p), new AtomicInteger()).getFirst();
		assertFalse(Db2ModuleMembers.get(unknown).orderKnown());
		assertThrows(IllegalArgumentException.class, () -> sql(unknown));
		c.put("MEMBER_CREATE_TIME", "2026-01-01 00:00:00");
		p.put("MEMBER_CREATE_TIME", "2026-01-01 00:00:01");
		AtomicInteger count = new AtomicInteger();
		var ordered = read(List.of(header("2")), List.of(c, p), count).getFirst();
		assertEquals(2, count.get());
		assertTrue(sql(ordered).getLast().contains("ADD PROCEDURE P()"));
		p.put("ROUTINE_REMARKS", "catalog comment");
		var commented = read(List.of(header("2")), List.of(c, p), new AtomicInteger()).getFirst();
		assertEquals("catalog comment",
				Db2ModuleMembers.get(commented).members().getLast().attributes().get("ROUTINE_REMARKS"));
		assertThrows(IllegalArgumentException.class, () -> sql(commented));
	}

	@Test
	void publishedArrayAndDependentVariableRecreateAfterXml() throws Exception {
		Package module = module();
		Db2ModuleMembers.set(module, List.of(type("A\".Q", true, Map.of()), typedVariable(module, "A\".Q")));
		Package restored = SchemaUtils.readXml(new StringReader(module.asXml()));
		assertEquals(List.of("CREATE MODULE \"APP\".\"M\"\".Q\"",
				"ALTER MODULE \"APP\".\"M\"\".Q\" PUBLISH TYPE \"A\"\".Q\" AS INTEGER ARRAY[10]",
				"ALTER MODULE \"APP\".\"M\"\".Q\" PUBLISH VARIABLE \"V\" \"A\"\".Q\""), sql(restored));
		assertEquals(module, restored);
		assertEquals(module, module.clone());
		Schema schema = new Schema("APP");
		schema.getPackages().add(restored);
		SqlFactory<Schema> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(schema,
				SqlType.CREATE);
		assertTrue(
				factory.createSql(schema).stream().map(SqlOperation::getSqlText).toList().containsAll(sql(restored)));
	}

	@Test
	void moduleTypesReuseDistinctConstraintsAndAssociativeArrayUnits() {
		Package module = module();
		Db2ModuleMembers.set(module, List.of(
				type("D", false,
						Map.of("TYPE_METATYPE", "T", "TYPE_TYPERULES", "W", "TYPE_NULLS", "N", "TYPE_CONSTRAINT_TEXT",
								"VALUE > 0")),
				type("A", true,
						Map.of("TYPE_METATYPE", "L", "TYPE_ARRAYINDEXTYPESCHEMA", "SYSIBM", "TYPE_ARRAYINDEXTYPENAME",
								"VARCHAR", "TYPE_ARRAYINDEXTYPE_STRINGUNITS", "CODEUNITS32",
								"TYPE_ARRAYINDEXTYPE_STRINGUNITSLENGTH", "8"))));
		var generated = sql(module);
		assertTrue(
				generated.get(1).endsWith("ADD TYPE \"D\" AS INTEGER WITH WEAK TYPE RULES NOT NULL CHECK (VALUE > 0)"));
		assertTrue(generated.get(2).endsWith("PUBLISH TYPE \"A\" AS INTEGER ARRAY[VARCHAR(8 CODEUNITS32)]"));
	}

	@Test
	void moduleTypeDependenciesRequireEarlierPublishedTypesInSameModule() {
		Package module = module();
		var variable = typedVariable(module, "A");
		for (List<Member> members : List.of(List.of(variable, type("A", true, Map.of())),
				List.of(type("A", false, Map.of()), variable), List.of(variable))) {
			Db2ModuleMembers.set(module, members);
			assertThrows(IllegalArgumentException.class, () -> sql(module));
		}
		var external = new HashMap<>(variable.attributes());
		external.put("TYPEMODULENAME", "OTHER");
		Db2ModuleMembers.set(module,
				List.of(type("A", true, Map.of()), new Member(Kind.VARIABLE, "V", null, true, true, external)));
		assertThrows(IllegalArgumentException.class, () -> sql(module));
	}

	@Test
	void unsupportedModuleTypesAndGeneratedMembersRequireFullDdl() {
		Package module = module();
		for (var entry : Map.of("TYPE_METATYPE", "F", "TYPE_NULLS", "?", "TYPE_ARRAY_LENGTH", "0", "SOURCEMODULENAME",
				"OTHER", "TYPE_REMARKS", "comment", "TYPE_COLLATIONSCHEMA", "CUSTOM", "UNKNOWN", "x").entrySet()) {
			Db2ModuleMembers.set(module, List.of(type("A", true, Map.of(entry.getKey(), entry.getValue()))));
			assertThrows(IllegalArgumentException.class, () -> sql(module));
		}
		Db2ModuleMembers.set(module,
				List.of(type("A", true, Map.of()), new Member(Kind.FUNCTION, "A", "CAST_A", true, false, Map.of())));
		assertThrows(IllegalArgumentException.class, () -> sql(module));
	}

	@Test
	void bulkReaderRetainsTypeComponentsAndChecksDependentVariableOrder() {
		var t = row("A", "TYPE");
		t.putAll(type("A", true, Map.of("TYPE_CODEPAGE", "1208")).attributes());
		var v = row("V", "VARIABLE");
		v.putAll(typedVariable(new Package("M").setSchemaName("APP"), "A").attributes());
		Package unknown = read(List.of(header("2")), List.of(t, v), new AtomicInteger()).getFirst();
		assertFalse(Db2ModuleMembers.get(unknown).orderKnown());
		t.put("MEMBER_CREATE_TIME", "2026-01-01 00:00:00");
		v.put("MEMBER_CREATE_TIME", "2026-01-01 00:00:01");
		AtomicInteger count = new AtomicInteger();
		Package ordered = read(List.of(header("2")), List.of(t, v), count).getFirst();
		assertEquals(2, count.get());
		assertEquals("1208", Db2ModuleMembers.get(ordered).members().getFirst().attributes().get("TYPE_CODEPAGE"));
		assertEquals(3, sql(ordered).size());
	}

	@Test
	void conditionAndVariableMembersRecreateAfterXmlAndThroughSchema() throws Exception {
		Package module = module();
		Db2ModuleMembers.set(module, List.of(condition(), variable()));
		Package restored = SchemaUtils.readXml(new StringReader(module.asXml()));
		assertEquals(module, restored);
		assertEquals(module, module.clone());
		assertEquals(Db2ModuleMembers.get(module), Db2ModuleMembers.get(restored));
		var expected = List.of("CREATE MODULE \"APP\".\"M\"\".Q\"",
				"ALTER MODULE \"APP\".\"M\"\".Q\" PUBLISH CONDITION \"C\"\".Q\" FOR SQLSTATE '75001'",
				"SET CURRENT SCHEMA = 'Base.Q'", "SET CURRENT PATH = \"SYSIBM\", \"Fns.Q\"",
				"ALTER MODULE \"APP\".\"M\"\".Q\" ADD VARIABLE \"V\"\".Q\" DECIMAL(12,2) CONSTANT ((1 +\n 2))");
		assertEquals(expected, sql(restored));
		Schema schema = new Schema("APP");
		schema.getPackages().add(restored);
		SqlFactory<Schema> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(schema,
				SqlType.CREATE);
		assertTrue(factory.createSql(schema).stream().map(SqlOperation::getSqlText).toList().containsAll(expected));
	}

	@Test
	void visibilityGenericConditionsAndVariablesWithoutDefaultsAreSupported() {
		Package module = new Package("M").setSchemaName("APP");
		Db2ModuleMembers.set(module,
				List.of(new Member(Kind.CONDITION, "C", null, false, true, Map.of()),
						new Member(Kind.VARIABLE, "V", null, true, true,
								Map.of("TYPESCHEMA", "SYSIBM", "TYPENAME", "INTEGER", "SCOPE", "S", "READONLY", "N"))));
		var sql = sql(module);
		assertEquals("M", module.getSpecifics().get("MODULETYPE"));
		assertTrue(sql.get(1).endsWith("ADD CONDITION \"C\""));
		assertTrue(sql.get(2).endsWith("PUBLISH VARIABLE \"V\" INTEGER"));
	}

	@Test
	void variablesReuseNativeUnitsAndRejectUnsupportedScopeTypesAndUnknownAttributes() {
		Package module = module();
		Map<String, String> fields = new HashMap<>(
				Map.of("TYPESCHEMA", "SYSIBM", "TYPENAME", "VARCHAR", "LENGTH", "20", "TYPESTRINGUNITS", "CODEUNITS32",
						"STRINGUNITSLENGTH", "5", "CODEPAGE", "1208", "SCOPE", "S", "READONLY", "N"));
		Db2ModuleMembers.set(module, List.of(new Member(Kind.VARIABLE, "V", null, true, true, fields)));
		assertTrue(sql(module).getLast().endsWith("VARCHAR(5 CODEUNITS32)"));
		for (var entry : Map.of("SCOPE", "D", "READONLY", "S", "TYPEMODULENAME", "PRIVATE_TYPE", "UNKNOWN_SETTING",
				"value", "VARIABLE_REMARKS", "comment", "COLLATIONSCHEMA", "CUSTOM").entrySet()) {
			var invalid = new HashMap<>(fields);
			invalid.put(entry.getKey(), entry.getValue());
			Db2ModuleMembers.set(module, List.of(new Member(Kind.VARIABLE, "V", null, true, true, invalid)));
			assertThrows(IllegalArgumentException.class, () -> sql(module));
		}
		Db2ModuleMembers.set(module, List.of(condition()));
		module.getSpecifics().put("MEMBER_COUNT", "2");
		assertThrows(IllegalArgumentException.class, () -> sql(module));
	}

	@Test
	void unsupportedMembersAmbiguousOrderAndCommentsRequireAuthoritativeDdl() {
		Package module = module();
		for (Member member : List.of(
				new Member(Kind.PROCEDURE, "P", "SPEC", true, true, Map.of("ROUTINE_TEXT", "native text")),
				new Member(Kind.TYPE, "T", null, true, true, Map.of()),
				new Member(Kind.CONDITION, "C", null, true, false, Map.of()),
				new Member(Kind.CONDITION, "C", null, true, true, Map.of("CONDITION_SQLSTATE", "00000")),
				new Member(Kind.CONDITION, "C", null, true, true, Map.of("CONDITION_REMARKS", "comment")))) {
			Db2ModuleMembers.set(module, List.of(member));
			assertThrows(IllegalArgumentException.class, () -> sql(module));
		}
		Db2ModuleMembers.set(module, List.of(condition(), variable()), false);
		assertThrows(IllegalArgumentException.class, () -> sql(module));
		module.setDefinition("--#SET TERMINATOR @\nCREATE MODULE APP.M@\nALTER MODULE APP.M PUBLISH CONDITION C@");
		assertEquals(List.of("CREATE MODULE APP.M", "ALTER MODULE APP.M PUBLISH CONDITION C"), sql(module));
	}

	@Test
	void snapshotsRejectDuplicatesStaleScopesMalformedFormatsAndUnknownJson() {
		Package module = module();
		Db2ModuleMembers.set(module, List.of(condition()));
		String original = module.getSpecifics().get(Db2ModuleMembers.SPECIFICS_KEY);
		assertThrows(IllegalArgumentException.class,
				() -> Db2ModuleMembers.set(module, List.of(condition(), condition())));
		assertEquals(original, module.getSpecifics().get(Db2ModuleMembers.SPECIFICS_KEY));
		assertThrows(UnsupportedOperationException.class, () -> Db2ModuleMembers.get(module).members().add(variable()));
		Package renamed = module.clone().setName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> Db2ModuleMembers.get(renamed));
		module.getSpecifics().put(Db2ModuleMembers.SPECIFICS_KEY, original.replace("\"format\":1", "\"format\":2"));
		assertThrows(RuntimeException.class, () -> Db2ModuleMembers.get(module));
		module.getSpecifics().put(Db2ModuleMembers.SPECIFICS_KEY, original.replaceFirst("\\{", "{\"unknown\":true,"));
		assertThrows(RuntimeException.class, () -> Db2ModuleMembers.get(module));
		assertThrows(IllegalArgumentException.class, () -> new Member(Kind.CONDITION, "C", null, null, true, Map.of()));
	}

	private Map<String, String> header(String count) {
		return Map.of("MODULESCHEMA", "APP", "MODULENAME", "M", "MODULETYPE", "M", "DIALECT", "SQL PL", "MEMBER_COUNT",
				count);
	}

	private Map<String, String> row(String name, String kind) {
		return new HashMap<>(Map.of("OBJECTSCHEMA", "APP", "OBJECTMODULENAME", "M", "OBJECTNAME", name, "OBJECTTYPE",
				kind, "PUBLISHED", "Y", "USERDEFINED", "Y"));
	}

	private List<Package> read(List<Map<String, String>> headers, List<Map<String, String>> members,
			AtomicInteger queries) {
		return read(headers, members, List.of(), queries);
	}

	private List<Package> read(List<Map<String, String>> headers, List<Map<String, String>> members,
			List<Map<String, String>> fields, AtomicInteger queries) {
		var reader = new Db2ModuleReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode node, ParametersContext p,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				for (var values : node.toString().contains("SYSCAT.ROWFIELDS") ? fields
						: node.toString().contains("CONDITION_SQLSTATE") ? members : headers)
					try {
						handler.handleResultSetNext(new ExResultSet(null) {
							@Override
							public String getString(String key) {
								return values.get(key);
							}

							@Override
							public Object getObject(String key) {
								return values.get(key);
							}

							@Override
							public long getLong(String key) {
								return Long.parseLong(values.getOrDefault(key, "0"));
							}

							@Override
							public int getInt(String key) {
								return Integer.parseInt(values.getOrDefault(key, "0"));
							}

							@Override
							public boolean wasNull() {
								return false;
							}

							@Override
							public Timestamp getTimestamp(String key) {
								return values.get(key) == null ? null : Timestamp.valueOf(values.get(key));
							}
						});
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				return null;
			}
		};
		return reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
	}

	private Type rowType(String name, String schema) {
		Type type = new Type(name).setSchemaName(schema).setMetaType(Type.MetaType.Row);
		type.getColumns().add("ID", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		var text = new TypeColumn("T\".X");
		text.getSpecifics().putAll(Map.of("DB2_TYPE_SCHEMA", "SYSIBM", "TYPENAME", "VARCHAR", "LENGTH", "20",
				"CODEPAGE", "1208", "TYPESTRINGUNITS", "CODEUNITS32", "STRINGUNITSLENGTH", "5"));
		type.getColumns().add(text);
		return type;
	}

	private Member rowMember(Type type, boolean published) {
		return Db2ModuleMembers.withRowType(new Member(Kind.TYPE, type.getName(), null, published, true,
				Map.of("TYPE_METATYPE", "F", "TYPE_NULLS", "Y")), type);
	}

	@Test
	void moduleRowTypeAndDependentVariableUseCanonicalModelsAfterXmlAndSchemaCreate() throws Exception {
		Package module = module();
		var type = rowType("R\".X", module.getSchemaName());
		Db2ModuleMembers.set(module, List.of(rowMember(type, true), typedVariable(module, type.getName())));
		Package restored = SchemaUtils.readXml(new StringReader(module.asXml()));
		var operations = sql(restored);
		assertTrue(operations.get(1)
				.endsWith("PUBLISH TYPE \"R\"\".X\" AS ROW (\"ID\" INTEGER, \"T\"\".X\" VARCHAR(5 CODEUNITS32))"));
		assertTrue(operations.getLast().endsWith("PUBLISH VARIABLE \"V\" \"R\"\".X\""));
		assertEquals(type, Db2ModuleMembers.getRowType(Db2ModuleMembers.get(restored).members().getFirst()));
		assertEquals(module, restored);
		assertEquals(module, module.clone());
		Schema schema = new Schema("APP");
		schema.getPackages().add(restored);
		SqlFactory<Schema> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(schema,
				SqlType.CREATE);
		assertTrue(factory.createSql(schema).stream().map(SqlOperation::getSqlText).toList().containsAll(operations));
	}

	@Test
	void moduleRowModelsRejectStaleIdentityScopeAndEmbeddedDdl() {
		Package module = module();
		Type type = rowType("R", "APP");
		var member = rowMember(type, true);
		Db2ModuleMembers.getRowType(member).getColumns().clear();
		assertEquals(2, Db2ModuleMembers.getRowType(member).getColumns().size());
		assertThrows(IllegalArgumentException.class,
				() -> Db2ModuleMembers.withRowType(member, type.clone().setName("OTHER")));
		for (Type invalid : List.of(type.clone().setSchemaName("OTHER"),
				type.clone().setDefinition("CREATE TYPE APP.R AS INTEGER"), type.clone().setRemarks("comment"))) {
			Db2ModuleMembers.set(module, List.of(rowMember(invalid, true)));
			assertThrows(IllegalArgumentException.class, () -> sql(module));
		}
		var attributes = new HashMap<>(member.attributes());
		attributes.put("TYPE_ROW_MODEL", "<schema name=\"R\"/>");
		assertThrows(IllegalArgumentException.class,
				() -> Db2ModuleMembers.getRowType(new Member(Kind.TYPE, "R", null, true, true, attributes)));
	}

	@Test
	void moduleRowsRetainUnsupportedFieldsAndRejectIncompleteOrPrivateDependencies() {
		Package module = module();
		Type type = rowType("R", "APP");
		type.getColumns().getFirst().getSpecifics().put("DB2_TYPE_SCHEMA", "CUSTOM");
		Db2ModuleMembers.set(module, List.of(rowMember(type, true)));
		assertThrows(IllegalArgumentException.class, () -> sql(module));
		type.getColumns().clear();
		Db2ModuleMembers.set(module, List.of(rowMember(type, true)));
		assertThrows(IllegalArgumentException.class, () -> sql(module));
		type = rowType("R", "APP");
		Db2ModuleMembers.set(module, List.of(rowMember(type, false), typedVariable(module, "R")));
		assertThrows(IllegalArgumentException.class, () -> sql(module));
	}

	private Map<String, String> fieldRow(String module, String type, String name, int ordinal) {
		var fields = new HashMap<>(Map.of("schema_name", "APP", "module_name", module, "type_name", type, "column_name",
				name, "data_type_schema", "SYSIBM", "data_type", "INTEGER", "LENGTH", "4", "SCALE", "0", "ORDINAL",
				Integer.toString(ordinal)));
		return fields;
	}

	@Test
	void twentyModuleRowTypesUseOneBulkFieldQueryAndKeepModuleIdentity() {
		var headers = new ArrayList<Map<String, String>>();
		var members = new ArrayList<Map<String, String>>();
		var fields = new ArrayList<Map<String, String>>();
		for (int i = 0; i < 20; i++) {
			var header = new HashMap<>(header("1"));
			header.put("MODULENAME", "M" + i);
			headers.add(header);
			var member = row("R", "TYPE");
			member.putAll(Map.of("OBJECTMODULENAME", "M" + i, "TYPE_METATYPE", "F", "TYPE_NULLS", "Y"));
			members.add(member);
			fields.add(fieldRow("M" + i, "R", "ID", 0));
			fields.add(fieldRow("M" + i, "R", "VALUE", 1));
		}
		AtomicInteger count = new AtomicInteger();
		var modules = read(headers, members, fields, count);
		assertEquals(3, count.get());
		assertEquals(20, modules.size());
		for (var module : modules) {
			var model = Db2ModuleMembers.getRowType(Db2ModuleMembers.get(module).members().getFirst());
			assertEquals(List.of("ID", "VALUE"), model.getColumns().stream().map(TypeColumn::getName).toList());
			assertTrue(sql(module).getLast().contains("AS ROW (\"ID\" INTEGER, \"VALUE\" INTEGER)"));
		}
	}

	@Test
	void rowFieldSnapshotsRejectGapsDuplicatesMissingFieldsAndScopeDrift() {
		var member = row("R", "TYPE");
		member.putAll(Map.of("TYPE_METATYPE", "F", "TYPE_NULLS", "Y"));
		for (var fields : List.of(List.<Map<String, String>>of(), List.of(fieldRow("M", "R", "ID", 1)),
				List.of(fieldRow("M", "R", "ID", 0), fieldRow("M", "R", "ID", 1)),
				List.of(fieldRow("OTHER", "R", "ID", 0))))
			assertThrows(IllegalStateException.class,
					() -> read(List.of(header("1")), List.of(member), fields, new AtomicInteger()));
	}

	@Test
	void bulkReaderPreservesAllKindsOverloadsAndRejectsIncompleteSnapshots() {
		var c = row("C", "CONDITION");
		c.put("CONDITION_SQLSTATE", "75001");
		var p1 = row("P", "PROCEDURE");
		p1.put("SPECIFICNAME", "P1");
		p1.put("ROUTINE_TEXT", "CREATE PROCEDURE native body");
		var p2 = row("P", "PROCEDURE");
		p2.put("SPECIFICNAME", "P2");
		var t = row("T", "TYPE");
		t.put("TYPE_METATYPE", "A");
		var f = row("F", "FUNCTION");
		f.put("SPECIFICNAME", "F1");
		var v = row("V", "VARIABLE");
		v.put("TYPENAME", "INTEGER");
		AtomicInteger count = new AtomicInteger();
		var modules = read(List.of(header("6")), List.of(c, p1, p2, t, f, v), count);
		assertEquals(2, count.get());
		assertEquals(6, Db2ModuleMembers.get(modules.getFirst()).members().size());
		assertEquals("CREATE PROCEDURE native body",
				Db2ModuleMembers.get(modules.getFirst()).members().get(1).attributes().get("ROUTINE_TEXT"));
		assertThrows(IllegalStateException.class, () -> read(List.of(header("2")), List.of(c), new AtomicInteger()));
		c.put("PUBLISHED", "?");
		assertThrows(IllegalStateException.class, () -> read(List.of(header("1")), List.of(c), new AtomicInteger()));
		count.set(0);
		assertTrue(read(List.of(), List.of(), count).isEmpty());
		assertEquals(1, count.get());
	}

	@Test
	void readerMarksVariableDefaultOrderUnknownWhenTimestampsAreUnavailable() {
		var c = row("C", "CONDITION");
		var v = row("V", "VARIABLE");
		v.putAll(Map.of("DEFAULT_EXPRESSION", "1", "TYPESCHEMA", "SYSIBM", "TYPENAME", "INTEGER", "SCOPE", "S",
				"READONLY", "N"));
		Package module = read(List.of(header("2")), List.of(c, v), new AtomicInteger()).getFirst();
		assertFalse(Db2ModuleMembers.get(module).orderKnown());
		assertThrows(IllegalArgumentException.class, () -> sql(module));
		c.put("MEMBER_CREATE_TIME", "2026-01-01 00:00:00");
		v.put("MEMBER_CREATE_TIME", "2026-01-01 00:00:01");
		Package ordered = read(List.of(header("2")), List.of(c, v), new AtomicInteger()).getFirst();
		assertTrue(Db2ModuleMembers.get(ordered).orderKnown());
		assertEquals(3, sql(ordered).size());
	}
}
