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

import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.graphviz.Edge;
import com.sqlapp.graphviz.Graph;
import com.sqlapp.graphviz.Node;
import com.sqlapp.graphviz.NodePort;
import com.sqlapp.graphviz.Port;
import com.sqlapp.graphviz.Compass;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.function.BiConsumer;

@Accessors(fluent = true, chain=true) 
@Getter
@Setter
public class ForeignKeyConstraintEdgeBuilder extends AbstractSchemaGraphBuilder{
	
	private BiConsumer<ForeignKeyConstraint, Edge> setAttribute=null;
	
	private ForeignKeyConstraintEdgeBuilder(){}

	public static ForeignKeyConstraintEdgeBuilder create(){
		ForeignKeyConstraintEdgeBuilder builder=new ForeignKeyConstraintEdgeBuilder();
		return builder;
	}

	public void build(ForeignKeyConstraint fk, Graph graph){
		Table table=fk.getTable();
		Table pkTable=fk.getRelatedTable();
		Node fkNode=graph.getNode(SchemaGraphUtils.getName(table));
		String fkPortName="head_"+SchemaGraphUtils.getFkPortName(fk);
		Port fkPort=fkNode.getPort(fkPortName);
		Node pkNode=graph.getNode(SchemaGraphUtils.getName(pkTable));
		if (pkNode==null){
			return;
		}
		String pkPortName="tail_"+SchemaGraphUtils.getPkPortName(fk);
		Port pkPort=pkNode.getPort(pkPortName);
		NodePort nodePort1=new NodePort(fkNode, fkPort, Compass.West);
		NodePort nodePort2=new NodePort(pkNode, pkPort);
		Edge edge=graph.addEdge(nodePort1, nodePort2);
		setArrow(fk, edge);
		edge.setLabel(createLabel(fk));
		edge.setComment(fk.getName());
		edge.setFontsize(this.getDrawOption().getEdgeFontsize());
		if (setAttribute!=null){
			setAttribute.accept(fk, edge);
		}
	}
	
	protected void setArrow(ForeignKeyConstraint fk, Edge edge){
		this.getDrawOption().getErDrawMethod().draw(fk, edge);
	}
	
	private String createLabel(ForeignKeyConstraint fk){
		StringBuilder builder=new StringBuilder();
		if (this.getDrawOption().isWithRelationName()){
			builder.append(fk.getName());
		}
		if (!this.getDrawOption().isWithRelationCascadeOption()){
			return builder.toString();
		}
		StringBuilder cascBuilder=new StringBuilder();
		if (fk.getDeleteRule()!=null&&!fk.getDeleteRule().isRestrict()){
			if (this.getDrawOption().isWithRelationName()){
				cascBuilder.append("\n");
			}
			cascBuilder.append("(");
			cascBuilder.append("DEL=");
			cascBuilder.append(fk.getDeleteRule().getAbbrName());
		}
		if (fk.getUpdateRule()!=null&&!fk.getUpdateRule().isRestrict()){
			if (cascBuilder.length()>0){
				cascBuilder.append(",");
			} else{
				if (this.getDrawOption().isWithRelationName()){
					cascBuilder.append("\n");
				}
				cascBuilder.append("(");
			}
			cascBuilder.append("UPD=");
			cascBuilder.append(fk.getUpdateRule().getAbbrName());
		}
		if (cascBuilder.length()>0){
			cascBuilder.append(")");
			builder.append(cascBuilder.toString());
		}
		if (fk.isVirtual()){
			if (cascBuilder.length()>0){
				builder.append("\n");
			}
			builder.append("Virtual");
		}
		return builder.toString();
	}
	
	protected ForeignKeyConstraintEdgeBuilder instance(){
		return this;
	}

}
