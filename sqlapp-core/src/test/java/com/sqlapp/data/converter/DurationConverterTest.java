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
package com.sqlapp.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class DurationConverterTest {

	@Test
	void test() {
		final DurationConverter converter=new DurationConverter();
		final Duration p=Duration.ofSeconds(253);
		assertEquals("PT4M13S", p.toString());
		final Duration p2=converter.convertObject(p.toString());
		assertEquals(p, p2);
		final Duration p3=converter.convertObject("Interval 4:13 minute to second");
		assertEquals(p3, p2);
		final Duration p4=converter.convertObject("Interval '10 12:4:13' day to second");
		assertEquals("PT12H4M13S", p4.toString());//日付は無視される
		final Duration p5=converter.convertObject("Interval '12:4:13' hour to second");
		assertEquals(p5, p4);
	}

}
