/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-graphviz.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.graphviz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class EdgeTest extends AbstractTest {

	@Test
	public void test() {
		Node a = new Node("a");
		Node b = new Node("b");
		Edge edge = new Edge(a, b);
		String expected = getResource("edge1.dot");
		// String expected = FileUtils.getResource(this, "edge1.dot");
		assertEquals(expected, edge.toString());
	}

	@Test
	public void test2() {
		Node a = new Node("a");
		Node b = new Node("b");
		Edge edge = new Edge(a, b);
		edge.setArrowsize(1.0);
		edge.setDir(DirType.forward);
		String expected = getResource("edge2.dot");
		assertEquals(expected, edge.toString());
	}

}
