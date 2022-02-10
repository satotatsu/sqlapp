/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Array;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.TestCaseBase;

public class ConvertersTest2 extends TestCaseBase {
	/**
	 * テスト開始時に状態を初期化します。
	 */
	@Override
	@BeforeEach
	public void setUpTestCaseBase() {
		setTimeZoneJST();
	}

	/**
	 * Optional変換テスト
	 */
	@Test
	public void testOptional() {
		final Converters converters=new Converters();
		final Optional<Integer> opt=Optional.of(Integer.valueOf(5));
		assertEquals(Integer.valueOf(5), converters.convertObject(opt, Integer.class));
		assertEquals(Integer.valueOf(5), converters.convertObject(opt, int.class));
		final Optional<Integer> nopt=Optional.empty();
		assertEquals(Integer.valueOf(0), converters.convertObject(nopt, int.class));
	}

	/**
	 * Number変換テスト
	 */
	@Test
	public void testNumber() {
		final Converters converters=new Converters();
		assertNull(converters.convertObject(" ", Integer.class));
		assertNull(converters.convertObject(" ", Byte.class));
	}
	
	/**
	 * Native変換テスト
	 */
	@Test
	public void testNative() {
		testNative(int.class, Integer.class, 0);
		testNative(byte.class, Byte.class, (byte)0);
		testNative(short.class, Short.class, (short)0);
		testNative(long.class, Long.class, 0L);
		testNative(float.class, Float.class, 0F);
		testNative(double.class, Double.class, 0D);
	}

	private void testNative(final Class<?> clazz, final Class<?> wrapper, final Object defaultValue) {
		final Converters converters=new Converters();
		assertEquals(defaultValue, converters.convertObject(null, clazz));
		assertEquals(defaultValue, converters.convertObject(" ", clazz));
		assertNull(converters.convertObject(null, wrapper));
		assertNull(converters.convertObject(" ", wrapper));
	}

	/**
	 * Native変換テスト
	 */
	@Test
	public void testNativeArray() {
		testNativeArray(new String[] {"1", "", "2"}, int[].class, new int[] {1,0,2});
		testNativeArray(new String[] {"1", "", "2"}, Integer[].class, new Integer[] {1,null,2});
		testNativeArray(new String[] {"1", "", "2"}, byte[].class, new byte[] {1,0,2});
		testNativeArray(new String[] {"1", "", "2"}, Byte[].class, new Byte[] {1,null,2});
		testNativeArray(new String[] {"1", "", "2"}, short[].class, new short[] {1,0,2});
		testNativeArray(new String[] {"1", "", "2"}, Short[].class, new Short[] {1,null,2});
		testNativeArray(new String[] {"1", "", "2"}, long[].class, new long[] {1,0,2});
		testNativeArray(new String[] {"1", "", "2"}, Long[].class, new Long[] {1L,null,2L});
		testNativeArray(new String[] {"1", "", "2"}, float[].class, new float[] {1,0,2});
		testNativeArray(new String[] {"1", "", "2"}, Float[].class, new Float[] {1f,null,2f});
		testNativeArray(new String[] {"1", "", "2"}, double[].class, new double[] {1,0,2});
		testNativeArray(new String[] {"1", "", "2"}, Double[].class, new Double[] {1d,null,2d});
	}

	private void testNativeArray(final String[] input, final Class<?> clazz, final Object output) {
		final Converters converters=new Converters();
		final Object obj=converters.convertObject(input, clazz);
		assertEquals(input.length, Array.getLength(obj));
		assertEquals(clazz, obj.getClass());
		final int size=Array.getLength(obj);
		assertEquals(input.length, size);
		assertEquals(clazz, obj.getClass());
		for(int i=0;i<size;i++) {
			assertEquals(Array.get(output, i), Array.get(obj, i));
		}
	}

	
}
