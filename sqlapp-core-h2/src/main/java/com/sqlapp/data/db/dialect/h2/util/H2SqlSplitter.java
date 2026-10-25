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

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;

public class H2SqlSplitter extends SqlSplitter {

	public H2SqlSplitter(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlTokenizer createSqlTokenizer(String input) {
		return new SqlTokenizer(input) {
			@Override
			protected int getQuotedTextEnd(String sql, int start) {
				if (sql.startsWith("$$", start)
						&& (start == 0 || !Character.isJavaIdentifierPart(sql.charAt(start - 1)))) {
					int end = sql.indexOf("$$", start + 2);
					if (end < 0)
						stringHolder.throwInvalidTextException("Closing dollar quote [$$] not found");
					return end + 2;
				}
				return super.getQuotedTextEnd(sql, start);
			}
		};
	}
}
