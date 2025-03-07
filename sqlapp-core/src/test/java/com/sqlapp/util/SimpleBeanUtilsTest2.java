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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Beanユーティリティのテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class SimpleBeanUtilsTest2 {

	/**
	 * Mapを含むクラスのパブリックフィールドのテストを実施します
	 */
	@Test
	public void testPublicfield1() {
		Dummy1 dummy1 = getDummy1();
		Map<String, Object> map = SimpleBeanUtils.toMap(dummy1);
		assertEquals(6, map.size());
		assertEquals("aaa", map.get("name"));
		assertEquals(dummy1.list, map.get("list"));
	}

	/**
	 * Mapを含むクラスの変換テストを実施します
	 */
	@Test
	public void testConvertCI() {
		Dummy1 dummy1 = getDummy1();
		Dummy2 dummy2 = SimpleBeanUtils.convertCI(dummy1, Dummy2.class);
		assertEquals(dummy1.id, dummy2.id);
		assertEquals(dummy1.name, dummy2.name);
		assertEquals(dummy1.fiscalYear, dummy2.fiscal_year);
		assertEquals(dummy1.list, dummy2.list);
		assertEquals(dummy1.child.fiscalYear, dummy2.child.get("fiscalYear"));
	}

	/**
	 * Mapを含むクラスの変換テストを実施します
	 */
	@Test
	public void testSetValues() {
		List<Dummy1> list = new ArrayList<Dummy1>();
		Dummy1 dummy1 = getDummy1();
		list.add(dummy1);
		dummy1 = getDummy1();
		list.add(dummy1);
		//
		SimpleBeanUtils.setValues(list, "name", "bbb");
		for (Dummy1 dummy : list) {
			assertEquals("bbb", dummy.name);
		}
		//
		SimpleBeanUtils.setValuesCI(list, "NAME", "ccc");
		for (Dummy1 dummy : list) {
			assertEquals("ccc", dummy.name);
		}
	}

	private Dummy1 getDummy1() {
		Dummy1 dummy1 = new Dummy1();
		dummy1.id = 1;
		dummy1.name = "aaa";
		dummy1.child = new Child1();
		dummy1.list = new ArrayList<String>();
		dummy1.child.fiscalYear = "200103";
		return dummy1;
	}

	/**
	 * Mapを含むクラスの変換テストを実施します
	 */
	@Test
	public void testConvertCI2() {
		Dummy2 dummy2 = new Dummy2();
		dummy2.id = 1;
		dummy2.name = "aaa";
		dummy2.child = new HashMap<String, Object>();
		dummy2.child.put("fiscalYear", "200103");
		Dummy1 dummy1 = SimpleBeanUtils.convertCI(dummy2, Dummy1.class);
		assertEquals(dummy2.id, dummy1.id);
		assertEquals(dummy2.name, dummy1.name);
		assertEquals(dummy2.fiscal_year, dummy1.fiscalYear);
		assertEquals(dummy2.child.get("fiscalYear"), dummy1.child.fiscalYear);
	}

	static class Dummy1 {
		public int id;
		public String name;
		public boolean enable;
		public String fiscalYear;
		public Child1 child;
		public List<String> list;
	}

	static class Child1 {
		public int id;
		public String name;
		public boolean enable;
		public String fiscalYear;
	}

	static class Dummy2 {
		public int id;
		public String name;
		public boolean enable;
		public String fiscal_year;
		public Map<String, Object> child;
		public List<String> list;
	}

}
