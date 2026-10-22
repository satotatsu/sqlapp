/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

/**
 * Execute generated DDL against a fresh H2 database, then compare native
 * catalogs.
 */
public class H2DialectRoundTripTest {
	static String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	static Schema read(Connection connection, String name) throws SQLException {
		var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader().getSchemaReader();
		reader.setSchemaName(name);
		return reader.getAllFull(connection).get(0);
	}

	static void create(Connection connection, Object object) throws Exception {
		var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		registry.getTableOptions().setWithColumnRemarks(true);
		try (var statement = connection.createStatement()) {
			for (var operation : registry.createSql((com.sqlapp.data.schemas.DbCommonObject<?>) object, SqlType.CREATE))
				statement.execute(operation.getSqlText());
		}
	}

	static String nativeType(Connection connection, String schema) throws SQLException {
		try (var statement = connection.prepareStatement(
				"SELECT DATA_TYPE_SQL(TABLE_SCHEMA,TABLE_NAME,'TABLE',DTD_IDENTIFIER) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME='T' AND COLUMN_NAME='C'")) {
			statement.setString(1, schema);
			try (var rows = statement.executeQuery()) {
				assertTrue(rows.next());
				return rows.getString(1);
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "DECIMAL(18,4)", "NUMERIC(28,9)", "VARCHAR(42)", "CHAR(7)", "VARBINARY(17)", "BINARY(5)",
			"TIMESTAMP(3)", "TIMESTAMP(2) WITH TIME ZONE", "TIME(4)", "TIME(2) WITH TIME ZONE", "INTEGER ARRAY[5]",
			"VARCHAR(9) ARRAY[3] ARRAY[4]", "ENUM('x','it''s','日本語')", "INTERVAL DAY(3) TO SECOND(4)",
			"INTERVAL YEAR(5) TO MONTH", "ROW(X INTEGER,Y VARCHAR(8))", "JSON", "UUID", "BOOLEAN", "DECFLOAT(23)",
			"CLOB", "BLOB" })
	void preservesNativeColumnTypes(String type) throws Exception {
		try (var source = DriverManager.getConnection("jdbc:h2:mem:");
				var target = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = source.createStatement();
				var destination = target.createStatement()) {
			String schema = "odd\" schema";
			statement.execute("CREATE SCHEMA " + quote(schema));
			destination.execute("CREATE SCHEMA " + quote(schema));
			statement.execute("CREATE TABLE " + quote(schema) + ".T(C " + type + ")");
			var model = read(source, schema).getTables().get("T");
			String xml = model.asXml();
			create(target, model);
			assertEquals(nativeType(source, schema), nativeType(target, schema));
			assertEquals(xml, model.asXml());
		}
	}

	@ParameterizedTest
	@CsvSource({ "PUBLIC,ALWAYS", "PUBLIC,BY DEFAULT", "odd\" schema,ALWAYS", "odd\" schema,BY DEFAULT" })
	void preservesIdentityStateGeneratedColumnsDomainsAndUpdateDefaults(String schema, String generation)
			throws Exception {
		try (var source = DriverManager.getConnection("jdbc:h2:mem:");
				var target = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = source.createStatement();
				var destination = target.createStatement()) {
			if (!schema.equals("PUBLIC")) {
				statement.execute("CREATE SCHEMA " + quote(schema));
				destination.execute("CREATE SCHEMA " + quote(schema));
			}
			String prefix = quote(schema) + ".";
			statement.execute(
					"CREATE DOMAIN " + prefix + "\"positive amount\" AS DECIMAL(18,4) DEFAULT 1 CHECK(VALUE>=0)");
			statement.execute("CREATE TABLE " + prefix + "T(ID BIGINT GENERATED " + generation
					+ " AS IDENTITY(START WITH 100 INCREMENT BY 10 CACHE 4) PRIMARY KEY,C " + prefix
					+ "\"positive amount\",G BIGINT GENERATED ALWAYS AS(ID*2),U VARCHAR(40) DEFAULT 'initial' ON UPDATE 'changed',SECRET INT INVISIBLE DEFAULT 7,N INT DEFAULT 9 DEFAULT ON NULL)");
			statement.execute("COMMENT ON COLUMN " + prefix + "T.C IS '日本語 '' amount'");
			statement.execute("INSERT INTO " + prefix + "T(C) VALUES(2)");
			var model = read(source, schema);
			create(target, model.getDomains().get("positive amount"));
			create(target, model.getTables().get("T"));
			destination.execute("INSERT INTO " + prefix + "T(C,N) VALUES(3,NULL)");
			try (var rows = destination.executeQuery("SELECT ID,C,G,U,SECRET,N FROM " + prefix + "T")) {
				assertTrue(rows.next());
				assertEquals(110, rows.getLong(1));
				assertEquals("3.0000", rows.getString(2));
				assertEquals(220, rows.getLong(3));
				assertEquals("initial", rows.getString(4));
				assertEquals(7, rows.getInt(5));
				assertEquals(9, rows.getInt(6));
			}
			destination.execute("UPDATE " + prefix + "T SET C=4");
			try (var rows = destination.executeQuery("SELECT U FROM " + prefix + "T")) {
				assertTrue(rows.next());
				assertEquals("changed", rows.getString(1));
			}
			assertThrows(SQLException.class, () -> destination.execute("INSERT INTO " + prefix + "T(C) VALUES(-1)"));
			var reread = read(target, schema).getTables().get("T");
			assertTrue(reread.getColumns().get("SECRET").isHidden());
			assertEquals("日本語 ' amount", reread.getColumns().get("C").getRemarks());
			assertEquals(model.getTables().get("T").getColumns().get("ID").getIdentityGenerationType(),
					reread.getColumns().get("ID").getIdentityGenerationType());
		}
	}

	public static void noop() {
	}

	public static int overloaded(int value) {
		return value;
	}

	public static int overloaded(int first, int second) {
		return first + second;
	}

	public static class CountingTrigger implements org.h2.api.Trigger {
		@Override
		public void fire(Connection connection, Object[] oldRow, Object[] newRow) {
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "PUBLIC", "odd\" schema" })
	void recreatesViewsAliasesConstantsAndMultiEventTriggers(String schema) throws Exception {
		try (var source = DriverManager.getConnection("jdbc:h2:mem:");
				var target = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = source.createStatement();
				var destination = target.createStatement()) {
			if (!schema.equals("PUBLIC")) {
				statement.execute("CREATE SCHEMA " + quote(schema));
				destination.execute("CREATE SCHEMA " + quote(schema));
			}
			String prefix = quote(schema) + ".";
			statement.execute("CREATE TABLE " + prefix + "T(ID INT PRIMARY KEY,C VARCHAR(30))");
			statement.execute("CREATE VIEW " + prefix + "\"view name\"(\"result column\") AS\n SELECT C FROM " + prefix
					+ "T WHERE ID>0");
			statement.execute("CREATE ALIAS " + prefix + "HEX DETERMINISTIC FOR 'java.lang.Integer.toHexString'");
			statement.execute("CREATE ALIAS " + prefix + "TWICE DETERMINISTIC AS 'int twice(int x) { return x*2; }'");
			statement.execute(
					"CREATE ALIAS " + prefix + "NOOP FOR '" + H2DialectRoundTripTest.class.getName() + ".noop'");
			statement.execute("CREATE CONSTANT " + prefix + "\"constant name\" VALUE '日本語 '' value'");
			statement.execute("CREATE TRIGGER " + prefix + "\"trigger name\" BEFORE INSERT,UPDATE ON " + prefix
					+ "T FOR EACH STATEMENT QUEUE 7 NOWAIT CALL '" + CountingTrigger.class.getName() + "'");
			var model = read(source, schema);
			assertEquals(1, model.getTriggers().size());
			assertEquals(2, model.getTriggers().get("trigger name").getEventManipulation().size());
			create(target, model.getTables().get("T"));
			for (var function : model.getFunctions())
				create(target, function);
			for (var constant : model.getConstants())
				create(target, constant);
			for (var trigger : model.getTriggers())
				create(target, trigger);
			for (var view : model.getViews())
				create(target, view);
			destination.execute("INSERT INTO " + prefix + "T VALUES(1,'kept')");
			destination.execute("UPDATE " + prefix + "T SET C='updated' WHERE ID=1");
			try (var rows = destination.executeQuery("SELECT \"result column\" FROM " + prefix + "\"view name\"")) {
				assertTrue(rows.next());
				assertEquals("updated", rows.getString(1));
			}
			try (var rows = destination.executeQuery(
					"SELECT " + prefix + "HEX(255)," + prefix + "TWICE(4)," + prefix + "\"constant name\"")) {
				assertTrue(rows.next());
				assertEquals("ff", rows.getString(1));
				assertEquals(8, rows.getInt(2));
				assertEquals("日本語 ' value", rows.getString(3));
			}
			destination.execute("CALL " + prefix + "NOOP()");
			var registry = DialectResolver.getInstance().getDialect(target).createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			for (var operation : registry.createSql(model.getFunctions().get("HEX"), SqlType.DROP))
				destination.execute(operation.getSqlText());
			assertThrows(SQLException.class, () -> destination.executeQuery("SELECT " + prefix + "HEX(1)"));
		}
	}
}
