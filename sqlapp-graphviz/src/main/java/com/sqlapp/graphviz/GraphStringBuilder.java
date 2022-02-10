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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

public class GraphStringBuilder implements Serializable{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6358515277099678728L;
	private List<Object> elements=new ArrayList<>();
	private Map<String,Object> map=new TreeMap<>();
	
	private boolean withLineBreak=true;
	
	/**
	 * インデントレベル
	 */
	private int indentLevel = 0;
	/**
	 * インデント文字
	 */
	private String indentString = "\t";
	
	private String open="[";
	private String close="]";
	
	private boolean withLastSemiColon=true;
	
	/**
	 * 現在のインデント文字列
	 */
	private String currentIndentString = null;
	public GraphStringBuilder(){
		this.name=null;
	}

	public GraphStringBuilder(String name){
		this.name=name;
	}

	private String name;

	public GraphStringBuilder put(Object value){
		this.elements.add(value);
		return instance();
	}

	public GraphStringBuilder put(String name, Object value){
		if (value==null){
			return instance();
		}
		if (value instanceof String){
			return put(name, (String)value);
		}else if (value instanceof Number){
			return put(name, (Number)value);
		}else if (value instanceof Enum){
			return put(name, (Enum<?>)value);
		}else if (value instanceof Color[]){
			return put(name, (Color[])value);
		}else if (value instanceof ArrowType[]){
			return put(name, (ArrowType[])value);
		}
		return put(name, value.toString());
	}
	
	public GraphStringBuilder put(String name, String value){
		return putInternal(name, value, ()->escapedValue(value));
	}

	public GraphStringBuilder putNoEscape(String name, String value){
		return putInternal(name, value, ()->value);
	}

	private String escapedValue(String value){
		return "\""+value+"\"";
	}
	
	public GraphStringBuilder put(String name, Number value){
		return putInternal(name, value, ()->value.toString());
	}

	public GraphStringBuilder put(String name, Enum<?> value){
		return putInternal(name, value, ()->value.toString());
	}

	public GraphStringBuilder put(String name, Color... value){
		return putInternal(name, value, ()->toString(value));
	}
	
	public GraphStringBuilder put(String name, String... args){
		return putInternal(name, args, ()->toString(args));
	}

	public GraphStringBuilder put(String name, double... args){
		return putInternal(name, args, ()->toString(args));
	}

	public GraphStringBuilder put(String name, ArrowType... args){
		return putInternal(name, args, ()->toString(args));
	}

	

	protected <T> GraphStringBuilder putInternal(String name, T value, Supplier<String> supplier){
		if (value==null){
			if (map.containsKey(name)){
				map.remove(name);
			}
			return instance();
		}
		map.put(name, supplier.get());
		return instance();
	}
	
	private String toString(String... args){
		if (args==null||args.length==0){
			return null;
		}
		StringBuilder builder=new StringBuilder();
		builder.append("\"");
		boolean first=true;
		for(String arg:args){
			if (!first){
				builder.append(",");
			} else{
				first=false;
			}
			builder.append(arg);
		}
		builder.append("\"");
		return builder.toString();
	}

	private String toString(double... args){
		if (args==null||args.length==0){
			return null;
		}
		StringBuilder builder=new StringBuilder();
		builder.append("\"");
		boolean first=true;
		for(double arg:args){
			if (!first){
				builder.append(",");
			} else{
				first=false;
			}
			builder.append(arg);
		}
		builder.append("\"");
		return builder.toString();
	}
	
	private String toString(Color... value){
		if (value==null||value.length==0){
			return null;
		}
		StringBuilder builder=new StringBuilder();
		builder.append("\"");
		boolean first=true;
		for(Color color:value){
			if (!first){
				builder.append(":");
			} else{
				first=false;
			}
			builder.append(color);
		}
		builder.append("\"");
		return builder.toString();
	}
	
	private String toString(ArrowType... args){
		if (args==null||args.length==0){
			return null;
		}
		StringBuilder builder=new StringBuilder();
		builder.append("\"");
		for(ArrowType arg:args){
			builder.append(arg);
		}
		builder.append("\"");
		return builder.toString();
	}
	
	public GraphStringBuilder put(String name, Boolean value){
		if (value==null){
			if (map.containsKey(name)){
				map.remove(name);
			}
			return instance();
		}
		map.put(name, value.toString());
		return instance();
	}

	protected GraphStringBuilder instance(){
		return this;
	}
	
