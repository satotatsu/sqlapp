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

import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.graphviz.labeltable.TableElement;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper=true)
public class Node extends AbstractNode<Node>{

	@Setter(lombok.AccessLevel.PRIVATE)
	private String name=null;

	
	public Node(String name){
		this.name=name;
	}

	public String getEscapedName() {
		return escapeName(this.name);
	}

	public String getName() {
		return this.name;
	}

	@Props
	private String group;
	
	@Getter(lombok.AccessLevel.PUBLIC)
	@Setter(lombok.AccessLevel.PROTECTED)
	private NodeCollection parent;

	@Getter(lombok.AccessLevel.PROTECTED)
	@Setter(lombok.AccessLevel.PROTECTED)
	private PortCollection ports=new PortCollection(this);

	public Graph getRoot(){
		Graph graph=this.getGraph();
		if (graph.getParent()==null){
			return null;
		}
		if (this.getParent().getParent()==null){
			return null;
		}
		return graph.getRoot();
	}
	
	public Graph getGraph(){
		if (this.getParent()==null){
			return null;
		}
		return this.getParent().getParent();
	}

	private static Pattern PORT_PATTERN=Pattern.compile("<([^>]+?)>", Pattern.DOTALL+Pattern.MULTILINE);
	
	@Override
	public Node setLabel(String label){
		super.setLabel(label);
		ports.clear();
		if (label==null){
		} else{
			String val=label.trim();
			if (val.startsWith("<")&&val.endsWith(">")){
				return instance();
			}
			Matcher matcher=PORT_PATTERN.matcher(label);
			while(matcher.find()){
				String value=matcher.group(1);
				this.getPorts().add(new Port(value));
			}
		}
		return instance();
	}

	public Node setLabel(Consumer<RecordLabelBuilder> c){
		RecordLabelBuilder builder=RecordLabelBuilder.create();
		c.accept(builder);
		return setLabel(builder.toString());
	}

	public Node setHtmlLabel(Consumer<TableElement> c){
		TableElement element=new TableElement(this);
		c.accept(element);
		super.setLabel("<\n"+element.toString()+">");
		return instance();
	}
	
	@Override
	protected void initializeLabel(GraphStringBuilder builder){
		if (getLabel()==null){
			return;
		}
		String val=getLabel().trim();
		if (val.startsWith("<")&&val.endsWith(">")){
			builder.putNoEscape("label", getLabel());
		} else{
			builder.put("label", getLabel());
		}
	}
	
	public Port getPort(String name){
		return this.getPorts().get(name);
	}
	
	@Override
	protected Map<String,Object> getProperties(){
		Map<String,Object> props=super.getProperties();
		put(props, "group", group);
		return props;
	}
	
}
