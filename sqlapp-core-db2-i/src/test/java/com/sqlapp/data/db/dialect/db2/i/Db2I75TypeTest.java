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

	@Test
	void preservesEditedSourceSizesAndRejectsIgnoredAttributes() throws Exception {
		for (String source : List.of("blob", "clob", "dbclob")) {
			Domain type = read(source).setLength(9L);
			type = SchemaUtils.readXml(new StringReader(type.asXml()));
			assertEquals(9L, type.getLength());
			assertTrue(create(type).contains("(9) ALLOCATE(8)"));
			if (source.equals("dbclob"))
				assertEquals(DataType.NCLOB, type.getDataType());
			type.setScale(1);
			Domain invalid = type;
			assertThrows(IllegalArgumentException.class, () -> create(invalid));
		}
		Domain decimal = read("decfloat");
		assertEquals(16L, decimal.getLength());
		decimal.setLength(34L);
		Domain restored = SchemaUtils.readXml(new StringReader(decimal.asXml()));
		assertTrue(create(restored).contains("DECFLOAT(34)"));
		restored.setLength(31L);
		assertThrows(IllegalArgumentException.class, () -> create(restored));
		decimal.getSpecifics().remove("DB2_HOST_LENGTH_UNIT");
		decimal.setLength(8L);
		Domain legacy = SchemaUtils.readXml(new StringReader(decimal.asXml()));
		assertTrue(create(legacy).contains("DECFLOAT(16)"));
		Domain rowid = read("rowid").setLength(1L);
		assertThrows(IllegalArgumentException.class, () -> create(rowid));
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
		var reader = new Db2I75().getCatalogReader().getSchemaReader().getDomainReader();
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
	void recreatesVarcharDistinctAfterXmlWithAllocationAndNormalization() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("varchar").asXml()));
		assertEquals("8", type.getSpecifics().get("DB2_HOST_ALLOCATED_LENGTH"));
		assertTrue(create(type).contains("AS VARCHAR(12) ALLOCATE(8) CCSID 1208 NORMALIZED WITH WEAK TYPE RULES"));
		type.getSpecifics().put("DB2_HOST_ALLOCATED_LENGTH", "0");
		type.getSpecifics().put("DB2_HOST_NORMALIZE_DATA", "NO");
		assertTrue(create(type).contains("AS VARCHAR(12) CCSID 1208 NOT NORMALIZED WITH WEAK TYPE RULES"));
		type.getSpecifics().put("DB2_HOST_ALLOCATED_LENGTH", "13");
		assertThrows(IllegalArgumentException.class, () -> create(type));
		type.getSpecifics().remove("DB2_HOST_ALLOCATED_LENGTH");
		assertThrows(IllegalArgumentException.class, () -> create(type));
		type.getSpecifics().put("DB2_HOST_ALLOCATED_LENGTH", "0");
		type.getSpecifics().remove("DB2_HOST_NORMALIZE_DATA");
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}

	@Test
	void recreatesCharDistinctWithCatalogCcsid() {
		Domain type = read("char");
		assertTrue(create(type).contains("AS CHAR(12) CCSID 37 WITH STRONG TYPE RULES"));
		type.getSpecifics().put("DB2_HOST_CCSID", "65535");
		assertTrue(create(type).contains("AS CHAR(12) FOR BIT DATA WITH STRONG TYPE RULES"));
		type.getSpecifics().put("DB2_HOST_CCSID", "0");
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}

	@Test
	void recreatesDatalinkDistinctAfterXmlWithNativeLengthAndCcsid() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("datalink").asXml()));
		assertEquals(DataType.DATALINK, type.getDataType());
		String ddl = create(type);
		assertTrue(ddl.contains("AS DATALINK(200) CCSID 37 WITH STRONG TYPE RULES"), ddl);
		type.setLength(32717L);
		assertTrue(create(type).contains("AS DATALINK(32717) CCSID 37"));
		type.setLength(32718L);
		assertThrows(IllegalArgumentException.class, () -> create(type));
		assertThrows(IllegalArgumentException.class, () -> create(read("datalink-array")));
	}

	@Test
	void recreatesXmlDistinctWithAllocationAfterSchemaXmlRoundTrip() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("xml").asXml()));
		assertEquals(DataType.SQLXML, type.getDataType());
		String ddl = create(type);
		assertTrue(ddl.contains("AS XML ALLOCATE(8) CCSID 1208 NORMALIZED WITH STRONG TYPE RULES"), ddl);
		type.getSpecifics().put("DB2_HOST_ALLOCATED_LENGTH", "0");
		assertTrue(create(type).contains("AS XML CCSID 1208 NORMALIZED WITH STRONG TYPE RULES"));
		type.getSpecifics().remove("DB2_HOST_ALLOCATED_LENGTH");
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}

	@Test
	void recreatesXmlArrayAfterSchemaXmlRoundTrip() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("xml-array").asXml()));
		assertEquals(DataType.SQLXML, type.getDataType());
		String ddl = create(type);
		assertTrue(ddl.contains("AS XML CCSID 1208 NORMALIZED ARRAY[17]\n"), ddl);
		type.getSpecifics().put("DB2_HOST_CCSID", "1200");
		type.getSpecifics().put("DB2_HOST_NORMALIZE_DATA", "NO");
		assertTrue(create(type).contains("AS XML CCSID 1200 NOT NORMALIZED ARRAY[17]\n"));
		type.getSpecifics().put("DB2_HOST_CCSID", "65535");
		assertThrows(IllegalArgumentException.class, () -> create(type));
		type.getSpecifics().put("DB2_HOST_CCSID", "1200");
		type.getSpecifics().remove("DB2_HOST_NORMALIZE_DATA");
		assertThrows(IllegalArgumentException.class, () -> create(type));
		type.getSpecifics().put("DB2_HOST_NORMALIZE_DATA", "NO");
		type.getSpecifics().put("DB2_HOST_ALLOCATED_LENGTH", "8");
		assertThrows(IllegalArgumentException.class, () -> create(type));
		type.getSpecifics().remove("DB2_HOST_ALLOCATED_LENGTH");
		type.setLength(10L);
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}

	@Test
	void recreatesRowidDistinctAfterXmlAndRejectsArray() throws Exception {
		Domain distinct = SchemaUtils.readXml(new StringReader(read("rowid").asXml()));
		assertEquals(DataType.ROWID, distinct.getDataType());
		assertTrue(create(distinct).contains("AS ROWID WITH STRONG TYPE RULES"));
		assertThrows(IllegalArgumentException.class, () -> create(read("rowid-array")));
	}

	@Test
	void recreatesNativeBooleanDistinctAndArrayAfterXml() throws Exception {
		Domain distinct = SchemaUtils.readXml(new StringReader(read("boolean").asXml()));
		assertEquals(DataType.BOOLEAN, distinct.getDataType());
		assertTrue(create(distinct).contains("AS BOOLEAN WITH STRONG TYPE RULES"));
		Domain array = SchemaUtils.readXml(new StringReader(read("boolean-array").asXml()));
		assertEquals(DataType.BOOLEAN, array.getDataType());
		assertTrue(create(array).contains("AS BOOLEAN ARRAY[17]\n"));
		array.setDataType(DataType.SMALLINT);
		assertThrows(IllegalArgumentException.class, () -> create(array));
	}

	@Test
	void recreatesClobArrayAfterXmlWithUtf8Attributes() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("clob-array").asXml()));
		String ddl = create(type);
		assertTrue(ddl.contains("AS CLOB(1048576) CCSID 1208 NORMALIZED ARRAY[17]\n"), ddl);
		type.setLength(1048577L);
		assertThrows(IllegalArgumentException.class, () -> create(type));
		type.setLength(1048576L);
		type.getSpecifics().put("DB2_HOST_CCSID", "65535");
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}

	@Test
	void recreatesLobDistinctTypesWithAllocationAfterXml() throws Exception {
		for (String source : List.of("blob", "clob", "dbclob")) {
			Domain type = SchemaUtils.readXml(new StringReader(read(source).asXml()));
			String suffix = switch (source) {
			case "blob" -> "";
			case "clob" -> " CCSID 1208 NORMALIZED";
			default -> " CCSID 1200 NOT NORMALIZED";
			};
			String expected = "AS " + source.toUpperCase(java.util.Locale.ROOT) + "(12) ALLOCATE(8)" + suffix
					+ " WITH STRONG TYPE RULES";
			assertTrue(create(type).contains(expected), create(type));
			long maximum = source.equals("dbclob") ? 1073741823L : 2147483647L;
			type.setLength(maximum);
			assertTrue(create(type).contains("(" + maximum + ") ALLOCATE(8)"));
			type.setLength(maximum + 1);
			assertThrows(IllegalArgumentException.class, () -> create(type));
			type.setLength(maximum);
			type.getSpecifics().put("DB2_HOST_ALLOCATED_LENGTH", "32767");
			assertThrows(IllegalArgumentException.class, () -> create(type));
			type.getSpecifics().remove("DB2_HOST_ALLOCATED_LENGTH");
			assertThrows(IllegalArgumentException.class, () -> create(type));
		}
	}

	@Test
	void restoresFloatStorageClassesAfterXml() throws Exception {
		Domain single = SchemaUtils.readXml(new StringReader(read("float").asXml()));
		assertTrue(create(single).contains("AS REAL WITH STRONG TYPE RULES"));
		Domain wide = SchemaUtils.readXml(new StringReader(read("float-array").asXml()));
		assertTrue(create(wide).contains("AS DOUBLE ARRAY[17]\n"));
		wide.getSpecifics().put("DB2_HOST_NATIVE_LENGTH", "4");
		assertThrows(IllegalArgumentException.class, () -> create(wide));
		wide.getSpecifics().put("DB2_HOST_NATIVE_LENGTH", "6");
		assertThrows(IllegalArgumentException.class, () -> create(wide));
	}

	@Test
	void recreatesLobArraysWithProductLengthLimitsAfterXml() throws Exception {
		Domain blob = SchemaUtils.readXml(new StringReader(read("blob-array").asXml()));
		assertEquals(DataType.BLOB, blob.getDataType());
		assertTrue(create(blob).contains("AS BLOB(1048576) ARRAY[17]\n"));
		blob.setLength(1048577L);
		assertThrows(IllegalArgumentException.class, () -> create(blob));

		Domain dbclob = SchemaUtils.readXml(new StringReader(read("dbclob-array").asXml()));
		assertEquals("DBCLOB", dbclob.getSpecifics().get("DB2_HOST_SOURCE_TYPE"));
		String ddl = create(dbclob);
		assertTrue(ddl.contains("AS DBCLOB(524288) CCSID 1200 NOT NORMALIZED ARRAY[17]\n"), ddl);
		dbclob.setLength(524289L);
		assertThrows(IllegalArgumentException.class, () -> create(dbclob));
		dbclob.setLength(524288L);
		dbclob.getSpecifics().put("DB2_HOST_ALLOCATED_LENGTH", "8");
		assertThrows(IllegalArgumentException.class, () -> create(dbclob));
	}

	@Test
	void recreatesDecfloatTypesUsingCatalogStorageLengthAfterXml() throws Exception {
		Domain distinct = SchemaUtils.readXml(new StringReader(read("decfloat").asXml()));
		assertEquals(DataType.DECIMALFLOAT, distinct.getDataType());
		assertTrue(create(distinct).contains("AS DECFLOAT(16) WITH STRONG TYPE RULES"));

		Domain array = SchemaUtils.readXml(new StringReader(read("decfloat-array").asXml()));
		assertEquals(DataType.DECIMALFLOAT, array.getDataType());
		assertTrue(create(array).contains("AS DECFLOAT(34) ARRAY[17]\n"));
		array.getSpecifics().put("DB2_HOST_NATIVE_LENGTH", "34");
		assertThrows(IllegalArgumentException.class, () -> create(array));
		array.getSpecifics().remove("DB2_HOST_NATIVE_LENGTH");
		assertThrows(IllegalArgumentException.class, () -> create(array));
	}

	@Test
	void recreatesCharacterArraysWithCcsidAndNormalizationAfterXml() throws Exception {
		Domain varchar = SchemaUtils.readXml(new StringReader(read("varchar-array").asXml()));
		assertTrue(create(varchar).contains("AS VARCHAR(12) CCSID 1208 NORMALIZED ARRAY[17]\n"));
		Domain vargraphic = SchemaUtils.readXml(new StringReader(read("vargraphic-array").asXml()));
		assertEquals(DataType.NVARCHAR, vargraphic.getDataType());
		String ddl = create(vargraphic);
		assertTrue(ddl.contains("AS VARGRAPHIC(12) CCSID 1200 NOT NORMALIZED ARRAY[17]\n"), ddl);
		vargraphic.getSpecifics().put("DB2_HOST_ALLOCATED_LENGTH", "0");
		assertEquals(ddl, create(vargraphic));
		vargraphic.getSpecifics().put("DB2_HOST_ALLOCATED_LENGTH", "8");
		assertThrows(IllegalArgumentException.class, () -> create(vargraphic));
		vargraphic.getSpecifics().remove("DB2_HOST_ALLOCATED_LENGTH");
		vargraphic.getSpecifics().remove("DB2_HOST_CCSID");
		assertThrows(IllegalArgumentException.class, () -> create(vargraphic));
	}

	@Test
	void recreatesBinaryArrayAfterXml() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("varbinary-array").asXml()));
		assertEquals(DataType.VARBINARY, type.getDataType());
		String ddl = create(type);
		assertTrue(ddl.contains("AS VARBINARY(12) ARRAY[17]\n"), ddl);
		type.getSpecifics().put("DB2_HOST_ALLOCATED_LENGTH", "0");
		assertEquals(ddl, create(type));
		type.getSpecifics().put("DB2_HOST_ALLOCATED_LENGTH", "8");
		assertThrows(IllegalArgumentException.class, () -> create(type));
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
		assertTrue(create(read("date")).contains("AS DATE WITH STRONG TYPE RULES"));
		assertTrue(create(read("time")).contains("AS TIME WITH STRONG TYPE RULES"));
		Domain timestamp = SchemaUtils.readXml(new StringReader(read("timestamp").asXml()));
		assertEquals(DataType.TIMESTAMP, timestamp.getDataType());
		assertTrue(create(timestamp).contains("AS TIMESTAMP(12) WITH STRONG TYPE RULES"));
		Domain seconds = SchemaUtils.readXml(new StringReader(read("timestamp-zero").asXml()));
		assertTrue(create(seconds).contains("AS TIMESTAMP(0) WITH STRONG TYPE RULES"));
		timestamp.getSpecifics().put("DB2_HOST_TIMESTAMP_PRECISION", "13");
		assertThrows(IllegalArgumentException.class, () -> create(timestamp));
		timestamp.getSpecifics().remove("DB2_HOST_TIMESTAMP_PRECISION");
		assertThrows(IllegalArgumentException.class, () -> create(timestamp));
	}

	@Test
	void recreatesBinaryDistinctTypesWithByteLengthsAndAllocation() throws Exception {
		Domain binary = read("binary");
		assertEquals(DataType.BINARY, binary.getDataType());
		binary.setLength(32766L);
		assertTrue(create(binary).contains("AS BINARY(32766) WITH STRONG TYPE RULES"));
		binary.setLength(32767L);
		assertThrows(IllegalArgumentException.class, () -> create(binary));

		Domain varying = SchemaUtils.readXml(new StringReader(read("varbinary").asXml()));
		assertEquals(DataType.VARBINARY, varying.getDataType());
		assertTrue(create(varying).contains("AS VARBINARY(12) ALLOCATE(8) WITH STRONG TYPE RULES"));
		varying.getSpecifics().put("DB2_HOST_ALLOCATED_LENGTH", "0");
		assertTrue(create(varying).contains("AS VARBINARY(12) WITH STRONG TYPE RULES"));
		varying.getSpecifics().remove("DB2_HOST_ALLOCATED_LENGTH");
		assertThrows(IllegalArgumentException.class, () -> create(varying));
	}

	@Test
	void recreatesGraphicDistinctWithCatalogCcsid() {
		Domain type = read("graphic");
		assertEquals(DataType.NCHAR, type.getDataType());
		assertTrue(create(type).contains("AS GRAPHIC(12) CCSID 13488 WITH STRONG TYPE RULES"));
		type.setLength(16384L);
		assertThrows(IllegalArgumentException.class, () -> create(type));
	}

	@Test
	void recreatesVargraphicDistinctAfterXmlUsingCharacterCounts() throws Exception {
		Domain type = SchemaUtils.readXml(new StringReader(read("vargraphic").asXml()));
		assertEquals(DataType.NVARCHAR, type.getDataType());
		assertEquals(12L, type.getLength());
		assertTrue(create(type).contains("AS VARGRAPHIC(12) ALLOCATE(8) CCSID 1200 NOT NORMALIZED"));
		type.setLength(16370L);
		assertTrue(create(type).contains("AS VARGRAPHIC(16370) ALLOCATE(8)"));
		type.setLength(16371L);
		assertThrows(IllegalArgumentException.class, () -> create(type));
		type.setLength(12L);
		type.getSpecifics().put("DB2_HOST_CCSID", "1208");
		assertThrows(IllegalArgumentException.class, () -> create(type));
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
		String drop = new Db2I75().createSqlFactoryRegistry().createSql(type, SqlType.DROP).get(0).getSqlText();
		assertEquals("DROP TYPE \"App\".\"Amount\" RESTRICT", drop);
		type.setDefinition("ALTER TYPE APP.CODE");
		assertThrows(IllegalArgumentException.class, () -> create(type));
		type.getSpecifics().remove("DB2_OBJECT_TYPE");
		assertThrows(IllegalArgumentException.class,
				() -> new Db2I75().createSqlFactoryRegistry().createSql(type, SqlType.DROP));
	}

	private static Domain read(String mode) {
		return new Db2I75().getCatalogReader().getSchemaReader().getDomainReader()
				.getAllFull(connection(new AtomicInteger(), new ArrayList<>(), mode)).get(0);
	}

	private static String create(Domain type) {
		return new Db2I75().createSqlFactoryRegistry().createSql(type, SqlType.CREATE).stream()
				.map(op -> op.getSqlText()).reduce("", (a, b) -> a + "\n" + b);
	}

	private static Connection connection(AtomicInteger calls, List<Object> bindings, String mode) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> metadata("DB2", ZOS ? "DSN13015" : "QSQ07050");
		case "prepareStatement" -> {
			assertTrue(((String) a[0]).contains("host:domains"));
			assertTrue(((String) a[0]).contains("t.\"ALLOCATE\" AS ALLOCATED_LENGTH"));
			yield proxy(PreparedStatement.class, (sp, sm, sa) -> {
				if (sm.getName().equals("setString") || sm.getName().equals("setObject"))
					bindings.add(sa[1]);
				if (!sm.getName().equals("executeQuery"))
					return fallback(sm.getReturnType());
				calls.incrementAndGet();
				String source = switch (mode) {
				case "missing" -> null;
				case "char" -> "CHAR";
				case "character", "varchar", "varchar-array" -> "VARCHAR";
				case "graphic" -> "GRAPHIC";
				case "vargraphic", "vargraphic-array" -> "VARGRAPHIC";
				case "binary" -> "BINARY";
				case "varbinary", "varbinary-array" -> "VARBINARY";
				case "date" -> "DATE";
				case "time" -> "TIME";
				case "timestamp", "timestamp-zero", "timestamp-array" -> "TIMESTAMP";
				case "decfloat", "decfloat-array" -> "DECFLOAT";
				case "float", "float-array" -> "FLOAT";
				case "boolean", "boolean-array" -> "BOOLEAN";
				case "rowid", "rowid-array" -> "ROWID";
				case "xml", "xml-array" -> "XML";
				case "datalink", "datalink-array" -> "DATALINK";
				case "blob", "blob-array" -> "BLOB";
				case "clob", "clob-array" -> "CLOB";
				case "dbclob", "dbclob-array" -> "DBCLOB";
				default -> "DECIMAL";
				};
				long nativeLength = switch (mode) {
				case "decfloat" -> 8L;
				case "decfloat-array" -> 16L;
				case "float" -> 4L;
				case "float-array" -> 8L;
				case "rowid", "rowid-array" -> 40L;
				case "xml", "xml-array" -> 2147483647L;
				case "datalink", "datalink-array" -> 200L;
				case "blob-array", "clob-array" -> 1048576L;
				case "dbclob-array" -> 524288L;
				default -> mode.contains("graphic") ? 24L : 12L;
				};
				String ccsid = switch (mode) {
				case "char" -> "37";
				case "datalink", "datalink-array" -> "37";
				case "character", "varchar", "varchar-array", "clob", "clob-array", "xml", "xml-array" -> "1208";
				case "graphic" -> "13488";
				case "vargraphic", "vargraphic-array", "dbclob", "dbclob-array" -> "1200";
				default -> null;
				};
				var first = row("SCHEMA_NAME", "App", "DOMAIN_NAME", "Amount", "SOURCE_SCHEMA", "SYSIBM", "SOURCE_TYPE",
						source, "METATYPE",
						mode.equals("unknown") ? "?"
								: mode.equals("associative") ? "L" : mode.contains("array") ? "A" : "T",
						"TYPE_RULES", mode.equals("weak") || mode.equals("varchar") ? "W" : "S", "NATIVE_LENGTH",
						nativeLength, "TYPE_LENGTH", mode.contains("graphic") ? 12L : nativeLength, "TYPE_SCALE",
						mode.equals("timestamp") || mode.equals("timestamp-array") ? 12 : 0, "CCSID", ccsid,
						"NORMALIZE_DATA", "1208".equals(ccsid) ? "YES" : "1200".equals(ccsid) ? "NO" : null,
						"ALLOCATED_LENGTH",
						List.of("varchar", "vargraphic", "varbinary", "blob", "clob", "dbclob", "xml").contains(mode)
								? 8
								: null,
						"SUBTYPE", null, "ENCODING_SCHEME", null, "ARRAY_CARDINALITY",
						mode.equals("large-array") ? 2147483648L : mode.endsWith("array") ? 17L : 0L,
						"ARRAY_INDEX_TYPE", mode.equals("associative") ? "opaque-id" : null, "ARRAY_INDEX_LENGTH", null,
						"ARRAY_INDEX_SUBTYPE", null, "INLINE_LENGTH", null, "CREATED_AT", null, "REMARKS",
						"Owner's type  ");
				var second = new java.util.LinkedHashMap<>(first);
				second.put("DOMAIN_NAME", "Amount2");
				return rows(List.of(first, second));
			});
		}
		default -> fallback(m.getReturnType());
		});
	}
}
