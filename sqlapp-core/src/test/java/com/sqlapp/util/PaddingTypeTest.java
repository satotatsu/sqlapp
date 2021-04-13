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
/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

class PaddingTypeTest {

	@Test
	void testAddPaddingBytesRIGHT1() {
		final String text="あいう";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.RIGHT.addPadding(text.getBytes(charset), 10, padText.getBytes(charset));
		assertEquals("あいう \t \t", new String(bytes, charset));
	}

	@Test
	void testAddPaddingBytesRIGHT2() {
		final String text="";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.RIGHT.addPadding(text.getBytes(charset), 4, padText.getBytes(charset));
		assertEquals(" \t \t", new String(bytes, charset));
	}

	@Test
	void testAddPaddingRIGHT1() {
		final String text="あいう";
		final String padText=" \t";
		final String bytes=PaddingType.RIGHT.addPadding(text, 10, padText);
		assertEquals("あいう \t \t \t ", bytes);
	}

	@Test
	void testAddPaddingCodePointRIGHT1() {
		final String text="あい𠀅";
		final String padText=" \t";
		final String bytes=PaddingType.RIGHT.addPaddingCodePoint(text, 10, padText);
		assertEquals("あい𠀅 \t \t \t ", bytes);
	}

	@Test
	void testAddPaddingRIGHT2() {
		final String text="";
		final String padText=" \t";
		final String bytes=PaddingType.RIGHT.addPadding(text, 4, padText);
		assertEquals(" \t \t", bytes);
	}

	@Test
	void testAddPaddingRIGHT3() {
		final String text="あい𠀅";
		final String padText=" \t";
		final String bytes=PaddingType.RIGHT.addPadding(text, 10, padText);
		assertEquals("あい𠀅 \t \t \t", bytes);
	}

	@Test
	void testTrimBytesRIGHT1() {
		final String text=" \t \tあいう \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.RIGHT.trimPadding(text.getBytes(charset), padText.getBytes(charset));
		assertEquals(" \t \tあいう", new String(bytes, charset));
	}

	@Test
	void testTrimBytesRIGHT2() {
		final String text=" \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.RIGHT.trimPadding(text.getBytes(charset), padText.getBytes(charset));
		assertEquals("", new String(bytes, charset));
	}

	@Test
	void testTrimBytesRIGHT3() {
		final String text="";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.RIGHT.trimPadding(text.getBytes(charset), padText.getBytes(charset));
		assertEquals("", new String(bytes, charset));
	}

	@Test
	void testTrimRIGHT1() {
		final String text=" \t \tあいう \t \t";
		final String padText=" \t";
		final String bytes=PaddingType.RIGHT.trimPadding(text, padText);
		assertEquals(" \t \tあいう", bytes);
	}

	@Test
	void testTrimRIGHT2() {
		final String text=" \t \t";
		final String padText=" \t";
		final String bytes=PaddingType.RIGHT.trimPadding(text, padText);
		assertEquals("", bytes);
	}

	@Test
	void testTrimRIGHT3() {
		final String text="";
		final String padText=" \t";
		final String bytes=PaddingType.RIGHT.trimPadding(text, padText);
		assertEquals("", bytes);
	}


	@Test
	void testToStringRIGHT1() {
		final String text=" \t \tあいう \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] textBytes=text.getBytes(charset);
		final int textBytesLen=textBytes.length;
		final byte[] padBytes=padText.getBytes(charset);
		final String bytes=PaddingType.RIGHT.toString(textBytes, 0, textBytesLen, padBytes, charset);
		assertEquals(" \t \tあいう", bytes);
	}

	@Test
	void testToStringRIGHT2() {
		final String text=" \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final String bytes=PaddingType.RIGHT.toString(text.getBytes(charset), 0, text.getBytes(charset).length, padText.getBytes(charset), charset);
		assertEquals("", bytes);
	}

	@Test
	void testToStringRIGHT3() {
		final String text="";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final String bytes=PaddingType.RIGHT.toString(text.getBytes(charset), 0, text.getBytes(charset).length, padText.getBytes(charset), charset);
		assertEquals("", bytes);
	}

	@Test
	void testToStringRIGHT4() {
		final String text=" \t \tあいう \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] textBytes=text.getBytes(charset);
		final int textBytesLen=textBytes.length;
		final byte[] padBytes=padText.getBytes(charset);
		final String bytes=PaddingType.RIGHT.toString(textBytes, 2, textBytesLen, padBytes, charset);
		assertEquals(" \tあいう", bytes);
	}

	@Test
	void testToStringRIGHT5() {
		final String text=" \t \tあいう \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] textBytes=text.getBytes(charset);
		final byte[] padBytes=padText.getBytes(charset);
		final String bytes=PaddingType.RIGHT.toString(textBytes, 6, 4, padBytes, charset);
		assertEquals("いう", bytes);
	}

