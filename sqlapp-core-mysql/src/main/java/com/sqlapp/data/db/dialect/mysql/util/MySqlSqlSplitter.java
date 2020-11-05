/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.mysql.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;
import com.sqlapp.data.db.dialect.util.StringHolder;

public class MySqlSqlSplitter extends SqlSplitter{

	public MySqlSqlSplitter(Dialect dialect) {
		super(dialect);
	}
	
	private static final Pattern CHANGE_DELIMITER=Pattern.compile("\\s*delimiter\\s+(?<delimiter>[^\\s]+)\\s*", Pattern.CASE_INSENSITIVE);

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
				}
				return false;
			}
			@Override
			protected boolean isLineComment(String value){
				if (value.startsWith("#")){
					return true;
				}
				if (value.trim().startsWith("--")){
					return true;
				}
				return false;
			}
		};
	}
	
	@Override
	protected TextType getTextType(boolean isComment, String sql){
		if (isComment){
			Matcher matcher=DIRECTIVE_PATTERN.matcher(sql);
			if (matcher.matches()){
				return TextType.COMMENT_DIRECTIVE;
			}
			return TextType.COMMENT;
		}
		return TextType.SQL;
	}

	private static final Pattern DIRECTIVE_PATTERN=Pattern.compile("/\\*![0-9]{5}\\s+.*?\\*/", Pattern.MULTILINE+Pattern.DOTALL);
	
}
