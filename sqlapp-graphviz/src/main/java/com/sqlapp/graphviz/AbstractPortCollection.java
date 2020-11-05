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

public abstract class AbstractPortCollection extends AbstractElementCollection<Port>{

	/**
	 * 
	 */
	private static final long serialVersionUID = 6685111479972906357L;

	protected AbstractPortCollection(){
	}
	
	private Map<String,Port> map=new LinkedHashMap<>();

	@Override
	protected void renew(){
		Map<String,Port> newMap=new LinkedHashMap<>();
		this.getList().forEach(c->{
			newMap.put(c.getValue(), c);
			newMap.put(c.getEscapedValue(), c);
			initializePort(c);
		});
		synchronized(this){
			this.map=newMap;
		}
	}

	
	protected void initializePort(Port port){
		
	}
	
	public Port get(String name){
		return map.get(name);
	}

	public Port remove(String name){
		Port node=get(name);
		if (node!=null){
			super.remove(node);
		}
		return node;
	}
	

}
