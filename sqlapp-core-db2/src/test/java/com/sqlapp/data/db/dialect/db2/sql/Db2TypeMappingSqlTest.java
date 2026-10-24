/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.db.dialect.db2.Db2TypeMappings.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class Db2TypeMappingSqlTest {
	private Mapping date(String name) { return new Mapping(name, Direction.FORWARD, Map.of("TYPENAME", "DATE", "REMOTE_TYPENAME", "DATE", "SERVERNAME", "S.Name"), null, null); }
	private Catalog catalog(Mapping... mappings) { var catalog = new Catalog(); Db2TypeMappings.set(catalog, List.of(mappings)); return catalog; }
	private List<String> sql(Mapping mapping) { return Db2TypeMappingSql.create(catalog(mapping), DialectHolder.Db2_1110Dialect); }
	@Test void forwardAndReverseMappingsRetainNativeDirectionScopesAndQuoting() {
		assertEquals(List.of("CREATE TYPE MAPPING \"M\" FROM LOCAL TYPE DATE TO SERVER \"S.Name\" REMOTE TYPE DATE"), sql(date("M")));
		var mapping = new Mapping("M\"A", Direction.REVERSE, Map.of("TYPENAME", "INTEGER", "REMOTE_TYPENAME", "INT", "SERVERTYPE", "ORACLE", "SERVERVERSION", "8i", "WRAPNAME", "W"), "mapping's comment", null);
		assertEquals("CREATE TYPE MAPPING \"M\"\"A\" TO LOCAL TYPE INTEGER FROM SERVER TYPE \"ORACLE\" VERSION '8i' WRAPPER \"W\" REMOTE TYPE INT", sql(mapping).getFirst());
		assertEquals("COMMENT ON TYPE MAPPING \"M\"\"A\" IS 'mapping''s comment'", sql(mapping).getLast());
	}
	@Test void decimalPrecisionAndForwardRemoteRangesAreRecreated() {
		var attributes = new HashMap<String, String>(Map.of("TYPENAME", "DECIMAL", "LENGTH", "10", "SCALE", "2", "SERVERNAME", "S", "REMOTE_TYPENAME", "NUMBER", "REMOTE_LOWER_LEN", "10", "REMOTE_UPPER_LEN", "38", "REMOTE_LOWER_SCALE", "2", "REMOTE_UPPER_SCALE", "2"));
		var mapping = new Mapping("D", Direction.FORWARD, attributes, null, null);
		assertEquals("CREATE TYPE MAPPING \"D\" FROM LOCAL TYPE DECIMAL(10,2) TO SERVER \"S\" REMOTE TYPE NUMBER([10..38],2)", sql(mapping).getFirst());
		assertThrows(IllegalArgumentException.class, () -> sql(new Mapping("D", Direction.REVERSE, attributes, null, null)));
		attributes.put("REMOTE_UPPER_LEN", "10");
		assertTrue(sql(new Mapping("D", Direction.REVERSE, attributes, null, null)).getFirst().endsWith("REMOTE TYPE NUMBER(10,2)"));
		attributes.put("SCALE", "11");
		assertThrows(IllegalArgumentException.class, () -> sql(new Mapping("D", Direction.FORWARD, attributes, null, null)));
	}
	@Test void unsupportedUnitsConditionsAndTypesRequireCompleteDefinitionsAndOldDialectsReject() {
		for (String key : List.of("S_OPR_P", "REMOTE_S_OPR_P", "TYPESCHEMA", "REMOTE_TYPESCHEMA", "BIT_DATA", "REMOTE_META_TYPE")) {
			var a = new HashMap<>(date("M").attributes()); a.put(key, "unsupported");
			assertThrows(IllegalArgumentException.class, () -> sql(new Mapping("M", Direction.FORWARD, a, null, null)));
		}
		var mapping = new Mapping("M", Direction.FORWARD, Map.of("TYPENAME", "VARCHAR", "REMOTE_TYPENAME", "CHAR"), null, null);
		assertThrows(IllegalArgumentException.class, () -> sql(mapping));
		String ddl = "CREATE TYPE MAPPING M FROM LOCAL TYPE SYSIBM.VARCHAR() TO SERVER S REMOTE TYPE CHAR()";
		assertEquals(List.of(ddl), sql(mapping.withDefinition(ddl)));
		assertThrows(IllegalArgumentException.class, () -> Db2TypeMappingSql.create(catalog(mapping.withDefinition(ddl)), DialectHolder.Db2_1050Dialect));
	}
	@Test void precedenceAndAppendOnlyCatalogDiffRetainSingleNativeOperations() {
		var before = catalog(date("Z")); var after = catalog(date("Z"), date("A"));
		assertTrue(Db2TypeMappingSql.diff(before, before.clone(), DialectHolder.Db2_1110Dialect).isEmpty());
		assertEquals(1, Db2TypeMappingSql.diff(before, after, DialectHolder.Db2_1110Dialect).size());
		assertThrows(IllegalArgumentException.class, () -> Db2TypeMappingSql.diff(after, before, DialectHolder.Db2_1110Dialect));
		assertThrows(IllegalArgumentException.class, () -> Db2TypeMappingSql.diff(after, catalog(date("A"), date("Z")), DialectHolder.Db2_1110Dialect));
		Db2TypeMappings.set(after, new Snapshot(1, Db2TypeMappings.get(after).mappings(), false));
		assertThrows(IllegalArgumentException.class, () -> Db2TypeMappingSql.create(after, DialectHolder.Db2_1110Dialect));
		Db2TypeMappings.set(after, Db2TypeMappings.get(after).mappings());
		SqlFactory<Catalog> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		assertEquals(1, factory.createDiffSql(before.diff(after)).stream().filter(o -> o.getSqlText().startsWith("CREATE TYPE MAPPING")).count());
	}
	@Test void catalogCreatesTypeMappingsBeforeTables() {
		var catalog = catalog(date("M")); var schema = new Schema("APP"); catalog.getSchemas().add(schema);
		var table = new Table("T"); table.getColumns().add("ID", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT)); schema.getTables().add(table);
		SqlFactory<Catalog> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(catalog, SqlType.CREATE);
		var sql = factory.createSql(catalog).stream().map(SqlOperation::getSqlText).toList();
		assertTrue(sql.indexOf(sql(date("M")).getFirst()) < java.util.stream.IntStream.range(0, sql.size()).filter(i -> sql.get(i).startsWith("CREATE TABLE")).findFirst().orElseThrow(), sql.toString());
	}
}
