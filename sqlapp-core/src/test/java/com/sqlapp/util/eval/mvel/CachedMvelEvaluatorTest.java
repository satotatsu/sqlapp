/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import static com.sqlapp.util.CommonUtils.list;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

import lombok.Data;

class CachedMvelEvaluatorTest {

	@Data
	public static class Dummy {
		private int a = 1;
		private int b = 2;
		private int c = 3;
	}

	@Test
	void test() {
		Map<String, Object> map = CommonUtils.map();
		map.put("a", 1);
		map.put("b", 2);
		map.put("c", 3);
		String exp = "[\"c\":(a+b), \"d\":(a+c)]";
		Object obj = CachedMvelEvaluator.getInstance().eval(exp, map);
		assertEquals(3, (Integer) SimpleBeanUtils.getValue(obj, "c"));
		assertEquals(4, (Integer) SimpleBeanUtils.getValue(obj, "d"));
		obj = CachedMvelEvaluator.getInstance().eval(exp, new Dummy());
		assertEquals(3, (Integer) SimpleBeanUtils.getValue(obj, "c"));
		assertEquals(4, (Integer) SimpleBeanUtils.getValue(obj, "d"));
	}

//	@Test
	void test2() {
		Map<String, Object> map = CommonUtils.map();
		map.put("a", 1);
		map.put("b", 2);
		map.put("c", 3);
		String exp = "a==b";
		boolean obj = CachedMvelEvaluator.getInstance().evalBoolean(exp, map);
		assertFalse(obj);
		obj = CachedMvelEvaluator.getInstance().evalBoolean(exp, new Dummy());
		assertFalse(obj);
	}

	void test3() {
		final ParametersContext context = new ParametersContext();
		context.put("a", 3);
		context.put("b", 2);
		context.put("d", list(2, 4));
		boolean obj = CachedMvelEvaluator.getInstance().evalBoolean("d", context);
	}

}
