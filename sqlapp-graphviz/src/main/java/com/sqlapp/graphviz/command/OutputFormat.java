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
package com.sqlapp.graphviz.command;

public enum OutputFormat {
	bmp("Windows Bitmap Format")
	, canon("DOT"){
		@Override
		public String getExtension(){
			return "dot";
		}
	}
	, dot("DOT"){
		@Override
		public String getExtension(){
			return "dot";
		}
	}
	, gv("DOT"){
		@Override
		public String getExtension(){
			return "dot";
		}
	}
	, xdot("DOT"){
		@Override
		public String getExtension(){
			return "dot";
		}
	}
	, xdot1_2("DOT"){
		@Override
		public String getExtension(){
			return "dot";
		}
		@Override
		public String getFormat(){
			return "xdot1.2";
		}
	}
	, xdot1_4("DOT"){
		@Override
		public String getExtension(){
			return "dot";
		}
		@Override
		public String getFormat(){
			return "xdot1.4";
		}
	}
	, cgimage("CGImage bitmap format")
	, cmap("Client-side imagemap"){
		@Override
		public boolean isDeprecated(){
			return true;
		}
	}
	, eps("Encapsulated PostScript")
	, exr("OpenEXR")
	, fig("FIG")
	, gd("GD/GD2 formats")
	, gd2("GD/GD2 formats")
	, gif("GIF")
	, gtk("GTK canvas")
	, ico("Icon Image File Format")
	, imap("Server-side and client-side imagemaps")
	, cmapx("Server-side and client-side imagemaps")
	, imap_np("Server-side and client-side imagemaps")
	, cmapx_np("Server-side and client-side imagemaps")
	, ismap("Server-side imagemap"){
		@Override
		public boolean isDeprecated(){
			return true;
		}
	}
	, jp2("JPEG 2000")
	, jpg("JPEG"){
		@Override
		public String getExtension(){
			return "jpg";
		}
	}
	, jpeg("JPEG"){
		@Override
		public String getExtension(){
			return "jpg";
		}
	}
	, jpe("JPEG"){
		@Override
		public String getExtension(){
			return "jpg";
		}
	}
	, json("Dot graph represented in JSON format"){
		@Override
		public boolean isText(){
			return false;
		}
	}
	, json0("Dot graph represented in JSON format"){
		@Override
		public boolean isText(){
			return false;
		}
		@Override
		public String getExtension(){
			return "json";
		}
	}
	, dot_json("Dot graph represented in JSON format"){
		@Override
		public boolean isText(){
			return false;
		}
		@Override
		public String getExtension(){
			return "json";
		}
	}
	, xdot_json("Dot graph represented in JSON format"){
		@Override
		public boolean isText(){
			return false;
		}
		@Override
		public String getExtension(){
			return "json";
		}
	}
	, pct("PICT") {
		@Override
		public String getExtension(){
			return "pct";
		}
	}
	, pict("PICT") {
		@Override
		public String getExtension(){
			return "pct";
		}
	}
	, pdf("Portable Document Format (PDF)")
	, pic("Kernighan's PIC graphics language")
	, plain("Simple text format") {
		@Override
		public String getExtension(){
			return "txt";
		}
	}
	, plain_ext("Simple text format") {
		@Override
		public String getExtension(){
			return "txt";
		}
	}
	, png("Portable Network Graphics format")
	, pov("POV-Ray markup language (prototype)")
	, ps("PostScript")
	, ps2("PostScript for PDF")
	, psd("PSD")
	, sgi("SGI")
	, svg("Scalable Vector Graphics"){
		@Override
		public boolean isText(){
			return false;
		}
	}
	, svgz("Scalable Vector Graphics") {
		@Override
		public String getExtension(){
			return "svg";
		}
	}
	, tga("Truevision TGA")
	, tif("TIFF (Tag Image File Format)")
	, tiff("TIFF (Tag Image File Format)")
	, tk("TK graphics")
	, vml("Vector Markup Language (VML)")
	, vmlz("Vector Markup Language (VML)")
	, vrml("VRML")
	, wbmp("Wireless BitMap format")
	, webp("Image format for the Web")
	, xlib ("Xlib can")
	, x11("Xlib can")
	,;
	private final String comment;

	private OutputFormat(){
		this.comment=null;
	}
	private OutputFormat(String comment){
		this.comment=comment;
	}
	
	/**
	 * @return the comment
	 */
	public String getComment() {
		return comment;
	}

	public boolean isDeprecated(){
		return false;
	}
	
	public String getFormat(){
		return this.toString();
	}

	public boolean isText(){
		return false;
	}

	public String getExtension(){
		return this.toString();
	}

	public static OutputFormat parse(String text){
		if (text==null){
			return null;
		}
		for(OutputFormat enm:OutputFormat.values()){
			if (enm.toString().equalsIgnoreCase(text)){
				return enm;
			}
		}
		for(OutputFormat enm:OutputFormat.values()){
			if (enm.getExtension().equalsIgnoreCase(text)){
				return enm;
			}
		}
		return OutputFormat.png;
	}
	
}
