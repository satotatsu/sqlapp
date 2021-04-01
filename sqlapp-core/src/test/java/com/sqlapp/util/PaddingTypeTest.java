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
