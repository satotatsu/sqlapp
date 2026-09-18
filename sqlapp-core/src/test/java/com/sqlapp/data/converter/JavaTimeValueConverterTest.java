/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class JavaTimeValueConverterTest {
	private final Converters converters = new Converters();

	@Test
	void monthDayRoundTripAndLeapDay() {
		final MonthDay leapDay = MonthDay.of(2, 29);
		assertTrue(converters.isConvertable(MonthDay.class));
		assertEquals(leapDay, converters.convertObject("--02-29", MonthDay.class));
		assertEquals("--02-29", converters.convertString(leapDay));
		assertSame(leapDay, converters.copy(leapDay));
		assertEquals(MonthDay.of(1, 1), converters.convertObject("--01-01", MonthDay.class));
		assertEquals(MonthDay.of(12, 31), converters.convertObject("--12-31", MonthDay.class));
	}

	@Test
	void monthDayRejectsInvalidOrAmbiguousInput() {
		for (String input : new String[] { "--02-30", "--04-31", "--13-01", "--00-01", "02-29", "2024-02-29" }) {
			assertThrows(DateTimeException.class, () -> converters.convertObject(input, MonthDay.class), input);
		}
		assertThrows(DateTimeException.class, () -> converters.convertObject(YearMonth.of(2024, 2), MonthDay.class));
		assertThrows(DateTimeException.class, () -> converters.convertObject(Instant.EPOCH, MonthDay.class));
		assertThrows(ConverterException.class, () -> converters.convertObject(229, MonthDay.class));
	}

	@Test
	void extractsLocalDateFieldsWithoutChangingOffset() {
		final OffsetDateTime date = OffsetDateTime.parse("2024-03-01T00:30:00+14:00");
		assertEquals(MonthDay.of(3, 1), converters.convertObject(date, MonthDay.class));
		assertEquals(Month.MARCH, converters.convertObject(date, Month.class));
		assertEquals(DayOfWeek.FRIDAY, converters.convertObject(date, DayOfWeek.class));
		final java.sql.Date sqlDate = java.sql.Date.valueOf("2024-02-29");
		assertEquals(MonthDay.of(2, 29), converters.convertObject(sqlDate, MonthDay.class));
		assertEquals(Month.FEBRUARY, converters.convertObject(sqlDate, Month.class));
		assertEquals(DayOfWeek.THURSDAY, converters.convertObject(sqlDate, DayOfWeek.class));
		assertEquals(MonthDay.of(2, 29), converters.convertObject(LocalDate.of(2024, 2, 29), MonthDay.class));
	}

	@Test
	void monthDayDefaultsAndWrappers() {
		final MonthDayConverter converter = converters.getConverter(MonthDay.class);
		assertNull(converter.convertObject(null));
		assertNull(converter.convertObject(" "));
		assertNull(converter.format(null));
		converter.setDefaultValue(() -> MonthDay.of(1, 1));
		assertEquals(MonthDay.of(1, 1), converter.convertObject(Optional.empty()));
		assertEquals(MonthDay.of(2, 29), converter.convertObject((Supplier<String>) () -> "--02-29"));
		assertEquals(MonthDay.of(2, 29), converter.convertObject(Optional.of("--02-29")));
	}

	@Test
	void monthDayArraysUseCommonConversionAndCopy() {
		final Iterable<String> input = () -> Arrays.asList("--02-29", null).iterator();
		final MonthDay[] result = converters.convertObject(input, MonthDay[].class);
		assertTrue(converters.isConvertable(MonthDay[].class));
		assertArrayEquals(new MonthDay[] { MonthDay.of(2, 29), null }, result);
		assertArrayEquals(result, converters.convertObject(new String[] { "--02-29", null }, MonthDay[].class));
		assertArrayEquals(result, (MonthDay[]) converters.copy(result));
		assertNotSame(result, converters.copy(result));
		assertEquals("[--02-29, null]", converters.convertString(result));
	}

	@Test
	void convertsIsoEnumNumbersAndRetainsNames() {
		for (int i = 1; i <= 12; i++) {
			assertEquals(Month.of(i), converters.convertObject(i, Month.class));
			assertEquals(Month.of(i), converters.convertObject(String.valueOf(i), Month.class));
			assertEquals(Month.of(i), converters.convertObject(Month.of(i).name(), Month.class));
			assertEquals(Month.of(i).name(), converters.convertString(Month.of(i)));
		}
		for (int i = 1; i <= 7; i++) {
			assertEquals(DayOfWeek.of(i), converters.convertObject(i, DayOfWeek.class));
			assertEquals(DayOfWeek.of(i), converters.convertObject(String.valueOf(i), DayOfWeek.class));
			assertEquals(DayOfWeek.of(i), converters.convertObject(DayOfWeek.of(i).name(), DayOfWeek.class));
			assertEquals(DayOfWeek.of(i).name(), converters.convertString(DayOfWeek.of(i)));
		}
	}

	@Test
	void rejectsOutOfRangeFractionalAndOverflowingEnumNumbers() {
		for (Class<?> type : new Class<?>[] { Month.class, DayOfWeek.class }) {
			for (Object input : new Object[] { 0, -1, 13, new BigDecimal("1.5"), 4294967297L, Double.NaN, "nonsense" }) {
				assertThrows(RuntimeException.class, () -> converters.convertObject(input, type));
			}
		}
		assertThrows(DateTimeException.class, () -> converters.convertObject(8, DayOfWeek.class));
	}

	@Test
	void enumArraysUseEnhancedElementConverters() {
		assertArrayEquals(new Month[] { Month.JANUARY, Month.DECEMBER, null },
				converters.convertObject(new Object[] { 1, "DECEMBER", null }, Month[].class));
		assertArrayEquals(new DayOfWeek[] { DayOfWeek.MONDAY, DayOfWeek.SUNDAY },
				converters.convertObject(Arrays.asList("1", "SUNDAY"), DayOfWeek[].class));
	}

	@Test
	void preservesEnumEmptyStringPolicy() {
		assertThrows(RuntimeException.class, () -> converters.convertObject("", Month.class));
		final Converters nullable = new Converters();
		nullable.setEnumEmptyToNull(true);
		assertNull(nullable.convertObject("", Month.class));
		assertNull(nullable.convertObject("", DayOfWeek.class));
		assertArrayEquals(new Month[] { null }, nullable.convertObject(new String[] { "" }, Month[].class));
		assertArrayEquals(new DayOfWeek[] { null }, nullable.convertObject(new String[] { "" }, DayOfWeek[].class));
	}
}
