/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.h2.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;
import com.sqlapp.data.db.dialect.util.StringHolder;

public class H2SqlSplitter extends SqlSplitter{

	public H2SqlSplitter(Dialect dialect) {
		super(dialect);
	}
	
	private static final Pattern FUNCTION_PATTERN=Pattern.compile("(?<all>\\s*(CREATE|ALTER)\\s+(?<type>ALIAS)[^;]*?\\s+AS\\s+\\$\\$(.*?)\\$\\$).*", Pattern.MULTILINE+Pattern.CASE_INSENSITIVE+Pattern.DOTALL);
	
	@Override
	protected SqlTokenizer createSqlTokenizer(String input){
		return new SqlTokenizer(input){
			@Override
			protected boolean isStartStatement(String text, StringHolder stringHolder){
				Matcher matcher=stringHolder.substringMatcher(FUNCTION_PATTERN);
				if (matcher.matches()){
					String all=matcher.group("all");
					setPosition(stringHolder.getPosition()+all.length());
					return true;
				}
				return false;
			}
		};
	}
}
