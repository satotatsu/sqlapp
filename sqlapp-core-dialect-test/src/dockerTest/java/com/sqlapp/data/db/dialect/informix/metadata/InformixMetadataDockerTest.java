/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;

import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.DoubleKeyMap;

/**
 * Creates and drops only its own randomly named tables in an explicitly
 * selected test DB.
 */
class InformixMetadataDockerTest {
	private static final GenericContainer<?> INFORMIX = ReusableTestcontainers
			.configure(new GenericContainer<>(DockerImageName.parse(System.getProperty("sqlapp.test.informix.image",
					"icr.io/informix/informix-developer-database:14.10.FC9W1DE"))).withEnv("LICENSE", "accept")
					.withEnv("STORAGE", "local").withEnv("SIZE", "small").withExposedPorts(9088)
					.waitingFor(Wait
							.forSuccessfulCommand("sh -c 'INFORMIXDIR=/opt/ibm/informix INFORMIXSERVER=informix "
									+ "LD_LIBRARY_PATH=/opt/ibm/informix/lib:/opt/ibm/informix/lib/esql "
									+ "/opt/ibm/informix/bin/dbaccess sysmaster - </dev/null'")
							.withStartupTimeout(Duration.ofMinutes(3))));

	@BeforeAll
	static void start() {
		ReusableTestcontainers.start(INFORMIX);
	}

	@AfterAll
	static void stop() {
		ReusableTestcontainers.stop(INFORMIX);
	}

