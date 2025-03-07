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

import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.graphviz.Edge;
import com.sqlapp.graphviz.Graph;
import com.sqlapp.graphviz.Node;
import com.sqlapp.graphviz.NodePort;
import com.sqlapp.graphviz.Port;
import com.sqlapp.graphviz.ArrowType;
import com.sqlapp.graphviz.Compass;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.function.BiConsumer;

@Accessors(fluent = true, chain=true) 
@Getter
@Setter
public class InheritsEdgeBuilder extends AbstractSchemaGraphBuilder{
	
	private BiConsumer<ForeignKeyConstraint, Edge> setAttribute=null;
	
	private InheritsEdgeBuilder(){}

	public static InheritsEdgeBuilder create(){
		InheritsEdgeBuilder builder=new InheritsEdgeBuilder();
		return builder;
	}

	public void build(Table table, Graph graph){
		for(Table inherit:table.getInherits()){
			String name=SchemaGraphUtils.getName(table);
			Node tableNode=graph.getNode(name);
			String portName=SchemaGraphUtils.getName(table);
			Port fromPort=tableNode.getPort(portName);
			Node inheritsNode=graph.getNode(SchemaGraphUtils.getName(inherit));
			String toPortName="footer_"+SchemaGraphUtils.getName(inherit);
			if (inheritsNode==null){
				return;
			}
			Port toPort=inheritsNode.getPort(toPortName);
			NodePort nodePort1=new NodePort(tableNode, fromPort, Compass.North);
			NodePort nodePort2=new NodePort(inheritsNode, toPort, Compass.South);
			if (toPort==null){
				nodePort2=new NodePort(inheritsNode, Compass.South);
			} else{
				nodePort2=new NodePort(inheritsNode, toPort, Compass.South);
			}
			Edge edge=graph.addEdge(nodePort1, nodePort2);
			setArrow(table, inherit , edge);
			edge.setWeight(10);
			edge.setLabel(createLabel(table, inherit));
		}
	}
	
	private String createLabel(Table table, Table inherit){
		StringBuilder builder=new StringBuilder();
		builder.append("Inherits");
		return builder.toString();
	}
	
	protected void setArrow(Table table, Table inherit , Edge edge){
		edge.setArrowhead(ArrowType.empty);
	}

	protected InheritsEdgeBuilder instance(){
		return this;
	}

}
