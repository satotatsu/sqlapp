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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class NodeTest extends AbstractTest{

	@Test
	public void test() {
		Node node=new Node("a");
		node.setShape(NodeShape.box);
		node.setLabel("label");
		String expected=this.getResource("node1.dot");
		assertEquals(expected, node.toString());
	}

	@Test
	public void test2() {
		Node node=new Node("a");
		node.setShape(NodeShape.box);
		node.setHtmlLabel(htmlTable->{
			htmlTable.addRows((tr, i)->{
				tr.addCells((cell, j)->{
					cell.setPort("p"+i+"_"+j);
				}, 2);
			}, 2);
		});
		String expected=this.getResource("node2.dot");
		assertEquals(expected, node.toString());
	}
}
