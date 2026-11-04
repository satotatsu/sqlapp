/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.i;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.db2.i.Db2I75Test.*;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.SchemaUtils;

class Db2I75TypeTest {
	private static final boolean ZOS = false;
	@Test void changedModelTypeCannotBeSilentlyOverriddenByCatalogSpecifics() {
		Domain type = read("distinct");
		type.setDataType(DataType.VARCHAR);
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}
	@Test void readsTypesInOneFilteredQueryAndRecreatesDistinctAfterXml() throws Exception {
		var calls = new AtomicInteger(); var bindings = new ArrayList<Object>();
		var reader = new Db2I75().getCatalogReader().getSchemaReader().getDomainReader();
		reader.setSchemaName("App"); reader.setObjectName("Amount");
		var types = reader.getAllFull(connection(calls, bindings, "distinct"));
		assertEquals(1, calls.get()); assertEquals(List.of("App", "Amount"), bindings);
		assertEquals(2, types.size());
		Domain type = SchemaUtils.readXml(new StringReader(types.get(0).asXml()));
		assertEquals(DataType.DECIMAL, type.getDataType());
		assertEquals(12L, type.getLength()); assertEquals(0, type.getScale());
		String text = create(type);
		assertTrue(text.contains("CREATE TYPE \"App\".\"Amount\" AS DECIMAL(12,0)"), text);
		if (ZOS) assertFalse(text.contains("TYPE RULES"), text);
		else assertTrue(text.contains("WITH STRONG TYPE RULES"), text);
		assertTrue(text.contains("COMMENT ON TYPE \"App\".\"Amount\" IS 'Owner''s type  '"), text);
	}
	@Test void recreatesOrdinaryNumericArraysWithExactCardinality() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("array").asXml()));
		assertEquals(1, type.getArrayDimension()); assertEquals(17, type.getArrayDimensionUpperBound());
		assertTrue(create(type).contains("AS DECIMAL(12,0) ARRAY[17]"));
		assertFalse(create(type).contains("TYPE RULES"));
		type.setArrayDimensionUpperBound(0);
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}
	@Test void characterAndAssociativeTypesRetainAttributesWithoutGuessingDdl() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("character").asXml()));
		assertEquals("1208", type.getSpecifics().get("DB2_HOST_CCSID"));
		assertThrows(IllegalArgumentException.class, () -> create(type));
		Domain associative = read("associative");
		assertEquals("opaque-id", associative.getSpecifics().get("DB2_HOST_ARRAY_INDEX_TYPE"));
		assertThrows(IllegalArgumentException.class, () -> create(associative));
	}
	@Test void largeCardinalityIsPreservedWithoutTruncatingToInt() {
		Domain type = read("large-array");
		assertEquals("2147483648", type.getSpecifics().get("DB2_HOST_ARRAY_CARDINALITY"));
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}
	@Test void weakRulesAndPrecisionLimitsFollowTheProduct() {
		Domain type = read("weak");
		if (!ZOS) assertTrue(create(type).contains("WITH WEAK TYPE RULES"));
		type.setLength(63L).setScale(0);
		if (ZOS) assertThrows(IllegalArgumentException.class, () -> create(type));
		else assertTrue(create(type).contains("DECIMAL(63,0)"));
		type.setScale(64); assertThrows(IllegalArgumentException.class, () -> create(type));
	}
	@Test void missingFieldsAndUnknownKindsFailClearly() {
		assertThrows(RuntimeException.class, () -> read("missing"));
		assertThrows(RuntimeException.class, () -> read("unknown"));
	}
	@Test void authoritativeDefinitionRecreatesUnsupportedTypeAndDropUsesType() {
		var type = read("character");
		String ddl = "CREATE TYPE APP.CODE AS VARCHAR(10) CCSID UNICODE";
		type.setDefinition(ddl);
		assertTrue(create(type).contains(ddl));
		String drop = new Db2I75().createSqlFactoryRegistry().createSql(type, SqlType.DROP).get(0).getSqlText();
		assertEquals("DROP TYPE \"App\".\"Amount\" RESTRICT", drop);
		type.setDefinition("ALTER TYPE APP.CODE");
		assertThrows(IllegalArgumentException.class, () -> create(type));
		type.getSpecifics().remove("DB2_OBJECT_TYPE");
		assertThrows(IllegalArgumentException.class, () -> new Db2I75().createSqlFactoryRegistry().createSql(type, SqlType.DROP));
	}
	private static Domain read(String mode) {
		return new Db2I75().getCatalogReader().getSchemaReader().getDomainReader()
				.getAllFull(connection(new AtomicInteger(), new ArrayList<>(), mode)).get(0);
	}
	private static String create(Domain type) {
		return new Db2I75().createSqlFactoryRegistry().createSql(type, SqlType.CREATE).stream()
				.map(op -> op.getSqlText()).reduce("", (a,b) -> a + "\n" + b);
	}
	private static Connection connection(AtomicInteger calls, List<Object> bindings, String mode) {
		return proxy(Connection.class, (p,m,a) -> switch (m.getName()) {
		case "getMetaData" -> metadata("DB2", ZOS ? "DSN13015" : "QSQ07050");
		case "prepareStatement" -> {
			assertTrue(((String)a[0]).contains("host:domains"));
			yield proxy(PreparedStatement.class, (sp,sm,sa) -> {
				if (sm.getName().equals("setString") || sm.getName().equals("setObject")) bindings.add(sa[1]);
				if (!sm.getName().equals("executeQuery")) return fallback(sm.getReturnType());
				calls.incrementAndGet();
				var first = row("SCHEMA_NAME", "App", "DOMAIN_NAME", "Amount", "SOURCE_SCHEMA", "SYSIBM",
						"SOURCE_TYPE", mode.equals("missing") ? null : mode.equals("character") ? "VARCHAR" : "DECIMAL",
						"METATYPE", mode.equals("unknown") ? "?" : mode.equals("associative") ? "L" : mode.contains("array") ? "A" : "T",
						"TYPE_RULES", mode.equals("weak") ? "W" : "S", "NATIVE_LENGTH", 12L,
						"TYPE_LENGTH", 12L, "TYPE_SCALE", 0, "CCSID", mode.equals("character") ? "1208" : null,
						"NORMALIZE_DATA", null, "SUBTYPE", null, "ENCODING_SCHEME", null,
						"ARRAY_CARDINALITY", mode.equals("large-array") ? 2147483648L : mode.equals("array") ? 17L : 0L,
						"ARRAY_INDEX_TYPE", mode.equals("associative") ? "opaque-id" : null,
						"ARRAY_INDEX_LENGTH", null, "ARRAY_INDEX_SUBTYPE", null, "INLINE_LENGTH", null,
						"CREATED_AT", null, "REMARKS", "Owner's type  ");
				var second = new java.util.LinkedHashMap<>(first); second.put("DOMAIN_NAME", "Amount2");
				return rows(List.of(first, second));
			});
		}
		default -> fallback(m.getReturnType());
		});
	}
}
