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

package com.sqlapp.graphviz.labeltable;

import java.io.StringWriter;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.graphviz.AbstractGraphVizElement;
import com.sqlapp.graphviz.Node;
import com.sqlapp.util.StaxWriter;

public abstract class AbstractHtmlElement extends AbstractGraphVizElement{

	protected abstract void writeXml(StaxWriter staxWriter)throws XMLStreamException;
	
	private AbstractHtmlElement parent=null;
	
	private Node node;
	
	protected AbstractHtmlElement(){
		
	}

	protected AbstractHtmlElement(Node node){
		this.node=node;
	}

	@Override
	public String toString(){
		StringWriter writer=new StringWriter();
		StaxWriter staxWriter;
		try {
			staxWriter = new StaxWriter(writer);
			staxWriter.setHtmlMode();
			this.writeXml(staxWriter);
			return writer.getBuffer().toString();
		} catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}
	}

	public String toString(int indentSize){
		StringWriter writer=new StringWriter();
		StaxWriter staxWriter;
		try {
			staxWriter = new StaxWriter(writer);
			staxWriter.setHtmlMode();
			staxWriter.setIndentLevel(indentSize);
			this.writeXml(staxWriter);
			return writer.getBuffer().toString();
		} catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * @return the node
	 */
	protected Node getNode() {
		return node;
	}

	/**
	 * @param node the node to set
	 */
	protected void setNode(Node node) {
		this.node = node;
	}

	protected void setParent(AbstractHtmlElement parent){
		this.parent=parent;
	}

	@SuppressWarnings("unchecked")
	protected <T extends AbstractHtmlElement> T getParent(){
		return (T)parent;
	}

	protected <T extends AbstractHtmlElement> T getRoot(){
		return getParent(this);
	}

	@SuppressWarnings("unchecked")
	protected <T extends AbstractHtmlElement> T getParent(AbstractHtmlElement element){
		if (element.parent==null){
			return (T)element;
		}
		return getParent(element.parent);
	}

}
