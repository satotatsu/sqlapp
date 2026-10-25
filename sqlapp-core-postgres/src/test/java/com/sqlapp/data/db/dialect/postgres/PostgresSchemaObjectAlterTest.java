/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObject.Kind;
import com.sqlapp.data.db.dialect.postgres.sql.PostgresAlterCatalogFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;

class PostgresSchemaObjectAlterTest {
	private PostgresSchemaObject object(Kind kind, String definition) {
		String schema = switch (kind) {
		case COLLATION, FOREIGN_TABLE, POLICY, ROW_SECURITY, STATISTICS -> "s";
		default -> null;
		};
		return new PostgresSchemaObject(kind, schema, "obj",
				kind == Kind.POLICY ? "table" : kind == Kind.USER_MAPPING ? "server" : null, List.of(definition),
				"DROP " + kind + " obj", Map.of());
	}

	private List<String> alter(PostgresSchemaObject old, PostgresSchemaObject next) {
		var original = new Catalog("db");
		var target = original.clone();
		if (old.schemaName() != null && old.kind() != Kind.EXTENSION) {
			var a = new Schema("s");
			var b = new Schema("s");
			PostgresSchemaObjects.set(a, List.of(old));
			PostgresSchemaObjects.set(b, List.of(next));
			original.getSchemas().add(a);
			target.getSchemas().add(b);
		} else {
			PostgresSchemaObjects.set(original, List.of(old));
			PostgresSchemaObjects.set(target, List.of(next));
		}
		var factory = (PostgresAlterCatalogFactory) DialectHolder.postgreSQL180.createSqlFactoryRegistry()
				.getSqlFactory(original, SqlType.ALTER);
		return factory.createDiffSql(original.diff(target)).stream().map(o -> o.getSqlText()).toList();
	}

	@Test
	void diffsOptionsWithQuotedKeywordsCommasAndEscapedValues() {
		var a = object(Kind.SERVER,
				"CREATE SERVER obj VERSION '1' FOREIGN DATA WRAPPER w OPTIONS (\"WITH\" 'a,b', obsolete 'x')");
		var b = object(Kind.SERVER,
				"CREATE SERVER obj VERSION '2' FOREIGN DATA WRAPPER w OPTIONS (\"WITH\" 'it''s, OPTIONS (x)', host 'new')");
		assertEquals(List.of("ALTER SERVER \"obj\" VERSION '2'",
				"ALTER SERVER \"obj\" OPTIONS (DROP \"obsolete\", SET \"WITH\" 'it''s, OPTIONS (x)', ADD \"host\" 'new')"),
				alter(a, b));
	}

	@Test
	void changesForeignColumnsWithoutReplacingTable() {
		var a = object(Kind.FOREIGN_TABLE,
				"CREATE FOREIGN TABLE s.obj (id numeric(10,2) OPTIONS (column_name 'x') DEFAULT 2 NOT NULL, old text, CONSTRAINT positive CHECK (id>0)) SERVER srv");
		var b = object(Kind.FOREIGN_TABLE,
				"CREATE FOREIGN TABLE s.obj (id bigint OPTIONS (column_name 'y') DEFAULT (3 + 4), added text DEFAULT 'NOT NULL, OPTIONS (', CONSTRAINT positive CHECK (id>1)) SERVER srv");
		var sql = alter(a, b);
		assertTrue(sql.contains("ALTER FOREIGN TABLE \"s\".\"obj\" ALTER COLUMN id TYPE bigint"), sql.toString());
		assertTrue(sql.contains("ALTER FOREIGN TABLE \"s\".\"obj\" ALTER COLUMN id SET DEFAULT (3 + 4)"),
				sql.toString());
		assertTrue(sql.stream().anyMatch(s -> s.endsWith("ALTER COLUMN id DROP NOT NULL")));
		assertTrue(sql.stream().anyMatch(s -> s.endsWith("OPTIONS (SET \"column_name\" 'y')")));
		assertTrue(sql.stream().anyMatch(s -> s.endsWith("DROP COLUMN old")));
		assertTrue(sql.stream().anyMatch(s -> s.endsWith("ADD COLUMN added text DEFAULT 'NOT NULL, OPTIONS ('")));
		assertTrue(sql.stream().anyMatch(s -> s.endsWith("DROP CONSTRAINT positive")));
		assertFalse(sql.stream().anyMatch(s -> s.startsWith("DROP FOREIGN TABLE")));
	}

