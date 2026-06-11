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

import static com.sqlapp.util.CommonUtils.map;
import static com.sqlapp.util.DateUtils.parse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ParserContextFactoryTest {

	CachedMvelEvaluator cmEval = new CachedMvelEvaluator();

	@BeforeEach
	public void setUp() throws Exception {
		cmEval = new CachedMvelEvaluator();
		cmEval.setParserContext(ParserContextFactory.getInstance().getParserContext());
	}

	@Test
	public void testParseBean() throws ParseException {
		Map<String, Object> map = map();
		Map<String, Object> innerMap = map();
		innerMap.put("b", "1");
		map.put("a", innerMap);
		Object val = cmEval.eval("a.b", map);
		assertEquals("1", val);
	}

	@Test
	public void testParse1() throws ParseException {
		Map<String, Object> map = map();
		map.put("a", "a");
		Object val = cmEval.evalBoolean("isEmpty(a)", map);
		assertEquals(Boolean.FALSE, val);
		val = cmEval.evalBoolean("isNotEmpty(a)", map);
		assertEquals(Boolean.TRUE, val);
		//
		map.put("a", null);
		val = cmEval.evalBoolean("isNotEmpty(a)", map);
		assertEquals(Boolean.FALSE, val);
		//
		map.put("a", "");
		val = cmEval.evalBoolean("isNotEmpty(a)", map);
		assertEquals(Boolean.FALSE, val);
	}

	@Test
	public void testJavaTime1() throws ParseException {
		Map<String, Object> map = map();
		Object val = cmEval.eval("LocalDate.of(2025, 3, 12)", map);
		LocalDate localDate = LocalDate.of(2025, 3, 12);
		assertEquals(localDate, val);
	}

	@Test
	public void testJavaTime2() throws ParseException {
		Map<String, Object> map = map();
		LocalTime val1 = (LocalTime) cmEval.eval("LocalTime.of(23,59,59)", map);
		map.put("_previous", val1);
		LocalTime val2 = (LocalTime) cmEval.eval("addSeconds(_previous, 1)", map);
		assertEquals(MvelUtils.addSeconds(val1, 1), val2);
		LocalTime val3 = (LocalTime) cmEval.eval("LocalTime.of(0,0,0)", map);
		assertEquals(val3, val2);
		System.out.println(val2);
	}

	@Test
	public void testParse() throws ParseException {
		Map<String, Object> map = map();
		map.put("a", null);
		map.put("b", null);
		map.put("c", "1");
		map.put("dt", LocalDateTime.of(2011, 05, 02, 0, 0, 0));
		Object val = cmEval.evalBoolean("isEmpty(a)", map);
		assertEquals(Boolean.TRUE, val);
		val = cmEval.evalBoolean("isNotEmpty(a)", map);
		assertEquals(Boolean.FALSE, val);
		//
		map.put("a", "a");
		val = cmEval.evalBoolean("isNotEmpty(a)", map);
		assertEquals(Boolean.TRUE, val);
		//
		map.put("a", 1);
		val = cmEval.evalBoolean("isNotEmpty(a)", map);
		assertEquals(Boolean.TRUE, val);
		//
		map.put("a", new String[0]);
		val = cmEval.evalBoolean("isNotEmpty(a)", map);
		assertEquals(Boolean.FALSE, val);
		//
		map.put("a", new ArrayList<String>());
		val = cmEval.evalBoolean("isNotEmpty(a)", map);
		assertEquals(Boolean.FALSE, val);
		//
		map.put("a", new HashSet<String>());
		val = cmEval.evalBoolean("isNotEmpty(a)", map);
		assertEquals(Boolean.FALSE, val);
		//
		map.put("a", new HashMap<String, Object>());
		val = cmEval.evalBoolean("isNotEmpty(a)", map);
		assertEquals(Boolean.FALSE, val);
		//
		map.put("a", null);
		val = cmEval.eval("coalesce(a, b, c)", map);
		assertEquals("1", val);
		val = cmEval.eval("addSeconds(dt, 59)", map);
		assertEquals(LocalDateTime.of(2011, 5, 2, 0, 0, 59), val);
		//
		val = cmEval.eval("addMinutes(dt, 15)", map);
		assertEquals(LocalDateTime.of(2011, 5, 2, 0, 15, 0), val);
		//
		val = cmEval.eval("addHours(dt, 2)", map);
		assertEquals(LocalDateTime.of(2011, 5, 2, 2, 0, 0), val);
		//
		val = cmEval.eval("addDays(dt, 2)", map);
		assertEquals(LocalDateTime.of(2011, 5, 4, 0, 0, 0), val);
		//
		val = cmEval.eval("addMonths(dt, 2)", map);
		assertEquals(LocalDateTime.of(2011, 7, 2, 0, 0, 0), val);
		//
		val = cmEval.eval("addYears(dt, 2)", map);
		assertEquals(LocalDateTime.of(2013, 5, 2, 0, 0, 0), val);
		//
		val = cmEval.eval("toDate('2011-07-18', 'yyyy-MM-dd')", map);
		assertEquals(val, parse("2011-07-18", "yyyy-MM-dd"));
		//
		val = cmEval.eval("currentDate()", map);
		val = cmEval.eval("currentDateTime()", map);
		val = cmEval.eval("currentTime()", map);
		val = cmEval.eval("currentTimestamp()", map);
	}

	@Test
	public void testRange() throws ParseException {
		CachedMvelEvaluator cmEval = new CachedMvelEvaluator();
		cmEval.setParserContext(ParserContextFactory.getInstance().getParserContext());
		Map<String, Object> map = map();
		map.put("a", null);
		map.put("b", null);
		map.put("c", "1");
		Object val = cmEval.eval("range(0,20,2)", map);
		int count = 0;
		StringBuilder builder = new StringBuilder();
		for (Object obj : (Iterable<?>) val) {
			builder.append(obj);
			count++;
		}
		assertEquals(10, count);
		assertEquals("024681012141618", builder.toString());
	}

}
