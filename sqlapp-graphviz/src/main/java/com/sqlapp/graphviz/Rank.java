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
public class Rank extends AbstractGraphVizElement implements ToGraphStringBuilder{
	
	private final RankType rank;
	
	private final Node[] nodes;
	
	public Rank(final RankType rank, final Node... nodes){
		this.rank=rank;
		this.nodes=nodes;
	}

	public Rank(final Node... nodes){
		this.rank=RankType.same;
		this.nodes=nodes;
	}

	@Getter(lombok.AccessLevel.PROTECTED)
	@Setter(lombok.AccessLevel.PROTECTED)
	private RankCollection parent;
	
	@Override
	public String toString(){
		if (isEmpty()){
			return "";
		}
		final GraphStringBuilder builder=toGraphStringBuilder();
		return builder.toString();
	}
	
	@Override
	public GraphStringBuilder toGraphStringBuilder(){
		final GraphStringBuilder builder=createGraphStringBuilder();
		builder.putNoEscape("rank", getRankValue());
		return builder;
	}
	
	public boolean isEmpty(){
		return nodes==null||nodes.length==0;
	}
	
	protected GraphStringBuilder createGraphStringBuilder(){
		final GraphStringBuilder builder=new GraphStringBuilder("");
		builder.setOpen("{").setClose("}");
		builder.setWithLineBreak(false);
		return builder;
	}

	
	private String getRankValue(){
		final StringBuilder builder=new StringBuilder();
		builder.append(rank);
		for(final Node node:nodes){
			builder.append("; ");
			builder.append(node.getEscapedName());
		}
		return builder.toString();
	}
}
