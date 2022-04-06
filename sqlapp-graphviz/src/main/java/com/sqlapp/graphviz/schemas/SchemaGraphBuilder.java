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


import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.graphviz.Color;
import com.sqlapp.graphviz.Graph;
import com.sqlapp.graphviz.Node;
import com.sqlapp.graphviz.NodeShape;
import com.sqlapp.graphviz.RankType;
import com.sqlapp.graphviz.Rankdir;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(fluent = true, chain=true) 
@Getter
@Setter
public class SchemaGraphBuilder extends AbstractSchemaGraphBuilder {

	private TableNodeBuilder tableNodeBuilder=TableNodeBuilder.create();

	private ForeignKeyConstraintEdgeBuilder foreignKeyConstraintEdgeBuilder=ForeignKeyConstraintEdgeBuilder.create();

	private InheritsEdgeBuilder inheritsEdgeBuilder=InheritsEdgeBuilder.create();

	private PartitionParentEdgeBuilder partitionParentEdgeBuilder=PartitionParentEdgeBuilder.create();

	public static SchemaGraphBuilder create(){
		SchemaGraphBuilder builder=new SchemaGraphBuilder();
		return builder;
	}

	public Graph createGraph(String name){
		Graph graph=new Graph(name);
		graph.setDirected(true);
		graph.addGraphSetting(setting->{
			setting.setNodesep(0.1);
			setting.setFontname(this.getDrawOption().getFont());
			setting.setFontsize(11);
			setting.setRankdir(Rankdir.RightToLeft);
			setting.setBgcolor(Color.transparent);
		});
		return graph;
	}
	
	public void create(Schema schema, Graph parentGraph){
		if(!this.getDrawOption().getSchemaFilter().test(schema)){
			return;
		}
		Graph graph=createGraph(schema.getName());
		graph.setLabel(schema.getName());
		graph.setCluster(true);
		parentGraph.addGraph(graph);
		graph.addNodeSetting(setting->{
			setting.setShape(NodeShape.plaintext);
			setting.setFontsize(11);
		});
		drawEdges(schema.getTables(), graph);
	}
	
	private void drawEdges(Table table, Graph graph) {
		if(!this.getDrawOption().getTableFilter().test(table)){
			return;
		}
		if (inheritsEdgeBuilder!=null) {
			inheritsEdgeBuilder.parent(this);
			inheritsEdgeBuilder.build(table, graph);
		}
		//
		if (partitionParentEdgeBuilder!=null) {
			partitionParentEdgeBuilder.parent(this);
			partitionParentEdgeBuilder.build(table, graph);
		}
		//
		if (foreignKeyConstraintEdgeBuilder!=null) {
			foreignKeyConstraintEdgeBuilder.parent(this);
			table.getConstraints().getForeignKeyConstraints().forEach(fk->{
				foreignKeyConstraintEdgeBuilder.build(fk, graph);
			});
		}
	}

	private void addRank(List<Node> nodes, Graph graph){
		List<Node> sameNodes=CommonUtils.list();
		for(Node node:nodes){
			sameNodes.add(node);
			if (sameNodes.size()>4){
				graph.addRank(RankType.same, sameNodes);
				sameNodes.clear();
			}
		}
		if (sameNodes.size()>1){
			graph.addRank(RankType.same, sameNodes);
		}
	}
	
	private List<Table> relationTables(Collection<Table> tables){
		return tables.stream().filter(table->this.getDrawOption().getTableFilter().test(table)).filter(table->table.getChildRelations().size()>0||table.getConstraints().getForeignKeyConstraints().size()>0).collect(Collectors.toList());
	}

	private List<Table> partitionTables(Collection<Table> tables){
		return tables.stream().filter(table->this.getDrawOption().getTableFilter().test(table)).filter(table->table.getPartitionParent()!=null).collect(Collectors.toList());
	}

	public void create(Collection<Table> tables, Graph graph){
//		Graph graph=createGraph(schema.getName());
//		graph.setCluster(true);
//		parentGraph.addGraph(graph);
		graph.addNodeSetting(setting->{
			setting.setShape(NodeShape.plaintext);
			setting.setFontsize(11);
		});
		drawEdges(tables, graph);
	}
	
	private void drawEdges(Collection<Table> tables, Graph graph) {
		List<Node> nodes=CommonUtils.list();
		tables.forEach(table->{
			if(!this.getDrawOption().getTableFilter().test(table)){
				return;
			}
			tableNodeBuilder.parent(this);
			Node node=tableNodeBuilder.build(table, graph);
			nodes.add(node);
		});
		List<Table> relationTables=relationTables(tables);
		List<Table> partitionTables=partitionTables(tables);
		if (relationTables.size()==0&&partitionTables.size()==0){
			addRank(nodes, graph);
		} else{
			tables.forEach(table->{
				drawEdges(table,graph);
			});
		}
	}
	
}
