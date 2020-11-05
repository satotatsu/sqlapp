/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.firebird.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;
import com.sqlapp.data.db.dialect.util.StringHolder;

public class FirebirdSqlSplitter extends SqlSplitter {

	public FirebirdSqlSplitter(Dialect dialect) {
		super(dialect);
	}

	private static final Pattern CHANGE_DELIMITER = Pattern
			.compile("set\\s+term\\s+(?<to>[^\\s]+)\\s+(?<from>[^\\s]+)\\s*", Pattern.CASE_INSENSITIVE);

	@Override
	protected SqlTokenizer createSqlTokenizer(String input) {
		return new SqlTokenizer(input) {
			@Override
			protected boolean isChangeDelimiter(String text, StringHolder stringHolder) {
				Matcher matcher = CHANGE_DELIMITER.matcher(text);
				if (matcher.matches()) {
					String to = matcher.group("to");
					// String from=matcher.group("from");
					this.setCurrentDelimiter(to);
					stringHolder.addPosition(matcher.group(0).length());
					return true;
				}
				return false;
			}
		};
	}
}
