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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper=true)
public class NodePort extends AbstractGraphVizElement{

	private Node node=null;

	private Port port=null;

	private Compass commpass=null;
	
	@Getter(lombok.AccessLevel.PROTECTED)
	@Setter(lombok.AccessLevel.PROTECTED)
	private Edge parent;
	
	public NodePort(Node node, Port port, Compass commpass){
		this.node=node;
		this.port=port;
		this.commpass=commpass;
	}

	public NodePort(Node node, Port port){
		this(node, port, null);
	}

	public NodePort(Node node){
		this(node, null, null);
	}

	public NodePort(Node node, Compass commpass){
		this(node, null, commpass);
	}

	@Override
	public String toString(){
		StringBuilder builder=new StringBuilder();
		builder.append(node.getEscapedName());
		if (port==null||port.toString().length()==0){
			return builder.toString();
		}
		builder.append(":");
		builder.append(port.toString());
		if (commpass==null){
			return builder.toString();
		}
		builder.append(":");
		builder.append(commpass);
		return builder.toString();
	}
}
