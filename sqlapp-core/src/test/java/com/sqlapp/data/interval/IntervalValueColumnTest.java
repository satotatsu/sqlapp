/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.interval;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Period;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.converter.IntervalValueConverter;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.MetaType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class IntervalValueColumnTest {
	@Test
	@SuppressWarnings("unchecked")
	void everyIntervalColumnKeepsCanonicalValuesWithoutLegacyProjection() {
		final IntervalValue value = IntervalValue.parse("P-14M3DT-90000.123456S");
		for (DataType type : DataType.values()) {
			if (type.getMetaTtype() != MetaType.INTERVAL) continue;
			final Table table = new Table("values");
			final Column column = new Column("value").setDataType(type);
			table.getColumns().add(column);
			final var row = table.newRow();
			row.put(column, value);
			assertSame(value, row.get(column), type.name());
			assertEquals(value.toString(), column.getFormatter().format(value), type.name());
			assertEquals(value, column.getConverter().convertObject(column.getFormatter().format(value)), type.name());
			assertSame(value, ((Converter<?>) column.getConverter()).copy(value), type.name());
			assertEquals(value, ((Converter<?>) column.getConverter()).copy(value.toString()), type.name());
		}
	}

	@Test
	void legacyInputsStillUseTheirTypedParser() {
		final Column column = new Column("value").setDataType(DataType.INTERVAL_YEAR_TO_MONTH);
		assertInstanceOf(IntervalYearToMonth.class, column.getConverter().convertObject("1-2"));
		assertNull(column.getConverter().convertObject((Object) null));
	}

	@Test
	void canonicalConverterIsRegisteredAndDoesNotGuessDatabaseText() {
		final var converter = Converters.getDefault().getConverter(IntervalValue.class);
		assertInstanceOf(IntervalValueConverter.class, converter);
		assertNull(converter.convertObject(""));
		assertThrows(java.time.format.DateTimeParseException.class, () -> converter.convertObject(" "));
		final IntervalValue value = IntervalValue.parse("P1DT-1H");
		assertEquals(DataType.INTERVAL, DataType.valueOf(value.getClass()));
		assertSame(value, converter.copy(value));
		assertEquals(value, converter.convertObject(value.toString()));
		assertEquals(new IntervalValue(14, -3, Duration.ZERO), converter.convertObject(Period.of(1, 2, -3)));
		assertEquals(new IntervalValue(0, 0, Duration.ofHours(25)), converter.convertObject(Duration.ofHours(25)));
		assertEquals(new IntervalDay(1).toIntervalValue(), converter.convertObject(new IntervalDay(1)));
		assertThrows(java.time.format.DateTimeParseException.class, () -> converter.convertObject("1 day -1 hour"));
		assertThrows(IllegalArgumentException.class, () -> converter.convertObject(42));
	}
}