	@Override
	public String toString(){
		StringBuilder builder=new StringBuilder();
		if (name!=null&&name.length()>0){
			builder.append(name);
		} else{
			if (isEmpty()){
				return "";
			}
		}
		if (isEmpty()){
			if (isWithLastSemiColon()){
				builder.append(";");
			}
			return builder.toString();
		}
		builder.append(" ").append(this.getOpen());
		this.addIndentLevel(1);
		for(Object obj:elements){
			if (obj==null){
				continue;
			}
			if (withLineBreak){
				lineBreak(builder);
			}
			builder.append(obj.toString());
		}
		boolean first=true;
		for(Map.Entry<String, Object> entry:map.entrySet()){
			if (entry.getValue()==null){
				continue;
			}
			if (withLineBreak){
				lineBreak(builder);
			}
			if (!first){
				builder.append(", ");
			} else{
				first=false;
			}
			String value=entry.getValue().toString();
			builder.append(entry.getKey());
			builder.append("=");
			appendValue(builder, value);
		}
		this.addIndentLevel(-1);
		if (withLineBreak){
			lineBreak(builder);
		}
		builder.append(this.getClose());
		if (name!=null&&(name.startsWith("graph")||name.startsWith("digraph")||name.startsWith("subgraph"))){
			if (!";".equals(this.getClose())){
				if ("}".equals(this.getClose())){
				} else{
					if (isWithLastSemiColon()){
						builder.append(";");
					}
				}
			}
		} else{
			if (isWithLastSemiColon()){
				if (!";".equals(this.getClose())){
					builder.append(";");
				}
			}
		}
		return builder.toString();
	}
	
	private GraphStringBuilder lineBreak(StringBuilder builder){
		builder.append("\n");
		indent(builder);
		return instance();
	}

	private GraphStringBuilder appendValue(StringBuilder builder, String value){
		builder.append(value);
		return instance();
	}

	/**
	 * @return the open
	 */
	public String getOpen() {
		return open;
	}

	/**
	 * @param open the open to set
	 */
	public GraphStringBuilder setOpen(String open) {
		this.open = open;
		return instance();
	}

	/**
	 * @return the close
	 */
	public String getClose() {
		return close;
	}

	/**
	 * @param close the close to set
	 */
	public GraphStringBuilder setClose(String close) {
		this.close = close;
		return instance();
	}

	/**
	 * @return the indentLevel
	 */
	public int getIndentLevel() {
		return indentLevel;
	}

	/**
	 * @param indentLevel
	 *            the indentLevel to set
	 */
	public GraphStringBuilder setIndentLevel(int indentLevel) {
		if (indentLevel >= 0) {
			this.indentLevel = indentLevel;
			this.currentIndentString = getString(this.indentString, indentLevel);
		}
		return instance();
	}

	public GraphStringBuilder addIndentLevel(int addCount) {
		setIndentLevel(this.getIndentLevel() + addCount);
		return instance();
	}

	/**
	 * @return the indentString
	 */
	public String getIndentString() {
		return indentString;
	}

	/**
	 * @param indentString
	 *            the indentString to set
	 */
	public GraphStringBuilder setIndentString(String indentString) {
		this.indentString = indentString;
		return setIndentLevel(this.getIndentLevel());
	}

	/**
	 * インデントの出力
	 * 
	 * @throws XMLStreamException
	 */
	private GraphStringBuilder indent(StringBuilder builder) {
		builder.append(this.currentIndentString);
		return instance();
	}

	/**
	 * 指定した文字、長さの文字列を取得します
	 * 
	 * @param val
	 *            指定文字
	 * @param size
	 *            長さ
	 * @return 指定した文字、長さの文字列
	 */
	private String getString(final String val, final int size) {
		StringBuilder builder = new StringBuilder(val.length() * size);
		for (int i = 0; i < size; i++) {
			builder.append(val);
		}
		return builder.toString();
	}
	
	public boolean isEmpty(){
		return map.isEmpty()&&elements.isEmpty()&&map.isEmpty();
	}

	/**
	 * @return the withLineBreak
	 */
	public boolean isWithLineBreak() {
		return withLineBreak;
	}

	/**
	 * @param withLineBreak the withLineBreak to set
	 */
	public GraphStringBuilder setWithLineBreak(boolean withLineBreak) {
		this.withLineBreak = withLineBreak;
		return this.instance();
	}

	/**
	 * @return the withLastSemiColon
	 */
	public boolean isWithLastSemiColon() {
		return withLastSemiColon;
	}

	/**
	 * @param withLastSemiColon the withLastSemiColon to set
	 */
	public GraphStringBuilder setWithLastSemiColon(boolean withLastSemiColon) {
		this.withLastSemiColon = withLastSemiColon;
		return this.instance();
	}
	
}
