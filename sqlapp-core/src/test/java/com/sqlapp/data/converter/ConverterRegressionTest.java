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

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.sql.Array;
import java.sql.Clob;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class ConverterRegressionTest {

	private final Converters converters = new Converters();

	@Test
	void convertsCharactersWithoutTruncation() {
		assertEquals(Character.valueOf('a'), converters.convertObject("a", Character.class));
		assertEquals(Character.valueOf(' '), converters.convertObject(" ", char.class));
		assertEquals(Character.valueOf('\0'), converters.convertObject(null, char.class));
		assertEquals(Character.valueOf('\0'), converters.convertObject("", char.class));
		assertNull(converters.convertObject(null, Character.class));
		assertNull(converters.convertObject("", Character.class));
		assertEquals(Character.valueOf('日'), converters.convertObject(new StringBuilder("日"), Character.class));
		assertThrows(ConverterException.class, () -> converters.convertObject("ab", Character.class));
		assertThrows(ConverterException.class, () -> converters.convertObject("\uD83D\uDE00", char.class));
		assertThrows(ConverterException.class, () -> converters.convertObject(65, char.class));
	}

	@Test
	void characterConverterSupportsWrappersAndCopy() {
		final CharacterConverter converter = converters.getConverter(Character.class);
		assertEquals(Character.valueOf('x'), converter.convertObject((Supplier<String>) () -> "x"));
		assertEquals(Character.valueOf('x'), converter.convertObject(Optional.of("x")));
		assertNull(converter.convertObject(Optional.empty()));
		assertEquals(Character.valueOf('x'), converter.copy('x'));
		assertEquals("x", converters.convertString('x'));
	}

	@Test
	void convertsCharacterArraysAndText() {
		assertArrayEquals(new char[] { 'a', '\0', ' ' },
				converters.convertObject(Arrays.asList("a", null, " "), char[].class));
		assertArrayEquals(new Character[] { 'a', null, ' ' },
				converters.convertObject(new String[] { "a", null, " " }, Character[].class));
		final String text = "a日\uD83D\uDE00";
		final char[] result = converters.convertObject(text, char[].class);
		assertEquals(text, converters.convertString(result));
		assertArrayEquals(new char[0], converters.convertObject("", char[].class));
		assertArrayEquals(result, (char[]) converters.copy(result));
		assertNotSame(result, converters.copy(result));
	}

	@Test
	void convertsBooleanArrays() {
		assertArrayEquals(new boolean[] { true, false, false, true },
				converters.convertObject(new String[] { "true", null, "off", "1" }, boolean[].class));
		assertArrayEquals(new Boolean[] { true, null, false },
				converters.convertObject(Arrays.asList("yes", null, "no"), Boolean[].class));
		assertEquals("[true, false]", converters.convertString(new boolean[] { true, false }));
		assertArrayEquals(new boolean[0], converters.convertObject(List.of(), boolean[].class));
	}

	@Test
	void convertsNonCollectionIterablesToPrimitiveArrays() {
		final Iterable<String> input = () -> Arrays.asList("1", null, "0").iterator();
		assertArrayEquals(new int[] { 1, 0, 0 }, converters.convertObject(input, int[].class));
		assertArrayEquals(new boolean[] { true, false, false }, converters.convertObject(input, boolean[].class));
		assertArrayEquals(new char[] { '1', '\0', '0' }, converters.convertObject(input, char[].class));
	}

	@Test
	void convertsJdbcArrayAndReleasesIt() {
		final AtomicBoolean freed = new AtomicBoolean();
		final Array input = jdbcArray(new String[] { "1", null, "2" }, freed);
		assertArrayEquals(new int[] { 1, 0, 2 }, converters.convertObject(input, int[].class));
		assertTrue(freed.get());
	}

	@Test
	void releasesJdbcArrayOnConversionFailure() {
		final AtomicBoolean freed = new AtomicBoolean();
		assertThrows(ConverterException.class,
				() -> converters.convertObject(jdbcArray(new String[] { "ab" }, freed), char[].class));
		assertTrue(freed.get());
	}

	private Array jdbcArray(final Object values, final AtomicBoolean freed) {
		return (Array) Proxy.newProxyInstance(Array.class.getClassLoader(), new Class<?>[] { Array.class },
				(proxy, method, args) -> {
					if (method.getName().equals("getArray")) {
						return values;
					} else if (method.getName().equals("free")) {
						freed.set(true);
						return null;
					}
					throw new UnsupportedOperationException(method.getName());
				});
	}

	@Test
	void formatsPrimitiveNumericArrays() {
		assertEquals("[1, 2]", converters.convertString(new int[] { 1, 2 }));
		assertEquals("[1, 2]", converters.convertString(new long[] { 1, 2 }));
		assertEquals("[1, 2]", converters.convertString(new short[] { 1, 2 }));
		assertEquals("[1.0, 2.0]", converters.convertString(new float[] { 1, 2 }));
		assertEquals("[1.0, 2.0]", converters.convertString(new double[] { 1, 2 }));
	}

	@Test
	void evaluatesArrayDefaultSupplierAndCopiesItsValue() {
		final Converter<int[]> converter = converters.getConverter(int[].class);
		final int[] value = { 1, 2 };
		converter.setDefaultValue(() -> value);
		assertArrayEquals(value, converter.getDefaultValue());
		assertNotSame(value, converter.getDefaultValue());
		converter.setDefaultValue(() -> null);
		assertNull(converter.getDefaultValue());
	}

	@Test
	void usesConfiguredStringConverterForScalarAndArray() {
		final TrimStringConverter converter = new TrimStringConverter();
		converters.setStringConverter(converter);
		assertSame(converter, converters.getConverter(String.class));
		assertSame(converter, converters.getConverter(Clob.class));
		assertEquals("a", converters.convertObject(" a ", String.class));
		assertArrayEquals(new String[] { "a" }, converters.convertObject(new String[] { " a " }, String[].class));
	}

	@Test
	void formatsNullWithInternEnabled() {
		final StringConverter converter = new StringConverter();
		converter.setUseIntern(true);
		assertNull(converter.format(null));
	}

	@Test
	void convertsNumberAndFormatsUnregisteredNumberSubclass() {
		assertTrue(converters.isConvertable(Number.class));
		assertEquals(new BigDecimal("1.25"), converters.convertObject("1.25", Number.class));
		assertEquals("42", converters.convertString(new AtomicInteger(42)));
		final NumberConverter converter = new NumberConverter().setDefaultClass(Long.class);
		converters.setNumberConverter(converter);
		assertEquals(42L, converters.convertObject("42", Number.class));
		assertArrayEquals(new Number[] { 42L }, converters.convertObject(new String[] { "42" }, Number[].class));
	}

	@Test
	void formatsLocalDateTimeAsIsoWithoutAnOffset() {
		converters.toIsoDateFormat();
		assertEquals("2026-09-18T12:34:56.123456789",
				converters.convertString(LocalDateTime.of(2026, 9, 18, 12, 34, 56, 123456789)));
	}

	@Test
	void formatsNumbersWithConfiguredNumberFormat() {
		assertNumberFormat(Byte.class, (byte) 12);
		assertNumberFormat(Short.class, (short) 12);
		assertNumberFormat(Integer.class, 12);
		assertNumberFormat(Long.class, 12L);
		assertNumberFormat(Float.class, 12f);
		assertNumberFormat(Double.class, 12d);
		assertNumberFormat(BigInteger.class, BigInteger.valueOf(12));
		assertNumberFormat(BigDecimal.class, BigDecimal.valueOf(12));
	}

	private <T extends Number> void assertNumberFormat(final Class<T> type, final T value) {
		final AbstractNumberConverter<T> converter = converters.getConverter(type);
		converter.setNumberFormat(new DecimalFormat("000.00", DecimalFormatSymbols.getInstance(Locale.ROOT)));
		assertEquals("012.00", converters.convertString(value));
	}

	@Test
	void preservesZoneOffsetWhenConvertingToTimeZone() {
		final TimeZone result = converters.convertObject(ZoneOffset.ofHoursMinutes(5, 30), TimeZone.class);
		assertEquals(19800000, result.getRawOffset());
	}

	@Test
	void comparesDefaultConvertersWithoutCastingToDateConverter() {
		assertEquals(new DefaultConverter(), new DefaultConverter());
	}
}
