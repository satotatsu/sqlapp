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
package com.sqlapp.graphviz.labeltable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.graphviz.GraphVizElementUtils;
import com.sqlapp.graphviz.Node;
import com.sqlapp.graphviz.Port;
import com.sqlapp.graphviz.PortCollection;
import com.sqlapp.util.StaxWriter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain=true) 
@Getter
@Setter
@EqualsAndHashCode(callSuper=true)
public class TableElement extends AbstractHtmlElement{
	
	public TableElement(){}

	public TableElement(Node node){
		super(node);
	}
	
	/**
	 * default CENTER
	 */
	private Align align=null;
	/**
	 * default CENTER
	 */
	private Align balign=null;
	private String bgcolor;
	/**
	 * default 1
	 * maximum 255
	 */
	private Integer border=null;
	/**
	 * default 2
	 * maximum 255
	 */
	private Integer cellpadding=null;
	/**
	 * default 2
	 * maximum 127
	 */
	private Integer cellspacing;
	private String color;
	private boolean fixedsize=false;
	private String gradientAngle;
	private String height;
	private String href;
	private String id;
	private Port port;
	private String sides;
	private String style;
	private String target;
	private String title;
	private String tooltip;
	private String width;
	private VAlign valign;
	
	private List<TrElement> rows=new ArrayList<>();

	protected PortCollection getPorts(){
		return GraphVizElementUtils.getPorts(this.getRoot().getNode());
	}
	
	
	protected String getElementName(){
		return "table";
	}
	
	public TableElement setPort(String value){
		Port port=new Port(value);
		GraphVizElementUtils.setParent(port, this.getRoot().getNode());
		this.port=port;
		return instance();
	}

	public TableElement addRow(Consumer<TrElement> c){
		TrElement tr=new TrElement();
		tr.setParent(this);
		this.rows.add(tr);
		c.accept(tr);
		return instance();
	}
	
	public TableElement addRow(int index, Consumer<TrElement> c){
		TrElement tr=new TrElement();
		tr.setParent(this);
		this.rows.add(index, tr);
		c.accept(tr);
		return instance();
	}
	
	public TableElement addRows(BiConsumer<TrElement, Integer> c, int rowSize){
		for(int i=0;i<rowSize;i++){
			TrElement tr=new TrElement();
			tr.setParent(this);
			this.rows.add(tr);
			c.accept(tr, i);
		}
		return instance();
	}
	
	protected TableElement instance(){
		return this;
	}
	
	@Override
	protected void writeXml(StaxWriter staxWriter) throws XMLStreamException {
		staxWriter.writeStartElement(getElementName());
		staxWriter.writeAttribute("align", align);
		staxWriter.writeAttribute("balign", balign);
		staxWriter.writeAttribute("bgcolor", bgcolor);
		staxWriter.writeAttribute("border", border);
		staxWriter.writeAttribute("cellpadding", cellpadding);
		staxWriter.writeAttribute("cellspacing", cellspacing);
		staxWriter.writeAttribute("color", color);
		if (fixedsize){
			staxWriter.writeAttribute("fixedsize", fixedsize);
		}
		staxWriter.writeAttribute("gradientangle", gradientAngle);
		staxWriter.writeAttribute("height", height);
		staxWriter.writeAttribute("href", href);
		staxWriter.writeAttribute("id", id);
		if (port!=null){
			staxWriter.writeAttribute("port", port.getValue());
		}
		staxWriter.writeAttribute("sides", sides);
		staxWriter.writeAttribute("style", style);
		staxWriter.writeAttribute("target", target);
		staxWriter.writeAttribute("title", title);
		staxWriter.writeAttribute("tooltip", tooltip);
		staxWriter.writeAttribute("width", width);
		staxWriter.writeAttribute("valign", valign);
		if (!rows.isEmpty()){
			staxWriter.addIndentLevel(1);
			for(TrElement element:rows){
				staxWriter.newLine();
				staxWriter.indent();
				element.writeXml(staxWriter);
			}
			staxWriter.addIndentLevel(-1);
			staxWriter.newLine();
			staxWriter.indent();
		}
		staxWriter.writeEndElement();
	}
}
