/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mysql.metadata.MySqlSchemaSnapshot;
import com.sqlapp.data.db.dialect.mysql.util.MySqlStoredObjectContext;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Trigger;

/**
 * Executes exported native DDL and modeled stored objects in disposable
 * databases.
 */
public final class MySqlRecreationAssertions {
	private MySqlRecreationAssertions() {
	}

	public static void verify(Connection connection) throws SQLException {
		String p = "recreate_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String table = p + "_data", view = p + "_view", first = p + "_z", second = p + "_a";
		String function = p + "_function", event = p + "_event";
		String mode = scalar(connection, "SELECT @@SESSION.sql_mode");
		String zone = scalar(connection, "SELECT @@SESSION.time_zone");
		String quoted = scalar(connection, "SELECT @@SESSION.sql_quote_show_create");
		String catalog = connection.getCatalog();
		var dialect = DialectResolver.getInstance().getDialect(connection);
		boolean mysql = "MySQL".equalsIgnoreCase(dialect.getProductName());
		try (Statement statement = connection.createStatement()) {
			try {
				statement.execute(
						"CREATE TABLE " + table + " (id INT PRIMARY KEY, code VARCHAR(60), FULLTEXT INDEX ft_code(code)"
								+ (mysql ? " WITH PARSER ngram" : "")
								+ ") ENGINE=InnoDB ROW_FORMAT=DYNAMIC COMMENT='snapshot table'");
				statement.execute("CREATE ALGORITHM=TEMPTABLE VIEW " + view + " AS SELECT code FROM " + table);
				statement.execute("CREATE TRIGGER " + first + " BEFORE INSERT ON " + table
						+ " FOR EACH ROW SET NEW.code=CONCAT('first:',NEW.code)");
				statement.execute("CREATE TRIGGER " + second + " BEFORE INSERT ON " + table
						+ " FOR EACH ROW SET NEW.code=CONCAT(NEW.code,':second')");
				set(connection, "sql_mode", "NO_BACKSLASH_ESCAPES");
				statement.execute("CREATE FUNCTION " + function + "() RETURNS VARCHAR(30) DETERMINISTIC RETURN 'a\\b'");
				set(connection, "sql_mode", mode);
				set(connection, "time_zone", "+09:00");
				statement.execute("CREATE EVENT " + event + " ON SCHEDULE AT '2035-01-01 09:00:00'"
						+ " ON COMPLETION PRESERVE DISABLE DO SET @snapshot_event=1");
				set(connection, "time_zone", zone);

				AtomicInteger shows = new AtomicInteger();
				Connection counted = CountingJdbc.wrap(connection,
						sql -> sql.startsWith("SHOW CREATE TABLE ") || sql.startsWith("SHOW CREATE VIEW "), shows);
				set(connection, "sql_mode", "ANSI_QUOTES");
				set(connection, "sql_quote_show_create", "0");
				var snapshot = MySqlSchemaSnapshot.read(counted, connection.getCatalog());
				assertEquals("0", scalar(connection, "SELECT @@SESSION.sql_quote_show_create"));
				set(connection, "sql_mode", mode);
				set(connection, "sql_quote_show_create", quoted);
				assertEquals(snapshot.getTables().size() + snapshot.getViews().size(), shows.get());
				var savedTable = snapshot.getTables().get(table);
				var savedView = snapshot.getViews().get(view);
				assertEquals("ANSI_QUOTES", savedTable.getSpecifics().get(MySqlStoredObjectContext.SQL_MODE));
				assertTrue(String.join("\n", savedView.getDefinition()).contains("ALGORITHM=TEMPTABLE"));
				if (mysql)
					assertTrue(String.join("\n", savedTable.getDefinition()).contains("PARSER"));
				assertEquals("NO_BACKSLASH_ESCAPES",
						snapshot.getFunctions().get(function).getSpecifics().get(MySqlStoredObjectContext.SQL_MODE));
				assertEquals("+09:00",
						snapshot.getEvents().get(event).getSpecifics().get(MySqlStoredObjectContext.TIME_ZONE));

				statement.execute("DROP VIEW " + view);
				statement.execute("DROP TRIGGER " + first);
				statement.execute("DROP TRIGGER " + second);
				statement.execute("DROP TABLE " + table);
				MySqlStoredObjectContext.create(connection, savedTable);
				MySqlStoredObjectContext.create(connection, savedView);
				assertEquals("TEMPTABLE",
						scalar(connection, "SHOW CREATE VIEW " + view, 2).contains("ALGORITHM=TEMPTABLE") ? "TEMPTABLE"
								: "missing");
				assertEquals("Dynamic", scalar(connection,
						"SELECT ROW_FORMAT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"
								+ table + "'"));
				SqlFactory<Trigger> factory = dialect.createSqlFactoryRegistry()
						.getSqlFactory(snapshot.getTriggers().get(first), SqlType.CREATE);
				var triggers = factory
						.createSql(List.of(snapshot.getTriggers().get(second), snapshot.getTriggers().get(first)));
				assertTrue(triggers.get(0).getSqlText().contains(first));
				// Detached triggers may identify their database only through the target table.
				Trigger detachedFirst = new Trigger(first);
				detachedFirst.setTableSchemaName(catalog);
				detachedFirst.setTableName(table);
				detachedFirst.setActionTiming("before");
				detachedFirst.addEventManipulation("insert");
				detachedFirst.setStatement("SET NEW.code=CONCAT('first:',NEW.code)");
				connection.setCatalog("information_schema");
				MySqlStoredObjectContext.create(connection, detachedFirst);
				assertEquals("information_schema", connection.getCatalog());
				Trigger invalidDetached = detachedFirst.clone();
				invalidDetached.setDefinition("CREATE TRIGGER deliberately invalid SQL");
				assertThrows(SQLException.class, () -> MySqlStoredObjectContext.create(connection, invalidDetached));
				assertEquals("information_schema", connection.getCatalog());
				connection.setCatalog(catalog);
				statement.execute(triggers.get(1).getSqlText());
				statement.execute("INSERT INTO " + table + " VALUES(1,'x')");
				assertEquals("first:x:second", scalar(connection, "SELECT code FROM " + table));

				statement.execute("DROP FUNCTION " + function);
				connection.setCatalog("information_schema");
				MySqlStoredObjectContext.create(connection, snapshot.getFunctions().get(function));
				assertEquals("information_schema", connection.getCatalog());
				connection.setCatalog(catalog);
				assertEquals("a\\b", scalar(connection, "SELECT " + function + "()"));
				assertEquals(mode, scalar(connection, "SELECT @@SESSION.sql_mode"));
				statement.execute("DROP EVENT " + event);
				MySqlStoredObjectContext.create(connection, snapshot.getEvents().get(event));
				assertEquals("+09:00", scalar(connection,
						"SELECT TIME_ZONE FROM information_schema.EVENTS WHERE EVENT_SCHEMA=DATABASE() AND EVENT_NAME='"
								+ event + "'"));
				assertEquals("2035-01-01 09:00:00", scalar(connection,
						"SELECT EXECUTE_AT FROM information_schema.EVENTS WHERE EVENT_SCHEMA=DATABASE() AND EVENT_NAME='"
								+ event + "'"));
				assertEquals(zone, scalar(connection, "SELECT @@SESSION.time_zone"));

				var broken = snapshot.getFunctions().get(function).clone();
				broken.setDefinition("CREATE FUNCTION deliberately invalid SQL");
				connection.setCatalog("information_schema");
				assertThrows(SQLException.class, () -> MySqlStoredObjectContext.create(connection, broken));
				assertEquals("information_schema", connection.getCatalog());
				connection.setCatalog(catalog);
				assertEquals(mode, scalar(connection, "SELECT @@SESSION.sql_mode"));
				broken.getSpecifics().put(MySqlStoredObjectContext.TIME_ZONE, "Not/A/Real/Zone");
				assertThrows(SQLException.class, () -> MySqlStoredObjectContext.create(connection, broken));
				assertEquals(mode, scalar(connection, "SELECT @@SESSION.sql_mode"));
				assertEquals(zone, scalar(connection, "SELECT @@SESSION.time_zone"));
			} finally {
				connection.setCatalog(catalog);
				set(connection, "sql_mode", mode);
				set(connection, "time_zone", zone);
				set(connection, "sql_quote_show_create", quoted);
				statement.execute("DROP EVENT IF EXISTS " + event);
				statement.execute("DROP FUNCTION IF EXISTS " + function);
				statement.execute("DROP TRIGGER IF EXISTS " + first);
				statement.execute("DROP TRIGGER IF EXISTS " + second);
				statement.execute("DROP VIEW IF EXISTS " + view);
				statement.execute("DROP TABLE IF EXISTS " + table);
			}
		}
	}

	private static void set(Connection connection, String name, String value) throws SQLException {
		try (var statement = connection.prepareStatement("SET SESSION " + name + "=?")) {
			if ("sql_quote_show_create".equals(name))
				statement.setInt(1, Integer.parseInt(value));
			else
				statement.setString(1, value);
			statement.execute();
		}
	}

	private static String scalar(Connection connection, String sql) throws SQLException {
		return scalar(connection, sql, 1);
	}

	private static String scalar(Connection connection, String sql, int column) throws SQLException {
		try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
			assertTrue(rows.next(), sql);
			return rows.getString(column);
		}
	}
}
