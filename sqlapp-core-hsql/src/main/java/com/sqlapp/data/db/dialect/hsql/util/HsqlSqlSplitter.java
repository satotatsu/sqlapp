/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.hsql.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;
import com.sqlapp.data.db.dialect.util.StringHolder;

public class HsqlSqlSplitter extends SqlSplitter{

	public HsqlSqlSplitter(Dialect dialect) {
		super(dialect);
	}
	
	private static final Pattern FUNCTION_PATTERN=Pattern.compile("\\s*(CREATE|ALTER)\\s+(AGGREGATE\\s+)?(?<type>FUNCTION|PROCEDURE|SPECIFIC\\s+ROUTINE)(?<part>.*?)\\s+BEGIN\\s+(.*)", Pattern.MULTILINE+Pattern.CASE_INSENSITIVE+Pattern.DOTALL);

	private static final Pattern END_PATTERN=Pattern.compile("\\s*END\\s*;?\\s*", Pattern.CASE_INSENSITIVE);
	
	@Override
	protected SqlTokenizer createSqlTokenizer(String input){
		return new SqlTokenizer(input){
			@Override
			protected boolean isStartStatement(String text, StringHolder stringHolder){
				Matcher matcher=stringHolder.substringMatcher(FUNCTION_PATTERN);
				if (matcher.matches()){
					String type=matcher.group("type");
					String part=matcher.group("part");
					if (part.contains(";")){
						return false;
					}
					int index=stringHolder.indexOf(type);
					int beginPos=stringHolder.searchWord("BEGIN", index+type.length());
					if (beginPos>=0){
						int endPos=stringHolder.searchLineOf(END_PATTERN, beginPos+5);
						if (endPos>=0){
							int delPos=stringHolder.indexOf(this.getCurrentDelimiter(), endPos+3);
							setPosition(delPos);
							return true;
						} else{
							stringHolder.throwInvalidTextException("[Delimiter["+this.getCurrentDelimiter()+"] of "+type+" not found.["+stringHolder.substringAt()+"]");
						}
						return true;
					} else{
						stringHolder.throwInvalidTextException("[END] of "+type+" not found.["+stringHolder.substringAt()+"]");
					}
				}
				return false;
			}
		};
	}
}
