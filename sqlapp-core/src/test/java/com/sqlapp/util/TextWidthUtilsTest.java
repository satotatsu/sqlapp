/**
 * Copyright (C) 2026-2027 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core. If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextWidthUtilsTest {

	@Test
	void test() {
		assertEquals(2.0d, TextWidthUtils.estimateTextWidth("aaa"));
		assertEquals(3.0d, TextWidthUtils.estimateTextWidth("WWW"));
		assertEquals(1.0d, TextWidthUtils.estimateTextWidth("iii"));
		assertEquals(3.0d, TextWidthUtils.estimateTextWidth("あいう"));
		assertEquals(3.0d, TextWidthUtils.estimateTextWidth("アイウ"));
		assertEquals(2.0d, TextWidthUtils.estimateTextWidth("ｱｲｳ"));
		assertEquals(2.0d, TextWidthUtils.estimateTextWidth("🗝"));
		assertEquals(4.0d, TextWidthUtils.estimateTextWidth("🗝🗝🗝"));
	}

	@Test
	void test2() {
		assertEquals(7.0d, TextWidthUtils.estimateTextWidth("カレンダー種別"));
		assertEquals(4.0d, TextWidthUtils.estimateTextWidth("abcdefg"));
	}

}
