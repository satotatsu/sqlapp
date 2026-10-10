/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.cockroach;
import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.io.StringReader;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import com.sqlapp.data.db.dialect.*;
import com.sqlapp.data.db.dialect.cockroach.CockroachDB;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.bulk.*;
/** Owned disposable container only; no external credentials or host data volumes. */
class CockroachMetadataReaderTest {
	private static final GenericContainer<?> DB=new GenericContainer<>(DockerImageName.parse(System.getProperty("sqlapp.test.cockroach.image","cockroachdb/cockroach:v25.4.0")))
		.withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/cockroach/cockroach"))
		.withExposedPorts(26257).withCommand("start-single-node","--insecure","--listen-addr=0.0.0.0:26257","--store=type=mem,size=0.25","--cache=64MiB","--max-sql-memory=512MiB")
		.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
	@BeforeAll static void start() throws Exception {
		DB.start();
		try(var c=connect();var sql=c.createStatement();var rows=sql.executeQuery("SELECT version()")) {assertTrue(rows.next());System.out.println(rows.getString(1));String expected=System.getProperty("sqlapp.test.cockroach.expectedVersion");if(expected!=null) assertTrue(rows.getString(1).contains(expected));}
	}
	@AfterAll static void stop() { DB.stop(); }
	private static Connection connect() throws SQLException { return DriverManager.getConnection("jdbc:postgresql://"+DB.getHost()+":"+DB.getMappedPort(26257)+"/defaultdb?sslmode=disable&connectTimeout=5&socketTimeout=120","root",""); }
	private String schema() { return "sqlapp_crdb_"+UUID.randomUUID().toString().replace("-",""); }
	private Schema read(Connection c,String name) {
		var reader=DialectResolver.getInstance().getDialect(c).getCatalogReader().getSchemaReader(); reader.setSchemaName(name);
		return reader.getAllFull(c).stream().filter(s->name.equals(s.getName())).findFirst().orElseThrow();
	}
	private void create(Connection c,Object object) throws Exception {
		var registry=new CockroachDB().createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
		try(var sql=c.createStatement()) { for(var op:registry.createSql(object,SqlType.CREATE)) {sql.execute(op.getSqlText());} }
	}
	@Test void identifiesCockroachThroughPostgresDriver() throws Exception {
		try(var c=connect()) {assertInstanceOf(CockroachDB.class,DialectResolver.getInstance().getDialect(c));}
	}
	@Test void roundTripsSchemaConstraintsIndexesViewAndSequence() throws Exception {
		String name=schema();
		try(var c=connect();var sql=c.createStatement()) {
			sql.execute("CREATE SCHEMA "+name);
			try {
				sql.execute("CREATE SEQUENCE "+name+".seq START 10 INCREMENT 3");
				sql.execute("CREATE TABLE "+name+".parent (id INT8 PRIMARY KEY, label STRING NOT NULL UNIQUE, balance DECIMAL(20,4) DEFAULT 0, CONSTRAINT positive CHECK (balance>=0))");
				sql.execute("CREATE TABLE "+name+".child (id INT8 PRIMARY KEY DEFAULT nextval('"+name+".seq'), parent_id INT8 REFERENCES "+name+".parent(id), payload BYTES, tags STRING[], meta JSONB)");
				sql.execute("CREATE INDEX child_parent ON "+name+".child (parent_id DESC) STORING (payload) WHERE parent_id IS NOT NULL");
				sql.execute("COMMENT ON TABLE "+name+".child IS 'child note'");
				sql.execute("CREATE VIEW "+name+".labels AS SELECT id,label FROM "+name+".parent");
				var original=read(c,name); assertEquals(2,original.getTables().size());
				assertNotNull(original.getTables().get("parent").getPrimaryKeyConstraint());
				assertEquals(1,original.getTables().get("child").getConstraints().getForeignKeyConstraints().size());
				Schema restored=SchemaUtils.readXml(new StringReader(original.asXml()));
				sql.execute("DROP SCHEMA "+name+" CASCADE"); create(c,restored);
				sql.execute("INSERT INTO "+name+".parent(id,label) VALUES (1,'ok')");
				sql.execute("INSERT INTO "+name+".child(parent_id,payload,tags,meta) VALUES (1,decode('00ff','hex'),ARRAY['a','日本語'],'{\"ok\":true}')");
				try(var rows=sql.executeQuery("SELECT id FROM "+name+".child")) {assertTrue(rows.next());assertEquals(10,rows.getLong(1));}
				var actual=read(c,name); assertEquals(original.getTables().get("child").getDefinition(),actual.getTables().get("child").getDefinition());
			} finally {if(!c.isClosed()) sql.execute("DROP SCHEMA IF EXISTS "+name+" CASCADE");}
		}
	}
	@Test void preservesHashShardingComputedHiddenColumnsAndFamilies() throws Exception {
		String name=schema();
		try(var c=connect();var sql=c.createStatement()) {
			sql.execute("CREATE SCHEMA "+name);
			try {
				sql.execute("CREATE TABLE "+name+".items (id INT8 PRIMARY KEY USING HASH WITH BUCKET_COUNT=8, label STRING, upper_label STRING AS (upper(label)) STORED, FAMILY keys (id), FAMILY data (label,upper_label))");
				var original=read(c,name).getTables().get("items");
				Table restored=SchemaUtils.readXml(new StringReader(original.asXml()));
				sql.execute("DROP TABLE "+name+".items"); create(c,restored);
				assertEquals(original.getDefinition(),read(c,name).getTables().get("items").getDefinition());
				sql.execute("INSERT INTO "+name+".items(id,label) VALUES (1,'hello')");
				try(var rows=sql.executeQuery("SELECT upper_label FROM "+name+".items")) {assertTrue(rows.next());assertEquals("HELLO",rows.getString(1));}
			} finally {if(!c.isClosed()) sql.execute("DROP SCHEMA IF EXISTS "+name+" CASCADE");}
		}
	}
	@Test void copyAndUpsertRespectCallerRollbackAndFailure() throws Exception {
		String name=schema();
		try(var c=connect();var sql=c.createStatement()) {
			sql.execute("CREATE SCHEMA "+name);
			try {
				sql.execute("CREATE TABLE "+name+".items (id INT8 PRIMARY KEY,label STRING NOT NULL)");
				var table=read(c,name).getTables().get("items");
				table.getRows().add(r -> { r.put("id",1L); r.put("label","日本語\n\"quoted\""); });
				assertEquals(1,BulkInsertResolver.resolve(new CockroachDB()).execute(c,table,BulkOption.defaults()));
				table.getRows().clear();table.getRows().add(r -> { r.put("id",1L); r.put("label","updated"); });table.getRows().add(r -> { r.put("id",2L); r.put("label","new"); });
				assertEquals(2,BulkUpsertResolver.resolve(new CockroachDB()).execute(c,table,BulkUpsertOption.defaults()));
				c.setAutoCommit(false);table.getRows().clear();table.getRows().add(r -> { r.put("id",3L); r.put("label","rollback"); });
				assertEquals(1,BulkInsertResolver.resolve(new CockroachDB()).execute(c,table,BulkOption.defaults()));c.rollback();c.setAutoCommit(true);
				try(var rows=sql.executeQuery("SELECT count(*) FROM "+name+".items")) {assertTrue(rows.next());assertEquals(2,rows.getInt(1));}
			} finally {if(!c.getAutoCommit()) {c.rollback();c.setAutoCommit(true);} sql.execute("DROP SCHEMA IF EXISTS "+name+" CASCADE");}
		}
	}

