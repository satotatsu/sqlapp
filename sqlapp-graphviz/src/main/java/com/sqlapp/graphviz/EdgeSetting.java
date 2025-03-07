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
import lombok.experimental.Accessors;

@Accessors(fluent=true, chain=true) 
@Getter
@Setter
@EqualsAndHashCode(callSuper=true)
public class EdgeSetting extends AbstractEdge<EdgeSetting>{

	private final Graph parent;
	public EdgeSetting(Graph parent){
		this.parent=parent;
	}

	@Override
	public String toString(){
		return super.toString();
	}
	
	@Override
	protected String getNodeName(){
		return "edge";
	}


}