	@Test
	void recreatesDisabledConstraintsAndNamedNotNullThroughXml() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix"); var statement = connection.createStatement()) {
			final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
			try {
				statement.execute("CREATE TABLE " + prefix + "(id INTEGER PRIMARY KEY CONSTRAINT " + prefix
						+ "_pk DISABLED," + "n INTEGER DEFAULT 42 NOT NULL CONSTRAINT " + prefix + "_nn DISABLED,"
						+ "val INTEGER UNIQUE CONSTRAINT " + prefix + "_uq DISABLED,CHECK(val>0) CONSTRAINT " + prefix
						+ "_ck DISABLED)");
				statement.execute("CREATE TABLE " + prefix + "_child(id INTEGER,FOREIGN KEY(id) REFERENCES " + prefix
						+ "(id) CONSTRAINT " + prefix + "_fk DISABLED)");
				final var context = new ParametersContext();
				context.put("schemaName", "informix");
				context.put("tableName", prefix + "%");
				final var columns = new InformixColumnReader(DialectHolder.defaultDialect);
				columns.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				final Table parent = new Table(prefix).setSchemaName("informix");
				final Table child = new Table(prefix + "_child").setSchemaName("informix");
				for (var column : columns.doGetAll(connection, context, null))
					(column.getTableName().equals(prefix) ? parent : child).getColumns().add(column);
				context.put("tableName", List.of(prefix, prefix + "_child"));
				assertFalse(parent.getColumns().get("id").isNotNull());
				assertFalse(parent.getColumns().get("n").isNotNull());
				parent.setPrimaryKey(prefix + "_pk", parent.getColumns().get("id"));
				child.getConstraints().addForeignKeyConstraint(prefix + "_fk", child.getColumns().get("id"),
						parent.getColumns().get("id"));
				final var checks = new InformixCheckConstraintReader(DialectHolder.defaultDialect);
				checks.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				parent.getConstraints().addAll(checks.doGetAll(connection, context, null));
				final var tables = SchemaUtils.toDoubleKeyMap(List.of(parent, child));
				final var reader = new InformixTableReader(DialectHolder.defaultDialect);
				reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				reader.setUniqueConstraints(connection, tables);
				final AtomicInteger queries = new AtomicInteger();
				final Connection counted = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
						new Class<?>[] { Connection.class }, (proxy, method, args) -> {
							if (method.getName().equals("prepareStatement"))
								queries.incrementAndGet();
							try {
								return method.invoke(connection, args);
							} catch (java.lang.reflect.InvocationTargetException e) {
								throw e.getCause();
							}
						});
				reader.setConstraintOwners(counted, tables);
				assertEquals(1, queries.get());
				assertEquals(1, parent.getConstraints().getNotNullConstraints().size());
				assertFalse(parent.getColumns().get("id").isNotNull());
				assertFalse(parent.getColumns().get("n").isNotNull());
				final var schema = new com.sqlapp.data.schemas.Schema("informix");
				schema.getTables().add(parent);
				schema.getTables().add(child);
				final com.sqlapp.data.schemas.Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
				statement.execute("DROP TABLE " + child.getName());
				statement.execute("DROP TABLE " + parent.getName());
				for (var table : restored.getTables()) {
					for (var constraint : table.getConstraints())
						assertFalse(constraint.isEnable(), constraint.getName());
					for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table,
							SqlType.CREATE))
						statement.execute(operation.getSqlText());
				}
				statement.execute("INSERT INTO " + prefix + " VALUES(NULL,NULL,-1)");
				statement.execute("INSERT INTO " + prefix + " VALUES(NULL,NULL,-1)");
				statement.execute("INSERT INTO " + prefix + "_child VALUES(999)");
				try (var rows = statement
						.executeQuery("SELECT COUNT(*) FROM sysobjstate WHERE objtype='C' AND state='D' AND name LIKE '"
								+ prefix + "_%'")) {
					assertTrue(rows.next());
					assertEquals(5, rows.getInt(1));
				}
				statement.execute("ALTER TABLE " + prefix + " DROP CONSTRAINT " + prefix + "_nn");
				for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(
						restored.getTables().get(prefix).getConstraints().get(prefix + "_nn"), SqlType.CREATE))
					statement.execute(operation.getSqlText());
				statement.execute("INSERT INTO " + prefix + "(id,val) VALUES(123,-1)");
				try (var rows = statement.executeQuery("SELECT n FROM " + prefix + " WHERE id=123")) {
					assertTrue(rows.next());
					assertEquals(42, rows.getInt(1));
				}
			} finally {
				statement.execute("DROP TABLE IF EXISTS " + prefix + "_child");
				statement.execute("DROP TABLE IF EXISTS " + prefix);
			}
		}
	}

	@Test
	void recreatesFilteringCheckModesThroughXml() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix"); var statement = connection.createStatement()) {
			for (String mode : List.of("F", "G")) {
				final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
				try {
					statement.execute("CREATE TABLE " + prefix + "(id INTEGER,CHECK(id>0) CONSTRAINT " + prefix + "_ck "
							+ com.sqlapp.data.db.dialect.informix.util.InformixConstraintModes.decode(mode) + ")");
					final Table table = new Table(prefix).setSchemaName("informix");
					table.getColumns().add("id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
					final var context = new ParametersContext();
					context.put("schemaName", "informix");
					context.put("tableName", prefix);
					final var reader = new InformixCheckConstraintReader(DialectHolder.defaultDialect);
					reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
					table.getConstraints().addAll(reader.doGetAll(connection, context, null));
					final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
					statement.execute("DROP TABLE " + prefix);
					for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored,
							SqlType.CREATE))
						statement.execute(operation.getSqlText());
					statement.execute("START VIOLATIONS TABLE FOR " + prefix);
					if (mode.equals("G"))
						assertThrows(java.sql.SQLException.class,
								() -> statement.execute("INSERT INTO " + prefix + " VALUES(-1)"));
					else
						statement.execute("INSERT INTO " + prefix + " VALUES(-1)");
					try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + prefix)) {
						assertTrue(rows.next());
						assertEquals(0, rows.getInt(1));
					}
					try (var rows = statement.executeQuery(
							"SELECT state FROM sysobjstate WHERE name='" + prefix + "_ck' AND objtype='C'")) {
						assertTrue(rows.next());
						assertEquals(mode, rows.getString(1).strip());
					}
				} finally {
					statement.execute("DROP TABLE IF EXISTS " + prefix);
					statement.execute("DROP TABLE IF EXISTS " + prefix + "_vio");
					statement.execute("DROP TABLE IF EXISTS " + prefix + "_dia");
				}
			}
		}
	}

	@Test
	void recreatesCheckExpressionsAndExactDefaultsIncludingKeywordsAndEmptyStrings() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix"); var statement = connection.createStatement()) {
			final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
			final String literal = "a ".repeat(40) + "quoted's ending ";
			try {
				statement.execute("CREATE TABLE " + prefix
						+ "(id INTEGER DEFAULT 42,txt VARCHAR(100) DEFAULT 'it''s  spaced ',"
						+ "emptytxt VARCHAR(100) DEFAULT '',literal_user VARCHAR(32) DEFAULT 'USER',who VARCHAR(32) DEFAULT USER,"
						+ "server_name VARCHAR(128) DEFAULT DBSERVERNAME,flag BOOLEAN DEFAULT 't',day_value DATE DEFAULT TODAY,"
						+ "ts DATETIME YEAR TO FRACTION(3) DEFAULT CURRENT YEAR TO FRACTION(3),ld DATE DEFAULT '2026-10-29',"
						+ "lts DATETIME HOUR TO MINUTE DEFAULT DATETIME(12:34) HOUR TO MINUTE,"
						+ "li INTERVAL DAY(3) TO FRACTION(3) DEFAULT INTERVAL(123 01:02:03.123) DAY(3) TO FRACTION(3),"
						+ "number_value DECIMAL(9,2) DEFAULT -123.45,number_float DECIMAL(9) DEFAULT 1.2345e10,"
						+ "nulltxt VARCHAR(10) DEFAULT NULL,no_default VARCHAR(10),marker VARCHAR(200) DEFAULT 'allowed',"
						+ "CHECK(marker IN ('allowed','" + literal.replace("'", "''") + "') AND id>0) CONSTRAINT "
						+ prefix + "_ck)");
				statement.execute("INSERT INTO " + prefix + "(marker) VALUES('allowed')");
				final String projection = "id,txt,emptytxt,literal_user,who,server_name,flag,day_value,ld,lts,li,number_value,number_float,nulltxt,no_default";
				final var original = new java.util.ArrayList<String>();
				try (var rows = statement.executeQuery("SELECT " + projection + " FROM " + prefix)) {
					assertTrue(rows.next());
					for (int i = 1; i <= 15; i++)
						original.add(rows.getString(i));
				}
				final var columns = new InformixColumnReader(DialectHolder.defaultDialect);
				columns.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				final var context = new ParametersContext();
				context.put("schemaName", "informix");
				context.put("tableName", prefix);
				final Table table = new Table(prefix).setSchemaName("informix");
				final AtomicInteger queries = new AtomicInteger();
				final Connection counted = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
						new Class<?>[] { Connection.class }, (proxy, method, args) -> {
							if (method.getName().equals("prepareStatement"))
								queries.incrementAndGet();
							try {
								return method.invoke(connection, args);
							} catch (java.lang.reflect.InvocationTargetException e) {
								throw e.getCause();
							}
						});
				table.getColumns().addAll(columns.doGetAll(counted, context, null));
				assertEquals(1, queries.get()); // One supplementary catalog query, beyond the JDBC metadata call.
				assertEquals("CURRENT YEAR TO FRACTION(3)", table.getColumns().get("ts").getDefaultValue());
				assertEquals("NULL", table.getColumns().get("nulltxt").getDefaultValue());
				assertNull(table.getColumns().get("no_default").getDefaultValue());
				final var checks = new InformixCheckConstraintReader(DialectHolder.defaultDialect);
				checks.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				table.getConstraints().addAll(checks.doGetAll(connection, context, null));
				assertEquals(1, table.getConstraints().getCheckConstraints().size());
				final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
				restored.setName(prefix + "_copy");
				statement.execute("DROP TABLE " + prefix);
				for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored,
						SqlType.CREATE))
					statement.execute(operation.getSqlText());
				statement.execute("INSERT INTO " + prefix + "_copy(marker) VALUES('allowed')");
				try (var rows = statement.executeQuery("SELECT " + projection + ",ts FROM " + prefix + "_copy")) {
					assertTrue(rows.next());
					for (int i = 1; i <= 15; i++)
						assertEquals(original.get(i - 1), rows.getString(i), "column " + i);
					assertNotNull(rows.getTimestamp(16));
				}
				statement.execute(
						"INSERT INTO " + prefix + "_copy(marker) VALUES('" + literal.replace("'", "''") + "')");
				assertThrows(java.sql.SQLException.class,
						() -> statement.execute("INSERT INTO " + prefix + "_copy(marker) VALUES('blocked')"));
			} finally {
				statement.execute("DROP TABLE IF EXISTS " + prefix + "_copy");
				statement.execute("DROP TABLE IF EXISTS " + prefix);
			}
		}
	}

	@Test
	void recreatesNativeColumnTypesAndPrecisionsFromJdbcThroughXml() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix"); var statement = connection.createStatement()) {
			final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
			try {
				statement.execute("CREATE TABLE " + prefix
						+ "(id BIGSERIAL,n BIGINT,n8 INT8,dt DATETIME YEAR TO FRACTION(3),"
						+ "tm DATETIME HOUR TO MINUTE,part DATETIME MONTH TO DAY,iv INTERVAL DAY(3) TO FRACTION(3),"
						+ "iy INTERVAL YEAR(4) TO MONTH,frac INTERVAL FRACTION TO FRACTION(5),seconds INTERVAL SECOND(9) TO FRACTION(1),"
						+ "ds DECIMAL(9),fixed DECIMAL(9,2),vc VARCHAR(100),lv LVARCHAR(4000),flag BOOLEAN)");
				statement.execute("CREATE TABLE " + prefix + "_s8(id SERIAL8)");
				statement.execute("CREATE TABLE " + prefix + "_s(id SERIAL)");
				for (String name : List.of(prefix, prefix + "_s8", prefix + "_s")) {
					final var table = new Table(name).setSchemaName("informix");
					final var reader = new InformixColumnReader(DialectHolder.defaultDialect);
					try (var rows = new com.sqlapp.jdbc.ExResultSet(
							connection.getMetaData().getColumns(null, "informix", name, null))) {
						while (rows.next())
							table.getColumns().add(reader.createColumn(rows));
					}
					final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
					restored.setName(name + "_copy");
					for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored,
							SqlType.CREATE))
						statement.execute(operation.getSqlText());
					final var catalog = new java.util.HashMap<String, java.util.List<String>>();
					try (var rows = statement.executeQuery(
							"SELECT t.tabname,c.colname,c.coltype,c.collength FROM systables t JOIN syscolumns c ON c.tabid=t.tabid "
									+ "WHERE t.tabname IN ('" + name + "','" + name
									+ "_copy') ORDER BY t.tabname,c.colno")) {
						while (rows.next())
							catalog.computeIfAbsent(rows.getString(1).stripTrailing(),
									key -> new java.util.ArrayList<>())
									.add(rows.getString(2).stripTrailing() + ":" + rows.getInt(3) + ":"
											+ rows.getInt(4));
					}
					assertNotNull(catalog.get(name));
					assertEquals(catalog.get(name), catalog.get(name + "_copy"), name);
				}
				statement.execute("INSERT INTO " + prefix + "_copy(id,ds,dt,iv) VALUES(0,123456789000000,"
						+ "DATETIME(2026-10-29 12:34:56.123) YEAR TO FRACTION(3),INTERVAL(123 01:02:03.123) DAY(3) TO FRACTION(3))");
				try (var rows = statement.executeQuery("SELECT id,ds,dt,iv FROM " + prefix + "_copy")) {
					assertTrue(rows.next());
					assertTrue(rows.getLong(1) > 0);
					assertEquals(0, rows.getBigDecimal(2).compareTo(new java.math.BigDecimal("123456789000000")));
					assertEquals(123000000, rows.getTimestamp(3).getNanos());
					assertTrue(rows.getString(4).contains("123 01:02:03.123"));
				}
			} finally {
				for (String name : List.of(prefix, prefix + "_s8", prefix + "_s")) {
					statement.execute("DROP TABLE IF EXISTS " + name + "_copy");
					statement.execute("DROP TABLE IF EXISTS " + name);
				}
			}
		}
	}

	@Test
	void recreatesOrderedAlterRoutineHistoryIncludingLongOverloadedSignatures() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix"); var statement = connection.createStatement()) {
			final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
			final String arguments = java.util.stream.IntStream.rangeClosed(1, 40).mapToObj(i -> "a" + i + " INTEGER")
					.collect(java.util.stream.Collectors.joining(","));
			final String types = java.util.stream.IntStream.rangeClosed(1, 40).mapToObj(i -> "INTEGER")
					.collect(java.util.stream.Collectors.joining(","));
			try {
				statement.execute("CREATE FUNCTION " + prefix + "_f(" + arguments
						+ ") RETURNING INTEGER; RETURN a1; END FUNCTION;");
				statement.execute("CREATE FUNCTION " + prefix
						+ "_f(p LVARCHAR) RETURNING LVARCHAR(2000); RETURN p; END FUNCTION;");
				statement.execute("CREATE PROCEDURE " + prefix + "_p(p INTEGER); RETURN; END PROCEDURE;");
				for (String target : List.of("FUNCTION " + prefix + "_f(" + types + ")",
						"PROCEDURE " + prefix + "_p(INTEGER)")) {
					statement.execute("ALTER " + target + " WITH (ADD NOT VARIANT)");
					statement.execute("ALTER " + target + " WITH (MODIFY VARIANT) /* previous value */");
					statement.execute("ALTER " + target + " WITH (MODIFY NOT VARIANT)");
				}
				final var functions = new InformixFunctionReader(DialectHolder.defaultDialect);
				functions.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				final var functionContext = new ParametersContext();
				functionContext.put("functionName", prefix + "_f");
				final var procedures = new InformixProcedureReader(DialectHolder.defaultDialect);
				procedures.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				final var procedureContext = new ParametersContext();
				procedureContext.put("procedureName", prefix + "_p");
				final var schema = new com.sqlapp.data.schemas.Schema("informix");
				schema.getFunctions().addAll(functions.doGetAll(connection, functionContext, null));
				schema.getProcedures().addAll(procedures.doGetAll(connection, procedureContext, null));
				final com.sqlapp.data.schemas.Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
				assertEquals(2, restored.getFunctions().size());
				assertEquals(1, restored.getProcedures().size());
				statement.execute("DROP FUNCTION " + prefix + "_f(" + types + ")");
				statement.execute("DROP FUNCTION " + prefix + "_f(LVARCHAR)");
				statement.execute("DROP PROCEDURE " + prefix + "_p");
				final var objects = new java.util.ArrayList<com.sqlapp.data.schemas.ArgumentRoutine<?>>();
				objects.addAll(restored.getFunctions());
				objects.addAll(restored.getProcedures());
				for (var object : objects) {
					final var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(object,
							SqlType.CREATE);
					assertEquals(
							object.getSpecifics()
									.containsKey(com.sqlapp.data.db.dialect.informix.Informix.ROUTINE_ALTER) ? 4 : 1,
							operations.size());
					for (var operation : operations)
						statement.execute(operation.getSqlText());
				}
				try (var rows = statement
						.executeQuery("SELECT procname,numargs,variant FROM sysprocedures WHERE procname IN ('" + prefix
								+ "_f','" + prefix + "_p')")) {
					int count = 0;
					while (rows.next()) {
						boolean unaltered = rows.getString(1).endsWith("_f") && rows.getInt(2) == 1;
						assertEquals(unaltered, rows.getBoolean(3));
						count++;
					}
					assertEquals(3, count);
				}
				final String values = java.util.stream.IntStream.rangeClosed(1, 40).mapToObj(Integer::toString)
						.collect(java.util.stream.Collectors.joining(","));
				try (var rows = statement.executeQuery("EXECUTE FUNCTION " + prefix + "_f(" + values + ")")) {
					assertTrue(rows.next());
					assertEquals(1, rows.getInt(1));
				}
				statement.execute("EXECUTE PROCEDURE " + prefix + "_p(1)");
			} finally {
				statement.execute("DROP FUNCTION IF EXISTS " + prefix + "_f(" + types + ")");
				statement.execute("DROP FUNCTION IF EXISTS " + prefix + "_f(LVARCHAR)");
				statement.execute("DROP PROCEDURE IF EXISTS " + prefix + "_p");
			}
		}
	}

	@Test
	void recreatesViewsAndOverloadedRoutinesWithLongSqlThroughXml() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix"); var statement = connection.createStatement()) {
			final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
			final String literal = "words and spaces ".repeat(40);
			try {
				statement.execute("CREATE TABLE " + prefix + "(id INTEGER)");
				statement.execute("CREATE TABLE " + prefix + "_audit(note LVARCHAR(2000))");
				statement.execute("CREATE VIEW " + prefix + "_view(id,label) AS SELECT id,CAST('" + literal
						+ "' AS LVARCHAR(2000)) FROM " + prefix + " WHERE id>0 WITH CHECK OPTION");
				statement.execute("CREATE FUNCTION " + prefix + "_f(p INTEGER) RETURNING LVARCHAR(2000); RETURN '"
						+ literal + "'; END FUNCTION;");
				statement.execute("CREATE FUNCTION " + prefix
						+ "_f(p LVARCHAR) RETURNING LVARCHAR(2000); RETURN p; END FUNCTION;");
				statement.execute("CREATE PROCEDURE " + prefix + "_p(p INTEGER); INSERT INTO " + prefix
						+ "_audit VALUES('" + literal + "'); END PROCEDURE;");
				final var schema = new com.sqlapp.data.schemas.Schema("informix");
				final var views = new InformixViewReader(DialectHolder.defaultDialect);
				views.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				final var viewContext = new ParametersContext();
				viewContext.put("tableName", prefix + "_view");
				for (var view : views.doGetAll(connection, viewContext, null))
					schema.getViews().add((com.sqlapp.data.schemas.View) view);
				final var functions = new InformixFunctionReader(DialectHolder.defaultDialect);
				functions.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				final var functionContext = new ParametersContext();
				functionContext.put("functionName", prefix + "_f");
				schema.getFunctions().addAll(functions.doGetAll(connection, functionContext, null));
				final var procedures = new InformixProcedureReader(DialectHolder.defaultDialect);
				procedures.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				final var procedureContext = new ParametersContext();
				procedureContext.put("procedureName", prefix + "_p");
				schema.getProcedures().addAll(procedures.doGetAll(connection, procedureContext, null));
				final com.sqlapp.data.schemas.Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
				assertEquals(1, restored.getViews().size());
				assertEquals(2, restored.getFunctions().size());
				assertEquals(1, restored.getProcedures().size());
				assertNotEquals(restored.getFunctions().get(0).getSpecificName(),
						restored.getFunctions().get(1).getSpecificName());
				statement.execute("DROP VIEW " + prefix + "_view");
				statement.execute("DROP FUNCTION " + prefix + "_f(INTEGER)");
				statement.execute("DROP FUNCTION " + prefix + "_f(LVARCHAR)");
				statement.execute("DROP PROCEDURE " + prefix + "_p");
				final var objects = new java.util.ArrayList<Object>();
				objects.addAll(restored.getViews());
				objects.addAll(restored.getFunctions());
				objects.addAll(restored.getProcedures());
				for (var object : objects) {
					final var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(object,
							SqlType.CREATE);
					assertEquals(1, operations.size());
					statement.execute(operations.get(0).getSqlText());
				}
				statement.execute("INSERT INTO " + prefix + " VALUES(1)");
				try (var rows = statement.executeQuery("SELECT label," + prefix + "_f(1)," + prefix
						+ "_f('overload'::LVARCHAR) FROM " + prefix + "_view")) {
					assertTrue(rows.next());
					assertEquals(literal, rows.getString(1));
					assertEquals(literal, rows.getString(2));
					assertEquals("overload", rows.getString(3));
				}
				statement.execute("EXECUTE PROCEDURE " + prefix + "_p(1)");
				try (var rows = statement.executeQuery("SELECT note FROM " + prefix + "_audit")) {
					assertTrue(rows.next());
					assertEquals(literal, rows.getString(1));
				}
				assertThrows(java.sql.SQLException.class,
						() -> statement.execute("INSERT INTO " + prefix + "_view(id) VALUES(-1)"));
			} finally {
				statement.execute("DROP VIEW IF EXISTS " + prefix + "_view");
				statement.execute("DROP FUNCTION IF EXISTS " + prefix + "_f(INTEGER)");
				statement.execute("DROP FUNCTION IF EXISTS " + prefix + "_f(LVARCHAR)");
				statement.execute("DROP PROCEDURE IF EXISTS " + prefix + "_p");
				statement.execute("DROP TABLE IF EXISTS " + prefix);
				statement.execute("DROP TABLE IF EXISTS " + prefix + "_audit");
			}
		}
	}

	@Test
	void recreatesMultiPhaseTriggerWithLongTextAndPreservesItsEffects() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix"); var statement = connection.createStatement()) {
			final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
			final String literal = "BEFORE FOR EACH STATEMENT " + "chunk boundary with spaces ".repeat(24);
			try {
				statement.execute("CREATE TABLE " + prefix + "(id INTEGER)");
				statement.execute("CREATE TABLE " + prefix + "_audit(phase INTEGER,note LVARCHAR(2000))");
				statement.execute("CREATE TRIGGER " + prefix + "_tr INSERT ON " + prefix + " REFERENCING NEW AS n "
						+ "BEFORE (INSERT INTO " + prefix + "_audit VALUES(0,'before')) " + "FOR EACH ROW (INSERT INTO "
						+ prefix + "_audit VALUES(n.id,'" + literal + "')) " + "AFTER (INSERT INTO " + prefix
						+ "_audit VALUES(2,'after'))");
				final var reader = new InformixTriggerReader(DialectHolder.defaultDialect);
				reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				final var context = new ParametersContext();
				context.put("triggerName", prefix + "_tr");
				final var triggers = reader.doGetAll(connection, context, null);
				assertEquals(1, triggers.size());
				final var schema = new com.sqlapp.data.schemas.Schema("informix");
				schema.getTriggers().addAll(triggers);
				final com.sqlapp.data.schemas.Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
				statement.execute("DROP TRIGGER " + prefix + "_tr");
				final var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry()
						.createSql(restored.getTriggers().get(0), SqlType.CREATE);
				assertEquals(2, operations.size());
				for (var operation : operations)
					statement.execute(operation.getSqlText());
				statement.execute("INSERT INTO " + prefix + " VALUES(1)");
				try (var rows = statement.executeQuery("SELECT phase,note FROM " + prefix + "_audit ORDER BY phase")) {
					for (int phase = 0; phase < 3; phase++) {
						assertTrue(rows.next());
						assertEquals(phase, rows.getInt(1));
						assertEquals(phase == 0 ? "before" : phase == 1 ? literal : "after", rows.getString(2));
					}
					assertFalse(rows.next());
				}
			} finally {
				statement.execute("DROP TABLE IF EXISTS " + prefix);
				statement.execute("DROP TABLE IF EXISTS " + prefix + "_audit");
			}
		}
	}

	@Test
	void recreatesIndexDbspacesExpressionFragmentsAndLegacyAttachedStorage() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix"); var statement = connection.createStatement()) {
			final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
			try {
				statement.execute("CREATE TABLE " + prefix + "(id INTEGER,a INTEGER,b INTEGER)");
				statement.execute("INSERT INTO " + prefix + " VALUES(1,5,7)");
				statement.execute("INSERT INTO " + prefix + " VALUES(2,15,8)");
				statement.execute("CREATE INDEX " + prefix + "_single ON " + prefix + "(id) IN rootdbs");
				statement.execute("CREATE INDEX " + prefix + "_expr ON " + prefix
						+ "(a) FRAGMENT BY EXPRESSION PARTITION lo (a<10) IN rootdbs,PARTITION hi REMAINDER IN rootdbs");
				statement.execute("CREATE INDEX " + prefix + "_attached ON " + prefix + "(b) IN TABLE");
				final String catalog = "SELECT f.indexname,f.strategy,f.partition,f.exprtext,f.dbspace,f.evalpos FROM sysfragments f JOIN systables t ON t.tabid=f.tabid WHERE t.owner='informix' AND t.tabname='"
						+ prefix + "' AND f.fragtype='I' ORDER BY f.indexname,f.evalpos";
				final var original = new java.util.ArrayList<List<String>>();
				try (var rows = statement.executeQuery(catalog)) {
					while (rows.next())
						original.add(java.util.Arrays.asList(rows.getString(1), rows.getString(2), rows.getString(3),
								rows.getString(4), rows.getString(5), rows.getString(6)));
				}
				final var reader = new InformixIndexReader(DialectHolder.defaultDialect);
				reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				final var context = new ParametersContext();
				context.put("schemaName", "informix");
				context.put("tableName", prefix);
				final AtomicInteger queries = new AtomicInteger();
				final Connection counted = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
						new Class<?>[] { Connection.class }, (proxy, method, args) -> {
							if (method.getName().equals("prepareStatement"))
								queries.incrementAndGet();
							try {
								return method.invoke(connection, args);
							} catch (java.lang.reflect.InvocationTargetException e) {
								throw e.getCause();
							}
						});
				final Table table = new Table(prefix).setSchemaName("informix");
				for (String name : List.of("id", "a", "b"))
					table.getColumns().add(name, c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
				table.getIndexes().addAll(reader.doGetAll(counted, context, null));
				assertEquals(2, queries.get());
				assertEquals(3, table.getIndexes().size());
				assertEquals("rootdbs", table.getIndexes().get(prefix + "_single").getTableSpaceName());
				assertEquals(2, table.getIndexes().get(prefix + "_expr").getPartitioning().getPartitions().size());
				assertEquals("true", table.getIndexes().get(prefix + "_attached").getSpecifics()
						.get(InformixIndexReader.INFORMIX_INDEX_IN_TABLE));
				final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
				for (var index : restored.getIndexes()) {
					statement.execute("DROP INDEX " + index.getName());
					for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(index,
							SqlType.CREATE))
						statement.execute(operation.getSqlText());
				}
				final var recreated = new java.util.ArrayList<List<String>>();
				try (var rows = statement.executeQuery(catalog)) {
					while (rows.next())
						recreated.add(java.util.Arrays.asList(rows.getString(1), rows.getString(2), rows.getString(3),
								rows.getString(4), rows.getString(5), rows.getString(6)));
				}
				assertEquals(original, recreated);
				try (var rows = statement.executeQuery("SELECT a FROM " + prefix + " ORDER BY a")) {
					assertTrue(rows.next());
					assertEquals(5, rows.getInt(1));
					assertTrue(rows.next());
					assertEquals(15, rows.getInt(1));
				}
			} finally {
				statement.execute("DROP TABLE IF EXISTS " + prefix);
			}
		}
	}

	@Test
	void loadsMetadataWithAReadOnlyNonDbaDatabaseUser() throws Exception {
		final var client = INFORMIX.getDockerClient();
		final var command = client.execCreateCmd(INFORMIX.getContainerId()).withUser("root").withAttachStdout(true)
				.withAttachStderr(true)
				.withCmd("sh", "-c", "useradd -m sqlapp_read && printf '%s\\n' 'sqlapp_read:ifx_test_pwd' | chpasswd")
				.exec();
		try (var callback = new com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>()) {
			assertTrue(client.execStartCmd(command.getId()).exec(callback).awaitCompletion(30,
					java.util.concurrent.TimeUnit.SECONDS));
		}
		assertEquals(0L, client.inspectExecCmd(command.getId()).exec().getExitCodeLong());
		final String database = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
		final String endpoint = "jdbc:informix-sqli://localhost:" + INFORMIX.getMappedPort(9088) + "/";
		final String options = ":INFORMIXSERVER=informix;DELIMIDENT=Y";
		try (var setup = DriverManager.getConnection(endpoint + "sysmaster" + options, "informix", "in4mix");
				var statement = setup.createStatement()) {
			statement.execute("CREATE DATABASE " + database + " WITH LOG");
		}
		try {
			try (var connection = DriverManager.getConnection(endpoint + database + options, "informix", "in4mix");
					var statement = connection.createStatement()) {
				statement.execute(
						"CREATE TABLE fixture.items(id INTEGER PRIMARY KEY CONSTRAINT fixture.items_pk,label VARCHAR(30) DEFAULT 'hello')");
				statement.execute("CREATE TABLE fixture.private_items(id INTEGER)");
				statement.execute("CREATE PRIVATE SYNONYM fixture.alias FOR fixture.items");
				statement.execute("REVOKE ALL ON fixture.items FROM PUBLIC AS fixture");
				statement.execute("REVOKE ALL ON fixture.private_items FROM PUBLIC AS fixture");
				statement.execute("GRANT CONNECT TO sqlapp_read");
				statement.execute("GRANT SELECT ON fixture.items TO sqlapp_read AS fixture");
				statement.execute("INSERT INTO fixture.items(id) VALUES(1)");
			}
			try (var connection = DriverManager.getConnection(endpoint + database + options, "sqlapp_read",
					"ifx_test_pwd"); var statement = connection.createStatement()) {
				try (var rows = statement.executeQuery("SELECT USER FROM sysmaster:sysdual")) {
					assertTrue(rows.next());
					assertEquals("sqlapp_read", rows.getString(1).strip());
				}
				try (var rows = statement.executeQuery("SELECT usertype FROM sysusers WHERE username='sqlapp_read'")) {
					assertTrue(rows.next());
					assertEquals("C", rows.getString(1).strip());
				}
				assertThrows(java.sql.SQLException.class,
						() -> statement.execute("INSERT INTO fixture.items(id) VALUES(2)"));
				assertThrows(java.sql.SQLException.class,
						() -> statement.executeQuery("SELECT * FROM fixture.private_items"));
				final var schema = new com.sqlapp.data.schemas.Schema("fixture").setCatalogName(database);
				DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().loadFull(connection, schema);
				assertNotNull(schema.getTables().get("items"));
				assertEquals(2, schema.getTables().get("items").getColumns().size());
				assertNotNull(schema.getTables().get("items").getConstraints().getPrimaryKeyConstraint());
				assertEquals("'hello'", schema.getTables().get("items").getColumns().get("label").getDefaultValue());
				assertEquals(1, schema.getSynonyms().size());
				final com.sqlapp.data.schemas.Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
				assertEquals("items", restored.getSynonyms().get(0).getObjectName());
				try (var rows = statement.executeQuery("SELECT label FROM fixture.alias WHERE id=1")) {
					assertTrue(rows.next());
					assertEquals("hello", rows.getString(1));
				}
			}
		} finally {
			try (var cleanup = DriverManager.getConnection(endpoint + "sysmaster" + options, "informix", "in4mix");
					var statement = cleanup.createStatement()) {
				statement.execute("DROP DATABASE " + database);
			}
		}
	}

	@Test
	void recreatesSequenceOrderCycleCacheAnd64BitValues() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix"); var statement = connection.createStatement()) {
			for (boolean ordered : List.of(true, false)) {
				final String name = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
				try {
					statement.execute("CREATE SEQUENCE " + name + (ordered
							? " START WITH 3000000000 INCREMENT BY 3 MINVALUE 2999999999 MAXVALUE 3000000010 NOCYCLE CACHE 2 ORDER"
							: " START WITH -2 INCREMENT BY -2 MINVALUE -6 MAXVALUE -2 CYCLE CACHE 2 NOORDER"));
					final var context = new ParametersContext();
					context.put("schemaName", "informix");
					context.put("sequenceName", name);
					final var reader = new InformixSequenceReader(DialectHolder.defaultDialect);
					reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
					final var sequences = reader.doGetAll(connection, context, null);
					assertEquals(1, sequences.size());
					assertEquals(ordered, sequences.get(0).isOrder());
					final com.sqlapp.data.schemas.Sequence restored = SchemaUtils
							.readXml(new StringReader(sequences.get(0).asXml()));
					statement.execute("DROP SEQUENCE " + name);
					for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored,
							SqlType.CREATE))
						statement.execute(operation.getSqlText());
					for (long expected : ordered ? new long[] { 3000000000L, 3000000003L }
							: new long[] { -2, -4, -6, -2 })
						try (var rows = statement.executeQuery("SELECT " + name + ".NEXTVAL FROM sysmaster:sysdual")) {
							assertTrue(rows.next());
							assertEquals(expected, rows.getLong(1));
						}
					final var actual = reader.doGetAll(connection, context, null).get(0);
					assertEquals(restored.getMinValue(), actual.getMinValue());
					assertEquals(restored.getMaxValue(), actual.getMaxValue());
					assertEquals(restored.getIncrementBy(), actual.getIncrementBy());
					assertEquals(restored.getCacheSize(), actual.getCacheSize());
					assertEquals(restored.isCycle(), actual.isCycle());
					assertEquals(restored.isOrder(), actual.isOrder());
				} finally {
					statement.execute("DROP SEQUENCE IF EXISTS " + name);
				}
			}
		}
	}

	@Test
	void recreatesQuotedCrossDatabaseSynonymsInAnsiAndNonAnsiDatabases() throws Exception {
		final String endpoint = "jdbc:informix-sqli://localhost:" + INFORMIX.getMappedPort(9088) + "/";
		final String options = ":INFORMIXSERVER=informix;DELIMIDENT=Y";
		for (boolean ansi : List.of(false, true)) {
			final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
			final String base = prefix + "_b";
			final String aliases = prefix + "_a";
			for (String name : List.of(base, aliases))
				try (var setup = DriverManager.getConnection(endpoint + "sysmaster" + options, "informix", "in4mix");
						var statement = setup.createStatement()) {
					statement.execute("CREATE DATABASE " + name + " WITH LOG" + (ansi ? " MODE ANSI" : ""));
				}
			try {
				try (var connection = DriverManager.getConnection(endpoint + base + options, "informix", "in4mix");
						var statement = connection.createStatement()) {
					connection.setAutoCommit(true);
					statement.execute("CREATE TABLE \"informix\".\"Odd\"\"Table\"(\"Odd Id\" INTEGER)");
					statement.execute("INSERT INTO \"informix\".\"Odd\"\"Table\" VALUES(7)");
				}
				try (var connection = DriverManager.getConnection(endpoint + aliases + options, "informix", "in4mix");
						var statement = connection.createStatement()) {
					connection.setAutoCommit(true);
					statement.execute(
							"CREATE " + (ansi ? "" : "PRIVATE ") + "SYNONYM \"informix\".\"Odd\"\"Alias\" FOR \"" + base
									+ "\":\"informix\".\"Odd\"\"Table\"");
					final var context = new ParametersContext();
					context.put("schemaName", "informix");
					final var reader = new InformixSynonymReader(DialectHolder.defaultDialect);
					reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
					final var synonyms = reader.doGetAll(connection, context, null);
					assertEquals(1, synonyms.size());
					assertEquals(base,
							synonyms.get(0).getSpecifics().get(InformixSynonymReader.INFORMIX_DATABASE_NAME));
					assertEquals(Boolean.toString(ansi),
							synonyms.get(0).getSpecifics().get(InformixSynonymReader.INFORMIX_ANSI_DATABASE));
					final com.sqlapp.data.schemas.Synonym restored = SchemaUtils
							.readXml(new StringReader(synonyms.get(0).asXml()));
					statement.execute("DROP SYNONYM \"informix\".\"Odd\"\"Alias\"");
					final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
					registry.getOptions().setDecorateSchemaName(true);
					for (var operation : registry.createSql(restored, SqlType.CREATE))
						statement.execute(operation.getSqlText());
					try (var rows = statement.executeQuery("SELECT \"Odd Id\" FROM \"informix\".\"Odd\"\"Alias\"")) {
						assertTrue(rows.next());
						assertEquals(7, rows.getInt(1));
					}
				}
			} finally {
				try (var cleanup = DriverManager.getConnection(endpoint + "sysmaster" + options, "informix", "in4mix");
						var statement = cleanup.createStatement()) {
					statement.execute("DROP DATABASE " + aliases);
					statement.execute("DROP DATABASE " + base);
				}
			}
		}
	}

	@Test
	void loadsWholeSchemaAndRecreatesReferencedObjectsThroughXml() throws Exception {
		final String database = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
		final String endpoint = "jdbc:informix-sqli://localhost:" + INFORMIX.getMappedPort(9088) + "/";
		final String options = ":INFORMIXSERVER=informix;DELIMIDENT=Y";
		try (var setup = DriverManager.getConnection(endpoint + "sysmaster" + options, "informix", "in4mix");
				var statement = setup.createStatement()) {
			statement.execute("CREATE DATABASE " + database + " WITH LOG");
		}
		try {
			try (var connection = DriverManager.getConnection(endpoint + database + options, "informix", "in4mix");
					var statement = connection.createStatement()) {
				statement.execute(
						"CREATE TABLE fixture.parent(id INTEGER PRIMARY KEY CONSTRAINT fixture.parent_pk,\"Odd Name\" VARCHAR(30) DEFAULT 'hello' NOT NULL CONSTRAINT fixture.parent_nn)");
				statement.execute(
						"CREATE TABLE fixture.child(pid INTEGER,n INTEGER NOT NULL CONSTRAINT fixture.child_nn DISABLED,FOREIGN KEY(pid) REFERENCES fixture.parent(id) CONSTRAINT fixture.child_fk,CHECK(pid>0) CONSTRAINT fixture.child_ck)");
				statement.execute("CREATE TABLE fixture.audit(id INTEGER)");
				statement.execute("CREATE INDEX fixture.child_ix ON fixture.child(pid DESC) DISABLED");
				statement.execute(
						"CREATE VIEW fixture.summary AS SELECT id,\"Odd Name\" FROM fixture.parent WHERE id>0");
				statement.execute(
						"CREATE SEQUENCE fixture.ids START WITH 10 INCREMENT BY 5 MINVALUE 10 MAXVALUE 100 NOCYCLE NOCACHE");
				statement.execute(
						"CREATE FUNCTION fixture.plus_one(p INTEGER) RETURNING INTEGER; RETURN p+1; END FUNCTION;");
				statement.execute(
						"CREATE PROCEDURE fixture.record_value(p INTEGER); INSERT INTO fixture.audit VALUES(p); END PROCEDURE;");
				statement.execute(
						"CREATE TRIGGER fixture.child_tr INSERT ON fixture.child REFERENCING NEW AS n FOR EACH ROW (INSERT INTO fixture.audit VALUES(n.pid))");
				statement.execute("CREATE PRIVATE SYNONYM fixture.parent_alias FOR fixture.parent");
				statement.execute("CREATE PUBLIC SYNONYM fixture.view_alias FOR fixture.summary");
				statement.execute("CREATE PRIVATE SYNONYM fixture.seq_alias FOR fixture.ids");
				final var schema = new com.sqlapp.data.schemas.Schema("fixture").setCatalogName(database);
				final var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader();
				reader.loadFull(connection, schema);
				assertEquals(3, schema.getTables().size(), schema.asXml());
				assertEquals(1, schema.getViews().size());
				assertEquals(1, schema.getSequences().size());
				assertEquals(1, schema.getFunctions().size());
				assertEquals(1, schema.getProcedures().size());
				assertEquals(1, schema.getTriggers().size());
				assertEquals(3, schema.getSynonyms().size());
				for (var synonym : schema.getSynonyms()) {
					assertEquals(database, synonym.getCatalogName());
					assertFalse(synonym.getSpecifics().containsKey(InformixSynonymReader.INFORMIX_DATABASE_NAME));
				}
				assertEquals(2, schema.getTables().get("parent").getColumns().size());
				assertFalse(schema.getTables().get("child").getColumns().get("n").isNotNull());
				assertFalse(schema.getTables().get("child").getIndexes().get("child_ix").isEnable());
				assertEquals(1, schema.getTables().get("child").getConstraints().getForeignKeyConstraints().size());
				final com.sqlapp.data.schemas.Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
				statement.execute("DROP VIEW fixture.summary");
				statement.execute("DROP FUNCTION fixture.plus_one(INTEGER)");
				statement.execute("DROP PROCEDURE fixture.record_value");
				statement.execute("DROP SEQUENCE fixture.ids");
				statement.execute("DROP TABLE fixture.child");
				statement.execute("DROP TABLE fixture.parent");
				statement.execute("DROP TABLE fixture.audit");
				final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				final var objects = new java.util.ArrayList<Object>();
				objects.add(restored.getSequences().get(0));
				for (String name : List.of("parent", "child", "audit"))
					objects.add(restored.getTables().get(name));
				objects.addAll(restored.getFunctions());
				objects.addAll(restored.getProcedures());
				objects.addAll(restored.getViews());
				objects.addAll(restored.getTriggers());
				objects.addAll(restored.getSynonyms());
				for (var object : objects)
					for (var operation : registry.createSql(object, SqlType.CREATE))
						statement.execute(operation.getSqlText());
				statement.execute("INSERT INTO fixture.parent(id) VALUES(1)");
				statement.execute("INSERT INTO fixture.child VALUES(1,NULL)");
				assertThrows(java.sql.SQLException.class,
						() -> statement.execute("INSERT INTO fixture.child VALUES(999,NULL)"));
				statement.execute("EXECUTE PROCEDURE fixture.record_value(2)");
				try (var rows = statement.executeQuery("SELECT \"Odd Name\" FROM fixture.summary WHERE id=1")) {
					assertTrue(rows.next());
					assertEquals("hello", rows.getString(1));
				}
				try (var rows = statement.executeQuery("SELECT SUM(id) FROM fixture.audit")) {
					assertTrue(rows.next());
					assertEquals(3, rows.getInt(1));
				}
				try (var rows = statement
						.executeQuery("SELECT fixture.plus_one(10),fixture.ids.NEXTVAL FROM sysmaster:sysdual")) {
					assertTrue(rows.next());
					assertEquals(11, rows.getInt(1));
					assertEquals(10, rows.getLong(2));
				}
				try (var rows = statement.executeQuery("SELECT \"Odd Name\" FROM fixture.parent_alias WHERE id=1")) {
					assertTrue(rows.next());
					assertEquals("hello", rows.getString(1));
				}
				try (var rows = statement.executeQuery("SELECT COUNT(*) FROM view_alias")) {
					assertTrue(rows.next());
					assertEquals(1, rows.getInt(1));
				}
				try (var rows = statement.executeQuery("SELECT fixture.seq_alias.NEXTVAL FROM sysmaster:sysdual")) {
					assertTrue(rows.next());
					assertEquals(15, rows.getLong(1));
				}
				final var reread = new com.sqlapp.data.schemas.Schema("fixture").setCatalogName(database);
				reader.loadFull(connection, reread);
				assertEquals(3, reread.getTables().size());
				assertEquals(1, reread.getTriggers().size());
				assertEquals(3, reread.getSynonyms().size());
			}
		} finally {
			try (var cleanup = DriverManager.getConnection(endpoint + "sysmaster" + options, "informix", "in4mix");
					var statement = cleanup.createStatement()) {
				statement.execute("DROP DATABASE " + database);
			}
		}
	}

	@Test
	void recreatesIndexModesIncludingDisabledUniqueIndexesOverDuplicateData() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix"); var statement = connection.createStatement()) {
			for (String state : List.of("E", "D", "F", "G")) {
				final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
				try {
					statement.execute("CREATE TABLE " + prefix + "(id INTEGER)");
					statement.execute("INSERT INTO " + prefix + " VALUES(1)");
					if (state.equals("D"))
						statement.execute("INSERT INTO " + prefix + " VALUES(1)");
					statement.execute("CREATE UNIQUE INDEX " + prefix + "_ix ON " + prefix + "(id) "
							+ com.sqlapp.data.db.dialect.informix.util.InformixConstraintModes.decode(state));
					statement.execute("CREATE INDEX " + prefix + "_non ON " + prefix + "(id DESC) DISABLED");
					final var reader = new InformixIndexReader(DialectHolder.defaultDialect);
					reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
					final var context = new ParametersContext();
					context.put("schemaName", "informix");
					context.put("tableName", prefix);
					final AtomicInteger queries = new AtomicInteger();
					final Connection counted = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
							new Class<?>[] { Connection.class }, (proxy, method, args) -> {
								if (method.getName().equals("prepareStatement"))
									queries.incrementAndGet();
								try {
									return method.invoke(connection, args);
								} catch (java.lang.reflect.InvocationTargetException e) {
									throw e.getCause();
								}
							});
					final Table table = new Table(prefix).setSchemaName("informix");
					table.getColumns().add("id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
					table.getIndexes().addAll(reader.doGetAll(counted, context, null));
					assertEquals(2, queries.get());
					assertEquals(2, table.getIndexes().size());
					final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
					assertEquals(!state.equals("D"), restored.getIndexes().get(prefix + "_ix").isEnable());
					assertFalse(restored.getIndexes().get(prefix + "_non").isEnable());
					for (var index : restored.getIndexes()) {
						statement.execute("DROP INDEX " + index.getName());
						final var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(index,
								SqlType.CREATE);
						assertEquals(1, operations.size());
						statement.execute(operations.get(0).getSqlText());
					}
					if (state.equals("F") || state.equals("G"))
						statement.execute("START VIOLATIONS TABLE FOR " + prefix);
					if (state.equals("E") || state.equals("G"))
						assertThrows(java.sql.SQLException.class,
								() -> statement.execute("INSERT INTO " + prefix + " VALUES(1)"));
					else
						statement.execute("INSERT INTO " + prefix + " VALUES(1)");
					try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + prefix)) {
						assertTrue(rows.next());
						assertEquals(state.equals("D") ? 3 : 1, rows.getInt(1));
					}
					if (state.equals("F"))
						try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + prefix + "_vio")) {
							assertTrue(rows.next());
							assertEquals(1, rows.getInt(1));
						}
					try (var rows = statement.executeQuery(
							"SELECT state FROM sysobjstate WHERE objtype='I' AND name='" + prefix + "_ix'")) {
						assertTrue(rows.next());
						assertEquals(state, rows.getString(1).strip());
					}
					try (var rows = statement.executeQuery(
							"SELECT state FROM sysobjstate WHERE objtype='I' AND name='" + prefix + "_non'")) {
						assertTrue(rows.next());
						assertEquals("D", rows.getString(1).strip());
					}
				} finally {
					statement.execute("DROP TABLE IF EXISTS " + prefix);
					statement.execute("DROP TABLE IF EXISTS " + prefix + "_vio");
					statement.execute("DROP TABLE IF EXISTS " + prefix + "_dia");
				}
			}
		}
	}

	@Test
	void recreatesTriggerModeAfterItChangesFromTheOriginalCreateMode() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix"); var statement = connection.createStatement()) {
			for (boolean enabled : List.of(false, true)) {
				final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
				try {
					statement.execute("CREATE TABLE " + prefix + "(id INTEGER)");
					statement.execute("CREATE TABLE " + prefix + "_audit(id INTEGER)");
					statement.execute("CREATE TRIGGER " + prefix + "_tr INSERT ON " + prefix
							+ " REFERENCING NEW AS n FOR EACH ROW " + "(INSERT INTO " + prefix + "_audit VALUES(n.id)) "
							+ (enabled ? "DISABLED" : "ENABLED"));
					statement.execute("SET TRIGGERS " + prefix + "_tr " + (enabled ? "ENABLED" : "DISABLED"));
					final var reader = new InformixTriggerReader(DialectHolder.defaultDialect);
					reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
					final var context = new ParametersContext();
					context.put("schemaName", "informix");
					context.put("triggerName", prefix + "_tr");
					final var triggers = reader.doGetAll(connection, context, null);
					assertEquals(1, triggers.size());
					assertEquals(enabled, triggers.get(0).isEnable());
					final com.sqlapp.data.schemas.Trigger restored = SchemaUtils
							.readXml(new StringReader(triggers.get(0).asXml()));
					statement.execute("DROP TRIGGER " + prefix + "_tr");
					for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored,
							SqlType.CREATE))
						statement.execute(operation.getSqlText());
					statement.execute("INSERT INTO " + prefix + " VALUES(123)");
					try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + prefix + "_audit")) {
						assertTrue(rows.next());
						assertEquals(enabled ? 1 : 0, rows.getInt(1));
					}
					try (var rows = statement.executeQuery(
							"SELECT state FROM sysobjstate WHERE objtype='T' AND name='" + prefix + "_tr'")) {
						assertTrue(rows.next());
						assertEquals(enabled ? "E" : "D", rows.getString(1).strip());
					}
				} finally {
					statement.execute("DROP TABLE IF EXISTS " + prefix);
					statement.execute("DROP TABLE IF EXISTS " + prefix + "_audit");
				}
			}
		}
	}

	@Test
	void executesGeneratedConstraintsClusterIndexAndNativeExpressions() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix")) {
			final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
			final var dialect = DialectHolder.defaultDialect;
			final var registry = dialect.createSqlFactoryRegistry();
			final Table parent = new Table(prefix);
			parent.getColumns().add("id",
					c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT).setIdentity(true));
			parent.setPrimaryKey(prefix + "_pk", parent.getColumns().get(0));
			final Table child = new Table(prefix + "_child");
			child.getColumns().add("pid", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
			child.getColumns().add("sort_key", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
			child.getConstraints().add(new com.sqlapp.data.schemas.CheckConstraint(prefix + "_ck", "pid > 0"));
			child.getConstraints()
					.addForeignKeyConstraint(prefix + "_fk", child.getColumns().get(0), parent.getColumns().get(0))
					.setDeleteRule(com.sqlapp.data.schemas.CascadeRule.Cascade);
			child.getIndexes().add(prefix + "_ix", child.getColumns().get("sort_key"));
			child.getIndexes().get(0).setIndexType(com.sqlapp.data.schemas.IndexType.Clustered);
			try (var statement = connection.createStatement()) {
				try {
					for (Table table : List.of(parent, child)) {
						for (var operation : registry.createSql(table, SqlType.CREATE)) {
							statement.execute(operation.getSqlText());
						}
					}
					statement.execute("INSERT INTO " + prefix + " VALUES(0)");
					try (var rows = statement.executeQuery(dialect.getIdentitySelectString())) {
						assertTrue(rows.next());
						assertEquals(1, rows.getInt(1));
					}
					try (var rows = statement.executeQuery("SELECT " + dialect.getCurrentDateFunction() + ", "
							+ dialect.getCurrentDateTimeFunction() + ", " + dialect.getCurrentTimeFunction() + ", "
							+ dialect.getCurrentTimestampFunction() + " FROM " + dialect.getSelectDummyTableName())) {
						assertTrue(rows.next());
						for (int i = 1; i <= 4; i++)
							assertNotNull(rows.getObject(i));
						assertFalse(rows.next());
					}
					final ParametersContext context = new ParametersContext();
					context.put("tableName", child.getName());
					final var tables = dialect.getCatalogReader().getSchemaReader().getTableReader()
							.getAllFull(connection, context);
					final Table read = tables.stream().filter(t -> t.getName().equals(child.getName())).findFirst()
							.orElseThrow();
					assertEquals(com.sqlapp.data.schemas.IndexType.Clustered,
							read.getIndexes().get(prefix + "_ix").getIndexType());
					assertNotNull(read.getConstraints().get(prefix + "_ck"));
					assertNotNull(read.getConstraints().get(prefix + "_fk"));
				} finally {
					statement.execute("DROP TABLE IF EXISTS " + child.getName());
					statement.execute("DROP TABLE IF EXISTS " + parent.getName());
				}
			}
		}
	}

	@Test
	void separatesSameNamedTablesAndUniqueConstraintsAcrossOwners() throws Exception {
		final String database = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
		final String endpoint = "jdbc:informix-sqli://localhost:" + INFORMIX.getMappedPort(9088) + "/";
		final String options = ":INFORMIXSERVER=informix;DELIMIDENT=Y";
		try (var setup = DriverManager.getConnection(endpoint + "sysmaster" + options, "informix", "in4mix");
				var statement = setup.createStatement()) {
			statement.execute("CREATE DATABASE " + database + " WITH LOG MODE ANSI");
		}
		try {
			try (var connection = DriverManager.getConnection(endpoint + database + options, "informix", "in4mix");
					var statement = connection.createStatement()) {
				connection.setAutoCommit(false);
				statement.execute(
						"CREATE TABLE owner_a.items (first INTEGER,second INTEGER,UNIQUE(first,second) CONSTRAINT owner_a.uq_items)");
				statement.execute(
						"CREATE TABLE owner_b.items (first INTEGER,second INTEGER,UNIQUE(second,first) CONSTRAINT owner_b.uq_items)");
				statement.execute("CREATE INDEX owner_a.ix_a ON owner_a.items(first DESC)");
				statement.execute("CREATE INDEX owner_b.ix_b ON owner_b.items(second DESC)");
				connection.commit();
				final var context = new ParametersContext();
				context.put("tableName", "items");
				context.put("schemaName", List.of("OWNER_A", "OWNER_B"));
				final var reader = new InformixIndexReader(DialectHolder.defaultDialect);
				reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				final var indexes = reader.doGetAll(connection, context, null);
				assertEquals(2, indexes.size());
				for (Index index : indexes) {
					assertEquals(index.getSchemaName().equals("OWNER_A") ? "first" : "second",
							index.getColumns().get(0).getName());
					assertEquals(com.sqlapp.data.schemas.Order.Desc, index.getColumns().get(0).getOrder());
				}
				final var tables = new DoubleKeyMap<String, String, Table>();
				for (String owner : List.of("OWNER_A", "OWNER_B")) {
					final Table table = new Table("items").setCatalogName(database).setSchemaName(owner);
					table.getColumns().add("first", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
					table.getColumns().add("second", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
					tables.put(owner, "items", table);
				}
				final var tableReader = new InformixTableReader(DialectHolder.defaultDialect);
				tableReader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				tableReader.setUniqueConstraints(connection, tables);
				assertEquals("first", tables.get("OWNER_A", "items").getConstraints().getUniqueConstraints().get(0)
						.getColumns().get(0).getName());
				assertEquals("second", tables.get("OWNER_B", "items").getConstraints().getUniqueConstraints().get(0)
						.getColumns().get(0).getName());
				final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (String owner : List.of("OWNER_A", "OWNER_B"))
					statement.execute("DROP TABLE " + owner + ".items");
				for (String owner : List.of("OWNER_A", "OWNER_B")) {
					for (var operation : registry.createSql(tables.get(owner, "items"), SqlType.CREATE))
						statement.execute(operation.getSqlText());
				}
				connection.rollback();
			}
		} finally {
			try (var cleanup = DriverManager.getConnection(endpoint + "sysmaster" + options, "informix", "in4mix");
					var statement = cleanup.createStatement()) {
				statement.execute("DROP DATABASE " + database);
			}
		}
	}

	@Test
	void recreatesCustomOperatorClassByNameAfterItsCatalogIdChanges() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix"); var statement = connection.createStatement()) {
			final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
			final String definition = " FOR btree STRATEGIES(lessthan,lessthanorequal,equal,greaterthanorequal,greaterthan) SUPPORT(compare)";
			try {
				statement.execute("CREATE TABLE " + prefix + " (id INTEGER,other INTEGER)");
				statement.execute("CREATE OPCLASS " + prefix + "_ops" + definition);
				statement.execute("CREATE INDEX " + prefix + "_ix ON " + prefix + "(id " + prefix + "_ops DESC,other)");
				final var reader = new InformixIndexReader(DialectHolder.defaultDialect);
				reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				final var context = new ParametersContext();
				context.put("tableName", prefix);
				final Table table = new Table(prefix).setSchemaName("informix");
				table.getColumns().add("id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
				table.getColumns().add("other", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
				table.getIndexes().addAll(reader.doGetAll(connection, context, null));
				final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
				int originalId;
				try (var rows = statement
						.executeQuery("SELECT opclassid FROM sysopclasses WHERE opclassname='" + prefix + "_ops'")) {
					assertTrue(rows.next());
					originalId = rows.getInt(1);
				}
				statement.execute("DROP INDEX " + prefix + "_ix");
				statement.execute("DROP OPCLASS " + prefix + "_ops RESTRICT");
				statement.execute("CREATE OPCLASS " + prefix + "_dummy" + definition);
				statement.execute("CREATE OPCLASS " + prefix + "_ops" + definition);
				try (var rows = statement
						.executeQuery("SELECT opclassid FROM sysopclasses WHERE opclassname='" + prefix + "_ops'")) {
					assertTrue(rows.next());
					assertNotEquals(originalId, rows.getInt(1));
				}
				for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry()
						.createSql(restored.getIndexes().get(0), SqlType.CREATE))
					statement.execute(operation.getSqlText());
				final Index recreated = reader.doGetAll(connection, context, null).get(0);
				assertEquals(prefix + "_ops",
						recreated.getColumns().get(0).getSpecifics().get(InformixIndexReader.INFORMIX_OPERATOR_CLASS));
				assertEquals(com.sqlapp.data.schemas.Order.Desc, recreated.getColumns().get(0).getOrder());
			} finally {
				statement.execute("DROP TABLE IF EXISTS " + prefix);
				final var names = new java.util.ArrayList<String>();
				try (var rows = statement.executeQuery("SELECT opclassname FROM sysopclasses WHERE opclassname IN ('"
						+ prefix + "_ops','" + prefix + "_dummy')")) {
					while (rows.next())
						names.add(rows.getString(1));
				}
				for (String name : names)
					try (var cleanup = connection.createStatement()) {
						cleanup.execute("DROP OPCLASS " + name + " RESTRICT");
					}
			}
		}
	}

	@Test
	void recreatesBtsAccessMethodParametersAndSearchBehavior() throws Exception {
		final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
		final String environment = "INFORMIXDIR=/opt/ibm/informix INFORMIXSERVER=informix "
				+ "LD_LIBRARY_PATH=/opt/ibm/informix/lib:/opt/ibm/informix/lib/esql ";
		final var space = INFORMIX.execInContainer("sh", "-c",
				"umask 007; touch /tmp/" + prefix + ".sb && " + environment + "/opt/ibm/informix/bin/onspaces -c -S "
						+ prefix + " -p /tmp/" + prefix + ".sb -o 0 -s 20000");
		assertEquals(0, space.getExitCode(), space.getStdout() + space.getStderr());
		final var config = INFORMIX.execInContainer("sh", "-c",
				environment + "/opt/ibm/informix/bin/onmode -wm SBSPACENAME=" + prefix);
		assertEquals(0, config.getExitCode(), config.getStdout() + config.getStderr());
		final var modules = INFORMIX.execInContainer("sh", "-c",
				"for directory in /opt/ibm/informix/extend/bts.*; do if [ -d \"$directory\" ]; then basename \"$directory\"; fi; done");
		assertEquals(0, modules.getExitCode());
		final var versions = modules.getStdout().lines().filter(v -> !v.isBlank()).toList();
		assertEquals(1, versions.size(), modules.getStdout());
		final String version = versions.get(0);
		assertTrue(version.matches("bts\\.[A-Za-z0-9.]+"), version);
		final String endpoint = "jdbc:informix-sqli://localhost:" + INFORMIX.getMappedPort(9088) + "/";
		final String options = ":INFORMIXSERVER=informix;DELIMIDENT=Y";
		try (var setup = DriverManager.getConnection(endpoint + "sysmaster" + options, "informix", "in4mix");
				var statement = setup.createStatement()) {
			statement.execute("CREATE DATABASE " + prefix + " WITH LOG");
		}
		try {
			// DataBlade registration scripts require the server's default string quoting.
			try (var registration = DriverManager.getConnection(endpoint + prefix + ":INFORMIXSERVER=informix",
					"informix", "in4mix"); var statement = registration.createStatement()) {
				int registered;
				try (var rows = statement.executeQuery("EXECUTE FUNCTION sysbldprepare('" + version + "','create')")) {
					assertTrue(rows.next());
					registered = rows.getInt(1);
				}
				assertEquals(0, registered, "BTS registration failed");
			}
			try (var connection = DriverManager.getConnection(endpoint + prefix + options, "informix", "in4mix");
					var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE documents(id INTEGER,txt VARCHAR(200))");
				statement.execute(
						"CREATE INDEX text_idx ON documents(txt bts_varchar_ops) USING bts (analyzer='keyword')");
				statement.execute("INSERT INTO documents VALUES(1,'Alpha Beta')");
				final var reader = new InformixIndexReader(DialectHolder.defaultDialect);
				reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				final var context = new ParametersContext();
				context.put("tableName", "documents");
				final Table table = new Table("documents").setSchemaName("informix");
				table.getColumns().add("id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
				table.getColumns().add("txt",
						c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.VARCHAR).setLength(200));
				table.getIndexes().addAll(reader.doGetAll(connection, context, null));
				final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
				assertEquals("bts",
						restored.getIndexes().get(0).getSpecifics().get(InformixIndexReader.INFORMIX_ACCESS_METHOD));
				assertEquals("analyzer='keyword'", restored.getIndexes().get(0).getSpecifics()
						.get(InformixIndexReader.INFORMIX_ACCESS_PARAMETERS));
				statement.execute("DROP INDEX text_idx");
				for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry()
						.createSql(restored.getIndexes().get(0), SqlType.CREATE))
					statement.execute(operation.getSqlText());
				try (var rows = statement
						.executeQuery("SELECT COUNT(*) FROM documents WHERE bts_contains(txt,'Alpha')")) {
					assertTrue(rows.next());
					assertEquals(0, rows.getInt(1)); // The keyword analyzer must not become the standard word analyzer.
				}
				try (var rows = statement
						.executeQuery("SELECT COUNT(*) FROM documents WHERE bts_contains(txt,'\"Alpha Beta\"')")) {
					assertTrue(rows.next());
					assertEquals(1, rows.getInt(1));
				}
				statement.execute("DROP INDEX text_idx");
				restored.getIndexes().get(0).setEnable(false);
				for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry()
						.createSql(restored.getIndexes().get(0), SqlType.CREATE))
					statement.execute(operation.getSqlText());
				final var disabled = reader.doGetAll(connection, context, null);
				assertEquals(1, disabled.size());
				assertFalse(disabled.get(0).isEnable());
				assertEquals("bts", disabled.get(0).getSpecifics().get(InformixIndexReader.INFORMIX_ACCESS_METHOD));
			}
		} finally {
			try (var cleanup = DriverManager.getConnection(endpoint + "sysmaster" + options, "informix", "in4mix");
					var statement = cleanup.createStatement()) {
				statement.execute("DROP DATABASE " + prefix);
			}
		}
	}

	@Test
	void recreatesFunctionalIndexesIncludingMoreThanSixteenArguments() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix"); var statement = connection.createStatement()) {
			final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
			final String columns = java.util.stream.IntStream.rangeClosed(1, 17).mapToObj(i -> "c" + i + " INTEGER")
					.collect(java.util.stream.Collectors.joining(","));
			final String arguments = java.util.stream.IntStream.rangeClosed(1, 17).mapToObj(i -> "a" + i + " INTEGER")
					.collect(java.util.stream.Collectors.joining(","));
			final String names = java.util.stream.IntStream.rangeClosed(1, 17).mapToObj(i -> "c" + i)
					.collect(java.util.stream.Collectors.joining(","));
			try {
				statement.execute("CREATE TABLE " + prefix + " (" + columns + ")");
				statement.execute("CREATE FUNCTION " + prefix + "_sum(" + arguments
						+ ") RETURNING INTEGER WITH (NOT VARIANT); RETURN a1+a17; END FUNCTION;");
				statement.execute("CREATE FUNCTION " + prefix
						+ "_pair(a INTEGER,b INTEGER) RETURNING INTEGER WITH (NOT VARIANT); RETURN a+b; END FUNCTION;");
				statement.execute("CREATE UNIQUE INDEX " + prefix + "_ix1 ON " + prefix + "(" + prefix
						+ "_pair(c1,c3) DESC,c2 ASC)");
				statement.execute(
						"CREATE INDEX " + prefix + "_ix2 ON " + prefix + "(" + prefix + "_sum(" + names + "))");
				final var reader = new InformixIndexReader(DialectHolder.defaultDialect);
				reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				final var context = new ParametersContext();
				context.put("tableName", prefix);
				final AtomicInteger queries = new AtomicInteger();
				final Connection counted = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
						new Class<?>[] { Connection.class }, (proxy, method, args) -> {
							if (method.getName().equals("prepareStatement"))
								queries.incrementAndGet();
							try {
								return method.invoke(connection, args);
							} catch (java.lang.reflect.InvocationTargetException e) {
								throw e.getCause();
							}
						});
				final var indexes = reader.doGetAll(counted, context, null);
				assertEquals(3, queries.get());
				assertEquals(2, indexes.size());
				final Table table = new Table(prefix).setSchemaName("informix");
				for (int i = 1; i <= 17; i++)
					table.getColumns().add("c" + i, c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
				table.getIndexes().addAll(indexes);
				final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
				for (Index index : restored.getIndexes()) {
					String original;
					try (var rows = statement.executeQuery(
							"SELECT indexkeys::LVARCHAR FROM sysindices WHERE idxname='" + index.getName() + "'")) {
						assertTrue(rows.next());
						original = rows.getString(1);
					}
					statement.execute("DROP INDEX " + index.getName());
					for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(index,
							SqlType.CREATE))
						statement.execute(operation.getSqlText());
					try (var rows = statement.executeQuery(
							"SELECT indexkeys::LVARCHAR FROM sysindices WHERE idxname='" + index.getName() + "'")) {
						assertTrue(rows.next());
						assertEquals(original, rows.getString(1));
					}
				}
			} finally {
				statement.execute("DROP TABLE IF EXISTS " + prefix);
				statement.execute("DROP FUNCTION IF EXISTS " + prefix + "_sum");
				statement.execute("DROP FUNCTION IF EXISTS " + prefix + "_pair");
			}
		}
	}

	@Test
	void recreatesRollingPoliciesAndDisabledIntervals() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix"); var statement = connection.createStatement()) {
			final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
			final var reader = new InformixTableReader(DialectHolder.defaultDialect);
			reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
			for (String policy : List.of("ROLLING(3 FRAGMENTS) DETACH",
					"ROLLING(5 FRAGMENTS) LIMIT TO 100MiB DISCARD ANY", "LIMIT TO 1G DISCARD INTERVAL ONLY",
					"LIMIT TO 100K DETACH INTERVAL FIRST")) {
				try {
					statement.execute("CREATE TABLE " + prefix + " (id INTEGER) FRAGMENT BY RANGE(id) INTERVAL(10) "
							+ policy + " STORE IN(rootdbs) PARTITION p1 VALUES < 10 IN rootdbs");
					// The count-only case verifies that a suspended purge policy stays suspended.
					final boolean disabled = policy.startsWith("ROLLING(3");
					if (disabled)
						statement.execute("ALTER FRAGMENT ON TABLE " + prefix + " MODIFY INTERVAL DISABLED");
					else
						statement.execute("INSERT INTO " + prefix + " VALUES(25)");
					final Table source = new Table(prefix).setSchemaName("informix");
					source.getColumns().add("id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
					final var tables = new DoubleKeyMap<String, String, Table>();
					tables.put("informix", prefix, source);
					reader.setFragmentation(connection, tables);
					assertNotNull(
							source.getPartitioning().getSpecifics().get(InformixTableReader.INFORMIX_ROLLING_ACTION));
					final Table restored = SchemaUtils.readXml(new StringReader(source.asXml()));
					restored.setName(prefix + "_copy");
					for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored,
							SqlType.CREATE))
						statement.execute(operation.getSqlText());
					if (!disabled)
						statement.execute("INSERT INTO " + prefix + "_copy VALUES(25)");
					final Table copy = new Table(prefix + "_copy").setSchemaName("informix");
					final var copies = new DoubleKeyMap<String, String, Table>();
					copies.put("informix", copy.getName(), copy);
					reader.setFragmentation(connection, copies);
					assertEquals(source.getPartitioning().getPartitions().size(),
							copy.getPartitioning().getPartitions().size());
					for (String key : List.of(InformixTableReader.INFORMIX_ROLLING_FRAGMENTS,
							InformixTableReader.INFORMIX_ROLLING_SIZE_KB, InformixTableReader.INFORMIX_ROLLING_ACTION,
							InformixTableReader.INFORMIX_ROLLING_SCOPE, InformixTableReader.INFORMIX_INTERVAL_DISABLED))
						assertEquals(source.getPartitioning().getSpecifics().get(key),
								copy.getPartitioning().getSpecifics().get(key), key);
				} finally {
					statement.execute("DROP TABLE IF EXISTS " + prefix + "_copy");
					statement.execute("DROP TABLE IF EXISTS " + prefix);
				}
			}
		}
	}

	@Test
	void bulkCatalogQueriesAndFragmentDdlRoundTrip() throws Exception {
		Class.forName("com.informix.jdbc.IfxDriver");
		try (Connection connection = DriverManager.getConnection("jdbc:informix-sqli://localhost:"
				+ INFORMIX.getMappedPort(9088) + "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y", "informix",
				"in4mix")) {
			final String prefix = "ifx_" + Long.toUnsignedString(System.nanoTime(), 36);
			try {
				try (var statement = connection.createStatement()) {
					statement.execute("CREATE TABLE " + prefix
							+ " (id SERIAL NOT NULL, a INTEGER, b INTEGER, UNIQUE(a,b) CONSTRAINT " + prefix + "_uq)");
					statement.execute("CREATE INDEX " + prefix + "_ix ON " + prefix + "(b DESC,a ASC)");
				}
				final AtomicInteger queries = new AtomicInteger();
				final Connection counted = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
						new Class<?>[] { Connection.class }, (proxy, method, args) -> {
							if (method.getName().equals("prepareStatement"))
								queries.incrementAndGet();
							try {
								return method.invoke(connection, args);
							} catch (java.lang.reflect.InvocationTargetException e) {
								throw e.getCause();
							}
						});
				final ParametersContext context = new ParametersContext();
				context.put("tableName", List.of(prefix));
				final InformixIndexReader indexReader = new InformixIndexReader(DialectHolder.defaultDialect);
				indexReader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				final List<Index> indexes = indexReader.doGetAll(counted, context, null);
				assertEquals(2, queries.get());
				final Index index = indexes.stream().filter(i -> i.getName().equals(prefix + "_ix")).findFirst()
						.orElseThrow();
				assertEquals("b", index.getColumns().get(0).getName());
				assertEquals(com.sqlapp.data.schemas.Order.Desc, index.getColumns().get(0).getOrder());
				final Table table = new Table(prefix).setCatalogName(index.getCatalogName())
						.setSchemaName(index.getSchemaName());
				table.getColumns().add("a", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
				table.getColumns().add("b", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
				final DoubleKeyMap<String, String, Table> tables = new DoubleKeyMap<>();
				tables.put(table.getSchemaName(), table.getName(), table);
				queries.set(0);
				final InformixTableReader reader = new InformixTableReader(DialectHolder.defaultDialect);
				reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
				reader.setUniqueConstraints(counted, tables);
				assertEquals(2, queries.get());
				assertEquals(1, table.getConstraints().size());
				context.put("tableName", prefix);
				final Table full = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader()
						.getAllFull(connection, context).stream().filter(t -> t.getName().equals(prefix)).findFirst()
						.orElseThrow();
				assertTrue(full.getColumns().get("id").isIdentity());
				assertEquals(1, full.getIndexes().size(), "table=" + full.asXml() + "; index=" + index.asXml());
				full.setName(prefix + "_copy");
				full.getIndexes().clear();
				full.getConstraints().clear();
				try (var statement = connection.createStatement()) {
					statement.execute(DialectHolder.defaultDialect.createSqlFactoryRegistry()
							.createSql(full, SqlType.CREATE).get(0).getSqlText());
					statement.execute("DROP TABLE " + prefix + "_copy");
				}
				for (String strategy : List.of(
						"EXPRESSION PARTITION p1 (id < 10) IN rootdbs, PARTITION p2 REMAINDER IN rootdbs",
						"LIST(id) PARTITION p1 VALUES(1,2) IN rootdbs, PARTITION p2 REMAINDER IN rootdbs",
						"RANGE(id) INTERVAL(10) STORE IN(rootdbs) PARTITION p1 VALUES < 10 IN rootdbs",
						"ROUND ROBIN PARTITION p1 IN rootdbs, PARTITION p2 IN rootdbs")) {
					try (var statement = connection.createStatement()) {
						statement.execute("CREATE TABLE " + prefix + "_frag (id INTEGER) FRAGMENT BY " + strategy);
					}
					final Table fragment = new Table(prefix + "_frag").setSchemaName(index.getSchemaName());
					fragment.getColumns().add("id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
					final DoubleKeyMap<String, String, Table> fragments = new DoubleKeyMap<>();
					fragments.put(fragment.getSchemaName(), fragment.getName(), fragment);
					reader.setFragmentation(connection, fragments);
					assertNotNull(fragment.getPartitioning());
					final Table restored = SchemaUtils.readXml(new StringReader(fragment.asXml()));
					restored.setName(prefix + "_copy");
					final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
							.createSql(restored, SqlType.CREATE).get(0).getSqlText();
					try (var statement = connection.createStatement()) {
						statement.execute(sql);
						statement.execute("DROP TABLE " + prefix + "_copy");
						statement.execute("DROP TABLE " + prefix + "_frag");
					}
				}
			} finally {
				for (String suffix : List.of("_copy", "_frag", "")) {
					try (var statement = connection.createStatement()) {
						statement.execute("DROP TABLE IF EXISTS " + prefix + suffix);
					}
				}
			}
		}
	}
}
