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

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;

import lombok.Data;

public class SqlTokenizer {

	private String delimiter = ";";

	private String currentDelimiter = null;
	
	protected StringHolder stringHolder;
	
	private boolean comment=false;
	
	public SqlTokenizer(String original){
		this.stringHolder=new StringHolder(original);
		preParse(stringHolder);
		currentDelimiter=null;
	}
	
	private TreeMap<Integer, TextMarker> textMarkers=new TreeMap<>();
	
	protected void preParse(StringHolder stringHolder){
		stringHolder=stringHolder.clone();
		if (!stringHolder.hasNext()){
		}
		while(stringHolder.hasNext()){
			int pos=stringHolder.getLineEnd();
			String text=stringHolder.getLine();
			if (isChangeDelimiter(text, stringHolder)){
				continue;
			}
			if (isLineComment(text)){
				TextMarker marker=new TextMarker();
				marker.setStart(stringHolder.getPosition());
				marker.setEnd(pos);
				marker.setValue(text);
				marker.setType(Type.SINGLE_LINE_COMMENT);
				addTextMarker(marker);
				stringHolder.setPosition(pos+1);
				continue;
			}
			if (isStartMultiLineComment(text, stringHolder)){
				int commStart=stringHolder.searchFirstElement("/*");
				stringHolder.setPosition(commStart);
				int commEnd=stringHolder.indexOf("*/");
				if (commEnd>=0){
					commEnd=commEnd+2;
					TextMarker marker=new TextMarker();
					marker.setStart(commStart);
					marker.setEnd(commEnd);
					marker.setValue(stringHolder.substringAt(commEnd));
					marker.setType(Type.MULTI_LINE_COMMENT);
					addTextMarker(marker);
					stringHolder.setPosition(commEnd);
				} else{
					stringHolder.throwInvalidTextException("[*/] not found.");
				}
				continue;
			}
			QuoteHolder quoteHolderMax=getQuoteHolder(stringHolder);
			if (quoteHolderMax.position>=0){
				StringHolder clone=stringHolder.clone();
				clone.setPosition(quoteHolderMax.position);
				clone.addPosition(quoteHolderMax.quote.length());
				int endPos=clone.searchEndQuote("'", "''");
				if (endPos>=0){
					TextMarker marker=new TextMarker();
					stringHolder.setPosition(quoteHolderMax.position);
					marker.setStart(quoteHolderMax.position);
					marker.setEnd(endPos);
					marker.setValue(stringHolder.substringAt(endPos+1));
					marker.setType(Type.QUOTED_STRING);
					addTextMarker(marker);
					stringHolder.setPosition(endPos+1);
				} else{
					stringHolder.throwInvalidTextException("['] end quote not found.");
				}
			} else{
				stringHolder.nextLine();
			}
		}
	}
	
	protected QuoteHolder getQuoteHolder(StringHolder stringHolder){
		return getQuoteHolder(stringHolder, "N'", "B'", "'");
	}

	protected QuoteHolder getQuoteHolder(StringHolder stringHolder, String...args){
		List<QuoteHolder> quoteHolders=CommonUtils.list();
		for(String arg:args){
			QuoteHolder quoteHolder=searchQuote(stringHolder, arg);
			quoteHolders.add(quoteHolder);
		}
		QuoteHolder quoteHolder=quoteHolders.get(0);
		for(int i=1;i<quoteHolders.size();i++){
			quoteHolder=quoteHolder.max(quoteHolders.get(i));
		}
		return quoteHolder;
	}

	static class QuoteHolder{
		public QuoteHolder(){};
		public QuoteHolder(String quote, int position){
			this.position=position;
			this.quote=quote;
		};
		public String quote;
		public int position;
		QuoteHolder max(QuoteHolder holder){
			if (this.position>=0){
				if (this.position<holder.position){
					return this;
				} else{
					if (holder.position>=0){
						return holder;
					}
				}
			} else{
				if (holder.position>0){
					return holder;
				} else{
					return this;
				}
			}
			return this;
		}

		@Override
		public String toString(){
			ToStringBuilder builder=new ToStringBuilder();
			builder.add("quote", quote);
			builder.add("position", position);
			return builder.toString();
		}
	}
	

	private QuoteHolder searchQuote(StringHolder stringHolder, String quote){
		int quotePos=stringHolder.searchStartQuote(quote);
		QuoteHolder holder=new QuoteHolder(quote, quotePos);
		return holder;
	}
	
	protected TextMarker getTextMarker(int position){
		Map.Entry<Integer, TextMarker> entry=textMarkers.floorEntry(position);
		if (entry!=null){
			TextMarker marker=entry.getValue();
			if (marker.match(position)){
				return marker;
			}
		}
		entry=textMarkers.ceilingEntry(position);
		if (entry!=null){
			TextMarker marker=entry.getValue();
			if (marker.match(position)){
				return marker;
			}
		}
		return null;
	}
	
