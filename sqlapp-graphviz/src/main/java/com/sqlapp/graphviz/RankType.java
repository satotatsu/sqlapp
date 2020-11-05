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

public enum RankType implements Default{
	same("all nodes are placed on the same rank.")
	, min("all nodes are placed on the minimum rank.")
	, source("all nodes are placed on the minimum rank, and the only nodes on the minimum rank belong to some subgraph whose rank attribute is \"source\" or \"min\".")
	, max("all nodes are placed on the maximum rank.")
	, sink("")
	,;

	private  final String comment;
	private RankType(String comment){
		this.comment=comment;
	}
	/**
	 * @return the comment
	 */
	public String getComment() {
		return comment;
	}
	
}
