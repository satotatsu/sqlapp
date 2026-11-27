/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;

/** Executable boundaries of the bundled 2.7.4 engine, without generated SQL. */
class HsqlEngineBoundariesTest {
	@Test
	void documentedAlterTypeRenameIsRejectedByTheBundledEngine() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:engine-type", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TYPE ORIGINAL_TYPE AS INTEGER");
			var failure = assertThrows(java.sql.SQLException.class,
					() -> statement.execute("ALTER TYPE ORIGINAL_TYPE RENAME TO NEW_TYPE"));
			assertTrue(failure.getSQLState().startsWith("42"), failure.toString());
		}
	}

	@Test
	void mutuallyDependentViewsAreRejectedByTheBundledEngine() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:engine-view-cycle", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE VIEW V(V) AS VALUES(1)");
			statement.execute("CREATE VIEW W(V) AS SELECT V FROM V");
			var failure = assertThrows(java.sql.SQLException.class,
					() -> statement.execute("ALTER VIEW V AS SELECT V FROM W"));
			assertTrue(failure.getSQLState().startsWith("42"), failure.toString());
			try (var rs = statement.executeQuery("SELECT V FROM V")) {
				assertTrue(rs.next());
				assertEquals(1, rs.getInt(1));
			}
		}
	}

	@Test
	void schemaConstantsAreNotAnHsqlObject() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:engine-constant", "SA", "");
				var statement = connection.createStatement()) {
			var failure = assertThrows(java.sql.SQLException.class,
					() -> statement.execute("CREATE CONSTANT C VALUE 1"));
			assertTrue(failure.getSQLState().startsWith("42"), failure.toString());
		}
	}

	@Test
	void statementTransitionTablesCannotBeQueriedByTheBundledEngine() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:engine-transition", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE T(V INTEGER)");
			statement.execute("CREATE TABLE AUDIT(V INTEGER)");
			for (String action : java.util.List.of("INSERT INTO AUDIT SELECT V FROM N",
					"BEGIN ATOMIC INSERT INTO AUDIT SELECT N.V FROM N; END", "INSERT INTO AUDIT TABLE N")) {
				var failure = assertThrows(java.sql.SQLException.class, () -> statement.execute(
						"CREATE TRIGGER TR AFTER INSERT ON T REFERENCING NEW TABLE AS N FOR EACH STATEMENT " + action));
				assertTrue(failure.getSQLState().startsWith("42"), failure.toString());
			}
		}
	}
}
