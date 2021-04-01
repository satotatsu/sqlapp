/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

class PaddingTypeTest {

	@Test
	void testtoPaddingBytesRIGHT1() {
		final String text="あいう";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.RIGHT.addPadding(text.getBytes(charset), 10, padText.getBytes(charset));
		assertEquals("あいう \t \t", new String(bytes, charset));
	}

	@Test
	void testtoPaddingBytesRIGHT2() {
		final String text="";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.RIGHT.addPadding(text.getBytes(charset), 4, padText.getBytes(charset));
		assertEquals(" \t \t", new String(bytes, charset));
	}

	@Test
	void testtoPaddingRIGHT1() {
		final String text="あいう";
		final String padText=" \t";
		final String bytes=PaddingType.RIGHT.addPadding(text, 10, padText);
		assertEquals("あいう \t \t \t ", bytes);
	}

	@Test
	void testtoPaddingRIGHT2() {
		final String text="";
		final String padText=" \t";
		final String bytes=PaddingType.RIGHT.addPadding(text, 4, padText);
		assertEquals(" \t \t", bytes);
	}

	@Test
	void testtrimBytesRIGHT1() {
		final String text=" \t \tあいう \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.RIGHT.trimPadding(text.getBytes(charset), padText.getBytes(charset));
		assertEquals(" \t \tあいう", new String(bytes, charset));
	}

	@Test
	void testtrimBytesRIGHT2() {
		final String text=" \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.RIGHT.trimPadding(text.getBytes(charset), padText.getBytes(charset));
		assertEquals("", new String(bytes, charset));
	}

	@Test
	void testtrimBytesRIGHT3() {
		final String text="";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.RIGHT.trimPadding(text.getBytes(charset), padText.getBytes(charset));
		assertEquals("", new String(bytes, charset));
	}

	@Test
	void testtrimRIGHT1() {
		final String text=" \t \tあいう \t \t";
		final String padText=" \t";
		final String bytes=PaddingType.RIGHT.trimPadding(text, padText);
		assertEquals(" \t \tあいう", bytes);
	}

	@Test
	void testtrimRIGHT2() {
		final String text=" \t \t";
		final String padText=" \t";
		final String bytes=PaddingType.RIGHT.trimPadding(text, padText);
		assertEquals("", bytes);
	}

	@Test
	void testtrimRIGHT3() {
		final String text="";
		final String padText=" \t";
		final String bytes=PaddingType.RIGHT.trimPadding(text, padText);
		assertEquals("", bytes);
	}

	@Test
	void testtrimBytesLEFT1() {
		final String text=" \t \tあいう \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.LEFT.trimPadding(text.getBytes(charset), padText.getBytes(charset));
		assertEquals("あいう \t \t", new String(bytes, charset));
	}

	@Test
	void testtrimBytesLEFT2() {
		final String text=" \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.LEFT.trimPadding(text.getBytes(charset), padText.getBytes(charset));
		assertEquals("", new String(bytes, charset));
	}

	@Test
	void testtrimBytesLEFT3() {
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
	void testtoPaddingBytesLEFT1() {
		final String text="あいう";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.LEFT.addPadding(text.getBytes(charset), 10, padText.getBytes(charset));
		assertEquals(" \t \tあいう", new String(bytes, charset));
	}

	@Test
	void testtoPaddingBytesLEFT2() {
		final String text="";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=PaddingType.LEFT.addPadding(text.getBytes(charset), 4, padText.getBytes(charset));
		assertEquals(" \t \t", new String(bytes, charset));
	}

	@Test
	void testtoPaddingLEFT1() {
		final String text="あいう";
		final String padText=" \t";
		final String bytes=PaddingType.LEFT.addPadding(text, 10, padText);
		assertEquals(" \t \t \t \tあいう", bytes);
	}

	@Test
	void testtoPaddingLEFT2() {
		final String text="";
		final String padText=" \t";
		final String bytes=PaddingType.LEFT.addPadding(text, 4, padText);
		assertEquals(" \t \t", bytes);
	}

}
