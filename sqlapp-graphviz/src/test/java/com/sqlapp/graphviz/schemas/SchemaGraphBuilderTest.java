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

package com.sqlapp.graphviz.schemas;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.SchemaCollection;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.graphviz.AbstractTest;
import com.sqlapp.graphviz.Graph;
import com.sqlapp.graphviz.Rankdir;

public class SchemaGraphBuilderTest extends AbstractTest{

//	@Test
	public void test() throws XMLStreamException, IOException {
		SchemaCollection schemas=SchemaUtils.readXml(getResourceAsInputStream("schemas.xml"));
		SchemaGraphBuilder builder=SchemaGraphBuilder.create();
		Graph graph=new Graph("ER");
		graph.addGraphSetting(setting->{
			setting.setRankdir(Rankdir.RightToLeft);
		});
		schemas.forEach(schema->{
			builder.create(schema, graph);
		});
		//assertEquals(this.getResource("graph.txt"), graph.toString());
	}

}
