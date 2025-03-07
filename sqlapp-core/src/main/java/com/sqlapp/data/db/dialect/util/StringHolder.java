/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.util;

import java.io.Serializable;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.exceptions.InvalidTextException;

public class StringHolder implements Cloneable, Serializable{

	/** serialVersionUID */
	private static final long serialVersionUID = 1L;

	private final String context;
	
	private int position=0;
	
	private String current=null;

	public StringHolder(String context){
		this.context=normalize(context);
	}
	
	private String normalize(String original){
		StringBuilder builder=new StringBuilder(original.length()+1);
		String[] splits=original.split("\n");
		for(String arg:splits){
			builder.append(arg.replace("\r", ""));
			builder.append('\n');
		}
		return builder.toString();
	}
	
	public boolean isSpace(int start, int end){
		for (int i=start;i<context.length()&&i<end;i++){
			char c=context.charAt(i);
			if(isSpace(c)){
				continue;
			}
			return false;
		}
		return true;
	}

	public boolean isSpace(char c){
		if(Character.isWhitespace(c)){
			return true;
		}
		return false;
	}
	
	public int indexOf(char target){
		return indexOf(target, position);
	}

	public int indexOf(char target, int from){
		for (int i=from;i<context.length();i++){
			char c=context.charAt(i);
			if(c==target){
				return i;
			}
		}
		return -1;
	}

	public int lastIndexOf(char target){
		return lastIndexOf(target, position);
	}

	public int lastIndexOf(char target, int from){
		for (int i=from;i>=0;i--){
			char c=context.charAt(i);
			if(c==target){
				return i;
			}
		}
		return -1;
	}

	public int indexOf(String target){
		return indexOf(target, position);
	}

	public int indexOf(String target, int from){
		int end=context.length()-target.length();
		int i=from;
		while (i<end){
			char c=context.charAt(i);
			boolean match=true;
			int j=0;
			for(j=0;j<target.length();j++){
				c=context.charAt(i+j);
				char t=target.charAt(j);
				if(c!=t){
					match=false;
					break;
				}
			}
			if (match){
				return i;
			}
			i++;
		}
		return -1;
	}

	public int indexOfLine(String target){
		return indexOfLine(target, position);
	}
	
	public int indexOfLine(String target, int from){
		int end=context.length()-target.length();
		int i=from;
		while (i<end){
			char c=context.charAt(i);
			if (c=='\n'){
				break;
			}
			boolean match=true;
			int j=0;
			for(j=0;j<target.length();j++){
				c=context.charAt(i+j);
				char t=target.charAt(j);
				if(c!=t){
					match=false;
					break;
				}
			}
			if (match){
				return i;
			}
			i++;
		}
		return -1;
	}
	
	public int searchEndQuote(String quote, String escape, int from){
		int end=context.length()-quote.length();
		int i=from;
		while (i<end){
			char c=context.charAt(i);
			boolean match=true;
			int j=0;
			if (i<context.length()-escape.length()){
				for(j=0;j<escape.length();j++){
					c=context.charAt(i+j);
					char t=escape.charAt(j);
					if(c!=t){
						match=false;
						break;
					}
				}
				if (match){
					i=i+escape.length();
					continue;
				}
			}
			match=true;
			j=0;
			for(j=0;j<quote.length();j++){
				c=context.charAt(i+j);
				char t=quote.charAt(j);
				if(c!=t){
					match=false;
					break;
				}
			}
			if (match){
				return i;
			}
			i++;
		}
		return -1;
	}
	
	public int searchEndQuote(String quote, String escape){
		return searchEndQuote(quote, escape, position);
	}

	public int searchStartQuote(String quote){
		int end=context.length()-quote.length();
		int i=position;
		while (i<end){
			char c=context.charAt(i);
			if (c=='\n'){
				break;
			}
			boolean match=true;
			int j=0;
			for(j=0;j<quote.length();j++){
				c=context.charAt(i+j);
				char t=quote.charAt(j);
				if(c!=t){
					match=false;
					break;
				}
			}
			if (match){
				if (i>0){
					char pre=context.charAt(i-1);
					if (!this.isSpace(pre)){
						if (pre!='('&&pre!=','&&pre!='/'&&pre!='+'){
							i=i+quote.length();
							continue;
						}
					}
				}
				return i;
			}
			i++;
		}
		return -1;
	}

	
	public int nextLineOf(String target){
		int end=context.indexOf("\n"+target, position);
		if (end>=0){
			int nextLineBreak=context.indexOf("\n", end+target.length());
			if (nextLineBreak>=0){
				if (isSpace(end+target.length()+1, nextLineBreak)){
					return end+1;
				}
			}
		}
		return -1;
	}
	
	public int searchLineOf(Pattern pattern, int from){
		return searchLineOf(pattern, from, true);
	}

	public int searchLineOf(Pattern pattern, int from, boolean inline){
		return searchLineOf(pattern, from, inline, null);
	}
	
	public int searchLineOf(Pattern pattern, int from, boolean inline, BiPredicate<Integer, Matcher> c){
		int i=from;
		while(i<context.length()&&i>=0){
			int pos=this.indexOf('\n', i);
			if (pos<0){
				pos=context.length();
			}
			String text;
			if (inline){
				text=context.substring(i, pos);
			} else{
				text=context.substring(i);
			}
			Matcher matcher=pattern.matcher(text);
			if (matcher.matches()){
				if (c!=null){
					if (c.test(i, matcher)){
						return i;
					}
				} else{
					return i;
				}
			}
			i=pos+1;
		}
		return -1;
	}
	
