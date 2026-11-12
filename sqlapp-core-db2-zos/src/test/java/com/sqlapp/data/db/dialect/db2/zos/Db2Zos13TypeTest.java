/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.zos;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.db2.zos.Db2Zos13Test.*;
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

class Db2Zos13TypeTest {
	private static final boolean ZOS = true;

	@Test
	void preservesEditedLobSizesAndRejectsIgnoredScale() throws Exception {
		for (String source : List.of("blob", "clob")) {
			Domain type = read(source).setLength(9L);
			Domain restored = SchemaUtils.readXml(new StringReader(type.asXml()));
			assertEquals(9L, restored.getLength());
			assertTrue(create(restored).contains("(9)"));
			restored.setScale(1);
			assertThrows(IllegalArgumentException.class, () -> create(restored));
		}
	}

	@Test
	void recreatesBlobDistinctAfterXmlAndValidatesInlineLength() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("blob").asXml()));
		assertEquals(DataType.BLOB, type.getDataType());
		assertTrue(create(type).contains("AS BLOB(12) INLINE LENGTH 8"));
		for (String inline : List.of("0", "12")) {
			type.getSpecifics().put("DB2_HOST_INLINE_LENGTH", inline);
			assertTrue(create(type).contains("INLINE LENGTH " + inline));
		}
		type.getSpecifics().put("DB2_HOST_INLINE_LENGTH", "-1");
		assertFalse(create(type).contains("INLINE LENGTH"));
		for (String inline : List.of("13", "32681", "-2", "invalid")) {
			type.getSpecifics().put("DB2_HOST_INLINE_LENGTH", inline);
			assertThrows(IllegalArgumentException.class, () -> create(type));
		}
		type.getSpecifics().remove("DB2_HOST_INLINE_LENGTH");
		assertThrows(IllegalArgumentException.class, () -> create(type));
		type.getSpecifics().put("DB2_HOST_INLINE_LENGTH", "0");
		for (long length : new long[] { 0L, 2147483648L }) {
			type.setLength(length);
			assertThrows(IllegalArgumentException.class, () -> create(type));
		}
		type.setLength(2147483647L);
		assertTrue(create(type).contains("BLOB(2147483647)"));
	}

	@Test
	void changedModelTypeCannotBeSilentlyOverriddenByCatalogSpecifics() {
		Domain type = read("distinct");
		type.setDataType(DataType.VARCHAR);
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}

	@Test
	void readsTypesInOneFilteredQueryAndRecreatesDistinctAfterXml() throws Exception {
		var calls = new AtomicInteger();
		var bindings = new ArrayList<Object>();
		var reader = new Db2Zos13().getCatalogReader().getSchemaReader().getDomainReader();
		reader.setSchemaName("App");
		reader.setObjectName("Amount");
		var types = reader.getAllFull(connection(calls, bindings, "distinct"));
		assertEquals(1, calls.get());
		assertEquals(List.of("App", "Amount"), bindings);
		assertEquals(2, types.size());
		Domain type = SchemaUtils.readXml(new StringReader(types.get(0).asXml()));
		assertEquals(DataType.DECIMAL, type.getDataType());
		assertEquals(12L, type.getLength());
		assertEquals(0, type.getScale());
		String text = create(type);
		assertTrue(text.contains("CREATE TYPE \"App\".\"Amount\" AS DECIMAL(12,0)"), text);
		if (ZOS)
			assertFalse(text.contains("TYPE RULES"), text);
		else
			assertTrue(text.contains("WITH STRONG TYPE RULES"), text);
		assertTrue(text.contains("COMMENT ON TYPE \"App\".\"Amount\" IS 'Owner''s type  '"), text);
	}

	@Test
	void recreatesOrdinaryNumericArraysWithExactCardinality() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("array").asXml()));
		assertEquals(1, type.getArrayDimension());
		assertEquals(17, type.getArrayDimensionUpperBound());
		assertTrue(create(type).contains("AS DECIMAL(12,0) ARRAY[17]"));
		assertFalse(create(type).contains("TYPE RULES"));
		type.setArrayDimensionUpperBound(0);
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}

	@Test
	void restoresVarcharBitArrayAfterXml() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("varchar-bit-array").asXml()));
		assertEquals("B", type.getSpecifics().get("DB2_HOST_SUBTYPE"));
		String ddl = create(type);
		assertTrue(ddl.contains("AS VARCHAR(12) CCSID EBCDIC FOR BIT DATA ARRAY[17]\n"), ddl);
		type.getSpecifics().put("DB2_HOST_METATYPE", "T");
		type.setArrayDimension(0);
		assertTrue(create(type).contains("AS VARCHAR(12) CCSID EBCDIC FOR BIT DATA\n"));
	}

	@Test
	void restoresRowidDistinctAfterXmlAndRejectsArray() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("rowid").asXml()));
		assertEquals(DataType.ROWID, type.getDataType());
		String ddl = create(type);
		assertTrue(ddl.contains("AS ROWID\n"), ddl);
		assertThrows(IllegalArgumentException.class, () -> create(read("rowid-array")));
	}

	@Test
	void restoresClobArrayAfterXmlWithEncodingAndSubtype() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("clob-array").asXml()));
		String ddl = create(type);
		assertTrue(ddl.contains("AS CLOB(12) CCSID ASCII FOR MIXED DATA ARRAY[17]\n"), ddl);
		type.getSpecifics().put("DB2_HOST_INLINE_LENGTH", "0");
		assertThrows(IllegalArgumentException.class, () -> create(type));
		type.getSpecifics().put("DB2_HOST_INLINE_LENGTH", "-1");
		type.getSpecifics().remove("DB2_HOST_ENCODING_SCHEME");
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}

	@Test
	void restoresBlobArrayAfterXmlAndRejectsInlineLength() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("blob-array").asXml()));
		assertTrue(create(type).contains("AS BLOB(12) ARRAY[17]\n"));
		type.getSpecifics().put("DB2_HOST_INLINE_LENGTH", "0");
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}

	@Test
	void recreatesVarcharArrayWithCatalogEncodingAndSubtypeAfterXml() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("varchar-array").asXml()));
		assertEquals(DataType.VARCHAR, type.getDataType());
		String ddl = create(type);
		assertTrue(ddl.contains("AS VARCHAR(12) CCSID UNICODE FOR MIXED DATA ARRAY[17]\n"), ddl);
		type.getSpecifics().put("DB2_HOST_ENCODING_SCHEME", "E");
		type.getSpecifics().put("DB2_HOST_SUBTYPE", "S");
		assertTrue(create(type).contains("AS VARCHAR(12) CCSID EBCDIC FOR SBCS DATA ARRAY[17]\n"));
		type.getSpecifics().remove("DB2_HOST_SUBTYPE");
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}

	@Test
	void recreatesBinaryArrayAfterXml() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("varbinary-array").asXml()));
		assertEquals(DataType.VARBINARY, type.getDataType());
		String ddl = create(type);
		assertTrue(ddl.contains("AS VARBINARY(12) ARRAY[17]\n"), ddl);
	}

	@Test
	void recreatesDatetimeArrayWithPrecisionAndBoundAfterXml() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("timestamp-array").asXml()));
		assertEquals(DataType.TIMESTAMP, type.getDataType());
		assertEquals(1, type.getArrayDimension());
		assertEquals(17, type.getArrayDimensionUpperBound());
		String ddl = create(type);
		assertTrue(ddl.contains("AS TIMESTAMP(12) ARRAY[17]\n"), ddl);
		type.setArrayDimensionUpperBound(16);
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}

	@Test
	void timestampModelPrecisionChangesAreReflectedAfterXml() throws Exception {
		Domain type = read("timestamp");
		assertEquals(12L, type.getLength());
		assertNull(type.getScale());
		type.setLength(3L);
		Domain restored = SchemaUtils.readXml(new StringReader(type.asXml()));
		assertTrue(create(restored).contains("AS TIMESTAMP(3)"));
		restored.setLength(13L);
		assertThrows(IllegalArgumentException.class, () -> create(restored));
		type.getSpecifics().remove("DB2_HOST_LENGTH_UNIT");
		type.setLength(10L);
		type.getSpecifics().put("DB2_HOST_TIMESTAMP_PRECISION", "6");
		assertTrue(create(type).contains("AS TIMESTAMP(6)"));
	}

	@Test
	void recreatesDatetimeDistinctTypesFromCatalogPrecision() throws Exception {
		assertTrue(create(read("date")).contains("AS DATE\n"));
		assertTrue(create(read("time")).contains("AS TIME\n"));
		Domain timestamp = SchemaUtils.readXml(new StringReader(read("timestamp").asXml()));
		assertEquals(DataType.TIMESTAMP, timestamp.getDataType());
		assertTrue(create(timestamp).contains("AS TIMESTAMP(12)\n"));
		Domain seconds = SchemaUtils.readXml(new StringReader(read("timestamp-zero").asXml()));
		assertTrue(create(seconds).contains("AS TIMESTAMP(0)\n"));
		timestamp.getSpecifics().put("DB2_HOST_TIMESTAMP_PRECISION", "13");
		assertThrows(IllegalArgumentException.class, () -> create(timestamp));
		timestamp.getSpecifics().remove("DB2_HOST_TIMESTAMP_PRECISION");
		assertThrows(IllegalArgumentException.class, () -> create(timestamp));
	}

	@Test
	void recreatesBinaryDistinctTypesWithZosByteLimits() throws Exception {
		Domain binary = read("binary");
		assertEquals(DataType.BINARY, binary.getDataType());
		binary.setLength(255L);
		assertTrue(create(binary).contains("AS BINARY(255)\n"));
		binary.setLength(256L);
		assertThrows(IllegalArgumentException.class, () -> create(binary));

		Domain varying = SchemaUtils.readXml(new StringReader(read("varbinary").asXml()));
		assertEquals(DataType.VARBINARY, varying.getDataType());
		assertTrue(create(varying).contains("AS VARBINARY(12)\n"));
		varying.setLength(32704L);
		assertTrue(create(varying).contains("AS VARBINARY(32704)\n"));
		varying.setLength(32705L);
		assertThrows(IllegalArgumentException.class, () -> create(varying));
	}

	@Test
	void recreatesCharacterDistinctTypesWithCatalogEncodingAndSubtype() throws Exception {
		Domain varchar = SchemaUtils.readXml(new StringReader(read("varchar").asXml()));
		assertTrue(create(varchar).contains("AS VARCHAR(12) CCSID UNICODE FOR MIXED DATA"));
		varchar.setLength(32705L);
		assertThrows(IllegalArgumentException.class, () -> create(varchar));

		Domain character = read("char");
		assertTrue(create(character).contains("AS CHAR(12) CCSID EBCDIC FOR SBCS DATA"));
		character.getSpecifics().put("DB2_HOST_SUBTYPE", "B");
		assertTrue(create(character).contains("AS CHAR(12) CCSID EBCDIC FOR BIT DATA"));
		character.getSpecifics().put("DB2_HOST_SUBTYPE", "S");
		character.getSpecifics().remove("DB2_HOST_ENCODING_SCHEME");
		assertThrows(IllegalArgumentException.class, () -> create(character));
	}

	@Test
	void recreatesClobDistinctAfterXmlWithInlineLength() throws Exception {
		Domain clob = SchemaUtils.readXml(new StringReader(read("clob").asXml()));
		assertTrue(create(clob).contains("AS CLOB(12) CCSID ASCII FOR MIXED DATA INLINE LENGTH 8"));
		clob.getSpecifics().put("DB2_HOST_INLINE_LENGTH", "13");
		assertThrows(IllegalArgumentException.class, () -> create(clob));
	}

	@Test
	void characterAndAssociativeTypesRetainAttributesWithoutGuessingDdl() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("character").asXml()));
		assertEquals("1208", type.getSpecifics().get("DB2_HOST_CCSID"));
		assertThrows(IllegalArgumentException.class, () -> create(type));
		Domain associative = read("associative");
		assertEquals("opaque-id", associative.getSpecifics().get("DB2_HOST_ARRAY_INDEX_TYPE"));
		assertThrows(IllegalArgumentException.class, () -> create(associative));
	}

	@Test
	void largeCardinalityIsPreservedWithoutTruncatingToInt() {
		Domain type = read("large-array");
		assertEquals("2147483648", type.getSpecifics().get("DB2_HOST_ARRAY_CARDINALITY"));
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}

	@Test
	void weakRulesAndPrecisionLimitsFollowTheProduct() {
		Domain type = read("weak");
		if (!ZOS)
			assertTrue(create(type).contains("WITH WEAK TYPE RULES"));
		type.setLength(63L).setScale(0);
		if (ZOS)
			assertThrows(IllegalArgumentException.class, () -> create(type));
		else
			assertTrue(create(type).contains("DECIMAL(63,0)"));
		type.setScale(64);
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}

	@Test
	void missingFieldsAndUnknownKindsFailClearly() {
		assertThrows(RuntimeException.class, () -> read("missing"));
		assertThrows(RuntimeException.class, () -> read("unknown"));
	}

	@Test
	void authoritativeDefinitionRecreatesUnsupportedTypeAndDropUsesType() {
		var type = read("character");
		String ddl = "CREATE TYPE APP.CODE AS VARCHAR(10) CCSID UNICODE";
		type.setDefinition(ddl);
		assertTrue(create(type).contains(ddl));
		String drop = new Db2Zos13().createSqlFactoryRegistry().createSql(type, SqlType.DROP).get(0).getSqlText();
		assertEquals("DROP TYPE \"App\".\"Amount\" RESTRICT", drop);
		type.setDefinition("ALTER TYPE APP.CODE");
		assertThrows(IllegalArgumentException.class, () -> create(type));
		type.getSpecifics().remove("DB2_OBJECT_TYPE");
		assertThrows(IllegalArgumentException.class,
				() -> new Db2Zos13().createSqlFactoryRegistry().createSql(type, SqlType.DROP));
	}

	private static Domain read(String mode) {
		return new Db2Zos13().getCatalogReader().getSchemaReader().getDomainReader()
				.getAllFull(connection(new AtomicInteger(), new ArrayList<>(), mode)).get(0);
	}

	private static String create(Domain type) {
		return new Db2Zos13().createSqlFactoryRegistry().createSql(type, SqlType.CREATE).stream()
				.map(op -> op.getSqlText()).reduce("", (a, b) -> a + "\n" + b);
	}

	private static Connection connection(AtomicInteger calls, List<Object> bindings, String mode) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> metadata("DB2", ZOS ? "DSN13015" : "QSQ07050");
		case "prepareStatement" -> {
			assertTrue(((String) a[0]).contains("host:domains"));
			yield proxy(PreparedStatement.class, (sp, sm, sa) -> {
				if (sm.getName().equals("setString") || sm.getName().equals("setObject"))
					bindings.add(sa[1]);
				if (!sm.getName().equals("executeQuery"))
					return fallback(sm.getReturnType());
				calls.incrementAndGet();
				String source = switch (mode) {
				case "missing" -> null;
				case "character", "varchar", "varchar-array", "varchar-bit-array" -> "VARCHAR";
				case "char" -> "CHAR";
				case "blob", "blob-array" -> "BLOB";
				case "clob", "clob-array" -> "CLOB";
				case "binary" -> "BINARY";
				case "varbinary", "varbinary-array" -> "VARBINARY";
				case "rowid", "rowid-array" -> "ROWID";
				case "date" -> "DATE";
				case "time" -> "TIME";
				case "timestamp", "timestamp-zero", "timestamp-array" -> "TIMESTAMP";
				default -> "DECIMAL";
				};
				String encoding = switch (mode) {
				case "char" -> "E";
				case "varchar-bit-array" -> "E";
				case "varchar", "varchar-array" -> "U";
				case "clob", "clob-array" -> "A";
				default -> null;
				};
				var first = row("SCHEMA_NAME", "App", "DOMAIN_NAME", "Amount", "SOURCE_SCHEMA", "SYSIBM", "SOURCE_TYPE",
						source, "METATYPE",
						mode.equals("unknown") ? "?"
								: mode.equals("associative") ? "L" : mode.contains("array") ? "A" : "T",
						"TYPE_RULES", mode.equals("weak") ? "W" : "S", "NATIVE_LENGTH", 12L, "TYPE_LENGTH", 12L,
						"TYPE_SCALE", mode.equals("timestamp") || mode.equals("timestamp-array") ? 12 : 0, "CCSID",
						mode.equals("character") ? "1208" : null, "NORMALIZE_DATA", null, "SUBTYPE",
						mode.equals("varchar-bit-array") ? "B"
								: encoding == null ? null : mode.equals("char") ? "S" : "M",
						"ENCODING_SCHEME", encoding, "ARRAY_CARDINALITY",
						mode.equals("large-array") ? 2147483648L : mode.endsWith("array") ? 17L : 0L,
						"ARRAY_INDEX_TYPE", mode.equals("associative") ? "opaque-id" : null, "ARRAY_INDEX_LENGTH", null,
						"ARRAY_INDEX_SUBTYPE", null, "INLINE_LENGTH", switch (mode) {
						case "blob-array", "clob-array" -> -1;
						case "blob", "clob" -> 8;
						default -> null;
						}, "CREATED_AT", null, "REMARKS", "Owner's type  ");
				var second = new java.util.LinkedHashMap<>(first);
				second.put("DOMAIN_NAME", "Amount2");
				return rows(List.of(first, second));
			});
		}
		default -> fallback(m.getReturnType());
		});
	}
}
