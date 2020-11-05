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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper=true)
public class Edge extends AbstractEdge<Edge>{
	
	public Edge(Object... args){
		this.nodes=toNodePorts(args);
	}

	public Edge(NodePort... nodes){
		this.nodes=nodes;
	}

	public Edge(NodePort from, NodePort to){
		this.nodes=new NodePort[]{from,to};
	}

	public Edge(Node from, Node to){
		this.nodes=new NodePort[]{new NodePort(from),new NodePort(to)};
	}

	public Edge(Node from, Port portFrom, Node to, Port portTo){
		this.nodes=new NodePort[]{new NodePort(from, portFrom),new NodePort(to, portTo)};
	}
	
	@Props
	private Double weight=null;

	public void setWeight(Double weight){
		this.weight=weight;
	}

	public void setWeight(double weight){
		this.weight=weight;
	}

	public void setWeight(int weight){
		this.weight=(double)weight;
	}

	private NodePort[] nodes;
	
	public Graph getRoot(){
		Graph graph=this.getGraph();
		if (graph.getParent()==null){
			return null;
		}
		if (this.getParent().getParent()==null){
			return null;
		}
		return graph.getRoot();
	}
	
	public Graph getGraph(){
		if (this.getParent()==null){
			return null;
		}
		return this.getParent().getParent();
	}
	
	@Override
	protected String getNodeName(){
		StringBuilder builder=new StringBuilder();
		boolean first=true;
		Graph graph=null;
		if (this.getParent()!=null&&this.getParent().getParent()!=null){
			graph=this.getParent().getParent().getRoot();
		}
		String arrow;
		if (graph!=null&& graph.isDirected()){
			arrow=" -> ";
		} else{
			arrow=" -- ";
		}
		for(NodePort node:nodes){
			if (!first){
				builder.append(arrow);
			} else{
				first=false;
			}
			builder.append(node.toString());
		}
		return builder.toString();
	}
	
	
	protected void setNode(NodePort from, NodePort to){
		setNode(new NodePort[]{from,to});
	}
	
	private void setNode(NodePort[] nodes){
		if (nodes!=null){
			for(NodePort node:nodes){
				node.setParent(this);
			}
		}
		this.nodes=nodes;
	}

	protected void setNode(Node from, Node to){
		setNode(new NodePort[]{new NodePort(from),new NodePort(to)});
	}

	protected void setNode(Node from, Port portFrom, Node to, Port portTo){
		setNode(new NodePort[]{new NodePort(from, portFrom),new NodePort(to, portTo)});
	}
	
	protected void setNode(Object...args){
		setNode(toNodePorts(args));
	}

	private static NodePort[] toNodePorts(Object...args){
		if (args==null||args.length==0){
			return new NodePort[0];
		}
		List<NodePort> list=new ArrayList<>();
		int i=0;
		while(i<args.length){
			Object obj=args[i++];
			if (obj instanceof Node){
				Node node=(Node)obj;
				Port port=null;
				if (i<args.length){
					if (args[i] instanceof Port){
						port=(Port)args[i];
					}
				}
				list.add(new NodePort(node, port));
			} else{
				throw new IllegalArgumentException("args["+(i-1)+"] is not Node. args["+(i-1)+"]="+obj);
			}
		}
		return list.toArray(new NodePort[0]);
	}

	@Override
	protected Map<String,Object> getProperties(){
		Map<String,Object> props=super.getProperties();
		put(props, "weight", weight);
		return props;
	}
}
