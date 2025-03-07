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
import lombok.experimental.Accessors;

@Accessors(chain=true) 
@Getter
@Setter
@EqualsAndHashCode(callSuper=true)
public class GraphSetting extends AbstractCommonElement<GraphSetting>{

	private final Graph parent;
	
	public GraphSetting(Graph parent){
		this.parent=parent;
	}
	@Props
	private Rect bb;
	@Props
	private String _background;
	/**
	 * default <none>
	 */
	@Props
	private String bgcolor;
	@Props
	private Boolean center;
	@Props
	private String charset;
	@Props
	private String comment;
	@Props
	private ClusterMode clusterrank;
	/**
	 * dot only
	 * default false
	 */
	@Props
	private Boolean compound;
	/**
	 * default false
	 */
	@Props
	private Boolean concentrate;
	/**
	 * default 0.99
	 * Minimum 0.0
	 * neato only
	 */
	@Props
	private Double Damping;
	/**
	 * svg, bitmap output only
	 */
	@Props
	private Double dpi;
	/**
	 * 	neato only
	 */
	@Props
	private Double defaultdist;
	/**
	 * sfdp, fdp, neato only
	 * default 2
	 */
	@Props
	private Integer dim;
	/**
	 * sfdp, fdp, neato only
	 * default 2
	 */
	@Props
	private Integer dimen;
	/**
	 * neato only
	 */
	@Props
	private String diredgeconstraints;
	/**
	 * 	neato only
	 */
	@Props
	private String epsilon;
	/**
	 * 	not dot
	 */
	@Props
	private Double esep;
	@Props
	private Boolean forcelabels;
	@Props
	private String fontpath;
	@Props
	private Integer gradientangle;
	@Props
	private String imagepath;
	@Props
	private Double inputscale;
	@Props
	private Double labelangle;
	/**
	 * 	sfdp only
	 */
	@Props
	private Integer label_scheme;
	@Props
	private String labeljust;
	@Props
	private String labelloc;
	@Props
	private Boolean landscape;
	@Props
	private String layerlistsep;
	@Props
	private String[] layers;
	@Props
	private String layersep;
	@Props
	private GraphLayout layout;
	@Props
	private Integer levels;
	@Props
	private Double levelsgap;
	@Props
	private Double lheight;
	@Props
	private Point lp;
	@Props
	private Double lwidth;
	@Props
	private Integer maxiter;
	@Props
	private Double mclimit;
	@Props
	private Double mindist;
	@Props
	private String mode;
	@Props
	private String model;
	@Props
	private Boolean mosek;
	@Props
	private Rankdir rankdir;
	/**
	 * default 0.25
	 * min 0.02
	 */
	@Props
	private Double nodesep;
	@Props
	private Boolean normalize;
	@Props
	private Boolean notranslate;
	@Props
	private Double nslimit;
	@Props
	private Double nslimit1;
	@Props
	private String ordering;
	@Props
	private String orientation;
	@Props
	private OutputOrderMode outputorder;
	@Props
	private double[] ranksep;
	@Props
	private NodeShape shape;
	@Props
	private SmoothType smoothing;
	/**
	 * default 0.75
	 */
	@Props
	private Double width;

	protected GraphSetting instance(){
		return this;
	}
	
	protected GraphStringBuilder createGraphStringBuilder(){
		GraphStringBuilder builder=new GraphStringBuilder("graph");
		return builder;
	}
	
	@Override
	protected Map<String,Object> getProperties(){
		Map<String,Object> props=super.getProperties();
		put(props, "bb", bb);
		put(props, "_background", _background);
		put(props, "bgcolor", bgcolor);
		put(props, "center", center);
		put(props, "charset", charset);
		put(props, "clusterrank", clusterrank);
		put(props, "compound", compound);
		put(props, "comment", comment);
		put(props, "Damping", Damping);
		put(props, "defaultdist", defaultdist);
		put(props, "dpi", dpi);
		put(props, "dim", dim);
		put(props, "dimen", dimen);
		put(props, "diredgeconstraints", diredgeconstraints);
		put(props, "epsilon", epsilon);
		put(props, "esep", esep);
		put(props, "forcelabels", forcelabels);
		put(props, "fontpath", fontpath);
		put(props, "gradientangle", gradientangle);
		put(props, "imagepath", imagepath);
		put(props, "inputscale", inputscale);
		put(props, "labelangle", labelangle);
		put(props, "label_scheme", label_scheme);
		put(props, "labeljust", labeljust);
		put(props, "labelloc", labelloc);
		put(props, "landscape", landscape);
		put(props, "layerlistsep", layerlistsep);
		put(props, "layers", layers);
		put(props, "layersep", layersep);
		put(props, "layout", layout);
		put(props, "levels", levels);
		put(props, "levelsgap", levelsgap);
		put(props, "lheight", lheight);
		put(props, "lp", lp);
		put(props, "lwidth", lwidth);
		put(props, "rankdir", rankdir);
		put(props, "nodesep", nodesep);
		put(props, "normalize", normalize);
		put(props, "notranslate", notranslate);
		put(props, "nslimit", nslimit);
		put(props, "nslimit1", nslimit1);
		put(props, "ordering", ordering);
		put(props, "orientation", orientation);
		put(props, "outputorder", outputorder);
		put(props, "ranksep", ranksep);
		put(props, "shape", shape);
		put(props, "smoothing", smoothing);
		put(props, "width", width);

		return props;
	}
	
	public GraphSetting setRanksep(double...args){
		this.ranksep=args;
		return instance();
	}

	public GraphSetting setBgcolor(String bgcolor){
		this.bgcolor=bgcolor;
		return instance();
	}
	
	public GraphSetting setBgcolor(Color bgcolor){
		if (bgcolor!=null){
			this.bgcolor=bgcolor.toString();
		} else{
			this.bgcolor=null;
		}
		return instance();
	}
	
	public GraphSetting setBgcolor(int bgcolor){
		if (this.getColorscheme()!=null){
			this.bgcolor=""+this.getColorscheme().mod(bgcolor);
		} else{
			this.bgcolor=""+bgcolor;
		}
		return instance();
	}
}
