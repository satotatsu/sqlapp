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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import com.sqlapp.util.CommonUtils;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Data
@EqualsAndHashCode(callSuper=true)
public class Graph extends AbstractCommonElement<Graph>{

	
	public Graph(String name){
		this.name=name;
	}
	
	private boolean directed;
	
	private boolean cluster;

	@Getter(value=lombok.AccessLevel.PUBLIC)
	@Setter(value=lombok.AccessLevel.PROTECTED)
	private GraphCollection parent;

	private String name=null;

	private Layout layout=null;
	
	@Getter(value=lombok.AccessLevel.PRIVATE)
	@Setter(value=lombok.AccessLevel.PRIVATE)
	private GraphCollection graphs=new GraphCollection(this);

	@Getter(value=lombok.AccessLevel.PRIVATE)
	@Setter(value=lombok.AccessLevel.PRIVATE)
	private NodeCollection nodes=new NodeCollection(this);
	
	@Getter(value=lombok.AccessLevel.PRIVATE)
	@Setter(value=lombok.AccessLevel.PRIVATE)
	private EdgeCollection edges=new EdgeCollection(this);

	@Getter(value=lombok.AccessLevel.PRIVATE)
	@Setter(value=lombok.AccessLevel.PRIVATE)
	private RankCollection ranks=new RankCollection(this);
	
	private List<AbstractGraphVizElement> elements=new ArrayList<>();
	
	public String getName() {
		if (name==null){
			return null;
		}
		if (this.isCluster()){
			return "cluster_"+name;
		}
		return name;
	}

	protected Graph instance(){
		return this;
	}
	
	public Node addNode(String name){
		Node node=this.nodes.get(name);
		if (node!=null){
			return node;
		}
		node=new Node(name);
		return addNode(node);
	}

	public Graph addNode(Consumer<Node> c, String... names){
		List<Node> list=new ArrayList<>();
		for(String name:names){
			Node node=this.nodes.get(name);
			if (node==null){
				node=new Node(name);
				list.add(node);
			}
		}
		this.nodes.addAll(list);
		this.elements.addAll(list);
		if(c!=null){ 
			list.forEach(obj->{
				c.accept(obj);
			});
		}
		return instance();
	}
	
	public Graph addRank(RankType rankType, Node... nodes){
		if (CommonUtils.isEmpty(nodes)){
			return instance();
		}
		Rank rank=new Rank(rankType, nodes);
		this.ranks.add(rank);
		this.elements.add(rank);
		return instance();
	}

	public Graph addRank(RankType rankType, java.util.Collection<Node> nodes){
		if (CommonUtils.isEmpty(nodes)){
			return instance();
		}
		Rank rank=new Rank(rankType, nodes.toArray(new Node[0]));
		this.ranks.add(rank);
		this.elements.add(rank);
		return instance();
	}

	
	public Graph addRank(Node... nodes){
		return addRank(RankType.same, nodes);
	}

	public Graph addRank(RankType rankType, String... nodes){
		return addRank(rankType, getNodesInternal(nodes).toArray(new Node[0]));
	}

	public Graph addRank(String... nodes){
		return addRank(RankType.same, nodes);
	}

	private List<Node> getNodesInternal(String... nodes){
		List<Node> list=new ArrayList<>();
		if (CommonUtils.isEmpty(nodes)){
			return list;
		}
		for(String name:nodes){
			Node node=this.nodes.get(name);
			if (node==null){
				throw new IllegalArgumentException("Node["+name+"] not found.");
			}
			list.add(node);
		}
		return list;
	}
	
	public <T extends Node> T addNode(T node){
		nodes.add(node);
		this.elements.add(node);
		return node;
	}


	public Graph addNode(Consumer<Node> c, Node... args){
		List<Node> list=new ArrayList<>();
		for(Node node:args){
			Node nd=this.nodes.get(node.getName());
			if (nd!=null){
				node=nd;
			}
			list.add(node);
		}
		nodes.addAll(list);
		this.elements.addAll(list);
		if(c!=null){ 
			list.forEach(obj->{
				c.accept(obj);
			});
		}
		return instance();
	}

	public Graph addGraph(Consumer<Graph> c, String... args){
		List<Graph> list=new ArrayList<>();
		for(String arg:args){
			Graph graph=new Graph(arg);
			list.add(graph);
		}
		graphs.addAll(list);
		this.elements.addAll(list);
		if(c!=null){ 
			list.forEach(obj->{
				c.accept(obj);
			});
		}
		return instance();
	}

	public Graph addGraph(Graph graph){
		graphs.add(graph);
		this.elements.add(graph);
		return instance();
	}

	public Edge addEdge(Node from, Node to){
		Edge obj=new Edge(from, to);
		edges.add(obj);
		this.elements.add(obj);
		return obj;
	}

	public Node getNode(String name){
		Node node= this.nodes.get(name);
		if (node!=null){
			return node;
		}
		for(Graph graph:this.graphs){
			node=graph.getNode(name);
			if (node!=null){
				return node;
			}
		}
		return null;
	}

	public Graph addGraphSetting(Consumer<GraphSetting> c){
		GraphSetting obj=new GraphSetting(this);
		this.elements.add(obj);
		c.accept(obj);
		return instance();
	}

	public GraphSetting addGraphSetting(){
		GraphSetting obj=new GraphSetting(this);
		this.elements.add(obj);
		return obj;
	}
	
