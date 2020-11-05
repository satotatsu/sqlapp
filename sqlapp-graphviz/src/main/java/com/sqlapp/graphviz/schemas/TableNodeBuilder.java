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
public class TableNodeBuilder extends AbstractSchemaGraphBuilder{

	private Function<Table, TableLabelBuilder> labelBuilder=(table)->TableLabelBuilder.create();

	private BiConsumer<Table, Node> setAttribute=null;
	
	private TableNodeBuilder(){}

	public static TableNodeBuilder create(){
		TableNodeBuilder builder=new TableNodeBuilder();
		return builder;
	}

	public Node build(Table table, Graph graph){
		TableLabelBuilder tableLabelBuilder=labelBuilder.apply(table);
		tableLabelBuilder.parent(this);
		Node node=graph.addNode(SchemaGraphUtils.getName(table));
		node.setFontname(this.getDrawOption().getFont());
		node.setFontsize(this.getDrawOption().getNodeFontsize());
		node.setUrl("tables/"+SchemaGraphUtils.getName(table)+".html");
		node.setHtmlLabel((tableElement)->{
			tableLabelBuilder.build(table, tableElement);
		});
		if (setAttribute!=null){
			setAttribute.accept(table, node);
		}
		node.set_context(table);
		return node;
	}
	
	protected TableNodeBuilder instance(){
		return this;
	}

	public TableNodeBuilder labelBuilder(Function<Table, TableLabelBuilder> labelBuilder){
		this.labelBuilder=labelBuilder;
		return this;
	}

	
	public TableNodeBuilder labelBuilder(TableLabelBuilder lableBuilder){
		labelBuilder=(table)->lableBuilder;
		return this;
	}
	
}
