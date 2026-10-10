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
	@Test void generatesSeparateColumnActionsAndRejectsUnmodeledChanges() {
		var registry=new CockroachDB().createSqlFactoryRegistry();registry.getOptions().setDecorateSchemaName(true);
		var table=new Table("items").setSchemaName("s");table.getColumns().add("label",c->c.setDataType(DataType.VARCHAR));
		var target=table.clone();target.getColumns().get("label").setDefaultValue("'x'").setNotNull(true);
		var sql=registry.createSql(table.diff(target)).stream().map(op->op.getSqlText()).toList();
		assertEquals(2,sql.size());assertTrue(sql.get(0).contains("SET DEFAULT"));assertTrue(sql.get(1).contains("COLUMN label SET NOT NULL"));
		assertThrows(UnsupportedOperationException.class,()->registry.createSql(table.diff(table.clone().setDefinition("CREATE TABLE s.items (label STRING) LOCALITY REGIONAL BY ROW"))));
		var generated=table.clone();generated.getColumns().get("label").setFormula("upper(label)");
		assertThrows(UnsupportedOperationException.class,()->registry.createSql(table.diff(generated)));
	}
	@Test void enumChangesPreserveOrderAndRejectRemoval() {
		var registry=new CockroachDB().createSqlFactoryRegistry();registry.getOptions().setDecorateSchemaName(true);
		var before=new com.sqlapp.data.schemas.Type("mood").setSchemaName("s").setDefinition("CREATE TYPE s.mood AS ENUM ('ok','done')");
		var after=before.clone().setDefinition("CREATE TYPE s.mood AS ENUM ('ok','a''b','done','日本語')");
		var sql=registry.createSql(before.diff(after)).stream().map(op->op.getSqlText()).toList();
		assertEquals(2,sql.size());assertTrue(sql.get(0).contains("'a''b' BEFORE 'done'"));
		assertThrows(UnsupportedOperationException.class,()->registry.createSql(before.diff(before.clone().setDefinition("CREATE TYPE s.mood AS ENUM ('ok')"))));
		assertThrows(UnsupportedOperationException.class,()->registry.createSql(before.diff(before.clone().setDefinition("CREATE TYPE s.mood AS ENUM ('done','ok')"))));
	}
	@Test void indexDropRequiresTableAndSequenceAlterNeverRestarts() {
		var registry=new CockroachDB().createSqlFactoryRegistry();registry.getOptions().setDecorateSchemaName(true);
		var table=new Table("Mixed Table").setSchemaName("s");table.getColumns().add("id",c->c.setDataType(DataType.BIGINT));table.getIndexes().add("by_id",table.getColumns().get("id"));
		assertTrue(registry.createSql(table.getIndexes().get("by_id"),SqlType.DROP).get(0).getSqlText().contains("s.\"Mixed Table\"@by_id"));
		assertThrows(IllegalArgumentException.class,()->registry.createSql(new Index("x"),SqlType.DROP));
		var sequence=new Sequence("seq").setSchemaName("s").setIncrementBy(java.math.BigInteger.ONE);
		var target=sequence.clone().setIncrementBy(java.math.BigInteger.valueOf(3));
		assertTrue(registry.createSql(sequence.diff(target)).get(0).getSqlText().contains("INCREMENT BY 3"));
		assertThrows(UnsupportedOperationException.class,()->registry.createSql(sequence.diff(sequence.clone().setLastValue(java.math.BigInteger.TEN))));
		assertThrows(UnsupportedOperationException.class,()->registry.createSql(sequence.diff(sequence.clone().setCycle(true))));
	}
	@Test void functionReplacementRetainsIdentityWithoutDrop() {
		var registry=new CockroachDB().createSqlFactoryRegistry();
		var function=new Function("inc").setSpecificName("inc(v INT8)").setSchemaName("s").setDefinition("CREATE FUNCTION s.inc(v INT8) RETURNS INT8 LANGUAGE SQL AS $$SELECT v+1$$");
		var target=function.clone().setDefinition("CREATE FUNCTION s.inc(v INT8) RETURNS INT8 LANGUAGE SQL AS $$SELECT v+2$$");
		assertTrue(registry.createSql(function.diff(target)).get(0).getSqlText().startsWith("CREATE OR REPLACE FUNCTION"));
		assertThrows(UnsupportedOperationException.class,()->registry.createSql(function.diff(function.clone().setStatement("SELECT 42"))));
	}

	@Test void rejectsStaleIndexDefinitionAndMalformedEnumRatherThanIgnoringChanges() {
		var registry=new CockroachDB().createSqlFactoryRegistry();
		var table=new Table("items");table.getColumns().add("id",c->c.setDataType(DataType.BIGINT));
		var index=table.getIndexes().add("by_id",table.getColumns().get("id")).setDefinition("CREATE INDEX by_id ON items(id)");
		var target=index.clone().setWhere("id>0");table.getIndexes().remove("by_id");table.getIndexes().add(target);
		assertThrows(UnsupportedOperationException.class,()->registry.createSql(index.diff(target)));
		var type=new com.sqlapp.data.schemas.Type("mood").setDefinition("CREATE TYPE mood AS ENUM ('ok')");
		assertThrows(UnsupportedOperationException.class,()->registry.createSql(type.diff(type.clone().setDefinition("CREATE TYPE mood AS ENUM ('ok','new',)"))));
	}

	@Test void rejectsSchemaRenameThatWouldDropTheOriginalTable() {
		var registry=new CockroachDB().createSqlFactoryRegistry();
		var schema=new Schema("s");schema.setDialect(new CockroachDB());schema.getTables().add(new Table("old_name").setId("123"));
		schema.getTables().get("old_name").getColumns().add("id",c->c.setDataType(DataType.BIGINT));
		var target=schema.clone();target.getTables().get("old_name").setName("new_name");
		assertThrows(UnsupportedOperationException.class,()->registry.createSql(schema.diff(target)));
		assertTrue(registry.createSql(schema.getTables().get("old_name").diff(target.getTables().get("new_name"))).get(0).getSqlText().contains("RENAME TO"));
	}
	@Test void localitySpecificsOverrideOnlyTheOuterClause() {
		var registry=new CockroachDB().createSqlFactoryRegistry();
		var table=new Table("items").setDefinition("CREATE TABLE items (id INT8, note STRING DEFAULT 'LOCALITY GLOBAL', \"LOCALITY\" STRING) LOCALITY GLOBAL; COMMENT ON TABLE items IS 'LOCALITY REGIONAL BY ROW'");
		table.getSpecifics().put("COCKROACH_LOCALITY","REGIONAL BY TABLE IN \"us-east\"");
		String sql=registry.createSql(table,SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("DEFAULT 'LOCALITY GLOBAL'"));assertTrue(sql.contains("LOCALITY REGIONAL BY TABLE IN \"us-east\"; COMMENT"));assertTrue(sql.endsWith("'LOCALITY REGIONAL BY ROW'"));
		assertNull(com.sqlapp.data.db.dialect.cockroach.util.CockroachPlacement.readLocality("CREATE TABLE locality (id INT8 DEFAULT 1)"));
		assertEquals("GLOBAL",com.sqlapp.data.db.dialect.cockroach.util.CockroachPlacement.readLocality("CREATE TABLE t (note STRING DEFAULT $$LOCALITY REGIONAL BY ROW$$) LOCALITY GLOBAL; COMMENT ON TABLE t IS 'x'"));
		table.getSpecifics().put("COCKROACH_LOCALITY","GLOBAL; DROP TABLE items");
		assertThrows(IllegalArgumentException.class,()->registry.createSql(table,SqlType.CREATE));
	}
	@Test void generatesModeledLocalityAndQuotesPlacementNames() {
		var registry=new CockroachDB().createSqlFactoryRegistry();
		var table=new Table("items");table.getColumns().add("id",c->c.setDataType(DataType.BIGINT));
		table.getSpecifics().put("COCKROACH_LOCALITY","REGIONAL BY ROW AS \"Region Column\"");
		assertTrue(registry.createSql(table,SqlType.CREATE).get(0).getSqlText().contains("LOCALITY REGIONAL BY ROW AS \"Region Column\""));
		var catalog=new Catalog("Target\"DB");catalog.getSpecifics().put("COCKROACH_PRIMARY_REGION","east\"1");
		catalog.getSpecifics().put("COCKROACH_REGIONS","[\"east\\\"1\",\"west-2\"]");catalog.getSpecifics().put("COCKROACH_SECONDARY_REGION","west-2");catalog.getSpecifics().put("COCKROACH_SURVIVAL_GOAL","REGION");
		var sql=registry.createSql(catalog,SqlType.CREATE).stream().map(op->op.getSqlText()).toList();
		assertEquals(4,sql.size());assertEquals("ALTER DATABASE \"Target\"\"DB\" PRIMARY REGION \"east\"\"1\"",sql.get(0));
		assertTrue(sql.get(1).contains("ADD REGION IF NOT EXISTS \"west-2\""));assertTrue(sql.get(2).contains("SECONDARY REGION"));assertTrue(sql.get(3).endsWith("SURVIVE REGION FAILURE"));
	}
	@Test void rejectsIncompleteAndInvalidDatabasePlacement() {
		var registry=new CockroachDB().createSqlFactoryRegistry();var catalog=new Catalog("db");
		catalog.getSpecifics().put("COCKROACH_REGIONS","[\"east\"]");assertThrows(IllegalArgumentException.class,()->registry.createSql(catalog,SqlType.CREATE));
		catalog.getSpecifics().put("COCKROACH_PRIMARY_REGION","east");
		for(String regions:java.util.List.of("[]","[1]","[\"west\"]","[\"east\",\"east\"]")) {
			catalog.getSpecifics().put("COCKROACH_REGIONS",regions);assertThrows(IllegalArgumentException.class,()->registry.createSql(catalog,SqlType.CREATE));
		}
		catalog.getSpecifics().put("COCKROACH_REGIONS","[\"east\"]");catalog.getSpecifics().put("COCKROACH_SURVIVAL_GOAL","NONE");
		assertThrows(IllegalArgumentException.class,()->registry.createSql(catalog,SqlType.CREATE));
	}
}
