/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class UUIDConverterTest {

	private final UUIDConverter converter = new UUIDConverter();

	@Test
	void convertsPlainAndBracedGuidText() {
		final UUID expected = UUID.fromString("12345678-1234-1234-1234-1234567890ab");
		assertEquals(expected, converter.convertObject("12345678-1234-1234-1234-1234567890ab"));
		// Access / SQL Server GUID text
		assertEquals(expected, converter.convertObject("{12345678-1234-1234-1234-1234567890AB}"));
		assertEquals(expected, converter.convertObject(" {12345678-1234-1234-1234-1234567890AB} "));
	}
}
