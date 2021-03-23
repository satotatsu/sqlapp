/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

class BytePaddingTest {

	@Test
	void testtoPaddingBytesSUFFIX1() {
		final String text="あいう";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=BytePadding.SUFFIX.toPaddingBytes(text.getBytes(charset), 10, padText.getBytes(charset));
		assertEquals("あいう \t \t", new String(bytes, charset));
	}

	@Test
	void testtoPaddingBytesSUFFIX2() {
		final String text="";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=BytePadding.SUFFIX.toPaddingBytes(text.getBytes(charset), 4, padText.getBytes(charset));
		assertEquals(" \t \t", new String(bytes, charset));
	}

	@Test
	void testtrimBytesSUFFIX1() {
		final String text=" \t \tあいう \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=BytePadding.SUFFIX.trimBytes(text.getBytes(charset), padText.getBytes(charset));
		assertEquals(" \t \tあいう", new String(bytes, charset));
	}

	@Test
	void testtrimBytesSUFFIX2() {
		final String text=" \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=BytePadding.SUFFIX.trimBytes(text.getBytes(charset), padText.getBytes(charset));
		assertEquals("", new String(bytes, charset));
	}

	@Test
	void testtrimBytesSUFFIX3() {
		final String text="";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=BytePadding.SUFFIX.trimBytes(text.getBytes(charset), padText.getBytes(charset));
		assertEquals("", new String(bytes, charset));
	}

	@Test
	void testtrimBytesPREFIX1() {
		final String text=" \t \tあいう \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=BytePadding.PREFIX.trimBytes(text.getBytes(charset), padText.getBytes(charset));
		assertEquals("あいう \t \t", new String(bytes, charset));
	}

	@Test
	void testtrimBytesPREFIX2() {
		final String text=" \t \t";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=BytePadding.PREFIX.trimBytes(text.getBytes(charset), padText.getBytes(charset));
		assertEquals("", new String(bytes, charset));
	}

	@Test
	void testtrimBytesPREFIX3() {
		final String text="";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=BytePadding.PREFIX.trimBytes(text.getBytes(charset), padText.getBytes(charset));
		assertEquals("", new String(bytes, charset));
	}

	@Test
	void testtoPaddingBytesPREFIX1() {
		final String text="あいう";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=BytePadding.PREFIX.toPaddingBytes(text.getBytes(charset), 10, padText.getBytes(charset));
		assertEquals(" \t \tあいう", new String(bytes, charset));
	}

	@Test
	void testtoPaddingBytesPREFIX2() {
		final String text="";
		final String padText=" \t";
		final Charset charset=Charset.forName("MS932");
		final byte[] bytes=BytePadding.PREFIX.toPaddingBytes(text.getBytes(charset), 4, padText.getBytes(charset));
		assertEquals(" \t \t", new String(bytes, charset));
	}
}
