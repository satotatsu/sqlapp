/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.migration.logic.PostgresCreateStatement.Kind;
import com.sqlapp.data.db.command.migration.logic.PostgresCreateStatement.Parsed;
import com.sqlapp.data.db.command.migration.logic.PostgresCreateStatement.QualifiedName;

class PostgresCreateStatementTest {
	private static final String FUNCTION = """
			-- audit helper; DROP TABLE x; is only a comment
			CREATE OR REPLACE FUNCTION app.orders_audit_fn()
			RETURNS trigger
			LANGUAGE plpgsql
			AS $body$
			BEGIN
			  /* nested /* comment */ ; */
			  INSERT INTO app.order_log(id, note) VALUES (NEW.id, 'CREATE TRIGGER x; DROP TABLE y;');
			  EXECUTE 'GRANT ALL ON app.orders TO public';
			  RETURN NEW;
			END;
			$body$;
			""";

	@Test
	void identifiesHelperFunctionWithoutReadingTheBody() throws Exception {
		final Parsed parsed = PostgresCreateStatement.parse(FUNCTION);
		assertEquals(Kind.FUNCTION, parsed.kind());
		assertTrue(parsed.orReplace());
		assertEquals(new QualifiedName("app", "orders_audit_fn"), parsed.name());
		assertEquals(0, parsed.argumentCount());
		assertEquals("trigger", parsed.returns());
		assertEquals("plpgsql", parsed.language());
		assertFalse(parsed.securityDefiner());
	}

	@Test
	void identifiesTriggerDeclaration() throws Exception {
		final Parsed parsed = PostgresCreateStatement.parse("""
				CREATE TRIGGER "Orders_Audit"
				AFTER INSERT OR UPDATE OF amount, "Status" OR DELETE ON APP."Orders"
				FOR EACH ROW
				WHEN (NEW.amount IS DISTINCT FROM OLD.amount OR ';' = ';')
				EXECUTE FUNCTION app.orders_audit_fn('x;y')""");
		assertEquals(Kind.TRIGGER, parsed.kind());
		assertFalse(parsed.orReplace());
		assertEquals("Orders_Audit", parsed.name().name());
		assertEquals("AFTER", parsed.timing());
		assertEquals(List.of("INSERT", "UPDATE", "DELETE"), parsed.events());
		assertEquals("ROW", parsed.orientation());
		assertEquals(new QualifiedName("app", "Orders"), parsed.table(), "unquoted names fold to lower case");
		assertEquals(new QualifiedName("app", "orders_audit_fn"), parsed.function());
	}

	@Test
	void defaultsToStatementLevelAndAcceptsExecuteProcedure() throws Exception {
		final Parsed parsed = PostgresCreateStatement
				.parse("create trigger t before truncate on app.orders execute procedure app.f();");
		assertEquals("STATEMENT", parsed.orientation());
		assertEquals(List.of("TRUNCATE"), parsed.events());
		assertEquals(new QualifiedName("app", "f"), parsed.function());
	}

	@Test
	void keepsUnqualifiedNamesVisibleForTheCaller() throws Exception {
		final Parsed parsed = PostgresCreateStatement
				.parse("CREATE TRIGGER t BEFORE INSERT ON orders FOR EACH ROW EXECUTE FUNCTION f()");
		assertNull(parsed.table().schema());
		assertNull(parsed.function().schema());
	}

	private static String rejected(final String sql) {
		return assertThrows(PostgresCreateStatement.Rejected.class, () -> PostgresCreateStatement.parse(sql))
				.getMessage();
	}

	@Test
	void rejectsOtherStatementsAndSeveralStatements() {
		assertTrue(rejected(FUNCTION + "\nDROP TABLE app.orders;").contains("exactly one"));
		assertTrue(rejected("DROP TABLE app.orders").contains("start with CREATE"));
		assertTrue(rejected("ALTER TABLE app.orders ADD COLUMN x int").contains("start with CREATE"));
		assertTrue(rejected("GRANT ALL ON app.orders TO public").contains("start with CREATE"));
		assertTrue(rejected("CREATE TABLE app.x (id int)").contains("Only CREATE FUNCTION and CREATE TRIGGER"));
		assertTrue(rejected("  -- nothing\n ; ").contains("no SQL statement"));
	}

	@Test
	void rejectsUnsupportedFunctionForms() {
		// Arguments are reported to the caller, which rejects them for helper
		// functions.
		assertEquals(1,
				assertDoesNotThrow(() -> PostgresCreateStatement
						.parse("CREATE FUNCTION app.f(a integer) RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN END $$"))
						.argumentCount());
		assertTrue(rejected("CREATE FUNCTION app.f() RETURNS void LANGUAGE plpgsql AS $$ BEGIN END $$")
				.contains("RETURNS trigger"));
		assertTrue(rejected("CREATE FUNCTION app.f() RETURNS SETOF integer LANGUAGE sql AS 'select 1'")
				.contains("Only RETURNS trigger"));
		assertTrue(rejected("CREATE FUNCTION app.f() RETURNS trigger LANGUAGE sql AS 'select 1'")
				.contains("LANGUAGE plpgsql"));
		assertTrue(rejected("CREATE FUNCTION app.f() RETURNS trigger LANGUAGE plpgsql").contains("body"));
		assertTrue(rejected("CREATE FUNCTION app.f() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN END")
				.contains("Unterminated dollar-quoted"));
		assertTrue(rejected("CREATE FUNCTION db.app.f() RETURNS trigger LANGUAGE plpgsql AS $$ $$")
				.contains("database-qualified"));
	}

	@Test
	void rejectsUnsupportedTriggerForms() {
		assertTrue(rejected("CREATE CONSTRAINT TRIGGER t AFTER INSERT ON app.x FOR EACH ROW EXECUTE FUNCTION app.f()")
				.contains("CONSTRAINT"));
		assertTrue(rejected("CREATE TRIGGER app.t AFTER INSERT ON app.x EXECUTE FUNCTION app.f()")
				.contains("cannot be schema-qualified"));
		assertTrue(rejected("CREATE TRIGGER t AFTER INSERT ON app.x FOR EACH ROW").contains("EXECUTE FUNCTION"));
		assertTrue(rejected("CREATE TRIGGER t AFTER SELECT ON app.x EXECUTE FUNCTION app.f()")
				.contains("Unexpected trigger event"));
		assertTrue(rejected("CREATE TRIGGER t AFTER INSERT ON app.x EXECUTE FUNCTION app.f() FOR EACH ROW")
				.contains("Unexpected text"));
	}
}
