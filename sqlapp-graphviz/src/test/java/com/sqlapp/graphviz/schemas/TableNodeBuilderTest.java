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

package com.sqlapp.graphviz.schemas;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Statistics;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.graphviz.AbstractTest;
import com.sqlapp.graphviz.Graph;
import com.sqlapp.graphviz.Node;
import com.sqlapp.graphviz.Rankdir;

public class TableNodeBuilderTest extends AbstractTest{

	@Test
	public void test() throws XMLStreamException, IOException {
		Graph graph=new Graph("ER");
		graph.addGraphSetting(setting->{
			setting.setRankdir(Rankdir.RightToLeft);
		});
		Table table=new Table("TableA");
		table.getColumns().add(c->{
			c.setName("cola");
		});
		Statistics.ROWS.setValue(table, 10L);
		Node node=TableNodeBuilder.create().build(table, graph);
		assertEquals(this.getResource("table1.txt"), node.toString());
	}

}
