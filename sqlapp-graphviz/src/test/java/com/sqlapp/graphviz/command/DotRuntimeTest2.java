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

package com.sqlapp.graphviz.command;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.SchemaCollection;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.graphviz.AbstractTest;
import com.sqlapp.graphviz.Graph;
import com.sqlapp.graphviz.schemas.SchemaGraphBuilder;
import com.sqlapp.util.FileUtils;

public class DotRuntimeTest2 extends AbstractTest{

//	@Test
	public void test() throws XMLStreamException, IOException {
		SchemaCollection schemas=SchemaUtils.readXml(getResourceAsInputStream("schemas.xml"));
		SchemaGraphBuilder builder=SchemaGraphBuilder.create();
		Graph graph=builder.createGraph("ER");
		graph.addGraphSetting(setting->{
		});
		schemas.forEach(schema->{
			builder.create(schema, graph);
		});
		
		FileUtils.writeText(getDotFile().getAbsolutePath(), "UTF8", graph.toString());
		DotRuntime dotRuntime=new DotRuntime();
		dotRuntime.setOutputFormat(OutputFormat.png);
		String result=dotRuntime.execute(getDotFile().getAbsolutePath(), getPath()+"/graphGen.png");
		System.out.println(result);
	}
	
	File getDotFile(){
		String path=getPath();
		return new File(path+"/graphGen.dot");
	}
	
	private String getPath(){
		String path="src/test/resources/"+this.getClass().getPackage().getName().toString().replace(".", "/");
		return path;
	}

}
