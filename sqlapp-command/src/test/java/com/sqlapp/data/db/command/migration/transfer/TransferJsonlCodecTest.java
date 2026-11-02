/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;
import org.junit.jupiter.api.Test;

class TransferJsonlCodecTest {
	@org.junit.jupiter.api.io.TempDir
	java.nio.file.Path directory;

	private Table table() {
		var table = new Table("データ");
		table.getColumns().add(new Column("ID").setDataType(DataType.BIGINT));
		table.getColumns().add(new Column("名前").setDataType(DataType.VARCHAR));
		return table;
	}

	@Test
	void flatRowsPreserveExactNumbersUnicodeNewlinesNullAndEmptyWithoutAHeader() throws Exception {
		var table = table();
		var fields = new String[] { "9007199254740993", "日本語\r\n\"\\N" };
		var line = TransferJsonlCodec.write(table, fields);
		assertFalse(line.contains("\n"));
		assertTrue(line.contains("\"ID\":9007199254740993"));
		assertArrayEquals(fields, TransferJsonlCodec.read(new StringReader(line + "\r\n"), table));
		for (String value : new String[] { null, "", "__SQLAPP_NULL__" }) {
			fields[1] = value;
			assertArrayEquals(fields,
					TransferJsonlCodec.read(new StringReader(TransferJsonlCodec.write(table, fields)), table));
		}
		assertNull(TransferJsonlCodec.read(new StringReader(""), table));
	}

	@Test
	void rejectsMissingExtraDuplicateNestedWrongTypedAndTrailingValues() {
		for (String invalid : new String[] { "", "\n", "null", "[]", "{}", "{\"ID\":1}",
				"{\"ID\":1,\"名前\":null,\"other\":2}", "{\"ID\":1,\"ID\":2,\"名前\":null}", "{\"ID\":\"1\",\"名前\":null}",
				"{\"ID\":1,\"名前\":2}", "{\"ID\":1,\"名前\":{}}", "{\"ID\":1,\"名前\":[]} ", "{\"ID\":1,\"名前\":\"\\uD800\"}",
				"{\"ID\":1,\"名前\":null} {}", "{\"ID\":NaN,\"名前\":null}", "{\"ID\":1e999999999,\"名前\":null}" }) {
			if (invalid.isEmpty())
				continue; // Empty files represent empty tables.
			assertThrows(Exception.class, () -> TransferJsonlCodec.read(new StringReader(invalid), table()), invalid);
		}
		assertThrows(Exception.class, () -> TransferJsonlCodec
				.read(new StringReader("{\"ID\":1,\"名前\":\"" + "x".repeat(1_048_577) + "\"}"), table()));
	}

	@Test
	void sourceValidatesExactCountsNumericKeysNotNullAndUtf8BeforeImport() throws Exception {
		var table = table();
		var key = table.getColumns().get("ID").setNotNull(true);
		table.setPrimaryKey(key);
		var settings = new TransferJobConfiguration.FileTransfer("packages", TransferJobConfiguration.FileFormat.JSONL,
				"UTF-8", null);
		var file = directory.resolve("data.jsonl");
		for (String data : new String[] { "{\"ID\":null,\"名前\":null}\n", "{\"ID\":1.5,\"名前\":null}\n",
				"{\"ID\":9223372036854775808,\"名前\":null}\n", "{\"ID\":2,\"名前\":null}\n{\"ID\":1,\"名前\":null}\n",
				"{\"ID\":1,\"名前\":null}\n{\"ID\":1,\"名前\":null}\n" }) {
			java.nio.file.Files.writeString(file, data);
			assertThrows(IllegalArgumentException.class,
					() -> new TransferCsvSource(table, file, settings, "test", 1).validate());
		}
		java.nio.file.Files.writeString(file, "{\"名前\":\"\\\\N\",\"ID\":1}\n");
		new TransferCsvSource(table, file, settings, "test", 1).validate();
		assertThrows(IllegalArgumentException.class,
				() -> new TransferCsvSource(table, file, settings, "test", 2).validate());
		java.nio.file.Files.write(file, new byte[] { (byte) 0xc3, (byte) 0x28 });
		assertThrows(IllegalArgumentException.class,
				() -> new TransferCsvSource(table, file, settings, "test", 1).validate());
	}
}