	@FunctionalInterface private interface Work { void run(Connection c,Statement sql,String schema) throws Exception; }
	private void withSchema(Work work) throws Exception {
		String name=schema();
		try(var c=connect();var sql=c.createStatement()) {
			sql.execute("CREATE SCHEMA "+name);
			try {work.run(c,sql,name);} finally {
				if(!c.isClosed()) {if(!c.getAutoCommit()) {c.rollback();c.setAutoCommit(true);} sql.execute("DROP SCHEMA IF EXISTS "+name+" CASCADE");}
			}
		}
	}
	private int count(Statement sql,String table) throws SQLException {
		try(var rows=sql.executeQuery("SELECT count(*) FROM "+table)) {assertTrue(rows.next());return rows.getInt(1);}
	}
	@Test void recreatesStandaloneIndexWithStoringAndPredicate() throws Exception {
		withSchema((c,sql,name)->{
			sql.execute("CREATE TABLE "+name+".items (id INT8 PRIMARY KEY,label STRING,payload BYTES, INDEX by_label (label DESC) STORING (payload) WHERE label IS NOT NULL)");
			var table=read(c,name).getTables().get("items");
			var index=table.getIndexes().get("by_label");assertNotNull(index);
			Index restored=SchemaUtils.readXml(new StringReader(index.asXml()));
			table.getIndexes().clear();table.getIndexes().add(restored);
			sql.execute("DROP INDEX "+name+".items@by_label");create(c,restored);
			assertEquals(index.getDefinition(),read(c,name).getTables().get("items").getIndexes().get("by_label").getDefinition());
		});
	}
	@Test void recreatesModeledTableWithoutDuplicatingConstraintIndexes() throws Exception {
		withSchema((c,sql,name)->{
			sql.execute("CREATE TABLE "+name+".items (id INT8 PRIMARY KEY,label STRING NOT NULL UNIQUE)");
			var table=read(c,name).getTables().get("items");table.setDefinition((String)null);
			sql.execute("DROP TABLE "+name+".items");create(c,table);
			sql.execute("INSERT INTO "+name+".items VALUES (1,'ok')");
			assertEquals("23505",assertThrows(SQLException.class,()->sql.execute("INSERT INTO "+name+".items VALUES (2,'ok')")).getSQLState());
			assertNotNull(read(c,name).getTables().get("items").getPrimaryKeyConstraint());
		});
	}

