/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.MetaType;
import com.sqlapp.data.interval.IntervalDayToSecond;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

class TransferIntervalCodecTest {
	static Stream<DataType> qualifiers() {
		return Stream.of(DataType.values()).filter(type -> type.getMetaTtype() == MetaType.INTERVAL);
	}

	@ParameterizedTest
	@MethodSource("qualifiers")
	void fileRepresentationDoesNotPerformDestinationNormalization(DataType type) {
		var column = new Column("C").setDataType(type);
		// Qualifier and precision conversion is checked by the destination adapter,
		// never by guessing a database interpretation while reading a file.
		for (String text : new String[] { "P1D", "PT24H", "P1M-1DT0.123456789S", "PT-0.000000001S", "PT-0S" }) {
			var value = IntervalValue.parse(text);
			assertEquals(value, TransferIntervalCodec.decode(column, TransferIntervalCodec.encode(column, value)));
		}
		assertNotEquals(TransferIntervalCodec.decode(column, "P1D"), TransferIntervalCodec.decode(column, "PT24H"));
		assertEquals("PT86400S", TransferIntervalCodec.encode(column, IntervalValue.parse("PT24H")));
		assertEquals("PT0S", TransferIntervalCodec.encode(column, IntervalValue.parse("PT-0S")));
		assertNull(TransferIntervalCodec.decode(column, null));
		assertNull(TransferIntervalCodec.encode(column, null));
	}

	@Test
	void legacyValuesBridgeExplicitlyAndAllCanonicalBoundsRoundTrip() {
		var column = new Column("C").setDataType(DataType.INTERVAL);
		var old = new IntervalDayToSecond(1, 2, 3, 4, 5);
		assertEquals(old.toIntervalValue(), TransferIntervalCodec.decode(column, TransferIntervalCodec.encode(column, old)));
		for (var value : new IntervalValue[] {
				new IntervalValue(Long.MIN_VALUE, Long.MAX_VALUE, Duration.ofSeconds(Long.MIN_VALUE)),
				new IntervalValue(Long.MAX_VALUE, Long.MIN_VALUE, Duration.ofSeconds(Long.MAX_VALUE, 999999999)) }) {
			assertEquals(value, TransferIntervalCodec.decode(column, TransferIntervalCodec.encode(column, value)));
		}
	}

	@Test
	void strictSyntaxArrayRejectionAndTurkishLocale() {
		var column = new Column("elapsed").setDataType(DataType.INTERVAL_DAY_TO_SECOND);
		for (String text : new String[] { "", " ", "INTERVAL '1' DAY", "1-2-3 4:5:6", "P1D garbage",
				"PT0.1234567890S", "PTNaNS" }) {
			var failure = assertThrows(IllegalArgumentException.class, () -> TransferIntervalCodec.decode(column, text));
			assertTrue(failure.getMessage().contains("elapsed"));
		}
		assertThrows(IllegalArgumentException.class, () -> TransferIntervalCodec.encode(column, ""));
		assertNull(TransferIntervalCodec.decode(column.clone().setArrayDimension(1), null));
		assertThrows(IllegalArgumentException.class, () -> TransferIntervalCodec.decode(column.clone().setArrayDimension(33), null));
		var previous = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			assertEquals("P1DT-0.123456789S", TransferIntervalCodec.encode(column,
					TransferIntervalCodec.decode(column, "p1dt-0.123456789s")));
		} finally { Locale.setDefault(previous); }
	}
}
