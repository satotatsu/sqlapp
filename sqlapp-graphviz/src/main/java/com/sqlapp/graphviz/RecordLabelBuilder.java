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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RecordLabelBuilder extends AbstractGraphVizElement{

	private List<Object> elements=new ArrayList<>();
	
	private int brace=0;
	
	private RecordLabelBuilder(){}
	
	public static RecordLabelBuilder create(){
		return new RecordLabelBuilder();
	}

	public RecordLabelBuilder addWithPort(String port, String value){
		elements.add("<"+this.escapeName(port)+"> : "+this.escapeName(value));
		return instance();
	}

	public RecordLabelBuilder add(String value){
		elements.add(this.escapeName(value));
		return instance();
	}

	public RecordLabelBuilder add(Consumer<RecordLabelBuilder> c){
		RecordLabelBuilder child=RecordLabelBuilder.create();
		this.elements.add(child);
		c.accept(child);
		return instance();
	}

	public RecordLabelBuilder add(RecordLabelBuilder builder){
		this.elements.add(builder);
		return instance();
	}
	
	public RecordLabelBuilder addBrace() {
		this.brace=this.brace+1;
		return instance();
	}

	
	protected RecordLabelBuilder instance(){
		return this;
	}
	
	@Override
	public String toString(){
		StringBuilder builder=new StringBuilder();
		for(int i=0;i<this.brace;i++){
			builder.append("{");
		}
		if (!elements.isEmpty()){
			boolean first=true;
			for(Object element:elements){
				if (!first){
					builder.append(" | ");
				} else{
					builder.append(" ");
					first=false;
				}
				if (element==null){
					builder.append("");
				} else{
					builder.append(element.toString());
				}
			}
			builder.append(" ");
		}
		for(int i=0;i<this.brace;i++){
			builder.append("}");
		}
		return builder.toString();
	}
}