	@Test void preservesEnumsAndQuotedIdentifiers() throws Exception {
		withSchema((c,sql,name)->{
			sql.execute("CREATE TYPE "+name+".mood AS ENUM ('ok','日本語','a''b')");
			sql.execute("CREATE TABLE "+name+".\"Mixed Case\" (\"select\" INT8 PRIMARY KEY, state "+name+".mood NOT NULL)");
			var model=read(c,name);assertEquals(1,model.getTypes().size());
			Schema restored=SchemaUtils.readXml(new StringReader(model.asXml()));
			sql.execute("DROP SCHEMA "+name+" CASCADE");create(c,restored);
			sql.execute("INSERT INTO "+name+".\"Mixed Case\" VALUES (1,'日本語')");
			assertEquals(1,count(sql,name+".\"Mixed Case\""));
		});
	}
	@Test void recreatesOverloadedSqlAndPlpgsqlFunctions() throws Exception {
		withSchema((c,sql,name)->{
			sql.execute("CREATE FUNCTION "+name+".inc(v INT8) RETURNS INT8 LANGUAGE SQL IMMUTABLE AS $$SELECT v+1$$");
			sql.execute("CREATE FUNCTION "+name+".inc(v STRING) RETURNS STRING LANGUAGE SQL AS $$SELECT v || '!'$$");
			sql.execute("CREATE FUNCTION "+name+".twice(v INT8 DEFAULT 3) RETURNS INT8 LANGUAGE PLpgSQL AS $$BEGIN RETURN v*2; END$$");
			var model=read(c,name);assertEquals(3,model.getFunctions().size());
			Schema restored=SchemaUtils.readXml(new StringReader(model.asXml()));sql.execute("DROP SCHEMA "+name+" CASCADE");create(c,restored);
			assertEquals(model.getFunctions().stream().map(Function::getSpecificName).sorted().toList(),read(c,name).getFunctions().stream().map(Function::getSpecificName).sorted().toList());
			try(var rows=sql.executeQuery("SELECT "+name+".inc(5::INT8),"+name+".inc('a'::STRING),"+name+".twice()")) {assertTrue(rows.next());assertEquals(6,rows.getInt(1));assertEquals("a!",rows.getString(2));assertEquals(6,rows.getInt(3));}
		});
	}
	@Test void copiesTypedUnicodeBinaryJsonArraysAndNumbers() throws Exception {
		withSchema((c,sql,name)->{
			sql.execute("CREATE TABLE "+name+".items (id UUID PRIMARY KEY, label STRING, payload BYTES, amount DECIMAL(20,4), meta JSONB, tags STRING[])");
			var table=read(c,name).getTables().get("items");var id=UUID.randomUUID();
			table.getRows().add(r->{r.put("id",id);r.put("label","日本語\n\"quoted\"");r.put("payload",new byte[]{0,(byte)255});r.put("amount",new java.math.BigDecimal("123.4500"));r.put("meta","{\"ok\":true}");r.put("tags",new String[]{"a","日本語"});});
			assertEquals(1,BulkInsertResolver.resolve(new CockroachDB()).execute(c,table,BulkOption.defaults()));
			assertEquals(1,BulkUpsertResolver.resolve(new CockroachDB()).execute(c,table,BulkUpsertOption.defaults()));
			try(var rows=sql.executeQuery("SELECT id,label,payload,amount,meta->>'ok',tags FROM "+name+".items")) {
				assertTrue(rows.next());assertEquals(id,rows.getObject(1));assertEquals("日本語\n\"quoted\"",rows.getString(2));assertArrayEquals(new byte[]{0,(byte)255},rows.getBytes(3));assertEquals(0,new java.math.BigDecimal("123.4500").compareTo(rows.getBigDecimal(4)));assertEquals("true",rows.getString(5));assertArrayEquals(new String[]{"a","日本語"},(Object[])rows.getArray(6).getArray());
			}
		});
	}
	@Test void rollsBackFailedCopyAndRestoresAutoCommit() throws Exception {
		withSchema((c,sql,name)->{
			sql.execute("CREATE TABLE "+name+".items (id INT8 PRIMARY KEY,label STRING NOT NULL)");
			var table=read(c,name).getTables().get("items");
			table.getRows().add(r->{r.put("id",1L);r.put("label","ok");});table.getRows().add(r->{r.put("id",1L);r.put("label","duplicate");});
			assertEquals("23505",assertThrows(SQLException.class,()->BulkInsertResolver.resolve(new CockroachDB()).execute(c,table,BulkOption.defaults())).getSQLState());
			assertTrue(c.getAutoCommit());assertEquals(0,count(sql,name+".items"));
		});
	}
	@Test void upsertHonorsActionsDuplicatesAndCallerRollback() throws Exception {
		withSchema((c,sql,name)->{
			sql.execute("CREATE TABLE "+name+".items (id INT8 PRIMARY KEY,label STRING)");sql.execute("INSERT INTO "+name+".items VALUES (1,'initial')");
			var table=read(c,name).getTables().get("items");var executor=BulkUpsertResolver.resolve(new CockroachDB());
			table.getRows().add(r->{r.put("id",1L);r.put("label","updated");});table.getRows().add(r->{r.put("id",2L);r.put("label","new");});
			assertEquals(1,executor.execute(c,table,BulkUpsertOption.builder().insertWhenNotMatched(false).build()));assertEquals(1,count(sql,name+".items"));
			assertEquals(1,executor.execute(c,table,BulkUpsertOption.builder().updateWhenMatched(false).build()));assertEquals(2,count(sql,name+".items"));
			c.setAutoCommit(false);table.getRows().clear();table.getRows().add(r->{r.put("id",3L);r.put("label","rollback");});executor.execute(c,table,BulkUpsertOption.defaults());c.rollback();c.setAutoCommit(true);assertEquals(2,count(sql,name+".items"));
			table.getRows().clear();table.getRows().add(r->{r.put("id",4L);r.put("label","first");});table.getRows().add(r->{r.put("id",4L);r.put("label","second");});
			assertThrows(IllegalArgumentException.class,()->executor.execute(c,table,BulkUpsertOption.defaults()));assertEquals(2,count(sql,name+".items"));
			assertEquals(1,executor.execute(c,table,BulkUpsertOption.builder().duplicateKeyStrategy(BulkUpsertDuplicateKeyStrategy.KEEP_FIRST).build()));
		});
	}
	@Test void upsertsKeyOnlyTablesWithoutEmptyUpdates() throws Exception {
		withSchema((c,sql,name)->{
			sql.execute("CREATE TABLE "+name+".items (id INT8 PRIMARY KEY)");var table=read(c,name).getTables().get("items");table.getRows().add(r->r.put("id",1L));
			var executor=BulkUpsertResolver.resolve(new CockroachDB());assertEquals(1,executor.execute(c,table,BulkUpsertOption.defaults()));assertEquals(0,executor.execute(c,table,BulkUpsertOption.defaults()));
		});
	}
	@Test void preservesHiddenImplicitKeyAndRejectsUnwritableUpsertKey() throws Exception {
		withSchema((c,sql,name)->{
			sql.execute("CREATE TABLE "+name+".items (label STRING)");var table=read(c,name).getTables().get("items");
			assertTrue(table.getColumns().get("rowid").isHidden());table.getRows().add(r->r.put("label","ok"));
			assertEquals(1,BulkInsertResolver.resolve(new CockroachDB()).execute(c,table,BulkOption.defaults()));
			assertThrows(IllegalArgumentException.class,()->BulkUpsertResolver.resolve(new CockroachDB()).execute(c,table,BulkUpsertOption.defaults()));
		});
	}
	@Test void surfacesSerializationFailureForCallerRetry() throws Exception {
		withSchema((c,sql,name)->{
			c.setAutoCommit(false);sql.execute("SELECT 1");
			var failure=assertThrows(SQLException.class,()->sql.execute("SELECT crdb_internal.force_retry('1h'::INTERVAL)"));
			assertEquals("40001",failure.getSQLState());c.rollback();c.setAutoCommit(true);
		});
	}
	@Test void migratesWithAtomicDatabaseCheckpointAndResume() throws Exception {
		withSchema((c,sql,name)->{
			sql.execute("CREATE TABLE "+name+".items (code STRING PRIMARY KEY,label STRING)");var table=read(c,name).getTables().get("items");
			com.sqlapp.data.db.dialect.test.BulkMigrationTransactionAssertions.assertDatabaseCheckpointAtomic(c,table,"code","label","SELECT count(*) FROM "+name+".items");
			sql.execute("TRUNCATE "+name+".items");com.sqlapp.data.db.dialect.test.BulkMigrationTransactionAssertions.assertDatabaseCheckpointInsertAtomic(c,table,"code","label","SELECT count(*) FROM "+name+".items");
		});
	}
	@Test void sustainsChunkedUnicodeBinaryAndDuplicateLoad() throws Exception {
		withSchema((c,sql,name)->{
			sql.execute("CREATE TABLE "+name+".items (code STRING PRIMARY KEY,content STRING,payload BYTES)");
			var table=com.sqlapp.data.db.dialect.test.BulkMigrationLoadAssertions.table(name,"items","code","content","payload",com.sqlapp.data.db.datatype.DataType.LONGVARCHAR,com.sqlapp.data.db.datatype.DataType.VARBINARY);
			com.sqlapp.data.db.dialect.test.BulkMigrationLoadAssertions.assertChunkedLobAndDuplicateLoad(c,table,"code","content","payload","SELECT count(*) FROM "+name+".items","SELECT content, payload FROM "+name+".items WHERE code=?");
		});
	}
}
