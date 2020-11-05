/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 *
 * sqlapp-core-sqlite is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlite is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlite.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sqlite.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;
import com.sqlapp.data.db.dialect.util.StringHolder;

public class SqliteSqlSplitter extends SqlSplitter{

	public SqliteSqlSplitter(Dialect dialect) {
		super(dialect);
	}
	
	private static final Pattern FUNCTION_PATTERN=Pattern.compile("\\s*CREATE\\s+(TEMP|TEMPORARY)?\\s*TRIGGER\\s*.*?\\s+BEGIN\\s*\\n(.*)", Pattern.MULTILINE+Pattern.CASE_INSENSITIVE+Pattern.DOTALL);

	private static final Pattern END_PATTERN=Pattern.compile("\\s*END\\s*;?", Pattern.MULTILINE+Pattern.CASE_INSENSITIVE+Pattern.DOTALL);

	@Override
	protected SqlTokenizer createSqlTokenizer(String input){
		return new SqlTokenizer(input){
			@Override
			protected boolean isStartStatement(String text, StringHolder stringHolder){
				Matcher matcher=stringHolder.substringMatcher(FUNCTION_PATTERN);
				if (matcher.matches()){
					int index=stringHolder.indexOf("BEGIN");
					if (index>=0){
						int ePos=stringHolder.searchLineOf(END_PATTERN, index+6);
						if (ePos>=0){
							int pos=stringHolder.indexOf(this.getCurrentDelimiter(), ePos+3);
							setPosition(pos);
							return true;
						} else{
							stringHolder.throwInvalidTextException("[END] of TRIGGER not found.["+stringHolder.substringAt()+"]");
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
