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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;
/**
 * Sql Splitter
 * @author tatsuo satoh
 *
 */
public class SqlSplitter {
	
	private Dialect dialect;
	
	protected List<SplitResult> statements=null;
	
	public SqlSplitter(){
		this.dialect=null;
	}

	public SqlSplitter(Dialect dialect){
		this.dialect=dialect;
	}

	public List<SplitResult> parse(String input) {
		statements=CommonUtils.list();
		parse(createSqlTokenizer(input));
		return statements;
	}

	/**
	 * @return the statements
	 */
	protected List<SplitResult> getStatements() {
		return statements;
	}

	protected SqlTokenizer createSqlTokenizer(String input){
		return new SqlTokenizer(input);
	}
	
	protected void parse(SqlTokenizer sqlTokenizer) {
		while(sqlTokenizer.hasNext()) {
			String line=getResult(sqlTokenizer);
			if (CommonUtils.isEmpty(line)){
				continue;
			}
			SplitResult splitResult=new SplitResult(getTextType(sqlTokenizer.isComment(), line), line);
			getStatements().add(splitResult);
			addStatementAfter();
		}
	}
	
	protected void addStatementAfter(){
		
	}
	
	protected TextType getTextType(boolean isComment, String sql){
		if (isComment){
			return TextType.COMMENT;
		}
		return TextType.SQL;
	}

	private static Pattern TERMINATE_PATTERN=Pattern.compile("(.*);$", Pattern.MULTILINE+Pattern.DOTALL);
	
	protected String getResult(SqlTokenizer sqlTokenizer){
		String line=sqlTokenizer.next();
		if (CommonUtils.isEmpty(line)){
			return line;
		}
		String val=CommonUtils.rtrim(line);
		Matcher matcher=TERMINATE_PATTERN.matcher(val);
		if (matcher.matches()){
			return matcher.group(1);
		}
		return val;
	}
	
	
	
	public static class SplitResult{
		public SplitResult(TextType textType, String text){
			this.textType=textType;
			this.text=text;
		}
		private TextType textType;
		private String text;
		/**
		 * @return the textType
		 */
		public TextType getTextType() {
			return textType;
		}
		/**
		 * @param textType the textType to set
		 */
		public void setTextType(TextType textType) {
			this.textType = textType;
		}
		/**
		 * @return the text
		 */
		public String getText() {
			return text;
		}
		/**
		 * @param text the text to set
		 */
		public void setText(String text) {
			this.text = text;
		}
		
		@Override
		public String toString(){
			ToStringBuilder builder=new ToStringBuilder("");
			builder.add("textType", textType);
			builder.add("text", text);
			return builder.toString();
		}
	}
	
	
	/**
	 * @return the dialect
	 */
	protected Dialect getDialect() {
		return dialect;
	}


	public static enum TextType{
		COMMENT(){
			public boolean isComment(){
				return true;
			}
		},
		COMMENT_DIRECTIVE(){
			public boolean isComment(){
				return true;
			}
		},
		SQL,
		;
		
		public boolean isComment(){
			return false;
		}
	}
	
}
