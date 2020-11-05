/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-graphviz.
 *
 * sqlapp-graphviz is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-graphviz is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-graphviz.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.graphviz;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class RecordLabelBuilderTest extends AbstractTest{

	@Test
	public void test0() {
		RecordLabelBuilder builder=RecordLabelBuilder.create();
		String ret=builder.toString();
		String expected=this.getResource("record0.dot");
		assertEquals(expected, ret);
	}

	@Test
	public void test1() {
		RecordLabelBuilder builder=RecordLabelBuilder.create();
		builder.add("a");
		String ret=builder.toString();
		String expected=this.getResource("record1.dot");
		assertEquals(expected, ret);
	}

	@Test
	public void test2() {
		RecordLabelBuilder builder=RecordLabelBuilder.create();
		builder.add("a").add("b").add("d");
		String ret=builder.toString();
		String expected=this.getResource("record2.dot");
		assertEquals(expected, ret);
	}
	
	@Test
	public void test3() {
		RecordLabelBuilder builder=RecordLabelBuilder.create();
		builder.addWithPort("p1", "a").add("b").addWithPort("p2", "d");
		String ret=builder.toString();
		String expected=this.getResource("record3.dot");
		assertEquals(expected, ret);
	}

	@Test
	public void test4() {
		RecordLabelBuilder builder=RecordLabelBuilder.create();
		builder.addWithPort("p1", "a").add(child->{
			child.addBrace().add("b").add("c").add("d");
		});
		String ret=builder.toString();
		String expected=this.getResource("record4.dot");
		assertEquals(expected, ret);
	}

	
}
