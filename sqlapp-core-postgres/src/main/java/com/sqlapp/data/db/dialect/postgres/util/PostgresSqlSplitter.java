/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.postgres.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;
import com.sqlapp.data.db.dialect.util.StringHolder;

public class PostgresSqlSplitter extends SqlSplitter{

	public PostgresSqlSplitter(Dialect dialect) {
		super(dialect);
	}
	
	private static final Pattern FUNCTION_PATTERN=Pattern.compile("\\s*CREATE\\s+(OR\\s+REPLACE\\s+)?FUNCTION.*?\\s+AS\\s+(.*)", Pattern.MULTILINE+Pattern.CASE_INSENSITIVE+Pattern.DOTALL);

	@Override
	protected SqlTokenizer createSqlTokenizer(String input){
		return new SqlTokenizer(input){
			@Override
			protected boolean isStartStatement(String text, StringHolder stringHolder){
				Matcher matcher=stringHolder.substringMatcher(FUNCTION_PATTERN);
				if (matcher.matches()){
					int index=stringHolder.indexOf("FUNCTION");
					int asPos=stringHolder.searchWord("AS", index+8);
					if (asPos>=0){
						int i=asPos+2;
						char c=' ';
						while(i<stringHolder.getContextLength()){
							c=stringHolder.charAt(i++);
							if (!stringHolder.isSpace(c)){
								break;
							}
						}
						if (c=='\''){
							TextMarker textMarker=this.getTextMarker(i);
							int pos=stringHolder.indexOf(this.getCurrentDelimiter(), textMarker.getEnd());
							if (pos>=0){
								pos=stringHolder.indexOf(this.getCurrentDelimiter(), pos);
								setPosition(pos);
								return true;
							} else{
								stringHolder.throwInvalidTextException("[Delimiter["+this.getCurrentDelimiter()+"] of Function not found.["+stringHolder.substringAt()+"]");
							}
							return true;
						} else{
							StringBuilder builder=new StringBuilder();
							builder.append(c);
							while(i<stringHolder.getContextLength()){
								c=stringHolder.charAt(i++);
								if (stringHolder.isSpace(c)){
									break;
								} else{
									builder.append(c);
								}
							}
							int pos=stringHolder.indexOf(builder.toString(), i);
							if (pos>=0){
								pos=stringHolder.indexOf(this.getCurrentDelimiter(), pos+builder.length());
								setPosition(pos);
								return true;
							} else{
								stringHolder.throwInvalidTextException("[Delimiter["+builder.toString()+"] of Function not found.["+stringHolder.substringAt()+"]");
							}
						}
					} else{
						stringHolder.throwInvalidTextException("[AS] of Function not found.["+stringHolder.substringAt()+"]");
					}
				}
				return false;
			}
		};
	}
}
