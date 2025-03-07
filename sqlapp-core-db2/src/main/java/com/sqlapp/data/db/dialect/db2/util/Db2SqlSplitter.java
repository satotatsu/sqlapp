/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;
import com.sqlapp.data.db.dialect.util.StringHolder;

public class Db2SqlSplitter extends SqlSplitter{

	public Db2SqlSplitter(Dialect dialect) {
		super(dialect);
	}
	
	private static final Pattern CHANGE_DELIMITER=Pattern.compile("--#SET\\s+TERMINATOR\\s+(?<delimiter>[^\\s]+)\\s*", Pattern.CASE_INSENSITIVE);

	private static final Pattern RESET_DELIMITER=Pattern.compile("--#SET\\s+TERMINATOR\\s*", Pattern.CASE_INSENSITIVE);

	@Override
	protected SqlTokenizer createSqlTokenizer(String input){
		return new SqlTokenizer(input){
			@Override
			protected boolean isChangeDelimiter(String text, StringHolder stringHolder){
				Matcher matcher=CHANGE_DELIMITER.matcher(text);
				if (matcher.matches()){
					String delimiter=matcher.group("delimiter");
					this.setCurrentDelimiter(delimiter);
					stringHolder.addPosition(matcher.group(0).length());
					return true;
				} else{
					matcher=RESET_DELIMITER.matcher(text);
					if (matcher.matches()){
						this.setCurrentDelimiter(null);
						stringHolder.addPosition(matcher.group(0).length());
						return true;
					}
				}
				return false;
			}
		};
	}
}
