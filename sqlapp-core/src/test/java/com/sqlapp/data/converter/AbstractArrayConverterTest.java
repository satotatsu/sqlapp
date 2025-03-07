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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.TestCaseBase;
import com.sqlapp.util.CommonUtils;

public class AbstractArrayConverterTest extends TestCaseBase {

	AbstractArrayConverter<String[], String> converter = new AbstractArrayConverter<String[], String>(
			Converters.getDefault().getConverter(String.class)) {
		/** serialVersionUID */
		private static final long serialVersionUID = 1L;

		@Override
		protected String[] newArrayInstance(int size) {
			return new String[size];
		}
	};

	/**
	 * 変換テスト1
	 */
	@Test
	public void testConvertObjectObject1() {
		String[] ret = converter.convertObject(new int[] { 0, 1 });
		assertTrue(CommonUtils.eq(new String[] { "0", "1" }, ret));
	}

	/**
	 * 変換テスト1
	 */
	@Test
	public void testConvertObjectObject2() {
		List<Integer> list = CommonUtils.list();
		list.add(Integer.valueOf(0));
		list.add(Integer.valueOf(1));
		String[] ret = converter.convertObject(list);
		assertTrue(CommonUtils.eq(new String[] { "0", "1" }, ret));
	}

}
