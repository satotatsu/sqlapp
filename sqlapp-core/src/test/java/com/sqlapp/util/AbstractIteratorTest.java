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

import static com.sqlapp.util.CommonUtils.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.util.iterator.Iterators;

public class AbstractIteratorTest {

	@BeforeEach
	public void setUp() throws Exception {
	}

	@Test
	public void testExecute1() throws Exception {
		final StringBuilder builder=new StringBuilder();
		AbstractIterator<String> itr=new AbstractIterator<String>(){
			@Override
			protected void handle(String obj, int index) {
				builder.append(obj);
			}
		};
		itr.execute(new String[]{"a", "b", "c"});
		assertEquals(builder.toString(), "abc");
	}

	@Test
	public void testExecute2() throws Exception {
		final StringBuilder builder=new StringBuilder();
		AbstractIterator<String> itr=new AbstractIterator<String>(){
			@Override
			protected void handle(String obj, int index) {
				builder.append(obj);
			}
		};
		itr.execute(new Object[]{"a", "b", "c"});
		assertEquals("abc", builder.toString());
	}

	@Test
	public void testExecute3() throws Exception {
		final StringBuilder builder=new StringBuilder();
		AbstractIterator<String> itr=new AbstractIterator<String>(){
			@Override
			protected void handle(String obj, int index) {
				builder.append(obj);
			}
		};
		itr.execute(list("a", "b", "c"));
		assertEquals("abc", builder.toString());
	}

	@Test
	public void testExecute4() throws Exception {
		final StringBuilder builder=new StringBuilder();
		AbstractIterator<Integer> itr=new AbstractIterator<Integer>(2){
			@Override
			protected void handle(Integer obj, int index) {
			}
			@Override
			protected void stepHandle(int index, int stepSize){
				builder.append(index);
			}
		};
		itr.execute(Iterators.range(10));
		assertEquals("01234", builder.toString());
	}
	
	@Test
	public void testExecute5() throws Exception {
		final StringBuilder builder=new StringBuilder();
		final StringBuilder stepBuilder=new StringBuilder();
		AbstractIterator<Integer> itr=new AbstractIterator<Integer>(3){
			@Override
			protected void handle(Integer obj, int index) {
			}
			protected void stepHandle(int index, int stepSize){
				builder.append(index);
				stepBuilder.append(stepSize);
			}
		};
		itr.execute(Iterators.range(10));
		assertEquals("0123", builder.toString());
		assertEquals("3331", stepBuilder.toString());
	}	
}
