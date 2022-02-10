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

package com.sqlapp.util.eval.mvel;


import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static com.sqlapp.util.CommonUtils.*;
import static com.sqlapp.util.DateUtils.*;

public class ParserContextFactoryTest {

	CachedMvelEvaluator cmEval=new CachedMvelEvaluator();
	
	@BeforeEach
	public void setUp() throws Exception {
		cmEval=new CachedMvelEvaluator();
		cmEval.setParserContext(ParserContextFactory.getInstance().getParserContext());
	}

	@Test
	public void testParseBean() throws ParseException{
		Map<String, Object> map=map();
		Map<String, Object> innerMap=map();
		innerMap.put("b", "1");
		map.put("a", innerMap);
		Object val=cmEval.getEvalExecutor("a.b").eval(map);
		assertEquals("1", val);
	}


	/*
	@Test
	public void testParse1() throws ParseException{
		Map<String, Object> map=map();
		map.put("a", "a");
		Object val=cmEval.getEvalExecutor("isEmpty(a)").evalBoolean(map);
		assertEquals(Boolean.FALSE, val);
		val=cmEval.getEvalExecutor("isNotEmpty(a)").evalBoolean(map);
		assertEquals(Boolean.TRUE, val);
		//
		map.put("a", null);
		val=cmEval.getEvalExecutor("isNotEmpty(a)").evalBoolean(map);
		assertEquals(Boolean.FALSE, val);
		//
		map.put("a", "");
		val=cmEval.getEvalExecutor("isNotEmpty(a)").evalBoolean(map);
		assertEquals(Boolean.FALSE, val);
	}
	*/
	
	@Test
	public void testParse() throws ParseException{
		Map<String, Object> map=map();
		map.put("a", null);
		map.put("b", null);
		map.put("c", "1");
		map.put("dt", parse("2011-05-02", "yyyy-MM-dd"));
		Object val=cmEval.getEvalExecutor("isEmpty(a)").evalBoolean(map);
		assertEquals(Boolean.TRUE, val);
		val=cmEval.getEvalExecutor("isNotEmpty(a)").evalBoolean(map);
		assertEquals(Boolean.FALSE, val);
		//
		map.put("a", "a");
		val=cmEval.getEvalExecutor("isNotEmpty(a)").evalBoolean(map);
		assertEquals(Boolean.TRUE, val);
		//
		map.put("a", 1);
		val=cmEval.getEvalExecutor("isNotEmpty(a)").evalBoolean(map);
		assertEquals(Boolean.TRUE, val);
		//
		map.put("a", new String[0]);
		val=cmEval.getEvalExecutor("isNotEmpty(a)").evalBoolean(map);
		assertEquals(Boolean.FALSE, val);
		//
		map.put("a", new ArrayList<String>());
		val=cmEval.getEvalExecutor("isNotEmpty(a)").evalBoolean(map);
		assertEquals(Boolean.FALSE, val);
		//
		map.put("a", new HashSet<String>());
		val=cmEval.getEvalExecutor("isNotEmpty(a)").evalBoolean(map);
		assertEquals(Boolean.FALSE, val);
		//
		map.put("a", new HashMap<String, Object>());
		val=cmEval.getEvalExecutor("isNotEmpty(a)").evalBoolean(map);
		assertEquals(Boolean.FALSE, val);
		//
		map.put("a", null);
		val=cmEval.getEvalExecutor("coalesce(a, b, c)").eval(map);
		assertEquals(val, "1");
		val=cmEval.getEvalExecutor("addSeconds(dt, 59)").eval(map);
		assertEquals(val, parse("2011-05-02 00:00:59", "yyyy-MM-dd HH:mm:ss"));
		//
		val=cmEval.getEvalExecutor("addMinutes(dt, 15)").eval(map);
		assertEquals(val, parse("2011-05-02 00:15", "yyyy-MM-dd HH:mm"));
		//
		val=cmEval.getEvalExecutor("addHours(dt, 2)").eval(map);
		assertEquals(val, parse("2011-05-02 02", "yyyy-MM-dd HH"));
		//
		val=cmEval.getEvalExecutor("addDays(dt, 2)").eval(map);
		assertEquals(val, parse("2011-05-04", "yyyy-MM-dd"));
		//
		val=cmEval.getEvalExecutor("addMonths(dt, 2)").eval(map);
		assertEquals(val, parse("2011-07-02", "yyyy-MM-dd"));
		//
		val=cmEval.getEvalExecutor("addYears(dt, 2)").eval(map);
		assertEquals(val, parse("2013-05-02", "yyyy-MM-dd"));
		//
		val=cmEval.getEvalExecutor("toDate('2011-07-18', 'yyyy-MM-dd')").eval(map);
		assertEquals(val, parse("2011-07-18", "yyyy-MM-dd"));
		//
		val=cmEval.getEvalExecutor("currentDate()").eval(map);
		val=cmEval.getEvalExecutor("currentDateTime()").eval(map);
		val=cmEval.getEvalExecutor("currentTime()").eval(map);
		val=cmEval.getEvalExecutor("currentTimestamp()").eval(map);
	}

	@Test
	public void testRange() throws ParseException{
		CachedMvelEvaluator cmEval=new CachedMvelEvaluator();
		cmEval.setParserContext(ParserContextFactory.getInstance().getParserContext());
		Map<String, Object> map=map();
		map.put("a", null);
		map.put("b", null);
		map.put("c", "1");
		Object val=cmEval.getEvalExecutor("range(0,20,2)").eval(map);
		int count=0;
		StringBuilder builder=new StringBuilder();
		for(Object obj:(Iterable<?>)val){
			builder.append(obj);
			count++;
		}
		assertEquals(10, count);
		assertEquals("024681012141618", builder.toString());
	}

}