	/**
	 * 空白を除いて指定した文字の位置を探します。
	 * @param target
	 * @return 空白を除いて指定した文字の位置
	 */
	public int searchFirstElement(String target){
		int end=context.length()-target.length();
		int i=position;
		while (i<end){
			char c=context.charAt(i);
			if(isSpace(c)){
				i++;
				continue;
			}
			boolean match=true;
			int j=0;
			for(j=0;j<target.length();j++){
				c=context.charAt(i+j);
				char t=target.charAt(j);
				if(c!=t){
					match=false;
					break;
				}
			}
			if (match){
				return i;
			}
			break;
		}
		return -1;
	}

	public int searchWord(String target){
		return searchWord(target, position);
	}
	
	/**
	 * 指定した文字の位置を探します。
	 * @param target 探す文字
	 * @param from 開始位置
	 * @return 指定した文字の位置
	 */
	public int searchWord(String target, int from){
		int end=context.length()-target.length();
		int i=from;
		while (i<end){
			char c=context.charAt(i);
			if(isSpace(c)){
				i++;
				continue;
			}
			boolean match=true;
			int j=0;
			for(j=0;j<target.length();j++){
				c=context.charAt(i+j);
				char t=target.charAt(j);
				if(c!=t){
					match=false;
					break;
				}
			}
			if (match){
				if (i+target.length()<context.length()){
					c=context.charAt(i+target.length());
					if (isSpace(c)){
						return i;
					}
				}
			}
			i++;
		}
		return -1;
	}
	
	public char charAt(int position){
		if (position<context.length()){
			return this.context.charAt(position);
		} else{
			return '\0';
		}
	}
	
	/**
	 * 空白でない最初の位置を探します。
	 * @return　空白でない最初の位置
	 */
	public int searchElement(){
		return searchElement(position);
	}
	
	public int searchElement(int from){
		int end=context.length();
		int i=from;
		while (i<end){
			char c=context.charAt(i);
			if(isSpace(c)){
				i++;
				continue;
			}
			return i;
		}
		return -1;
	}

	public int getLineEnd(){
		int i=indexOf('\n');
		if (i>=0){
			return i;
		}
		return context.length();
	}

	public boolean hasNext(){
		return this.context.length()>position;
	}
	
	/**
	 * 現在の位置を返します。
	 * @return 現在の位置
	 */
	public int getPosition(){
		return this.position;
	}

	public void setPosition(int position){
		this.position=position;
	}

	public void addPosition(int val){
		this.position=this.position+val;
	}

	public Matcher substringMatcher(Pattern pattern){
		return pattern.matcher(substringAt());
	}
	
	public String left(int length){
		return substringAt(length);
	}
	
	public String substringAt(int end){
		if (position<context.length()){
			if (end<=context.length()){
				return context.substring(position, end);
			}
		}
		return null;
	}

	public String substringAt(){
		return context.substring(position);
	}

	public InvalidTextException throwInvalidTextException(String message){
		throw new InvalidTextException(getCurrentLine(), getCurrentLineNumber(), getCurrentLinePosition(), message);
	}
	
	public String getLine(){
		int pos=getLineEnd();
		if (pos>=0){
			return substringAt(pos);
		}
		return substringAt();
	}

	public void nextLine(){
		int pos=getLineEnd();
		if (pos>=0){
			this.setPosition(pos+1);
		}
	}

	/**
	 * 現在の行数を返します。
	 * @return 現在の行数
	 */
	public int getCurrentLineNumber(){
		int num=1;
		for(int i=0;i<position;i++){
			char c=context.charAt(i);
			if (c=='\n'){
				num++;
			}
		}
		return num;
	}

	/**
	 * 現在の行を返します。
	 * @return 現在の行
	 */
	public String getCurrentLine(){
		int start=context.lastIndexOf('\n', position);
		if (start<0){
			start=0;
		} else{
			start=start+1;
		}
		int end=context.indexOf('\n', position);
		if (end>=0){
			return context.substring(start, end);
		} else{
			return context.substring(start);
		}
	}

	
	/**
	 * 現在の行での位置を返します。
	 * @return 現在の行での位置
	 */
	public int getCurrentLinePosition(){
		int pos=lastIndexOf('\n');
		if (pos>=0){
			return this.position-pos+1;
		}
		return position+1;
	}

	
	public boolean startsWith(String text){
		if (position+text.length()>context.length()){
			return false;
		}
		for(int i=position;i<position+text.length();i++){
			if (context.charAt(i)!=text.charAt(i)){
				return false;
			}
		}
		return true;
	}

	public int getContextLength(){
		return this.context.length();
	}
	
	/**
	 * @return the current
	 */
	public String getCurrent() {
		return current;
	}

	/**
	 * @param current the current to set
	 */
	public void setCurrent(String current) {
		this.current = current;
	}

	@Override
	public StringHolder clone(){
		StringHolder clone=new StringHolder(this.context);
		clone.setPosition(this.position);
		clone.current=this.current;
		return clone;
	}

	public String toString(){
		StringBuilder builder=new StringBuilder();
		builder.append("position=").append(position);
		builder.append(", current=").append(current);
		builder.append(", substring=").append(this.substringAt());
		return builder.toString();
	}
}
