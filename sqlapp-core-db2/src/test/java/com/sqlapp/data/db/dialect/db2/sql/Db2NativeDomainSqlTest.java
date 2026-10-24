/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class Db2NativeDomainSqlTest {
	private Domain type(String kind) {
		var type = new Domain("D.Name").setSchemaName("A.Schema");
		type.getSpecifics().putAll(Map.of("DB2_OBJECT_TYPE", "DATA_TYPE", "METATYPE", kind, "SOURCESCHEMA", "SYSIBM", "SOURCENAME", "INTEGER", "TYPERULES", "S"));
		return type;
	}
	private List<String> sql(Domain type, SqlType operation) {
		SqlFactory<Domain> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(type, operation);
		return factory.createSql(type).stream().map(SqlOperation::getSqlText).toList();
	}
	@Test void strongAndWeakDistinctTypesRetainConstraintsAndCommentsAfterXml() throws Exception {
		var type = type("T");
		assertEquals(List.of("CREATE TYPE \"A.Schema\".\"D.Name\" AS INTEGER WITH STRONG TYPE RULES"), sql(type, SqlType.CREATE));
		type.getSpecifics().put("TYPERULES", "W"); type.setNotNull(true); type.setCheck("VALUE >= 0"); type.setRemarks("type's comment");
		Domain restored = SchemaUtils.readXml(new java.io.StringReader(type.asXml()));
		assertEquals(type, restored); assertEquals(type, type.clone());
		assertEquals("CREATE TYPE \"A.Schema\".\"D.Name\" AS INTEGER WITH WEAK TYPE RULES NOT NULL CHECK (VALUE >= 0)", sql(restored, SqlType.CREATE).getFirst());
		assertEquals("COMMENT ON TYPE \"A.Schema\".\"D.Name\" IS 'type''s comment'", sql(restored, SqlType.CREATE).getLast());
		assertEquals(List.of("DROP TYPE \"A.Schema\".\"D.Name\""), sql(type, SqlType.DROP));
	}
	@Test void ordinaryAndAssociativeArraysKeepCardinalitySeparateFromIndexType() {
		var type = type("A"); type.setArrayDimension(1); type.setArrayDimensionUpperBound(50);
		type.getSpecifics().put("SOURCENAME", "DECIMAL"); type.setLength(10L); type.setScale(0);
		assertEquals("CREATE TYPE \"A.Schema\".\"D.Name\" AS DECIMAL(10,0) ARRAY[50]", sql(type, SqlType.CREATE).getFirst());
		type.getSpecifics().putAll(Map.of("METATYPE", "L", "ARRAYINDEXTYPESCHEMA", "SYSIBM", "ARRAYINDEXTYPENAME", "INTEGER")); type.setArrayDimensionUpperBound(0);
		assertTrue(sql(type, SqlType.CREATE).getFirst().endsWith("ARRAY[INTEGER]"));
		type.getSpecifics().put("ARRAYINDEXTYPENAME", "VARCHAR"); assertThrows(IllegalArgumentException.class, () -> sql(type, SqlType.CREATE));
	}
	@Test void unsupportedComponentsNeedFullDdlAndCannotBecomeCreateDomain() {
		var type = type("T"); type.getSpecifics().put("SOURCENAME", "VARCHAR");
		assertThrows(IllegalArgumentException.class, () -> sql(type, SqlType.CREATE));
		String ddl = "CREATE TYPE \"A.Schema\".\"D.Name\" AS VARCHAR(5 CODEUNITS32)"; type.getDefinition().add(ddl);
		assertEquals(List.of(ddl), sql(type, SqlType.CREATE));
		SqlFactory<Domain> old = DialectHolder.Db2_1050Dialect.createSqlFactoryRegistry().getSqlFactory(type, SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> old.createSql(type));
	}
	@Test void invalidPrecisionRulesAndDestructiveDiffsFailClearly() {
		var type = type("T"); type.getSpecifics().put("SOURCENAME", "DECIMAL"); type.setLength(10L); type.setScale(11);
		assertThrows(IllegalArgumentException.class, () -> sql(type, SqlType.CREATE)); type.setScale(0);
		type.setNotNull(true); assertThrows(IllegalArgumentException.class, () -> sql(type, SqlType.CREATE)); type.setNotNull(false);
		var target = type.clone(); target.setRemarks("modified");
		SqlFactory<Domain> alter = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(type, SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(type.diff(target)));
		assertTrue(alter.createDiffSql(type.diff(type.clone())).isEmpty());
	}
	@Test void catalogCreatesTypesBeforeTablesUsingThem() {
		var catalog = new Catalog(); var schema = new Schema("A.Schema"); catalog.getSchemas().add(schema); schema.getDomains().add(type("T"));
		var table = new Table("T"); table.getColumns().add("V", c -> c.setDataTypeName("\"A.Schema\".\"D.Name\"")); schema.getTables().add(table);
		SqlFactory<Catalog> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(catalog, SqlType.CREATE);
		var sql = factory.createSql(catalog).stream().map(SqlOperation::getSqlText).toList();
		int typeIndex = java.util.stream.IntStream.range(0, sql.size()).filter(i -> sql.get(i).startsWith("CREATE TYPE")).findFirst().orElseThrow();
		int tableIndex = java.util.stream.IntStream.range(0, sql.size()).filter(i -> sql.get(i).startsWith("CREATE TABLE")).findFirst().orElseThrow(); assertTrue(typeIndex < tableIndex, sql.toString());
	}
}
