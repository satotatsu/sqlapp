package com.sqlapp.data.db.datatype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class BitStringJdbcTypeHandlerTest {
	@Test
	void numericConversionPreservesLargeAndIntegralValues() {
		assertEquals("1" + "0".repeat(64),
				BitStringJdbcTypeHandler.toBitString(new BigDecimal("18446744073709551616"), 0));
		assertEquals("0011", BitStringJdbcTypeHandler.toBitString(new BigDecimal("3.000"), 4));
		assertEquals("101", BitStringJdbcTypeHandler.toBitString(BigInteger.valueOf(5), 0));
		assertEquals("101", BitStringJdbcTypeHandler.toBitString(5L, 0));
		assertEquals("10", BitStringJdbcTypeHandler.toBitString(2.0, 0));
		assertEquals("1" + "0".repeat(64), BitStringJdbcTypeHandler.toBitString(Math.scalb(1.0, 64), 0));
		assertEquals("1" + "0".repeat(64), BitStringJdbcTypeHandler.toBitString(Math.scalb(1.0f, 64), 0));
	}

	@Test
	void invalidNumbersFailBeforeStatementBinding() {
		List<String> calls = new ArrayList<>();
		PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[] {PreparedStatement.class},
				(proxy, method, args) -> { calls.add(method.getName()); return null; });
		var handler = new BitStringJdbcTypeHandler();
		for (Number value : List.of(new BigDecimal("1.5"), new BigDecimal("-0.5"),
				-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
			assertThrows(IllegalArgumentException.class, () -> handler.setObject(statement, 1, value));
		}
		assertEquals(List.of(), calls);
	}

	@Test
	void bitSetUsesTheSameBitOrderAsNumbers() {
		assertEquals("0101", BitStringJdbcTypeHandler.toBitString(BitSet.valueOf(new long[] { 5L }), 4));
		assertEquals(BitStringJdbcTypeHandler.toBitString(5, 4),
				BitStringJdbcTypeHandler.toBitString(BitSet.valueOf(new long[] { 5L }), 4));
		assertEquals("101", BitStringJdbcTypeHandler.toBitString(BitSet.valueOf(new long[] { 5L }), 0));
		assertEquals("0000", BitStringJdbcTypeHandler.toBitString(new BitSet(), 4));
		assertThrows(IllegalArgumentException.class,
				() -> BitStringJdbcTypeHandler.toBitString(BitSet.valueOf(new long[] { 16L }), 4));
	}

	@Test
	void rightAlignedBytesDropOnlyZeroPadding() {
		assertEquals("0101", BitStringJdbcTypeHandler.toBitString(new byte[] { 0x05 }, 4));
		assertEquals("0000000100000001",
				BitStringJdbcTypeHandler.toBitString(new byte[] { 0x01, 0x01 }, 0));
		assertEquals("100000001", BitStringJdbcTypeHandler.toBitString(new byte[] { 0x01, 0x01 }, 9));
		assertThrows(IllegalArgumentException.class,
				() -> BitStringJdbcTypeHandler.toBitString(new byte[] { (byte) 0xF0 }, 4));
	}
}
