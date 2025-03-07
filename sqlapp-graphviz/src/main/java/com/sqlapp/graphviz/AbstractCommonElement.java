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

import com.sqlapp.util.CommonUtils;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain=true) 
@Getter
@Setter
@EqualsAndHashCode(callSuper=true)
public abstract class AbstractCommonElement<T extends AbstractCommonElement<?>> extends AbstractGraphVizElement implements ToGraphStringBuilder{

	private String label=null;
	
	@Props("URL")
	private String url=null;
	
	@Props
	private BrewerColorScheme colorscheme=null;
	
	@Props
	private Color fontcolor=null;

	@Props
	private String fontname=null;
	/**
	 * default 14.0
	 * Minimum 1.0
	 */
	@Props
	private Double fontsize=null;
	/**
	 * svg, postscript, map only
	 */
	@Props
	private String href;
	/**
	 * svg, postscript, map only
	 */
	@Props
	private String id;
	/**
	 * default false
	 */
	@Props
	private Boolean nojustify;
	
	@Props
	private String style;
	/**
	 * svg, map only
	 */
	@Props
	private String target;
	
	private Object _context=null;
	
	@Override
	public String toString(){
		GraphStringBuilder builder=toGraphStringBuilder();
		return builder.toString();
	}

	@Override
	public GraphStringBuilder toGraphStringBuilder(){
		GraphStringBuilder builder=createGraphStringBuilder();
		initializeProperties(builder);
		return builder;
	}
	
	private void initializeProperties(GraphStringBuilder builder){
		Map<String,Object> props=getProperties();
		props.forEach((k,v)->{
			builder.put(k, v);
		});
		initializeLabel(builder);
	}

	protected void initializeLabel(GraphStringBuilder builder){
		builder.put("label", getLabel());
	}

	protected Map<String,Object> getProperties(){
		Map<String,Object> props=CommonUtils.linkedMap();
		put(props, "colorscheme", colorscheme);
		put(props, "fontcolor", fontcolor);
		put(props, "fontname", fontname);
		put(props, "fontsize", fontsize);
		put(props, "href", href);
		put(props, "id", id);
		put(props, "nojustify", nojustify);
		put(props, "URL", url);
		put(props, "style", style);
		put(props, "target", target);
		return props;
	}
	
	protected void put(Map<String,Object> props, String name, Object value){
		if (value!=null){
			props.put(name, value);
		}
	}
	
	protected abstract GraphStringBuilder createGraphStringBuilder();
	
	@Props
	public String getLabel(){
		if (label==null){
			return null;
		}
		return label;
	}

	public T setLabel(String label){
		this.label=label;
		return instance();
	}
	
	public T setColorscheme(BrewerColorScheme colorScheme){
		this.colorscheme=colorScheme;
		return instance();
	}

	@SuppressWarnings("unchecked")
	protected T instance(){
		return (T)this;
	}
	
	public T setFontsize(int size){
		this.fontsize=0.0+size;
		return instance();
	}

	public T setFontsize(double size){
		this.fontsize=size;
		return instance();
	}

	public T setFontsize(Number size){
		if (size==null){
			
		} else{
			return setFontsize(size.doubleValue());
		}
		return instance();
	}

	@SuppressWarnings("unchecked")
	public <S> S get_context(){
		return (S)this._context;
	}
}
