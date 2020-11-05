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

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;

public class GraphCollection extends AbstractElementCollection<Graph>{

	/**
	 * 
	 */
	private static final long serialVersionUID = 6685111479972906357L;

	protected GraphCollection(Graph parent){
		this.parent=parent;
	}
	
	@Getter(value=lombok.AccessLevel.PUBLIC)
	private final Graph parent;
	
	private Map<String,Graph> map=new LinkedHashMap<>();

	@Override
	protected void renew(){
		map.clear();
		this.getList().forEach(c->{
			if (map.containsKey(c.getName())){
				throw new DuplicateGraphException(c);
			}
			map.put(c.getName(), c);
			c.setParent(this);
		});
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Graph> T get(String name){
		return (T)map.get(name);
	}

}
