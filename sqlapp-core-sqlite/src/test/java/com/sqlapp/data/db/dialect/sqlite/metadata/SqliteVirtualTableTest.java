/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;

class SqliteVirtualTableTest {
	@Test
	void ftsDefinitionAndHiddenColumnsSurviveSchemaXmlAndRecreation() throws Exception {
		try (var source = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = source.createStatement();
				var target = DriverManager.getConnection("jdbc:sqlite::memory:"); var destination = target.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"Other]Db\"");
			destination.execute("ATTACH ':memory:' AS \"Other]Db\"");
			statement.execute("CREATE VIRTUAL TABLE \"Other]Db\".docs USING fts5(title,body,tokenize='porter unicode61')");
			statement.execute("CREATE TABLE \"Other]Db\".docs_custom(id INT)");
			var reader = reader();
			reader.setSchemaName("Other]Db");
			var tables = reader.getAllFull(source);
			assertEquals(List.of("docs","docs_custom"),tables.stream().map(Table::getName).toList());
			var docs = tables.get(0);
			assertEquals("true",docs.getSpecifics().get("sqlite.virtual"));
			assertTrue(docs.getColumns().get("rank").isHidden());
			assertTrue(docs.getConstraints().isEmpty()); assertTrue(docs.getIndexes().isEmpty());
			var schema = new Schema("Other]Db"); schema.getTables().add(docs);
			Schema copy = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
			var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(copy.getTables().get(0),SqlType.CREATE);
			assertEquals(1,operations.size());
			destination.execute(operations.get(0).getSqlText());
			destination.execute("INSERT INTO \"Other]Db\".docs(title,body) VALUES('Running','runners run quickly')");
			try (var result = destination.executeQuery("SELECT title FROM \"Other]Db\".docs WHERE docs MATCH 'run'")) {
				assertTrue(result.next()); assertEquals("Running",result.getString(1)); assertFalse(result.next());
			}
		}
	}

	@Test
	void rtreeModuleKeepsItsNativeSpatialBehaviorAndCanBeRenamedInTheModel() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("CREATE VIRTUAL TABLE boxes USING rtree(id,minX,maxX,minY,maxY)");
			var reader = reader(); reader.setSchemaName("MAIN");
			var tables = reader.getAllFull(connection); assertEquals(1,tables.size());
			var table = tables.get(0); table.setName("renamed]boxes");
			statement.execute(DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table,SqlType.CREATE).get(0).getSqlText());
			statement.execute("INSERT INTO \"renamed]boxes\" VALUES(1,0,10,0,10)");
			try (var result = statement.executeQuery("SELECT id FROM \"renamed]boxes\" WHERE minX<=5 AND maxX>=5")) {
				assertTrue(result.next()); assertEquals(1,result.getInt(1));
			}
		}
	}

	@Test
	void legacyEnumerationAvoidsTableListButRetainsVirtualDefinitions() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("CREATE VIRTUAL TABLE docs USING fts5(body)");
			var reader = reader();
			var context = new ParametersContext(); context.put("schemaName","main"); context.put("tableName","docs");
			var tables = reader.doGetAll(connection,context,new ProductVersionInfo().setMajorVersion(3).setMinorVersion(36));
			assertEquals(1,tables.size());
			assertTrue(SqliteDdl.isVirtualTable(String.join("\n",tables.get(0).getDefinition())));
		}
	}

	@Test
	void virtualOnlyBulkReadingUsesAConstantNumberOfPreparedQueries() throws Exception {
		try (var raw = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = raw.createStatement()) {
			statement.execute("CREATE VIRTUAL TABLE docs0 USING fts5(body)");
			var count = new AtomicInteger();
			Connection connection = (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[] {Connection.class},(proxy,method,args) -> {
				if (method.getName().equals("prepareStatement")) count.incrementAndGet();
				try { return method.invoke(raw,args); } catch (InvocationTargetException e) { throw e.getCause(); }
			});
			var reader = reader(); reader.setSchemaName("main");
			assertEquals(1,reader.getAllFull(connection).size());
			int singleCount = count.get(); assertTrue(singleCount>0);
			for (int i=1;i<5;i++) statement.execute("CREATE VIRTUAL TABLE docs" + i + " USING fts5(body)");
			count.set(0); assertEquals(5,reader.getAllFull(connection).size());
			assertEquals(singleCount,count.get());
		}
	}

	@Test
	void disabledDdlReadingStillPreventsOrdinaryTableRecreation() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			statement.execute("CREATE VIRTUAL TABLE docs USING fts5(body)");
			var reader = reader(); reader.setSchemaName("main"); reader.setObjectName("docs");
			reader.getReaderOptions().setReadStatement(false); reader.getReaderOptions().setReadDefinition(false);
			var table = reader.getAllFull(connection).get(0);
			assertTrue(table.getDefinition().isEmpty());
			assertEquals("true",table.getSpecifics().get("sqlite.virtual"));
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			assertThrows(IllegalArgumentException.class,() -> registry.createSql(table,SqlType.CREATE));
			var alter = (com.sqlapp.data.db.dialect.sqlite.sql.SqliteAlterTableFactory)registry.getSqlFactory(table,SqlType.ALTER);
			assertThrows(IllegalArgumentException.class,() -> alter.renameColumn(table,"body","renamed"));
		}
	}

	@Test
	void ftsTextTypesAreUsableAcrossBulkAndLegacyColumnReading() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = connection.createStatement()) {
			for (String module : List.of("fts3","fts4","fts5")) {
				statement.execute("CREATE VIRTUAL TABLE " + module + "_docs USING " + module + "(body)");
				var reader = reader(); reader.setSchemaName("main"); reader.setObjectName(module + "_docs");
				assertEquals(com.sqlapp.data.db.datatype.DataType.VARCHAR,reader.getAllFull(connection).get(0).getColumns().get("body").getDataType());
				var context = new ParametersContext(); context.put("schemaName","main"); context.put("tableName",module + "_docs");
				var columns = new SqliteColumnReader(DialectHolder.defaultDialect).doGetAll(connection,context,
						new ProductVersionInfo().setMajorVersion(3).setMinorVersion(15));
				assertEquals(com.sqlapp.data.db.datatype.DataType.VARCHAR,columns.stream().filter(c -> c.getName().equals("body")).findFirst().orElseThrow().getDataType());
			}
			statement.execute("CREATE TABLE ordinary(value)");
			var reader = reader(); reader.setSchemaName("main"); reader.setObjectName("ordinary");
			assertEquals(com.sqlapp.data.db.datatype.DataType.BLOB,reader.getAllFull(connection).get(0).getColumns().get("value").getDataType());
			assertEquals("fts5",SqliteDdl.virtualTableModule("CREATE VIRTUAL TABLE IF NOT EXISTS main.[USING] USING fts5(body)"));
		}
	}

	@Test
	void missingDefinitionsAndGenericAlterAreRejected() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var table = new Table("docs").setSchemaName("main"); table.getSpecifics().put("sqlite.virtual","true");
		assertThrows(IllegalArgumentException.class,() -> registry.createSql(table,SqlType.CREATE));
		table.setDefinition("CREATE /* comment */ VIRTUAL TABLE docs USING fts5(body)");
		assertEquals(1,registry.createSql(table,SqlType.CREATE).size());
		var target = table.clone(); target.setName("other");
		assertThrows(IllegalArgumentException.class,() -> registry.createSql(table.diff(target),SqlType.ALTER));
		assertFalse(SqliteDdl.isVirtualTable("CREATE TABLE t(value TEXT DEFAULT 'CREATE VIRTUAL TABLE')"));
	}

	private SqliteTableReader reader() {
		return (SqliteTableReader)DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
	}
}
