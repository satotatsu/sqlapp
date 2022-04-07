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
/**
 * 
 * @author satot
 * @see <a href="https://www.graphviz.org/doc/info/shapes.html">shapes</a>
 */
public enum NodeShape {
	 box
	, polygon(){
		@Override
		public Polygon toPolygon(Node node){
			return (Polygon)node;
		}
	 }
	, ellipse
	, oval
	, circle
	, point
	, egg
	, triangle
	, plaintext
	, plain
	, diamond
	, trapezium
	, parallelogram	
	, house
	, pentagon
	, hexagon
	, septagon
	, octagon
	, doublecircle
	, doubleoctagon
	, tripleoctagon
	, invtriangle
	, invtrapezium
	, invhouse
	, Mdiamond
	, Msquare
	, Mcircle
	, rect
	, rectangle
	, square
	, star
	, none
	, folder
	, box3d
	, component
	, promoter
	, cds
	, terminator
	, utr
	, primersite
	, restrictionsite
	, fivepoverhang
	, threepoverhang
	, noverhang
	, assembly
	, signature
	, insulator
	, ribosite
	, rnastab
	, proteasesite
	, proteinstab
	, rpromoter
	, rarrow
	, larrow
	, lpromoter
	,;

	public Polygon toPolygon(Node node){
		return null;
	}
}
