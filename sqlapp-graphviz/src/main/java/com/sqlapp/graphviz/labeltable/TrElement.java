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

import com.sqlapp.util.StaxWriter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain=true) 
@Getter
@Setter
@EqualsAndHashCode(callSuper=true)
public class TrElement extends AbstractHtmlElement{
	
	private List<TdElement> cells=new ArrayList<>();
	
	protected String getElementName(){
		return "tr";
	}

	public TrElement addCell(Consumer<TdElement> c){
		TdElement element=new TdElement();
		element.setParent(this);
		this.cells.add(element);
		c.accept(element);
		return instance();
	}

	public TrElement addCell(int index, Consumer<TdElement> c){
		TdElement element=new TdElement();
		element.setParent(this);
		this.cells.add(index, element);
		c.accept(element);
		return instance();
	}

	public TrElement addCells(BiConsumer<TdElement, Integer> c, int cellSize){
		for(int i=0;i<cellSize;i++){
			TdElement element=new TdElement();
			element.setParent(this);
			this.cells.add(element);
			c.accept(element, i);
		}
		return instance();
	}

	protected TrElement instance(){
		return this;
	}
	
	@Override
	protected void writeXml(StaxWriter staxWriter) throws XMLStreamException {
		staxWriter.writeStartElement(getElementName());
		for(TdElement td:cells){
			td.writeXml(staxWriter);
		}
		staxWriter.writeEndElement();
	}
}
