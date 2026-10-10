/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.cockroach.resolver.CockroachDialectResolver;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.bulk.*;
class CockroachDialectTest {
	private DatabaseMetaData metadata(String name,String version) {
		return (DatabaseMetaData)Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),new Class<?>[]{DatabaseMetaData.class},(p,m,a)->switch(m.getName()) {
			case "getDatabaseProductName" -> name;
			case "getDatabaseProductVersion" -> version;
			case "getConnection" -> null;
			case "getDatabaseMajorVersion" -> 13;
			case "getDatabaseMinorVersion" -> 0;
			default -> throw new UnsupportedOperationException(m.getName());
		});
	}
	@Test void resolvesReleaseBannerWithoutPollutingPostgresCache() {
		var resolver=DialectResolver.getInstance();
		assertEquals("PostgreSQL",resolver.getDialect("PostgreSQL",13,0,null).getProductName());
		assertInstanceOf(CockroachDB.class,resolver.getDialect(metadata("PostgreSQL","CockroachDB CCL v25.4.0 (x86_64)")));
		assertEquals("PostgreSQL",resolver.getDialect(metadata("PostgreSQL","13.0")).getProductName());
		assertInstanceOf(CockroachDB.class,resolver.getDialect("CockroachDB",24,3,null));
	}
	@Test void rejectsUnsupportedReleaseAndUnidentifiedVendor() {
		var resolver=new CockroachDialectResolver();
		assertThrows(IllegalArgumentException.class,()->resolver.getDialect("CockroachDB",24,2,null));
		assertThrows(IllegalArgumentException.class,()->resolver.resolveDatabaseMetaData(metadata("CockroachDB","unknown")));
		assertNull(resolver.resolveDatabaseMetaData(metadata("Other","CockroachDB CCL v25.4.0")));
	}
	@Test void surfacesIdentificationFailure() {
		var metadata=(DatabaseMetaData)Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),new Class<?>[]{DatabaseMetaData.class},(p,m,a)->{throw new SQLException("broken");});
		assertInstanceOf(SQLException.class,assertThrows(IllegalStateException.class,()->new CockroachDialectResolver().resolveDatabaseMetaData(metadata)).getCause());
	}
	@Test void registersDedicatedBulkProviders() {
		var dialect=new CockroachDB();
		assertInstanceOf(com.sqlapp.data.db.dialect.cockroach.bulk.CockroachBulkInsertExecutor.class,BulkInsertResolver.resolve(dialect));
		assertInstanceOf(com.sqlapp.data.db.dialect.cockroach.bulk.CockroachBulkUpsertExecutor.class,BulkUpsertResolver.resolve(dialect));
		assertFalse(new com.sqlapp.data.db.dialect.postgres.bulk.PostgresBulkInsertProvider().supports(dialect));
	}
	@Test void preservesCompleteDefinitionThroughXmlWithoutDuplicatingComponents() throws Exception {
		var table=new Table("Odd Table").setSchemaName("Odd Schema");
		table.getColumns().add("id",c->c.setDataType(DataType.BIGINT));
		table.getIndexes().add("extra",table.getColumns().get("id"));
		table.setDefinition("CREATE TABLE \"Odd Schema\".\"Odd Table\" (id INT8 PRIMARY KEY USING HASH WITH BUCKET_COUNT=8)");
		Table restored=SchemaUtils.readXml(new StringReader(table.asXml()));
		var operations=new CockroachDB().createSqlFactoryRegistry().createSql(restored,SqlType.CREATE);
		assertEquals(1,operations.size()); assertTrue(operations.get(0).getSqlText().contains("BUCKET_COUNT=8"));
	}
	@Test void generatesModelDdlAndRejectsKeylessMerge() {
		var table=new Table("items"); table.getColumns().add("id",c->c.setDataType(DataType.BIGINT));
		var registry=new CockroachDB().createSqlFactoryRegistry();
		assertTrue(registry.createSql(table,SqlType.CREATE).get(0).getSqlText().contains("CREATE TABLE"));
		assertThrows(IllegalArgumentException.class,()->registry.createSql(table,SqlType.MERGE));
	}

	@Test void propagatesRetryWithoutReplayingRowsAndClosesIterator() throws Exception {
		var table=new Table("items");table.getColumns().add("id",c->c.setDataType(DataType.BIGINT));table.getConstraints().addPrimaryKeyConstraint("pk",table.getColumns().get("id"));
		var row=table.newRow();row.put("id",1L);
		var closed=new java.util.concurrent.atomic.AtomicBoolean();
		class Rows implements java.util.Iterator<Row>,AutoCloseable {
			private boolean pending=true;
			public boolean hasNext() {return pending;}
			public Row next() {if(!pending) throw new java.util.NoSuchElementException();pending=false;return row;}
			public void close() {closed.set(true);}
		}
		table.setRowIteratorHandler(rows->new Rows());
		var attempts=new java.util.concurrent.atomic.AtomicInteger();var rollbacks=new java.util.concurrent.atomic.AtomicInteger();var autoCommit=new java.util.concurrent.atomic.AtomicBoolean(true);
		var statement=(PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
			if(m.getName().equals("executeBatch")) {attempts.incrementAndGet();throw new SQLException("restart transaction","40001");}return null;
		});
		var connection=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->switch(m.getName()) {
			case "getAutoCommit" -> autoCommit.get();
			case "setAutoCommit" -> {autoCommit.set((Boolean)a[0]);yield null;}
			case "rollback" -> {rollbacks.incrementAndGet();yield null;}
			case "prepareStatement" -> statement;
			default -> throw new UnsupportedOperationException(m.getName());
		});
		assertEquals("40001",assertThrows(SQLException.class,()->BulkUpsertResolver.resolve(new CockroachDB()).execute(connection,table,BulkUpsertOption.defaults())).getSQLState());
		assertEquals(1,attempts.get());assertEquals(1,rollbacks.get());assertTrue(autoCommit.get());assertTrue(closed.get());
	}
}
