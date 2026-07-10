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
		assertEquals(2.0d, TextWidthUtils.estimateTextWidth("ｱｲｳ"));
		assertEquals(2.0d, TextWidthUtils.estimateTextWidth("🗝"));
		assertEquals(4.0d, TextWidthUtils.estimateTextWidth("🗝🗝🗝"));
	}

}
