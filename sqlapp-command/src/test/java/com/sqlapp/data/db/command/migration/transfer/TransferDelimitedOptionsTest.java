/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.Charset;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TransferDelimitedOptionsTest {
	@TempDir
	Path directory;

	private Table table() {
		var table = new Table("ITEMS");
		var key = new Column("ID").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(key);
		table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR));
		table.setPrimaryKey(key);
		return table;
	}

	@ParameterizedTest
	@EnumSource(value = FileFormat.class, names = { "CSV", "TSV" })
	void skipConsumesOneParsedHeaderRecordAndNoneRetainsTheFirstDataRow(FileFormat format) throws Exception {
		var file = directory.resolve("data.txt");
		String delimiter = format == FileFormat.TSV ? "\t" : ",";
		String data = "1" + delimiter + "日本語\n2" + delimiter + "\"\"\n";
		String header = "\"translated\nkey\"" + delimiter + "other name\n";
		Files.writeString(file, header + data, Charset.forName("windows-31j"));
		new TransferCsvSource(table(), file,
				new FileTransfer("packages", format, "windows-31j", "__NULL__", TemporalPolicy.WARN, HeaderMode.SKIP),
				"test", 2).validate();
		assertThrows(IllegalArgumentException.class, () -> new TransferCsvSource(table(), file,
				new FileTransfer("packages", format, "windows-31j", "__NULL__"), "test", 2).validate());
		Files.writeString(file, data, Charset.forName("windows-31j"));
		var source = new TransferCsvSource(table(), file,
				new FileTransfer("packages", format, "windows-31j", "__NULL__", TemporalPolicy.WARN, HeaderMode.NONE),
				"test", 2);
		source.validate();
		var rows = source.iterator(null);
		try {
			var first = rows.next();
			assertEquals(1, (Object) first.get("ID"));
			assertEquals("日本語", first.get("NAME"));
			assertTrue(rows.hasNext());
			assertEquals(2, (Object) rows.next().get("ID"));
			assertFalse(rows.hasNext());
		} finally {
			com.sqlapp.util.iterator.IteratorCloseUtils.close(null, rows);
		}
		Files.writeString(file, "");
		new TransferCsvSource(table(), file,
				new FileTransfer("packages", format, "UTF-8", "__NULL__", TemporalPolicy.WARN, HeaderMode.NONE), "test",
				0).validate();
		assertThrows(IllegalArgumentException.class,
				() -> new TransferCsvSource(table(), file,
						new FileTransfer("packages", format, "UTF-8", "__NULL__", TemporalPolicy.WARN, HeaderMode.SKIP),
						"test", 0).validate());
	}

	@Test
	void legacyDefaultsAndInvalidOptionsRemainExplicit() {
		assertEquals(HeaderMode.VALIDATE, new FileTransfer("packages", FileFormat.CSV, null, "__NULL__").headerMode());
		assertEquals(HeaderMode.NONE, new FileTransfer("packages", FileFormat.JSONL, null, null).headerMode());
		assertEquals("UTF-8", new FileTransfer("packages", FileFormat.TSV, null, "__NULL__").encoding());
		assertThrows(IllegalArgumentException.class, () -> new FileTransfer("packages", FileFormat.JSONL, "UTF-8", null,
				TemporalPolicy.WARN, HeaderMode.SKIP));
		assertThrows(IllegalArgumentException.class,
				() -> new FileTransfer("packages", FileFormat.CSV, "invalid-charset", "__NULL__"));
	}
}
