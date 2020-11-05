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

import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.graphviz.Graph;
import com.sqlapp.graphviz.Node;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.function.BiConsumer;
import java.util.function.Function;

@Accessors(fluent = true, chain=true) 
@Getter
@Setter
public class PartitionNodeBuilder extends AbstractSchemaGraphBuilder{

	private Function<Partition, PartitionLabelBuilder> labelBuilder=(partition)->PartitionLabelBuilder.create();

	private BiConsumer<Partition, Node> setAttribute=null;
	
	private PartitionNodeBuilder(){}

	public static PartitionNodeBuilder create(){
		PartitionNodeBuilder builder=new PartitionNodeBuilder();
		return builder;
	}

	public Node build(Partition partition, Graph graph){
		PartitionLabelBuilder labelBuilder=this.labelBuilder.apply(partition);
		labelBuilder.parent(this);
		Node node=graph.addNode(SchemaGraphUtils.getName(partition));
		node.setFontname(this.getDrawOption().getFont());
		node.setFontsize(this.getDrawOption().getNodeFontsize());
		Table table=partition.getAncestor(Table.class);
		if (table!=null) {
			node.setUrl("tables/"+SchemaGraphUtils.getName(table)+".html");
		}
		node.setHtmlLabel((tableElement)->{
			labelBuilder.build(partition, tableElement);
		});
		if (setAttribute!=null){
			setAttribute.accept(partition, node);
		}
		node.set_context(partition);
		return node;
	}
	
	protected PartitionNodeBuilder instance(){
		return this;
	}

	public PartitionNodeBuilder labelBuilder(Function<Partition, PartitionLabelBuilder> labelBuilder){
		this.labelBuilder=labelBuilder;
		return this;
	}

	
	public PartitionNodeBuilder labelBuilder(PartitionLabelBuilder lableBuilder){
		labelBuilder=(partition)->lableBuilder;
		return this;
	}
	
}
