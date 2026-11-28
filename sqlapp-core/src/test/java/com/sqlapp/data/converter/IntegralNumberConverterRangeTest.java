/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * Integral converters truncate fractions as before but reject values outside
 * the target range instead of wrapping or saturating them.
 */
class IntegralNumberConverterRangeTest {

	private final IntegerConverter integer = new IntegerConverter();
	private final ShortConverter shortConverter = new ShortConverter();
	private final ByteConverter byteConverter = new ByteConverter();
	private final LongConverter longConverter = new LongConverter();

	@Test
	void keepsValuesInRangeAndTruncatesFractions() {
		assertEquals(Integer.MAX_VALUE, integer.convertObject((long) Integer.MAX_VALUE));
		assertEquals(Integer.MIN_VALUE, integer.convertObject(new BigDecimal("-2147483648.9")));
		assertEquals(2, integer.convertObject(new BigDecimal("2.5")));
		assertEquals(-2, integer.convertObject(-2.9d));
		assertEquals(2, integer.convertObject(2.99f));
		assertEquals(7, integer.convertObject(BigInteger.valueOf(7)));
		assertEquals(5, integer.convertObject(new AtomicLong(5)));
		assertEquals(12, integer.convertObject("12"));
		assertEquals(12, integer.convertObject(" 12.7 "));
		assertEquals(1000, integer.convertObject("1e3"));
		assertEquals(3, integer.convertObject(OptionalLong.of(3)));
		assertEquals(3, integer.convertObject(OptionalDouble.of(3.5)));
		assertEquals(4, integer.convertObject(Optional.of(4L)));
		assertNull(integer.convertObject(null));

		assertEquals((short) 32767, shortConverter.convertObject(32767L));
		assertEquals((short) -32768, shortConverter.convertObject("-32768.5"));
		assertEquals((byte) 127, byteConverter.convertObject(127.9d));
		assertEquals((byte) -128, byteConverter.convertObject(OptionalLong.of(-128)));
		assertEquals(Long.MAX_VALUE, longConverter.convertObject(new BigDecimal("9223372036854775807.99")));
		assertEquals(Long.MIN_VALUE, longConverter.convertObject(BigInteger.valueOf(Long.MIN_VALUE)));
		assertEquals(Long.MIN_VALUE, longConverter.convertObject(-9.223372036854775808E18));
		assertEquals(123L, longConverter.convertObject("123.9"));
	}

	@Test
	void rejectsValuesOutOfRange() {
		// Previously stored as -1294967296, 2147483647 and so on.
		assertOutOfRange(() -> integer.convertObject(3_000_000_000L), "Integer", "3000000000");
		assertOutOfRange(() -> integer.convertObject(3e9), "Integer", "3.0E9");
		assertOutOfRange(() -> integer.convertObject(new BigDecimal("2147483648")), "Integer", "2147483648");
		assertOutOfRange(() -> integer.convertObject(new BigInteger("99999999999999999999")), "Integer", null);
		assertOutOfRange(() -> integer.convertObject("3000000000"), "Integer", "3000000000");
		assertOutOfRange(() -> integer.convertObject("1e30"), "Integer", null);
		assertOutOfRange(() -> integer.convertObject(OptionalLong.of(3_000_000_000L)), "Integer", null);
		assertOutOfRange(() -> integer.convertObject(OptionalDouble.of(Double.NaN)), "Integer", null);
		assertOutOfRange(() -> integer.convertObject(Double.POSITIVE_INFINITY), "Integer", null);

		assertOutOfRange(() -> shortConverter.convertObject(32768), "Short", null);
		assertOutOfRange(() -> shortConverter.convertObject("-32769"), "Short", null);
		assertOutOfRange(() -> byteConverter.convertObject(128), "Byte", null);
		assertOutOfRange(() -> byteConverter.convertObject(OptionalDouble.of(-129.0)), "Byte", null);
		assertOutOfRange(() -> longConverter.convertObject(new BigDecimal("9223372036854775808")), "Long", null);
		assertOutOfRange(() -> longConverter.convertObject(new BigInteger("-9223372036854775809")), "Long", null);
		assertOutOfRange(() -> longConverter.convertObject(1e19), "Long", null);
		assertOutOfRange(() -> longConverter.convertObject("99999999999999999999"), "Long", null);
	}

	private static void assertOutOfRange(final org.junit.jupiter.api.function.Executable executable, final String type,
			final String value) {
		final ConverterException e = assertThrows(ConverterException.class, executable);
		assertTrue(e.getMessage().startsWith("Value out of range for " + type + ": "), e.getMessage());
		if (value != null) {
			assertTrue(e.getMessage().contains(value), e.getMessage());
		}
	}
}
