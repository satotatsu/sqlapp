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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
/**
 * Diffクラステスト
 * @author 竜夫
 *
 */
public class DiffTest {

	@Test
	public void testGetLcs1() {
		Diff<String> diff=new Diff<String>(getList("axbybczxc"), getList("afbgbcf"));
		assertEquals("abbc", getString(diff.getLcs()));
		for(Map.Entry<Integer, String> entry:diff.getLcs1().entrySet()){
			if (CommonUtils.eq(Integer.valueOf(0), entry.getKey())){
				assertEquals("a", entry.getValue());
			}
			if (CommonUtils.eq(Integer.valueOf(2), entry.getKey())){
				assertEquals("b", entry.getValue());
			}
		}
	}

	@Test
	public void testGetLcs2() {
		Diff<String> diff=new Diff<String>(getList("abc"), getList("fbca"));
		assertEquals("bc", getString(diff.getLcs()));
	}

	@Test
	public void testGetLcs3() {
		Diff<String> diff=new Diff<String>(getList("abc"), getList("bca"));
		assertEquals("bc", getString(diff.getLcs()));
	}

	@Test
	public void testGetLcs4() {
		Diff<String> diff=new Diff<String>(getList("bca"), getList("abc"));
		assertEquals("bc", getString(diff.getLcs()));
	}

	@Test
	public void testGetLcs5() {
		Diff<String> diff=new Diff<String>(getList("fbd"), getList("abe"));
		assertEquals("b", getString(diff.getLcs()));
	}
	
	@Test
	public void testGetLcs6() {
		Diff<String> diff=new Diff<String>(getList("abc"), getList("abc"));
		assertEquals("abc", getString(diff.getLcs()));
	}

	private List<String> getList(String val){
		List<String> list=CommonUtils.list();
		for(int i=0;i<val.length();i++){
			list.add(val.substring(i, i+1));
		}
		return list;
	}

	private String getString(List<String> val){
		StringBuilder builder=new StringBuilder();
		for(int i=0;i<val.size();i++){
			builder.append(val.get(i));
		}
		return builder.toString();
	}

	
}