	@Test
	void publicationMembershipAndOptionsAreChangedInPlace() {
		var a = object(Kind.PUBLICATION, "CREATE PUBLICATION obj FOR TABLE ONLY s.a WITH (publish='insert')");
		var b = object(Kind.PUBLICATION,
				"CREATE PUBLICATION obj FOR TABLE ONLY s.b WHERE (id > 1), TABLES IN SCHEMA \"WITH\" WITH (publish='insert, update')");
		assertEquals(List.of("ALTER PUBLICATION \"obj\" SET TABLE ONLY s.b WHERE (id > 1), TABLES IN SCHEMA \"WITH\"",
				"ALTER PUBLICATION \"obj\" SET (publish = 'insert, update')"), alter(a, b));
		assertEquals(List.of("ALTER PUBLICATION \"obj\" DROP TABLE ONLY s.b"),
				alter(object(Kind.PUBLICATION,
						"CREATE PUBLICATION obj FOR TABLE ONLY s.b (id) WHERE (id>1) WITH (publish='insert')"),
						object(Kind.PUBLICATION, "CREATE PUBLICATION obj WITH (publish='insert')")));
	}

	@Test
	void subscriptionChangesNeverRefreshOrRecreateSlots() {
		var a = object(Kind.SUBSCRIPTION,
				"CREATE SUBSCRIPTION obj CONNECTION __SQLAPP_SUBSCRIPTION_CONNECTION__ PUBLICATION a WITH (connect=false,enabled=false,create_slot=false,copy_data=false,slot_name=NONE,synchronous_commit='off')");
		var b = object(Kind.SUBSCRIPTION,
				"CREATE SUBSCRIPTION obj CONNECTION __SQLAPP_SUBSCRIPTION_CONNECTION__ PUBLICATION b WITH (connect=false,enabled=false,create_slot=false,copy_data=false,slot_name=NONE,synchronous_commit='local')");
		assertEquals(List.of("ALTER SUBSCRIPTION \"obj\" SET PUBLICATION b WITH (refresh = false)",
				"ALTER SUBSCRIPTION \"obj\" SET (synchronous_commit = 'local')"), alter(a, b));
	}

	@Test
	void immutableChangesFailBeforeReturningPartialSql() {
		assertThrows(IllegalArgumentException.class,
				() -> alter(object(Kind.COLLATION, "CREATE COLLATION s.obj (locale='C')"),
						object(Kind.COLLATION, "CREATE COLLATION s.obj (locale='POSIX')")));
		assertThrows(IllegalArgumentException.class,
				() -> alter(object(Kind.SERVER, "CREATE SERVER obj TYPE 'a' FOREIGN DATA WRAPPER w"),
						object(Kind.SERVER, "CREATE SERVER obj TYPE 'b' FOREIGN DATA WRAPPER w")));
	}

	@Test
	void reorderingObjectsIsNotAnAlterAndRemovedCommentsAreCleared() {
		var a = object(Kind.SERVER, "CREATE SERVER obj FOREIGN DATA WRAPPER w");
		var old = new PostgresSchemaObject(a.kind(), a.schemaName(), a.name(), a.parentName(),
				List.of(a.definition().getFirst(), "COMMENT ON SERVER obj IS 'old'"), a.dropDefinition(),
				Map.of("remarks", "old"));
		assertEquals(List.of("COMMENT ON SERVER \"obj\" IS NULL"), alter(old, a));
		assertEquals(List.of(), alter(a, a));
		var original = new Catalog("db");
		var target = original.clone();
		var second = new PostgresSchemaObject(Kind.SERVER, null, "second", null,
				List.of("CREATE SERVER second FOREIGN DATA WRAPPER w"), "DROP SERVER second", Map.of());
		PostgresSchemaObjects.set(original, List.of(a, second));
		PostgresSchemaObjects.set(target, List.of(second, a));
		assertTrue(DialectHolder.postgreSQL180.createSqlFactoryRegistry().getSqlFactory(original, SqlType.ALTER)
				.createDiffSql(original.diff(target)).isEmpty());
	}

	@Test
	void altersPolicyExpressionsAndStatisticsTargetsWithoutReplacement() {
		assertEquals(List.of("ALTER POLICY \"obj\" ON \"s\".\"table\" USING ((id > 2)) WITH CHECK ((amount > 3))"),
				alter(object(Kind.POLICY,
						"CREATE POLICY obj ON s.table AS PERMISSIVE FOR ALL TO PUBLIC USING ((id > 0)) WITH CHECK ((amount > 0))"),
						object(Kind.POLICY,
								"CREATE POLICY obj ON s.table AS PERMISSIVE FOR ALL TO PUBLIC USING ((id > 2)) WITH CHECK ((amount > 3))")));
		assertEquals(List.of("ALTER STATISTICS s.obj SET STATISTICS 42"), alter(
				object(Kind.STATISTICS,
						"CREATE STATISTICS s.obj ON id,amount FROM s.table; ALTER STATISTICS s.obj SET STATISTICS 12"),
				object(Kind.STATISTICS,
						"CREATE STATISTICS s.obj ON id,amount FROM s.table; ALTER STATISTICS s.obj SET STATISTICS 42")));
	}