	private void addTextMarker(TextMarker marker){
		textMarkers.put(marker.getStart(), marker);
	}
	
	public boolean hasNext(){
		if (!stringHolder.hasNext()){
			return false;
		}
		int pos=stringHolder.getLineEnd();
		if (pos>=0){
			String text=stringHolder.getLine();
			comment=false;
			if (isChangeDelimiter(text, stringHolder)){
				return this.hasNext();
			}
			if (text.trim().length()==0){
				stringHolder.setCurrent(text);
				stringHolder.nextLine();
				return this.hasNext();
			}
			TextMarker textMarker=this.getTextMarker(stringHolder.getPosition());
			if (textMarker!=null){
				if (textMarker.getType().isComment()){
					comment=true;
					stringHolder.setCurrent(textMarker.getValue());
					stringHolder.setPosition(textMarker.getEnd());
					return true;
				}
			}
			if (isStatementDelimiter(text, stringHolder)){
				return true;
			}
			if (isStartStatement(text, stringHolder)){
				return true;
			}
			handleElse(stringHolder);
			return true;
		} else{
			stringHolder.setPosition(stringHolder.getContextLength()+1);
		}
		return false;
	}
	
	protected void setPosition(int pos){
		if (pos>=0){
			stringHolder.setCurrent(stringHolder.substringAt(pos));
			stringHolder.setPosition(pos+1);
		} else{
			pos=stringHolder.getContextLength();
			String value=stringHolder.substringAt(pos);
			stringHolder.setPosition(pos);
			stringHolder.setCurrent(value);
		}
	}

	protected void handleElse(StringHolder stringHolder){
		int delPos=searchDelimiter();
		if (delPos>=0){
			handleSimpleStatement(delPos, stringHolder);
		} else{
			setPosition(delPos);
		}
	}
	
	protected int searchDelimiter(){
		int start=stringHolder.getPosition();
		while(true){
			int pos= stringHolder.indexOf(this.getCurrentDelimiter(), start);
			if (pos>=0){
				TextMarker textMarker=this.getTextMarker(pos);
				if (textMarker==null){
					return pos;
				}
				start= textMarker.getEnd();
			} else{
				return pos;
			}
		}
	}

	/**
	 * @return the comment
	 */
	public boolean isComment() {
		return comment;
	}
	
	protected void handleSimpleStatement(int delPos, StringHolder stringHolder){
		String value=stringHolder.substringAt(delPos);
		stringHolder.setPosition(delPos+this.getCurrentDelimiter().length()+1);
		stringHolder.setCurrent(value);
	}

	protected boolean isLineComment(String text){
		if (text.trim().startsWith("--")){
			return true;
		}
		return false;
	}

	private boolean isStartMultiLineComment(String text, StringHolder stringHolder){
		int pos=stringHolder.searchFirstElement("/*");
		if (pos>=0){
			StringHolder clone=stringHolder.clone();
			clone.setPosition(pos);
			int commEnd=clone.indexOf("*/");
			if (commEnd>=0){
				return true;
			}
			return true;
		}
		return false;
	}
	
	protected boolean isChangeDelimiter(String text, StringHolder stringHolder){
		return false;
	}

	protected boolean isStartStatement(String text, StringHolder stringHolder){
		return false;
	}

	protected boolean isStatementDelimiter(String text, StringHolder stringHolder){
		return false;
	}

	/**
	 * @return the currentDelimiter
	 */
	protected String getCurrentDelimiter() {
		if (currentDelimiter==null){
			return this.getDelimiter();
		}
		return currentDelimiter;
	}

	/**
	 * @param currentDelimiter the currentDelimiter to set
	 */
	protected void setCurrentDelimiter(String currentDelimiter) {
		this.currentDelimiter = currentDelimiter;
	}

	/**
	 * @return the delimiter
	 */
	protected String getDelimiter() {
		return delimiter;
	}

	/**
	 * @param delimiter the delimiter to set
	 */
	protected void setDelimiter(String delimiter) {
		this.delimiter = delimiter;
	}
	
	public String next(){
		return stringHolder.getCurrent();
	}

	@Data
	public static class TextMarker{
		private int start;
		private int end;
		private Type type;
		private String value;
		
		public boolean match(int position){
			return start<=position&&position<end;
		}
	}

	static enum Type{
		QUOTED_STRING
		, MULTI_LINE_COMMENT(){
			@Override
			public boolean isComment(){
				  return true;
			}
		}
		, SINGLE_LINE_COMMENT(){
			@Override
			public boolean isComment(){
				  return true;
			}
		}
		,;
		public boolean isBlank(){
			  return false;
		}

		public boolean isComment(){
			  return false;
		}

	}
	
}