	public Graph addNodeSetting(Consumer<NodeSetting> c){
		NodeSetting obj=new NodeSetting(this);
		this.elements.add(obj);
		c.accept(obj);
		return instance();
	}

	public NodeSetting addNodeSetting(){
		NodeSetting obj=new NodeSetting(this);
		this.elements.add(obj);
		return obj;
	}

	public Graph addEdgeSetting(Consumer<EdgeSetting> c){
		EdgeSetting obj=new EdgeSetting(this);
		this.elements.add(obj);
		c.accept(obj);
		return instance();
	}
	
	public EdgeSetting addEdgeSetting(){
		EdgeSetting obj=new EdgeSetting(this);
		this.elements.add(obj);
		return obj;
	}

	public Edge addEdge(NodePort... nodes){
		Edge obj=new Edge(nodes);
		edges.add(obj);
		this.elements.add(obj);
		return obj;
	}

	public Graph addEdge(Consumer<Edge> c, Node... nodes){
		Edge obj=new Edge((Object[])nodes);
		edges.add(obj);
		this.elements.add(obj);
		if (c!=null){
			c.accept(obj);
		}
		return instance();
	}

	public Graph addEdge(Consumer<Edge> c, NodePort... nodes){
		Edge obj=new Edge(nodes);
		edges.add(obj);
		this.elements.add(obj);
		if (c!=null){
			c.accept(obj);
		}
		return instance();
	}
	
	public Graph addEdge(Consumer<Edge> c, String... nodePorts){
		for(String nodePort:nodePorts){
			String[] splits=nodePort.split("\\s*->\\s*");
			for(int i=0;i<splits.length-1;i++){
				addEdgeInternal(c, splits[i], splits[i+1]);
			}
		}
		return instance();
	}
	
	public Graph addEdge(String... nodePorts){
		if (nodePorts==null||nodePorts.length==0){
			throw new IllegalArgumentException("nodePorts="+Arrays.toString(nodePorts));
		}
		return addEdge(null, nodePorts);
	}

	private void addEdgeInternal(Consumer<Edge> c, String... nodePorts){
		List<NodePort> list=new ArrayList<>();
		for(String nodePort:nodePorts){
			Node node=null;
			Port port=null;
			if (nodePort.contains(":")){
				String[] splits=nodePort.split("\\s*:\\s*");
				node=this.getNode(splits[0].trim());
				port=node.getPort(splits[1].trim());
				if (port==null){
					throw new PortNotFoundException(splits[0].trim(), splits[1].trim());
				}
			} else{
				node=this.getNode(nodePort.trim());
				port=null;
			}
			list.add(new NodePort(node, port));
		}
		addEdge(c, list.toArray(new NodePort[0]));
	}

	public Edge addEdge(Node from, Port portFrom, Node to, Port portTo){
		Edge obj=new Edge(from, portFrom, to, portTo);
		edges.add(obj);
		this.elements.add(obj);
		return obj;
	}

	@Override
	protected GraphStringBuilder createGraphStringBuilder() {
		GraphStringBuilder builder=new GraphStringBuilder(graphName());
		builder.setIndentLevel(level());
		builder.setOpen("{").setClose("}");
		return builder;
	}

	private String graphName(){
		if (level()==0){
			if (this.isDirected()){
				return "digraph "+GraphVizElementUtils.escapeName(this.getName());
			} else{
				return "graph "+GraphVizElementUtils.escapeName(this.getName());
			}
		} else{
			return "subgraph "+GraphVizElementUtils.escapeName(this.getName());
		}
	}
	
	public boolean isSubgraph(){
		return this.getParent()!=null;
	}

	public boolean isCluster(){
		return isSubgraph()&&this.cluster;
	}

	private int level(){
		int i=0;
		if (this.getParent()==null){
			return i;
		}
		return level(this.getParent().getParent(), i+1);
	}

	private int level(Graph graph, int i){
		if (graph.getParent()==null){
			return i;
		}
		return level(this.getParent().getParent(), i+1);
	}
	
	@Override
	public String toString(){
		GraphStringBuilder builder=toGraphStringBuilder();
		return builder.toString();
	}
	
	@Override
	public GraphStringBuilder toGraphStringBuilder(){
		GraphStringBuilder builder=createGraphStringBuilder();
		builder.put("label", getLabel());
		builder.put("layout", getLayout());
		this.getProperties().forEach((k,v)->{
			builder.put(k, v);
		});
		for(AbstractGraphVizElement element:elements){
			if (element instanceof ToGraphStringBuilder){
				GraphStringBuilder child=((ToGraphStringBuilder)element).toGraphStringBuilder();
				if (element instanceof Edge){
					child.setIndentLevel(builder.getIndentLevel()+1);
					builder.put(child);
				} else{
					if (!child.isEmpty()){
						child.setIndentLevel(builder.getIndentLevel()+1);
						builder.put(child);
					}
				}
			} else{
				throw new RuntimeException("Invalid elemnt. element=["+element+"]");
			}
		}
		return builder;
	}
	
	protected Graph getRoot(){
		return getParent(this);
	}

	protected Graph getParent(Graph graph){
		if (graph==null){
			return this;
		}
		if (graph.parent==null){
			return graph;
		}
		return getParent(graph.parent.getParent());
	}

}
