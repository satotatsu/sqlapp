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
 * @see https://graphviz.org/doc/info/colors.html#brewer
 */
public enum BrewerColorScheme {
	accent3(fromTo(1,3), "#7fc97f", "#beaed4", "#fdc086"),
	accent4(fromTo(1,4), "#7fc97f", "#beaed4", "#fdc086", "#ffff99"),
	accent5(fromTo(1,5), "#7fc97f", "#beaed4", "#fdc086", "#ffff99", "#386cb0"),
	accent6(fromTo(1,6), "#7fc97f", "#beaed4", "#fdc086", "#ffff99", "#386cb0", "#f0027f"),
	accent7(fromTo(1,7), "#7fc97f", "#beaed4", "#fdc086", "#ffff99", "#386cb0", "#f0027f", "#bf5b17"),
	accent8(fromTo(1,8), "#7fc97f", "#beaed4", "#fdc086", "#ffff99", "#386cb0", "#f0027f", "#bf5b17", "#666666")
	//
	,
	blues3(fromTo(1,3), "#deebf7", "#9ecae1", "#3182bd"),
	blues4(fromTo(1,4), "#eff3ff", "#bdd7e7", "#6baed6", "#2171b5"),
	blues5(fromTo(1,5), "#f7fbff", "#deebf7", "#6baed6", "#3182bd", "#08519c"),
	blues6(fromTo(1,6), "#f7fbff", "#deebf7", "#9ecae1", "#6baed6", "#3182bd", "#08519c"),
	blues7(fromTo(1,7), "#f7fbff", "#deebf7", "#c6dbef", "#9ecae1", "#6baed6", "#4292c6", "#2171b5"),
	blues8(fromTo(1,8), "#f7fbff", "#deebf7", "#c6dbef", "#9ecae1", "#6baed6", "#4292c6", "#2171b5", "#08519c"),
	blues9(fromTo(1,9), "#f7fbff", "#deebf7", "#c6dbef", "#9ecae1", "#6baed6", "#4292c6", "#2171b5", "#08519c", "#08306b")
	//
	,
	brbg3(fromTo(1,3)),
	brbg4(fromTo(1,4)),
	brbg5(fromTo(1,5)),
	brbg6(fromTo(1,6)),
	brbg7(fromTo(1,7)),
	brbg8(fromTo(1,8)),
	brbg9(fromTo(1,9)),
	brbg10(fromTo(1,10)),
	brbg11(fromTo(1,11))
	//
	,
	bugn3(fromTo(1,3)),
	bugn4(fromTo(1,4)),
	bugn5(fromTo(1,5)),
	bugn6(fromTo(1,6)),
	bugn7(fromTo(1,7)),
	bugn8(fromTo(1,8)),
	bugn9(fromTo(1,9))
	//
	,
	bupu3(fromTo(1,3)),
	bupu4(fromTo(1,4)),
	bupu5(fromTo(1,5)),
	bupu6(fromTo(1,6)),
	bupu7(fromTo(1,7)),
	bupu8(fromTo(1,8)),
	bupu9(fromTo(1,9))
	//
	,
	dark23(fromTo(1,3)),
	dark24(fromTo(1,4)),
	dark25(fromTo(1,5)),
	dark26(fromTo(1,6)),
	dark27(fromTo(1,7)),
	dark28(fromTo(1,8))
	//
	,
	gnbu3(fromTo(1,3)),
	gnbu4(fromTo(1,4)),
	gnbu5(fromTo(1,5)),
	gnbu6(fromTo(1,6)),
	gnbu7(fromTo(1,7)),
	gnbu8(fromTo(1,8)),
	gnbu9(fromTo(1,9))
	,
	greens3(fromTo(1,3)),
	greens4(fromTo(1,4)),
	greens5(fromTo(1,5)),
	greens6(fromTo(1,6)),
	greens7(fromTo(1,7)),
	greens8(fromTo(1,8)),
	greens9(fromTo(1,9))
	,
	greys3(fromTo(1,3)),
	greys4(fromTo(1,4)),
	greys5(fromTo(1,5)),
	greys6(fromTo(1,6)),
	greys7(fromTo(1,7)),
	greys8(fromTo(1,8)),
	greys9(fromTo(1,9))
	;

	private final int[] values;

	private final String[] colorValues;

	private BrewerColorScheme(int[] values, String... colorValues){
		this.values=values;
		this.colorValues=colorValues;
	}
	
	public int getMax(){
		return values[values.length-1];
	}

	public int getMin(){
		return values[0];
	}

	public int get(int value){
		value=mod(value);
		return values[value];
	}

	public String getFontcolor(int value){
		value=mod(value);
		if (this.values.length>6){
			if (value>6){
				return "white";
			}
		}
		return null;
	}
	
	protected int mod(int value){
		value=Math.abs(value);
		return (value-((value)/values.length)*values.length);
	}

	public static int[] fromTo(int start, int end){
		int[] ret=new int[end-start+1];
		for(int i=start;i<=end;i++){
			ret[i-start]=i;
		}
		return ret;
	}

	/**
	 * @return the values
	 */
	public int[] getValues() {
		return values;
	}

	/**
	 * @return the colorValues
	 */
	public String[] getColorValues() {
		return colorValues;
	}
	
}
