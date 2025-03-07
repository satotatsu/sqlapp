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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper=true)
public abstract class AbstractNode<T extends AbstractNode<?>> extends AbstractCommonElement<T>{

	
	protected abstract String getName();

	protected String getEscapedName(){
		return getName();
	}
	@Props
	private String comment;
	/**
	 * default lightgrey(nodes) black(clusters)
	 */
	@Props
	private Color[] fillcolor;
	
	@Props
	private String fixedsize;

	private Integer gradientangle;
	@Props
	private String group;
	@Props
	private String image;
	
	@Props
	private NodeShape shape;
	
	@Props
	private Double labelangle;
	/**
	 * default 1.0
	 */
	@Props
	private Double labeldistance;
	/**
	 * default 0.75
	 */
	@Props
	private Double width;
	/**
	 * default 0.75
	 */
	@Props
	private Double height;
	@Props
	private Double z;

	protected GraphStringBuilder createGraphStringBuilder(){
		GraphStringBuilder builder=new GraphStringBuilder(getEscapedName());
		return builder;
	}

	public T setFillColor(Color... fillcolor){
		this.fillcolor=fillcolor;
		return instance();
	}

	public T setFillColor(Color fillcolor){
		if (fillcolor==null){
			this.fillcolor=null;
		} else{
			this.fillcolor=new Color[]{fillcolor};
		}
		return instance();
	}
	
	@Override
	protected Map<String,Object> getProperties(){
		Map<String,Object> props=super.getProperties();
		put(props, "comment", comment);
		put(props, "fillcolor", fillcolor);
		put(props, "gradientangle", gradientangle);
		put(props, "group", group);
		put(props, "height", height);
		put(props, "image", image);
		put(props, "labelangle", labelangle);
		put(props, "labeldistance", labeldistance);
		put(props, "shape", shape);
		put(props, "width", width);
		put(props, "z", z);
		return props;
	}

	public T setZ(String z){
		if ("MAXFLOAT".equalsIgnoreCase(z)){
			this.z=Double.MAX_VALUE;
			return instance();
		}else if ("-MAXFLOAT".equalsIgnoreCase(z)){
			this.z=Double.MAX_VALUE;
			return instance();
		}
		throw new IllegalArgumentException("z="+z);
	}
	
}
