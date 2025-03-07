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

package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteOrder;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class BinaryUtilsTest {

	@Test
	public void testToBinaryObject() {
	}

	@Test
	public void testToObject() {
	}

	@Test
	public void testDecodeBase64String() {
	}

	@Test
	public void testDecodeBase64ByteArray() {
	}

	@Test
	public void testEncodeBase64String() {
	}

	@Test
	public void testEncodeBase64ByteArray() {
	}

	@Test
	public void testToBinaryUUID() {
		UUID val=UUID.randomUUID();
		byte[] bytes=BinaryUtils.toBinary(val);
		UUID ret=BinaryUtils.toUUID(bytes);
		assertEquals(val, ret);
	}

	@Test
	public void testToBinaryUUIDArray() {
		UUID[] vals=new UUID[]{UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
		byte[] bytes=BinaryUtils.toBinary(vals);
		UUID[] ret=BinaryUtils.toUUIDArray(bytes);
		assertArrayEquals(vals, ret);
	}

	@Test
	public void testToBinaryByteOrderUUIDArray() {
		UUID[] vals=new UUID[]{UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
		byte[] bytes=BinaryUtils.toBinary(ByteOrder.LITTLE_ENDIAN, vals);
		UUID[] ret=BinaryUtils.toUUIDArray(ByteOrder.LITTLE_ENDIAN, bytes);
		assertArrayEquals(vals, ret);
	}

	@Test
	public void testToBinaryShort() {
		short val=5;
		byte[] bytes=BinaryUtils.toBinary(val);
		short ret=BinaryUtils.toShort(bytes);
		assertEquals(val, ret);
	}

	@Test
	public void testToShortArrayByteArray() {
		short[] vals=new short[]{1, 2, 3};
		byte[] bytes=BinaryUtils.toBinary(vals);
		short[] ret=BinaryUtils.toShortArray(bytes);
		assertArrayEquals(vals, ret);
	}

	@Test
	public void testToShortArrayByteOrderByteArray() {
		short[] vals=new short[]{1, 2, 3};
		byte[] bytes=BinaryUtils.toBinary(ByteOrder.LITTLE_ENDIAN, vals);
		short[] ret=BinaryUtils.toShortArray(ByteOrder.LITTLE_ENDIAN, bytes);
		assertArrayEquals(vals, ret);
	}

	@Test
	public void testToBinaryInt() {
		int val=5;
		byte[] bytes=BinaryUtils.toBinary(val);
		int ret=BinaryUtils.toInt(bytes);
		assertEquals(val, ret);
	}

	@Test
	public void testToIntByteOrderByteArray() {
		int[] vals=new int[]{1, 2, 3};
		byte[] bytes=BinaryUtils.toBinary(vals);
		int[] ret=BinaryUtils.toIntArray(bytes);
		assertArrayEquals(vals, ret);
	}

	@Test
	public void testToIntArrayByteOrderByteArray() {
		int[] vals=new int[]{1, 2, 3};
		byte[] bytes=BinaryUtils.toBinary(ByteOrder.LITTLE_ENDIAN, vals);
		int[] ret=BinaryUtils.toIntArray(ByteOrder.LITTLE_ENDIAN, bytes);
		assertArrayEquals(vals, ret);
	}

	@Test
	public void testToBinaryLong() {
		long val=5L;
		byte[] bytes=BinaryUtils.toBinary(val);
		long ret=BinaryUtils.toLong(bytes);
		assertEquals(val, ret);
	}

	@Test
	public void testToBinaryLongArray() {
		long[] vals=new long[]{1L, 2L, 3L};
		byte[] bytes=BinaryUtils.toBinary(vals);
		long[] ret=BinaryUtils.toLongArray(bytes);
		assertArrayEquals(vals, ret);
	}

	@Test
	public void testToBinaryByteOrderLongArray() {
		long[] vals=new long[]{1L, 2L, 3L};
		byte[] bytes=BinaryUtils.toBinary(ByteOrder.LITTLE_ENDIAN, vals);
		long[] ret=BinaryUtils.toLongArray(ByteOrder.LITTLE_ENDIAN, bytes);
		assertArrayEquals(vals, ret);
	}


}
