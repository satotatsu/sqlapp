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

public class GraphTest extends AbstractTest{

	@Test
	public void test() {
		Graph graph=new Graph("sample1");
		graph.setLayout(Layout.dot);
		graph.addGraphSetting(g->{
			g.setBgcolor(Color.azure);
		});
		graph.addNode((node)->{
			node.setId("id_a");
		}, "a");
		String dot=graph.toString();
		String expected=this.getResource("graph1.dot");
		assertEquals(expected, dot);
	}
	
	@Test
	public void test2() {
		Graph graph=new Graph("sample1");
		graph.addGraphSetting(g->{
			g.setBgcolor(Color.azure);
		});
		Node node1=graph.addNode("a");
		node1.setId("id_a");
		Node node2=graph.addNode("b");
		node2.setId("id_b");
		graph.addEdge(node1, node2);
		graph.addRank("a", "b");
		//
		graph.addGraph((subgraph->{
			subgraph.addNode((node)->{
				node.setId("id_suba");
			}, "suba");
			subgraph.setCluster(true);
		}), "subgraph1");
		String dot=graph.toString();
		String expected=this.getResource("graph2.dot");
		assertEquals(expected, dot);
	}

	
	@Test
	public void test3() {
		Graph graph=new Graph("sample1");
		graph.setDirected(true);
		graph.addGraphSetting(g->{
			g.setBgcolor(Color.azure);
		});
		Node node1=graph.addNode("a");
		node1.setId("id_a");
		node1.setLabel("<pl>left|center|<pr>right");
		Node node2=graph.addNode("b");
		node2.setId("id_b");
		node2.setLabel("<pl>left|<pc>center|<pr>right");
		graph.addEdge(e->{
			e.setArrowsize(1.2);
		}
		, "a:pl->b:pr");
		graph.addRank("a", "b");
		//
		graph.addGraph((subgraph->{
			subgraph.addNode((node)->{
				node.setId("id_suba");
			}, "suba");
			subgraph.setCluster(true);
		}), "subgraph1");
		String dot=graph.toString();
		String expected=this.getResource("graph3.dot");
		assertEquals(expected, dot);
		assertEquals("pl", graph.getNode("a").getPort("pl").getValue());
	}
}
