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

import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper=true)
public abstract class AbstractEdge<T extends AbstractEdge<?>> extends AbstractCommonElement<T>{
	
	public AbstractEdge(){
	}
	
	/**
	 * default normal
	 */
	@Props
	private ArrowType[] arrowhead=null;
	/**
	 * default 1.0
	 * Minimum 0.0
	 */
	@Props
	private Double arrowsize=null;
	
	@Props
	private ArrowType[] arrowtail=null;
	/**
	 * default black
	 */
	@Props
	private Color[] color;
	/**
	 * 
	 */
	@Props
	private String comment;
	/**
	 * default true
	 * dot only
	 */
	@Props
	private Boolean constraint=null;
	/**
	 * default false
	 * dot only
	 */
	@Props
	private Boolean decorate=null;
	/**
	 * default forward(directed) none(undirected)
	 */
	@Props
	private DirType dir=null;
	/**
	 * svg, map only
	 */
	@Props
	private String edgeURL;
	/**
	 * svg, map only
	 */
	@Props
	private String edgehref;
	/**
	 * svg, map only
	 */
	@Props
	private String edgetarget;
	/**
	 * svg, map only
	 */
	@Props
	private String edgetooltip;
	@Props
	private Color[] fillcolor;
	@Props
	private String headURL;
	@Props
	private Point head_lp;
	/**
	 * default: true
	 */
	@Props
	private Boolean headclip;
	/**
	 * svg, map only
	 */
	@Props
	private String headhref;
	@Props
	private String headlabel;
	@Props
	private String headtarget;
	@Props
	private String headtooltip;
	@Props
	private String labelURL;
	@Props
	private Double labelangle;
	@Props
	private Double labeldistance;
	@Props
	private Boolean labelfloat;
	@Props
	private String labelfontcolor;
	@Props
	private String labelfontname;
	@Props
	private Double labelfontsize;
	@Props
	private String labelhref;
	@Props
	private String tailhref;
	@Props
	private String taillabel;
	@Props
	private String tailport;
	@Props
	private String tailtarget;
	@Props
	private String tailtooltip;
	@Props
	private String tailURL;
	
	@Getter(lombok.AccessLevel.PUBLIC)
	@Setter(lombok.AccessLevel.PROTECTED)
	private EdgeCollection parent;
	
	@Override
	protected Map<String,Object> getProperties(){
		Map<String,Object> props=super.getProperties();
		put(props, "arrowhead", arrowhead);
		put(props, "arrowsize", arrowsize);
		put(props, "arrowtail", arrowtail);
		put(props, "color", color);
		put(props, "comment", comment);
		put(props, "constraint", constraint);
		put(props, "decorate", decorate);
		put(props, "dir", dir);
		put(props, "edgeURL", edgeURL);
		put(props, "edgehref", edgehref);
		put(props, "edgehref", edgehref);
		put(props, "edgetarget", edgetarget);
		put(props, "edgetooltip", edgetooltip);
		put(props, "fillcolor", fillcolor);
		put(props, "headURL", headURL);
		put(props, "head_lp", head_lp);
		put(props, "headclip", headclip);
		put(props, "headhref", headhref);
		put(props, "headtarget", headtarget);
		put(props, "labelURL", labelURL);
		put(props, "labelangle", labelangle);
		put(props, "labeldistance", labeldistance);
		put(props, "labelfloat", labelfloat);
		put(props, "labelfontcolor", labelfontcolor);
		put(props, "labelfontname", labelfontname);
		put(props, "labelfontsize", labelfontsize);
		put(props, "labelhref", labelhref);
		put(props, "tailhref", tailhref);
		put(props, "taillabel", taillabel);
		put(props, "tailport", tailport);
		put(props, "tailtarget", tailtarget);
		put(props, "tailtooltip", tailtooltip);
		put(props, "tailURL", tailURL);
		return props;
	}

	@Override
	protected GraphStringBuilder createGraphStringBuilder(){
		GraphStringBuilder builder=new GraphStringBuilder(getNodeName());
		return builder;
	}
	
	protected abstract String getNodeName();

	public T setColor(Color... color){
		this.color=color;
		return instance();
	}

	public T setColor(Color color){
		if (color==null){
			this.color=null;
		} else{
			this.color=new Color[]{color};
		}
		return instance();
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
	
	public T setPoint(double x, double y){
		this.head_lp=new Point(x,y);
		return instance();
	}

	public T setPoint(double x, double y, double z){
		this.head_lp=new Point3D(x,y,z);
		return instance();
	}

	public T setArrowhead(ArrowType... arrowhead){
		this.arrowhead=arrowhead;
		return instance();
	}

	public T setArrowtail(ArrowType... arrowtail){
		this.arrowtail=arrowtail;
		return instance();
	}

	public T setStyle(EdgeStyle style){
		if (style!=null){
			super.setStyle(style.toString());
		} else{
			super.setStyle((String)null);
		}
		return instance();
	}
}
