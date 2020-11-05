/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.jdbc.sql.node;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.jdbc.sql.ResultSetConcurrency;
import com.sqlapp.jdbc.sql.ResultSetHoldability;
import com.sqlapp.jdbc.sql.ResultSetType;

/**
 * SQLコメントのifノードのファクトリ
 * 
 * @author satoh
 *
 */
public class QueryNodeFactory extends AbstractCommentNodeFactory<QueryNode> {

	protected static final Pattern[] MATCH_PATTERNS = new Pattern[] { Pattern
			.compile("(?<value>/\\*query[\\s]*\\((?<expression>[^)]+?)\\)[\\s]*\\*/)") };

	@Override
	public QueryNode newInstance() {
		return new QueryNode();
	}

	@Override
	protected Pattern[] getMatchPatterns() {
		return MATCH_PATTERNS;
	}

	@Override
	protected void setNodeValue(QueryNode node, Matcher matcher) {
		super.setNodeValue(node, matcher);
		String[] params = node.getExpression().split("\\s*,\\s*");
		for (String param : params) {
			String[] pair = param.split("\\s*=\\s*");
			if (pair.length != 2) {
				continue;
			}
			if ("fetchSize".equalsIgnoreCase(pair[0])) {
				node.setFetchSize(Integer.valueOf(pair[1]));
			} else if ("resultSetType".equalsIgnoreCase(pair[0])) {
				node.setResultSetType(ResultSetType.parse(pair[1]));
			} else if ("resultSetConcurrency".equalsIgnoreCase(pair[0])) {
				node.setResultSetConcurrency(ResultSetConcurrency
						.parse(pair[1]));
			} else if ("resultSetHoldability".equalsIgnoreCase(pair[0])) {
				node.setResultSetHoldability(ResultSetHoldability
						.parse(pair[1]));
			}
		}
	}
}
