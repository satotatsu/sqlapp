/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.converter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.sqlapp.util.OnOffAutoType;
import com.sqlapp.util.OnOffType;

class BooleanConverterTest {

	private final Converters converters = new Converters();

	@Test
	void convertsWords() {
		final BooleanConverter converter = new BooleanConverter();
		assertEquals(Boolean.TRUE, converter.convertObject("true"));
		assertEquals(Boolean.TRUE, converter.convertObject(" YES "));
		assertEquals(Boolean.TRUE, converter.convertObject("'On'"));
		assertEquals(Boolean.FALSE, converter.convertObject("f"));
		assertEquals(Boolean.FALSE, converter.convertObject("x'0'"));
		assertEquals(Boolean.FALSE, converter.convertObject("ng"));
		assertEquals(Boolean.TRUE, converter.convertObject(Boolean.TRUE));
		assertEquals(Boolean.TRUE, converter.convertObject(new AtomicBoolean(true)));
		assertEquals(Boolean.TRUE, converter.convertObject('Y'));
	}

	@Test
	void convertsNumbersZeroAsFalseOthersAsTrue() {
		final BooleanConverter converter = new BooleanConverter();
		assertEquals(Boolean.TRUE, converter.convertObject(new BigDecimal("1.00")));
		assertEquals(Boolean.FALSE, converter.convertObject(new BigDecimal("0.00")));
		assertEquals(Boolean.FALSE, converter.convertObject(new BigDecimal("0E-10")));
		assertEquals(Boolean.TRUE, converter.convertObject(2));
		assertEquals(Boolean.TRUE, converter.convertObject(-1L));
		assertEquals(Boolean.TRUE, converter.convertObject(0.5d));
		assertEquals(Boolean.FALSE, converter.convertObject(-0.0d));
		assertEquals(Boolean.FALSE, converter.convertObject((short) 0));
		assertEquals(Boolean.TRUE, converter.convertObject(BigInteger.TEN));
		assertEquals(Boolean.TRUE, converter.convertObject(new AtomicLong(3)));
		assertEquals(Boolean.TRUE, converter.convertObject(Double.POSITIVE_INFINITY));
	}

	@Test
	void convertsNumericText() {
		final BooleanConverter converter = new BooleanConverter();
		assertEquals(Boolean.TRUE, converter.convertObject("1.00"));
		assertEquals(Boolean.FALSE, converter.convertObject("0.000"));
		assertEquals(Boolean.TRUE, converter.convertObject("2"));
		assertEquals(Boolean.TRUE, converter.convertObject("-1"));
		assertEquals(Boolean.TRUE, converter.convertObject("+0.5"));
		assertEquals(Boolean.TRUE, converter.convertObject("1e3"));
		assertEquals(Boolean.FALSE, converter.convertObject("'0.0'"));
		assertEquals(Boolean.TRUE, converter.convertObject("'2'"));
	}

	@Test
	void emptyValuesUseDefault() {
		final BooleanConverter converter = new BooleanConverter();
		assertNull(converter.convertObject(null));
		assertNull(converter.convertObject(""));
		assertNull(converter.convertObject("   "));
		assertNull(converter.convertObject(Optional.empty()));
		assertEquals(Boolean.TRUE, converter.convertObject(Optional.of("yes")));
		assertEquals(Boolean.FALSE, converter.convertObject((java.util.function.Supplier<Object>) () -> 0));
	}

	@Test
	void rejectsUnknownValues() {
		final BooleanConverter converter = new BooleanConverter();
		assertThrows(ConverterException.class, () -> converter.convertObject("maybe"));
		assertThrows(ConverterException.class, () -> converter.convertObject("1.0.0"));
		assertThrows(ConverterException.class, () -> converter.convertObject("'"));
		assertThrows(ConverterException.class, () -> converter.convertObject("''"));
		assertThrows(ConverterException.class, () -> converter.convertObject(Double.NaN));
		assertThrows(ConverterException.class, () -> converter.convertObject(Float.NaN));
	}

	@Test
	void primitiveBooleanBehavesTheSame() {
		final Converter<Boolean> converter = converters.getConverter(boolean.class);
		assertEquals(Boolean.TRUE, converter.convertObject(new BigDecimal("1.00")));
		assertEquals(Boolean.FALSE, converter.convertObject("0.00"));
		assertEquals(Boolean.TRUE, converter.convertObject(2));
		assertEquals(Boolean.FALSE, converter.convertObject(null));
		assertEquals(Boolean.FALSE, converter.convertObject(""));
		assertThrows(ConverterException.class, () -> converter.convertObject("maybe"));
		assertEquals(Boolean.TRUE, converters.convertObject(new BigDecimal("1.00"), boolean.class));
		assertEquals(Boolean.FALSE, converters.convertObject(null, boolean.class));
		assertThrows(ConverterException.class, () -> converters.convertObject("maybe", boolean.class));
	}

	@Test
	void arraysBehaveTheSame() {
		assertArrayEquals(new boolean[] { true, false, true, false },
				converters.convertObject(new Object[] { new BigDecimal("1.00"), "0.0", 2, null }, boolean[].class));
		assertArrayEquals(new Boolean[] { true, false, null },
				converters.convertObject(new Object[] { "-1", 0L, "" }, Boolean[].class));
		assertThrows(ConverterException.class,
				() -> converters.convertObject(new Object[] { "maybe" }, boolean[].class));
	}

	@Test
	void quotedPairsAreBalanced() {
		final BooleanConverter converter = new BooleanConverter();
		converter.setFalseString("'0'");
		assertEquals("'1'", converter.getTrueString());
		assertEquals("'0'", converter.getFalseString());
		converter.setTrueString("'yes'");
		assertEquals("'no'", converter.getFalseString());
	}

	@Test
	void booleanTrueInstanceKeepsPrimitiveDefault() {
		final Converters trueConverters = Converters.getNewBooleanTrueInstance();
		final Converter<Boolean> objectConverter = trueConverters.getConverter(Boolean.class);
		final Converter<Boolean> primitiveConverter = trueConverters.getConverter(boolean.class);
		assertEquals("true", objectConverter.format(true));
		assertEquals("false", objectConverter.format(false));
		assertEquals("true", primitiveConverter.format(true));
		assertEquals("false", primitiveConverter.format(false));
		assertNull(objectConverter.convertObject(""));
		assertEquals(Boolean.FALSE, primitiveConverter.convertObject(""));
		assertEquals("true", trueConverters.convertString(Boolean.TRUE));
		assertEquals("1", converters.convertString(Boolean.TRUE));
	}

	@Test
	void lenientCallersKeepDefaultForUnknownText() {
		assertEquals(OnOffType.OFF, OnOffType.parse("maybe"));
		assertEquals(OnOffType.ON, OnOffType.parse("2"));
		assertEquals(OnOffAutoType.OFF, OnOffAutoType.parse("maybe"));
		assertEquals(OnOffAutoType.AUTO, OnOffAutoType.parse("auto"));
		assertEquals(OnOffAutoType.ON, OnOffAutoType.parse(new BigDecimal("1.00")));
	}
}
