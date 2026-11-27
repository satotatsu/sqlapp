/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import org.junit.jupiter.api.Test;

class TransferCsvTemporalCodecTest {
	@Test
	void rejectsInvalidDatesOffsetsAndPrecisionLossRatherThanNormalizing() {
		final var date = new Column("D").setDataType(DataType.DATE);
		final var time = new Column("T").setDataType(DataType.TIME).setLength(0);
		final var timestamp = new Column("TS").setDataType(DataType.TIMESTAMP).setLength(9);
		for (String invalid : new String[] { "2023-02-29", "2024-02-30", "0000-01-01", "2024-1-1", "2024-01-01Z",
				" 2024-01-01" })
			assertThrows(RuntimeException.class, () -> TransferCsvTemporalCodec.decode(date, invalid), invalid);
		for (String invalid : new String[] { "24:00:00", "23:59:60", "12:00", "12:00:00.1", "12:00:00Z",
				"12:00:00+09:00" })
			assertThrows(RuntimeException.class, () -> TransferCsvTemporalCodec.decode(time, invalid), invalid);
		for (String invalid : new String[] { "2024-02-30T12:00:00", "2024-01-01 12:00:00", "2024-01-01T12:00:00Z",
				"2024-01-01T12:00:00.1234567890" })
			assertThrows(RuntimeException.class, () -> TransferCsvTemporalCodec.decode(timestamp, invalid), invalid);
		assertThrows(RuntimeException.class,
				() -> TransferCsvTemporalCodec.decode(timestamp.clone().setLength(3), "2024-01-01T12:00:00.1234"));
		for (var column : new Column[] { time.clone().setLength(6), time.clone().setLength(null),
				timestamp.clone().setLength(null), new Column("TZ").setDataType(DataType.TIMESTAMP_WITH_TIMEZONE),
				new Column("ALIAS").setDataType(DataType.DATETIME) })
			assertThrows(IllegalArgumentException.class, () -> TransferCsvTemporalCodec.decode(column, null));
	}

	@Test
	void preservesNanosecondsAndRejectsLocalTimesNormalizedByTheRuntimeTimezone() {
		final var timestamp = new Column("TS").setDataType(DataType.TIMESTAMP).setLength(9);
		final var original = java.util.TimeZone.getDefault();
		try {
			java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"));
			final var value = (java.sql.Timestamp) TransferCsvTemporalCodec.decode(timestamp,
					"2024-02-29T23:59:59.123456789");
			assertEquals(123456789, value.getNanos());
			assertEquals("2024-02-29T23:59:59.123456789", TransferCsvTemporalCodec.encode(timestamp, value));
			assertThrows(IllegalArgumentException.class,
					() -> TransferCsvTemporalCodec.decode(timestamp, "2024-03-10T02:30:00"));
			assertNull(TransferCsvTemporalCodec.decode(timestamp, null));
		} finally {
			java.util.TimeZone.setDefault(original);
		}
	}
}