	@Test
	void testToStringRIGHT6() {
		final String text=" \t \t \t \t \t \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] textBytes=text.getBytes(charset);
		final byte[] padBytes=padText.getBytes(charset);
		final String bytes=PaddingType.RIGHT.toString(textBytes, 6, 4, padBytes, charset);
		assertEquals("", bytes);
	}

	
	@Test
	void testTrimBytesLEFT1() {
		final String text=" \t \tあいう \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.LEFT.trimPadding(text.getBytes(charset), padText.getBytes(charset));
		assertEquals("あいう \t \t", new String(bytes, charset));
	}

	@Test
	void testTrimBytesLEFT2() {
		final String text=" \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.LEFT.trimPadding(text.getBytes(charset), padText.getBytes(charset));
		assertEquals("", new String(bytes, charset));
	}

	@Test
	void testTrimBytesLEFT3() {
		final String text="";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.LEFT.trimPadding(text.getBytes(charset), padText.getBytes(charset));
		assertEquals("", new String(bytes, charset));
	}

	@Test
	void testToStringLEFT1() {
		final String text=" \t \tあいう \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final String bytes=PaddingType.LEFT.toString(text.getBytes(charset), 0, text.getBytes(charset).length, padText.getBytes(charset), charset);
		assertEquals("あいう \t \t", bytes);
	}

	@Test
	void testToStringLEFT2() {
		final String text=" \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final String bytes=PaddingType.LEFT.toString(text.getBytes(charset), 0, text.getBytes(charset).length, padText.getBytes(charset), charset);
		assertEquals("", bytes);
	}

	@Test
	void testToStringLEFT3() {
		final String text="";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final String bytes=PaddingType.LEFT.toString(text.getBytes(charset), 0, text.getBytes(charset).length, padText.getBytes(charset), charset);
		assertEquals("", bytes);
	}

	@Test
	void testToStringLEFT4() {
		final String text=" \t \tあいう \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] textBytes=text.getBytes(charset);
		final int textBytesLen=textBytes.length;
		final byte[] padBytes=padText.getBytes(charset);
		final String bytes=PaddingType.LEFT.toString(textBytes, 6, textBytesLen, padBytes, charset);
		assertEquals("いう \t \t", bytes);
	}

	@Test
	void testToStringLEFT5() {
		final String text=" \t \tあいう \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] textBytes=text.getBytes(charset);
		final byte[] padBytes=padText.getBytes(charset);
		final String bytes=PaddingType.LEFT.toString(textBytes, 6, 4, padBytes, charset);
		assertEquals("いう", bytes);
	}

	@Test
	void testToStringLEFT6() {
		final String text=" \t \t \t \t \t \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] textBytes=text.getBytes(charset);
		final byte[] padBytes=padText.getBytes(charset);
		final String bytes=PaddingType.LEFT.toString(textBytes, 6, 4, padBytes, charset);
		assertEquals("", bytes);
	}

	@Test
	void testtrimLEFT1() {
		final String text=" \t \tあいう \t \t";
		final String padText=" \t";
		final String bytes=PaddingType.LEFT.trimPadding(text, padText);
		assertEquals("あいう \t \t", bytes);
	}

	@Test
	void testtrimLEFT2() {
		final String text=" \t \t";
		final String padText=" \t";
		final String bytes=PaddingType.LEFT.trimPadding(text, padText);
		assertEquals("", bytes);
	}

	@Test
	void testtrimLEFT3() {
		final String text="";
		final String padText=" \t";
		final String bytes=PaddingType.LEFT.trimPadding(text, padText);
		assertEquals("", bytes);
	}

	@Test
	void testAddPaddingBytesLEFT1() {
		final String text="あいう";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.LEFT.addPadding(text.getBytes(charset), 10, padText.getBytes(charset));
		assertEquals(" \t \tあいう", new String(bytes, charset));
	}

	@Test
	void testAddPaddingBytesLEFT2() {
		final String text="";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.LEFT.addPadding(text.getBytes(charset), 4, padText.getBytes(charset));
		assertEquals(" \t \t", new String(bytes, charset));
	}

	@Test
	void testAddPaddingLEFT1() {
		final String text="あいう";
		final String padText=" \t";
		final String bytes=PaddingType.LEFT.addPadding(text, 10, padText);
		assertEquals("\t \t \t \tあいう", bytes);
	}

	@Test
	void testAddPaddingLEFT2() {
		final String text="";
		final String padText=" \t";
		final String bytes=PaddingType.LEFT.addPadding(text, 4, padText);
		assertEquals(" \t \t", bytes);
	}

	@Test
	void testAddPaddingLEFT3() {
		final String text="あい𠀅";
		final String padText=" \t";
		final String bytes=PaddingType.LEFT.addPadding(text, 10, padText);
		assertEquals(" \t \t \tあい𠀅", bytes);
	}

	@Test
	void testAddPaddingCodePointLEFT1() {
		final String text="あい𠀅";
		final String padText=" \t";
		final String bytes=PaddingType.LEFT.addPaddingCodePoint(text, 10, padText);
		assertEquals(" \t \t \tあい𠀅", bytes);
	}

}
