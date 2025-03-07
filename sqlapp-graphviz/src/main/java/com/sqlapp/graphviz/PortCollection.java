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

public class PortCollection extends AbstractPortCollection{

	/**
	 * 
	 */
	private static final long serialVersionUID = 6685111479972906357L;

	protected PortCollection(Node parent){
		this.parent=parent;
	}

	@Override
	protected void initializePort(Port port){
		port.setParent(this);
	}
	
	private final Node parent;

	@Override
	public Port remove(String name){
		return super.remove(name);
	}
	
	protected Node getParent(){
		return parent;
	}

}
