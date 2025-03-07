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

public class GraphVizElementUtils {
	public static void setParent(Port port, Node node){
		node.getPorts().add(port);
	}
	
	public static PortCollection getPorts(Node node){
		return node.getPorts();
	}
	
	public static void setNodeAndPort(Node from, Port portFrom, Node to, Port portTo, Edge edge){
		edge.setNode(from, portFrom, to, portTo);
	}
	
	public static String escapeName(String name) {
		return name.replace(".", "_dot_");
	}
}
