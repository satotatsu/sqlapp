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

package com.sqlapp.util.eval.mvel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalField;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.util.FileUtils;

public class MvelUtilsTest {

	@BeforeEach
	public void setUp() throws Exception {
	}

	@Test
	public void testParseBean() throws ParseException, URISyntaxException, IOException {
		MvelUtils.setBasePath("src/test/resources");
		String path = MvelUtils.writeZip("com/sqlapp/data/schemas", "schemas.zip", "MS932");
		FileUtils.remove("src/test/resources/schemas.zip");
	}

	@Test
	public void testNextHex() {
		String value = MvelUtils.nextHex(10);
		System.out.println("nextHex(10)=" + value);
		assertEquals(10, value.length());
	}

	@Test
	public void testNextAlphaNumeric() {
		String value = MvelUtils.nextAlphaNumeric(10);
		System.out.println("nextAlphaNumeric(10)=" + value);
		assertEquals(10, value.length());
	}

	@Test
	public void testNextAlpha() {
		String value = MvelUtils.nextAlpha(10);
		System.out.println("nextAlpha(10)=" + value);
		assertEquals(10, value.length());
	}

	@Test
	public void testAddSeconds() {
		int add = 3;
		TemporalField field = ChronoField.SECOND_OF_MINUTE;
		Instant instant1 = Instant.parse("2025-03-12T10:10:10Z");
		Instant instant2 = (Instant) MvelUtils.addSeconds(instant1, add);
		assertEquals(instant1.getEpochSecond() + add, instant2.getEpochSecond());
		LocalDateTime localDateTime1 = java.time.LocalDateTime.of(2025, 3, 12, 10, 10, 10);
		LocalDateTime localDateTime2 = (LocalDateTime) MvelUtils.addSeconds(localDateTime1, add);
		assertEquals(localDateTime1.get(field) + add, localDateTime2.get(field));
	}

	@Test
	public void testAddDays() {
		int add = 3;
		TemporalField field = ChronoField.DAY_OF_MONTH;
		LocalDate localDate1 = java.time.LocalDate.of(2025, 3, 12);
		LocalDate localDate2 = (LocalDate) MvelUtils.addDays(localDate1, add);
		assertEquals(localDate1.get(field) + add, localDate2.get(field));
		LocalDateTime localDateTime1 = java.time.LocalDateTime.of(2025, 3, 12, 10, 10, 10);
		LocalDateTime localDateTime2 = (LocalDateTime) MvelUtils.addDays(localDateTime1, add);
		assertEquals(localDateTime1.get(field) + add, localDateTime2.get(field));
	}

	@Test
	public void testNextDouble() {
		double val = MvelUtils.nextDouble(0.1d, 0.9d);
		assertTrue(val >= 0.1d);
		assertTrue(val < 0.9d);
	}

	@Test
	public void testNextInt() {
		int val = MvelUtils.nextInt(1, 8);
		assertTrue(val >= 1);
		assertTrue(val < 8);
	}

	@Test
	public void testNextLong() {
		long val = MvelUtils.nextLong(1, 8);
		assertTrue(val >= 1);
		assertTrue(val < 8);
	}

}
