/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.cobol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.legacy.GenerateLegacyMigrationContractCommand;
import com.sqlapp.exceptions.CommandException;

class CobolCsvExtractCommandTest {

	static final Charset EBCDIC = Charset.forName("x-IBM939");

	@TempDir
	File directory;

	// ------------------------------------------------------------ customer (OCCURS
	// DEPENDING ON)

	private static final String CUSTOMER_IMPORT = """
			formatVersion: 1
			schemaName: HOST
			records:
			  - name: CUSTOMER-REC
			    tableName: CUSTOMER
			    primaryKey: [CUST-ID]
			""";

	/**
	 * One CUSTOMER-REC (layout in CobolCopybookParserTest), padded to 106 bytes
	 * when {@code pad}.
	 */
	static byte[] customer(String id, String kanji, String status, long balanceCents, long credit, String birth,
			long points, String[][] orders, boolean pad) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		write(out, text(id, 8));
		write(out, graphic(kanji, 10));
		write(out, text(status, 1));
		write(out, packed(balanceCents, 5));
		write(out, binary(credit, 4));
		write(out, text("", 2));
		write(out, zoned(birth, false));
		write(out, points < 0 ? new byte[] { 0x60 } : new byte[] { 0x4E });
		write(out, zoned(String.format("%05d", Math.abs(points)), false));
		write(out, binary(orders.length, 2));
		for (String[] order : orders) {
			write(out, text(order[0], 6));
			write(out, packed(Long.parseLong(order[1]), 4));
		}
		byte[] record = out.toByteArray();
		return pad ? Arrays.copyOf(record, 106) : record;
	}

	private static byte[][] customers(boolean pad) {
		return new byte[][] {
				customer("C0000001", "山田太郎", "A", -1234567, 50000, "19800102", 12,
						new String[][] { { "OR0001", "12345" }, { "OR0002", "-500" } }, pad),
				customer("C0000002", "鈴木", "C", 0, 0, "20001231", -3, new String[0][], pad) };
	}

	@Test
	void extractsFixedRecordsWithOccursDependingOn() throws Exception {
		File contract = contract(CobolCopybookParserTest.CUSTOMER, CUSTOMER_IMPORT);
		File data = data(fixed(customers(true)));
		CobolCsvExtractCommand command = extract(contract, data, """
				formatVersion: 1
				recordFormat: FIXED
				recordLength: 106
				encoding:
				  charset: x-IBM939
				""");
		assertEquals(List.of(
				"CUST_ID,CUST_NAME,CUST_STATUS,BALANCE,CREDIT_LIMIT,BIRTH_YY,BIRTH_MM,BIRTH_DD,POINTS,ORDER_COUNT",
				"C0000001,山田太郎,A,-12345.67,50000,1980,1,2,12,2", "C0000002,鈴木,C,0.00,0,2000,12,31,-3,0"),
				csv("customer.csv"));
		assertEquals(
				List.of("ORDER_NO,ORDER_AMT,ORDERS_NO,CUST_ID", "OR0001,123.45,1,C0000001", "OR0002,-5.00,2,C0000001"),
				csv("orders.csv"));
		assertEquals(Map.of("CUSTOMER-REC", 2L), command.getRecordCounts());
		assertEquals(Map.of("table-host-customer", 2L, "table-host-orders", 2L), command.getRowCounts());
	}

	@Test
	void variableBlockedAndComputedFramingGiveTheSameCsv() throws Exception {
		File contract = contract(CobolCopybookParserTest.CUSTOMER, CUSTOMER_IMPORT);
		byte[][] records = customers(false);
		ByteArrayOutputStream blocked = new ByteArrayOutputStream();
		byte[] block = variable(records);
		write(blocked, new byte[] { (byte) ((block.length + 4) >> 8), (byte) (block.length + 4), 0, 0 });
		write(blocked, block);
		Map<String, byte[]> formats = Map.of("VARIABLE", variable(records), "VARIABLE_BLOCKED", blocked.toByteArray(),
				"COMPUTED", fixed(records));
		for (var format : formats.entrySet()) {
			extract(contract, data(format.getValue()), """
					formatVersion: 1
					recordFormat: %s
					encoding:
					  charset: x-IBM939
					""".formatted(format.getKey()));
			assertEquals(3, csv("customer.csv").size(), format.getKey());
			assertEquals("C0000001,山田太郎,A,-12345.67,50000,1980,1,2,12,2", csv("customer.csv").get(1), format.getKey());
			assertEquals(3, csv("orders.csv").size(), format.getKey());
		}
	}

	// ------------------------------------------------------------ header / detail
	// / trailer

	static final String ORDERS = """
			       01  HEADER-REC.
			           05  REC-TYPE      PIC X.
			           05  ORDER-NO      PIC 9(6).
			           05  ORDER-DATE    PIC 9(8).
			       01  DETAIL-REC.
			           05  REC-TYPE      PIC X.
			           05  ITEM-CODE     PIC X(5).
			           05  QTY           PIC S9(3) COMP-3.
			           05  FILLER        PIC X(7).
			       01  TRAILER-REC.
			           05  REC-TYPE      PIC X.
			           05  REC-COUNT     PIC 9(5).
			           05  FILLER        PIC X(9).
			""";

	static final String ORDERS_IMPORT = """
			formatVersion: 1
			schemaName: HOST
			excludeRecords: [TRAILER-REC]
			columnNames:
			  DETAIL-REC.REC-TYPE: DETAIL_TYPE
			records:
			  - name: HEADER-REC
			    tableName: ORDER_HEADER
			    primaryKey: [ORDER-NO]
			  - name: DETAIL-REC
			    tableName: ORDER_DETAIL
			    parent: HEADER-REC
			    sequenceColumn: LINE_NO
			""";

	static final String ORDERS_EXTRACT = """
			formatVersion: 1
			recordLength: 15
			encoding:
			  charset: x-IBM939
			recordTypes:
			  - record: HEADER-REC
			    when: {offset: 0, length: 1, text: H}
			  - record: DETAIL-REC
			    when: {offset: 0, hex: C4}
			  - record: TRAILER-REC
			    expression: "record.text(0, 1) == 'T' && record.length() == 15"
			    skip: true
			""";

	static byte[] header(String orderNo, String date) {
		return concat(text("H", 1), zoned(orderNo, false), zoned(date, false));
	}

	static byte[] detail(String item, long quantity) {
		return concat(text("D", 1), text(item, 5), packed(quantity, 2), text("", 7));
	}

	static byte[] trailer(int count) {
		return concat(text("T", 1), zoned(String.format("%05d", count), false), text("", 9));
	}

	@Test
	void detailRecordsInheritTheirHeaderKey() throws Exception {
		File contract = contract(ORDERS, ORDERS_IMPORT);
		CobolCsvExtractCommand command = extract(
				contract, data(fixed(new byte[][] { header("000100", "20261116"), detail("A0001", 3),
						detail("B0002", -1), header("000200", "20261117"), detail("C0003", 10), trailer(5) })),
				ORDERS_EXTRACT);
		assertEquals(List.of("REC_TYPE,ORDER_NO,ORDER_DATE", "H,100,20261116", "H,200,20261117"),
				csv("order_header.csv"));
		assertEquals(List.of("DETAIL_TYPE,ITEM_CODE,QTY,LINE_NO,ORDER_NO", "D,A0001,3,1,100", "D,B0002,-1,2,100",
				"D,C0003,10,1,200"), csv("order_detail.csv"));
		assertEquals(Map.of("HEADER-REC", 2L, "DETAIL-REC", 3L, "TRAILER-REC (skipped)", 1L),
				command.getRecordCounts());
	}

	@Test
	void recordTypeResolverAndUnknownRecords() throws Exception {
		File contract = contract(ORDERS, ORDERS_IMPORT);
		byte[] data = fixed(new byte[][] { header("000100", "20261116"), detail("A0001", 3), trailer(1) });
		extract(contract, data(data), """
				formatVersion: 1
				recordLength: 15
				encoding:
				  charset: x-IBM939
				recordTypeResolver: %s
				unknownRecord: SKIP
				""".formatted(FirstByteResolver.class.getName()));
		assertEquals(2, csv("order_detail.csv").size());
		CommandException unknown = assertThrows(CommandException.class, () -> extract(contract, data(data), """
				formatVersion: 1
				recordLength: 15
				encoding:
				  charset: x-IBM939
				recordTypeResolver: %s
				""".formatted(FirstByteResolver.class.getName())));
		assertTrue(unknown.getMessage().contains("Record 3: record type is not identified (first bytes X'E3"),
				unknown.getMessage());
		CommandException orphan = assertThrows(CommandException.class,
				() -> extract(contract, data(fixed(new byte[][] { detail("A0001", 3) })), ORDERS_EXTRACT));
		assertTrue(orphan.getMessage().contains("appears before its parent record HEADER-REC"), orphan.getMessage());
	}

	/** Resolver used by the test; identifies H and D records. */
	public static class FirstByteResolver implements CobolRecordTypeResolver {
		@Override
		public String resolve(CobolRecordView record, long recordNumber) {
			return switch (record.text(0, 1)) {
			case "H" -> "HEADER-REC";
			case "D" -> "DETAIL-REC";
			default -> null;
			};
		}
	}

	@Test
	void invalidAndBlankNumbersFollowTheConfiguration() throws Exception {
		File contract = contract(ORDERS, ORDERS_IMPORT);
		byte[] badQuantity = detail("A0001", 3);
		badQuantity[7] = (byte) 0x17;
		byte[] blankQuantity = detail("A0002", 3);
		blankQuantity[6] = 0x40;
		blankQuantity[7] = 0x40;
		byte[] data = fixed(new byte[][] { header("000100", "20261116"), badQuantity, blankQuantity });
		CommandException invalid = assertThrows(CommandException.class,
				() -> extract(contract, data(data), ORDERS_EXTRACT));
		assertTrue(
				invalid.getMessage().contains(
						"Record 2 (DETAIL-REC): DETAIL-REC.QTY: packed decimal sign is invalid" + " (X'0017')"),
				invalid.getMessage());
		extract(contract, data(data), ORDERS_EXTRACT + "invalidNumeric: NULL\nblankNumeric: NULL\n");
		assertEquals(List.of("DETAIL_TYPE,ITEM_CODE,QTY,LINE_NO,ORDER_NO", "D,A0001,,1,100", "D,A0002,,2,100"),
				csv("order_detail.csv"));
		CommandException length = assertThrows(CommandException.class, () -> extract(contract,
				data(concat(header("000100", "20261116"), Arrays.copyOf(detail("A0001", 3), 12))), ORDERS_EXTRACT));
		assertTrue(length.getMessage().contains("ends inside a record (12 of 15 bytes)"), length.getMessage());
	}

	@Test
	void tableProfileAndUserDefinedCharacters() throws Exception {
		File contract = contract(ORDERS, ORDERS_IMPORT.replace("excludeRecords: [TRAILER-REC]", """
				excludeRecords: [TRAILER-REC, DETAIL-REC]
				records:
				  - name: HEADER-REC
				    tableName: ORDER_HEADER
				    primaryKey: [ORDER-NO]
				ignored:""").replaceAll("(?s)ignored:.*", ""));
		// A made-up single-byte table: X'C8' is H, digits are X'F0'-X'F9'.
		StringBuilder single = new StringBuilder("C8 U+0048\n40 U+0020\n");
		for (int i = 0; i < 10; i++) {
			single.append(String.format("F%d U+%04X%n", i, '0' + i));
		}
		Files.writeString(new File(directory, "single.txt").toPath(), single);
		Files.writeString(new File(directory, "double.txt").toPath(), "# empty\n");
		extract(contract, data(fixed(new byte[][] { header("000100", "20261116") })), """
				formatVersion: 1
				recordLength: 15
				encoding:
				  profile: TABLE
				  singleByteTable: single.txt
				  doubleByteTable: double.txt
				  shiftOut: "28"
				  shiftIn: "29"
				""");
		assertEquals(List.of("REC_TYPE,ORDER_NO,ORDER_DATE", "H,100,20261116"), csv("order_header.csv"));

		HostTextDecoder ibm = HostTextDecoder.of(encoding("x-IBM939", "gaiji.txt", "69A1 U+20B9F"), directory);
		byte[] graphic = { 0x45, 0x41, 0x69, (byte) 0xA1 };
		assertEquals(new String(Arrays.copyOfRange(new byte[] { 0x0E, 0x45, 0x41, 0x0F }, 0, 4), EBCDIC)
				+ new String(Character.toChars(0x20B9F)), ibm.graphic(graphic, 0, 4));
		assertThrows(CommandException.class, () -> HostTextDecoder.of(encodingTable(null), directory));
	}

	// ------------------------------------------------------------ trailer checks

	static final String ORDERS_WITH_TOTALS = ORDERS.replace("""
			           05  REC-COUNT     PIC 9(5).
			           05  FILLER        PIC X(9).
			""", """
			           05  REC-COUNT     PIC 9(5).
			           05  TOTAL-QTY     PIC S9(5) COMP-3.
			           05  FILLER        PIC X(6).
			""");

	private static final String CONTROL_IMPORT = ORDERS_IMPORT.replace("excludeRecords: [TRAILER-REC]\n", "") + """
			  - name: TRAILER-REC
			    control: true
			""";

	private static final String TRAILER_EXTRACT = ORDERS_EXTRACT + """
			trailerChecks:
			  - record: TRAILER-REC
			    count: TRAILER-REC.REC-COUNT
			    countOf: [DETAIL-REC]
			    sums:
			      - field: TRAILER-REC.TOTAL-QTY
			        of: DETAIL-REC.QTY
			""";

	private static byte[] trailer(int count, long total) {
		return concat(text("T", 1), zoned(String.format("%05d", count), false), packed(total, 3), text("", 6));
	}

	@Test
	void trailersAreCheckedAgainstTheRecordsSinceThePreviousTrailer() throws Exception {
		File contract = contract(ORDERS_WITH_TOTALS, CONTROL_IMPORT);
		String mapping = Files.readString(new File(directory, "layout-legacy-migration.yaml").toPath());
		assertTrue(mapping.contains("cobolControlRecords"), mapping);
		assertTrue(mapping.contains("TRAILER-REC.TOTAL-QTY"), mapping);
		assertTrue(!Files.readString(new File(directory, "layout.xml").toPath()).contains("TRAILER"));
		CobolCsvExtractCommand command = extract(contract,
				data(fixed(new byte[][] { header("000100", "20261116"), detail("A0001", 3), detail("B0002", -1),
						trailer(2, 2), header("000200", "20261117"), detail("C0003", 10), trailer(1, 10) })),
				TRAILER_EXTRACT);
		assertEquals(4, csv("order_detail.csv").size());
		assertEquals(2L, command.getRecordCounts().get("TRAILER-REC (skipped)"));
		// Without an RDW, a control record is framed by its own layout length.
		extract(contract, data(fixed(new byte[][] { header("000100", "20261116"), detail("A0001", 3), trailer(1, 3) })),
				TRAILER_EXTRACT + "recordFormat: COMPUTED\n");
		assertEquals(2, csv("order_detail.csv").size());
	}

	@Test
	void trailerMismatchesAndMissingTrailersStopTheExtraction() throws Exception {
		File contract = contract(ORDERS_WITH_TOTALS, CONTROL_IMPORT);
		Map<String, byte[][]> cases = new java.util.LinkedHashMap<>();
		cases.put("Record 4 (TRAILER-REC): trailer check failed: TRAILER-REC.REC-COUNT = 3, but 2 records were read",
				new byte[][] { header("000100", "20261116"), detail("A0001", 3), detail("B0002", -1), trailer(3, 2) });
		cases.put("trailer check failed: TRAILER-REC.TOTAL-QTY = 5, but the sum of DETAIL-REC.QTY is 2",
				new byte[][] { header("000100", "20261116"), detail("A0001", 3), detail("B0002", -1), trailer(2, 5) });
		cases.put("1 counted records after the last TRAILER-REC are not covered by a trailer",
				new byte[][] { header("000100", "20261116"), detail("A0001", 3), trailer(1, 3),
						header("000200", "20261117"), detail("C0003", 10) });
		cases.put("trailer TRAILER-REC was not found", new byte[][] { header("000100", "20261116") });
		for (var entry : cases.entrySet()) {
			CommandException failure = assertThrows(CommandException.class,
					() -> extract(contract, data(fixed(entry.getValue())), TRAILER_EXTRACT), entry.getKey());
			assertTrue(failure.getMessage().contains(entry.getKey()), failure.getMessage());
		}
		for (String invalid : List.of(
				TRAILER_EXTRACT.replace("record: TRAILER-REC\n    count", "record: HEADER-REC\n    count"),
				TRAILER_EXTRACT.replace("count: TRAILER-REC.REC-COUNT", "count: TRAILER-REC.REC-TYPE"),
				TRAILER_EXTRACT.replace("of: DETAIL-REC.QTY", "of: DETAIL-REC.ITEM-CODE"),
				TRAILER_EXTRACT.replace("    skip: true\n", ""))) {
			assertThrows(CommandException.class,
					() -> extract(contract, data(fixed(new byte[][] { header("000100", "20261116") })), invalid),
					invalid);
		}
	}

	@Test
	void icuCharsetsDecodeCcsid1390And1399() throws Exception {
		// CCSID 1390/1399 come from ICU4J (icu4j-charset), not from the JDK.
		for (String charset : List.of("x-IBM1399", "ibm-1390")) {
			CobolCsvExtractConfiguration.Encoding encoding = new CobolCsvExtractConfiguration.Encoding();
			encoding.setCharset(charset);
			HostTextDecoder decoder = HostTextDecoder.of(encoding, directory);
			// PIC X: A, shifted 山 (X'4565'), then the euro sign X'E1' added by 1390/1399.
			byte[] mixed = { (byte) 0xC1, 0x0E, 0x45, 0x65, 0x0F, (byte) 0xE1 };
			assertEquals("A山€", decoder.text(mixed, 0, mixed.length), charset);
			// PIC N: 山田 and 𠮟 (X'B38D'), which 930/939 cannot represent.
			byte[] graphic = { 0x45, 0x65, 0x45, 0x63, (byte) 0xB3, (byte) 0x8D };
			assertEquals("山田𠮟", decoder.graphic(graphic, 0, graphic.length), charset);
			assertThrows(java.nio.charset.CharacterCodingException.class,
					() -> decoder.graphic(new byte[] { (byte) 0xFE, (byte) 0xFE }, 0, 2), charset);
		}
		// The converters map X'43A1' differently: JDK 939 to U+FF5E, ICU 1399 to
		// U+301C.
		byte[] tilde = { 0x43, (byte) 0xA1 };
		assertEquals("\uFF5E", HostTextDecoder.of(encodingFor("x-IBM939"), directory).graphic(tilde, 0, 2));
		assertEquals("\u301C", HostTextDecoder.of(encodingFor("x-IBM1399"), directory).graphic(tilde, 0, 2));
	}

	private static CobolCsvExtractConfiguration.Encoding encodingFor(String charset) {
		CobolCsvExtractConfiguration.Encoding encoding = new CobolCsvExtractConfiguration.Encoding();
		encoding.setCharset(charset);
		return encoding;
	}

	@Test
	void decodesHexadecimalFloatingPoint() {
		assertEquals(1.0, CobolFieldDecoder.hexadecimalFloat(0x41100000L, 4));
		assertEquals(-1.0, CobolFieldDecoder.hexadecimalFloat(0xC1100000L, 4));
		assertEquals(100.0, CobolFieldDecoder.hexadecimalFloat(0x42640000L, 4));
		assertEquals(0.5, CobolFieldDecoder.hexadecimalFloat(0x4080000000000000L, 8));
	}

	// ------------------------------------------------------------ helpers

	private CobolCsvExtractConfiguration.Encoding encoding(String charset, String gaijiFile, String gaiji)
			throws Exception {
		Files.writeString(new File(directory, gaijiFile).toPath(), gaiji + "\n");
		CobolCsvExtractConfiguration.Encoding encoding = new CobolCsvExtractConfiguration.Encoding();
		encoding.setCharset(charset);
		encoding.setUserDefinedCharacters(gaijiFile);
		return encoding;
	}

	private static CobolCsvExtractConfiguration.Encoding encodingTable(String shiftOut) {
		CobolCsvExtractConfiguration.Encoding encoding = new CobolCsvExtractConfiguration.Encoding();
		encoding.setProfile(HostTextDecoder.Profile.TABLE);
		encoding.setSingleByteTable("single.txt");
		encoding.setDoubleByteTable("double.txt");
		encoding.setShiftOut(shiftOut);
		return encoding;
	}

	private File contract(String copybook, String importConfiguration) throws Exception {
		File source = new File(directory, "layout.cpy");
		Files.writeString(source.toPath(), copybook);
		File config = new File(directory, "import.yaml");
		Files.writeString(config.toPath(), importConfiguration);
		CobolSchemaImportCommand importer = new CobolSchemaImportCommand();
		importer.setTargetFile(source);
		importer.setConfigurationFile(config);
		importer.setOutputDirectory(directory);
		importer.run();
		GenerateLegacyMigrationContractCommand contract = new GenerateLegacyMigrationContractCommand();
		contract.setMappingFile(new File(directory, "layout-legacy-migration.yaml"));
		contract.setOutputDirectory(directory);
		contract.run();
		return new File(directory, "layout-contract.yaml");
	}

	private CobolCsvExtractCommand extract(File contract, File data, String configuration) throws Exception {
		File config = new File(directory, "extract.yaml");
		Files.writeString(config.toPath(), configuration);
		CobolCsvExtractCommand command = new CobolCsvExtractCommand();
		command.setContractFile(contract);
		command.setConfigurationFile(config);
		command.setDataFile(data);
		command.setOutputDirectory(new File(directory, "csv"));
		command.run();
		return command;
	}

	private File data(byte[] bytes) throws Exception {
		File file = new File(directory, "data.bin");
		Files.write(file.toPath(), bytes);
		return file;
	}

	private List<String> csv(String name) throws Exception {
		return Files.readAllLines(new File(directory, "csv/" + name).toPath(), StandardCharsets.UTF_8);
	}

	static byte[] fixed(byte[][] records) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] record : records) {
			write(out, record);
		}
		return out.toByteArray();
	}

	static byte[] variable(byte[][] records) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] record : records) {
			int length = record.length + 4;
			write(out, new byte[] { (byte) (length >> 8), (byte) length, 0, 0 });
			write(out, record);
		}
		return out.toByteArray();
	}

	static byte[] text(String value, int length) {
		byte[] bytes = Arrays.copyOf(value.getBytes(EBCDIC), length);
		for (int i = value.getBytes(EBCDIC).length; i < length; i++) {
			bytes[i] = 0x40;
		}
		return bytes;
	}

	static byte[] graphic(String value, int characters) {
		byte[] shifted = value.getBytes(EBCDIC);
		byte[] bytes = new byte[characters * 2];
		System.arraycopy(shifted, 1, bytes, 0, shifted.length - 2);
		for (int i = shifted.length - 2; i < bytes.length; i += 2) {
			bytes[i] = 0x40;
			bytes[i + 1] = 0x40;
		}
		return bytes;
	}

	static byte[] packed(long value, int length) {
		String digits = Long.toString(Math.abs(value));
		byte[] bytes = new byte[length];
		int nibbles = length * 2 - 1;
		String padded = "0".repeat(nibbles - digits.length()) + digits;
		for (int i = 0; i < length; i++) {
			int high = padded.charAt(i * 2) - '0';
			int low = i == length - 1 ? (value < 0 ? 0xD : 0xC) : padded.charAt(i * 2 + 1) - '0';
			bytes[i] = (byte) (high << 4 | low);
		}
		return bytes;
	}

	static byte[] binary(long value, int length) {
		byte[] raw = BigInteger.valueOf(value).toByteArray();
		byte[] bytes = new byte[length];
		Arrays.fill(bytes, value < 0 ? (byte) 0xFF : 0);
		System.arraycopy(raw, Math.max(0, raw.length - length), bytes, Math.max(0, length - raw.length),
				Math.min(length, raw.length));
		return bytes;
	}

	static byte[] zoned(String digits, boolean negative) {
		byte[] bytes = new byte[digits.length()];
		for (int i = 0; i < digits.length(); i++) {
			bytes[i] = (byte) (0xF0 | digits.charAt(i) - '0');
		}
		if (negative) {
			bytes[bytes.length - 1] = (byte) (0xD0 | bytes[bytes.length - 1] & 0x0F);
		}
		return bytes;
	}

	static byte[] concat(byte[]... parts) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] part : parts) {
			write(out, part);
		}
		return out.toByteArray();
	}

	private static void write(ByteArrayOutputStream out, byte[] bytes) {
		out.write(bytes, 0, bytes.length);
	}
}
