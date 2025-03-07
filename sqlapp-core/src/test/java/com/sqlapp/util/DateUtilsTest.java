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

package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.*;

import java.text.ParseException;
import java.util.Date;

import org.junit.jupiter.api.Test;
import static com.sqlapp.util.DateUtils.*;

public class DateUtilsTest {

	@Test
	public void testFormatDate() throws ParseException {
		Date date=parse("2011-05-02", "yyyy-MM-dd");
		assertEquals("2011-05-02", format(date, "yyyy-MM-dd"));
	}
	
	@Test
	public void testSetDate() throws ParseException {
		Date date=parse("2011-05-02", "yyyy-MM-dd");
		Date ret=setDate(date, 1);
		assertEquals(parse("2011-05-01"), ret);
	}
	
}