	@Test
	void wrapperHandlersAndExtensionUpdatesUseAlter() {
		assertEquals(
				List.of("ALTER FOREIGN DATA WRAPPER \"obj\" HANDLER \"HANDLER\".fn",
						"ALTER FOREIGN DATA WRAPPER \"obj\" VALIDATOR s.validator"),
				alter(object(Kind.FOREIGN_DATA_WRAPPER, "CREATE FOREIGN DATA WRAPPER obj NO HANDLER NO VALIDATOR"),
						object(Kind.FOREIGN_DATA_WRAPPER,
								"CREATE FOREIGN DATA WRAPPER obj HANDLER \"HANDLER\".fn VALIDATOR s.validator")));
		var a = new PostgresSchemaObject(Kind.EXTENSION, "s", "obj", null,
				List.of("CREATE EXTENSION obj WITH SCHEMA s VERSION '1'"), "DROP EXTENSION obj", Map.of());
		var b = new PostgresSchemaObject(Kind.EXTENSION, "other", "obj", null,
				List.of("CREATE EXTENSION obj WITH SCHEMA other VERSION '2'"), "DROP EXTENSION obj", Map.of());
		assertEquals(List.of("ALTER EXTENSION \"obj\" UPDATE TO '2'", "ALTER EXTENSION \"obj\" SET SCHEMA \"other\""),
				alter(a, b));
	}

	@Test
	void externalSecretChangesAreExplicitAndDoNotRequireUnchangedSecrets() throws Exception {
		var object = object(Kind.USER_MAPPING,
				"CREATE USER MAPPING FOR obj SERVER server OPTIONS (password '__SQLAPP_OPTION_password__')");
		var original = new Catalog("db");
		PostgresSchemaObjects.set(original, List.of(object));
		var target = original.clone();
		var factory = (PostgresAlterCatalogFactory) DialectHolder.postgreSQL180.createSqlFactoryRegistry()
				.getSqlFactory(original, SqlType.ALTER);
		assertTrue(factory.createDiffSql(original.diff(target)).isEmpty());
		var sql = factory.createDiffSql(original.diff(target), Map.of(object.id() + ":password", "new'password"));
		assertEquals("ALTER USER MAPPING FOR \"obj\" SERVER \"server\" OPTIONS (SET \"password\" E'new''password')",
				sql.getFirst().getSqlText());
		assertFalse(target.asXml().contains("new'password"));
	}

	@Test
	void extensionMembersAreNeverDroppedIndependently() {
		var original = new Schema("s");
		var target = new Schema("s");
		var function = new com.sqlapp.data.schemas.Function("extension_function");
		function.getSpecifics().put("postgres.extension", "example");
		original.getFunctions().add(function);
		var factory = DialectHolder.postgreSQL180.createSqlFactoryRegistry().getSqlFactory(original, SqlType.ALTER);
		assertTrue(factory.createDiffSql(original.diff(target)).isEmpty());
		assertEquals(1, original.getFunctions().size());
	}

	@Test
	void arrayAndDollarQuotedDefaultsKeepInternalSeparatorsOpaque() {
		var a = object(Kind.FOREIGN_TABLE,
				"CREATE FOREIGN TABLE s.obj (values integer[] DEFAULT ARRAY[1,2], label text DEFAULT $value$old, DEFAULT$value$) SERVER srv");
		var b = object(Kind.FOREIGN_TABLE,
				"CREATE FOREIGN TABLE s.obj (values integer[] DEFAULT ARRAY[3,4], label text DEFAULT $value$WITH, OPTIONS ( 'quoted' );$value$) SERVER srv");
		assertEquals(List.of("ALTER FOREIGN TABLE \"s\".\"obj\" ALTER COLUMN values SET DEFAULT ARRAY[3,4]",
				"ALTER FOREIGN TABLE \"s\".\"obj\" ALTER COLUMN label SET DEFAULT $value$WITH, OPTIONS ( 'quoted' );$value$"),
				alter(a, b));
	}

	@Test
	void twoPhaseAlterHonorsThePostgres18Boundary() {
		var a = object(Kind.SUBSCRIPTION,
				"CREATE SUBSCRIPTION obj CONNECTION __SQLAPP_SUBSCRIPTION_CONNECTION__ PUBLICATION p WITH (two_phase=false)");
		var b = object(Kind.SUBSCRIPTION,
				"CREATE SUBSCRIPTION obj CONNECTION __SQLAPP_SUBSCRIPTION_CONNECTION__ PUBLICATION p WITH (two_phase=true)");
		assertEquals(List.of("ALTER SUBSCRIPTION \"obj\" SET (two_phase = true)"), alter(a, b));
		var original = new Catalog("db");
		var target = original.clone();
		PostgresSchemaObjects.set(original, List.of(a));
		PostgresSchemaObjects.set(target, List.of(b));
		var factory = DialectHolder.postgreSQL170.createSqlFactoryRegistry().getSqlFactory(original, SqlType.ALTER);
		assertTrue(assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(target)))
				.getMessage().contains("18"));
	}
}
