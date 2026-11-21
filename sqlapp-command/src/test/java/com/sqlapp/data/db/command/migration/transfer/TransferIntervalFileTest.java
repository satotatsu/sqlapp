/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.FileFormat;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.FileTransfer;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class TransferIntervalFileTest {
	@ParameterizedTest
	@EnumSource(FileFormat.class)
	void intervalArraysUseJsonAndPreserveDimensionsNullsAndIndependentComponents(FileFormat format) throws Exception {
		var table = table();
		var column = table.getColumns().get("C").setArrayDimension(2);
		Object[] value = { new Object[] { IntervalValue.parse("P1D"), null, IntervalValue.parse("PT24H") },
				new Object[] { IntervalValue.parse("P1M-1DT0.123456789S"), IntervalValue.ZERO,
						IntervalValue.parse("PT-0.000000001S") } };
		String text = TransferIntervalCodec.encode(column, value);
		var path = directory.resolve("arrays.txt");
		if (format == FileFormat.JSONL) {
			String json = TransferJsonlCodec.write(table, new String[] { "1", text });
			assertTrue(json.contains("\"C\":[["));
			Files.writeString(path, json + "\n");
		} else {
			try (var writer = new com.sqlapp.util.file.CsvWriter(Files.newBufferedWriter(path), options -> {
				options.setQuoteAllFields(true);
				options.getFormat().setDelimiter(format == FileFormat.TSV ? '\t' : ',');
				options.getFormat().setLineSeparator("\n");
			})) {
				writer.writeHeader(new String[] { "ID", "C" });
				writer.writeRow(new String[] { "1", text });
			}
		}
		var source = new TransferCsvSource(table, path,
				new FileTransfer("files", format, "UTF-8", format == FileFormat.JSONL ? null : "__NULL__"), "test", 1);
		source.validate();
		var rows = source.iterator(null);
		try {
			assertTrue(rows.hasNext());
			assertArrayEquals(value, (Object[]) rows.next().get("C"));
			assertFalse(rows.hasNext());
		} finally {
			com.sqlapp.util.iterator.IteratorCloseUtils.close(null, rows);
		}
		for (String invalid : new String[] { "[[\"P1D\"],\"PT1S\"]", "[[1]]", "[[{}]]" })
			assertThrows(IllegalArgumentException.class, () -> TransferIntervalCodec.decode(column, invalid));
	}

	@TempDir
	Path directory;

	private Table table() {
		var table = new Table("T");
		var id = new Column("ID").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(id);
		table.getColumns().add(new Column("C").setDataType(DataType.INTERVAL));
		table.setPrimaryKey(id);
		return table;
	}

	@ParameterizedTest
	@EnumSource(FileFormat.class)
	void actualFilesPreserveCalendarDaysMixedSignsWideRangesNanosAndNull(FileFormat format) throws Exception {
		var table = table();
		var column = table.getColumns().get("C");
		var values = new IntervalValue[] { IntervalValue.parse("P1D"), IntervalValue.parse("PT24H"),
				IntervalValue.parse("P1M-1DT0.123456789S"), IntervalValue.parse("PT-0.000000001S"), IntervalValue.ZERO,
				null, new IntervalValue(Long.MIN_VALUE, Long.MAX_VALUE, Duration.ofSeconds(Long.MIN_VALUE)),
				new IntervalValue(Long.MAX_VALUE, Long.MIN_VALUE, Duration.ofSeconds(Long.MAX_VALUE, 999999999)) };
		var content = new StringBuilder(
				format == FileFormat.JSONL ? "" : format == FileFormat.TSV ? "ID\tC\n" : "ID,C\n");
		for (int i = 0; i < values.length; i++) {
			String text = TransferIntervalCodec.encode(column, values[i]);
			content.append(format == FileFormat.JSONL
					? TransferJsonlCodec.write(table, new String[] { Integer.toString(i + 1), text })
					: (i + 1) + (format == FileFormat.TSV ? "\t" : ",") + (text == null ? "__NULL__" : text));
			content.append('\n');
		}
		var path = Files.writeString(directory.resolve("values.txt"), content);
		var source = new TransferCsvSource(table, path,
				new FileTransfer("files", format, "UTF-8", format == FileFormat.JSONL ? null : "__NULL__"), "test",
				values.length);
		source.validate();
		var rows = source.iterator(null);
		try {
			for (var value : values) {
				assertTrue(rows.hasNext());
				assertEquals(value, rows.next().get("C"));
			}
			assertFalse(rows.hasNext());
		} finally {
			com.sqlapp.util.iterator.IteratorCloseUtils.close(null, rows);
		}
	}

	@ParameterizedTest
	@EnumSource(FileFormat.class)
	void malformedNonNullIntervalsAreRejectedInsteadOfBeingTreatedAsText(FileFormat format) throws Exception {
		var table = table();
		for (String text : new String[] { "", "INTERVAL '1' DAY", "P1D garbage", "PT0.1234567890S" }) {
			var payload = format == FileFormat.JSONL
					? TransferJsonlCodec.write(table, new String[] { "1", text }) + "\n"
					: (format == FileFormat.TSV ? "ID\tC\n1\t" : "ID,C\n1,") + "\"" + text + "\"\n";
			var path = Files.writeString(directory.resolve("bad.txt"), payload);
			var source = new TransferCsvSource(table, path,
					new FileTransfer("files", format, "UTF-8", format == FileFormat.JSONL ? null : "__NULL__"), "test",
					1);
			assertThrows(IllegalArgumentException.class, source::validate);
		}
	}

	@Test
	void emptyOrNullOnlyTablesStillRejectUnsupportedArrayDimensions() {
		var table = table();
		table.getColumns().get("C").setArrayDimension(33);
		assertThrows(IllegalArgumentException.class, () -> TransferCsvSource.validateColumnTypes(table));
	}
}
