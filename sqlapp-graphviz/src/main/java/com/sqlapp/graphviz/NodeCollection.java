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

import java.util.LinkedHashMap;
import java.util.Map;

public class NodeCollection extends AbstractElementCollection<Node>{

	/**
	 * 
	 */
	private static final long serialVersionUID = 6685111479972906357L;

	protected NodeCollection(Graph parent){
		this.parent=parent;
	}
	
	private Graph parent;
	
	private Map<String,Node> map=new LinkedHashMap<>();

	@Override
	protected void renew(){
		Map<String,Node> newMap=new LinkedHashMap<>();
		this.getList().forEach(c->{
			if (newMap.containsKey(c.getName())){
				throw new DuplicateNodeException(c);
			}
			newMap.put(c.getName(), c);
			newMap.put(c.getEscapedName(), c);
			c.setParent(this);
		});
		synchronized(this){
			this.map=newMap;
		}
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Node> T get(String name){
		return (T)map.get(name);
	}

	public <T extends Node> T remove(String name){
		T node=get(name);
		if (node!=null){
			super.remove(node);
		}
		return node;
	}
	
	protected Graph getParent(){
		return parent;
	}

}
