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

package com.sqlapp.jdbc.sql.node;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.sql.ColumnSelectionStrategy;
import com.sqlapp.util.CommonUtils;

/**
 * SQLコメントのROW_EQUALS(PRIMARY_KEY)ファクトリ
 * 
 * @author satoh
 *
 */
public class RowsEqualsBindVariableNodeFactory extends AbstractCommentNodeFactory<RowsEqualsBindVariableNode> {

	static {
		MATCH_PATTERNS = new Pattern[] {
				Pattern.compile("(?<value>\\s*/\\*ROWS_EQUALS\\((?<selector>([^)]+))\\)\\*/)") };
	}

	protected static Pattern[] MATCH_PATTERNS;

	@Override
	protected void setNodeValue(RowsEqualsBindVariableNode node, Matcher matcher) {
		node.setMatchText(matcher.group("value"));
		node.setExpression(matcher.group("selector"));
		String[] args = node.getExpression().trim().split("\\s*;\\s*");
		final Map<String, String> keyMap = CommonUtils.parseKeyValue(args);
		node.setTarget(keyMap.get("target"));
		node.setKeyType(ColumnSelectionStrategy.parse(keyMap.get("keyType")));
		node.setPrefix(keyMap.get("prefix"));
		final String columnsArg = keyMap.get("columns");
		if (!CommonUtils.isEmpty(columnsArg)) {
			final Set<String> columns = CommonUtils.linkedSet();
			String[] colArgs = columnsArg.split("\\s*,\\s*");
			for (int i = 0; i < colArgs.length; i++) {
				String colArg = CommonUtils.trim(colArgs[i]);
				if (!CommonUtils.isEmpty(colArg)) {
					columns.add(colArg);
				}
			}
			node.setColumns(columns);
		}
	}

	@Override
	public RowsEqualsBindVariableNode newInstance() {
		return new RowsEqualsBindVariableNode();
	}

	@Override
	protected Pattern[] getMatchPatterns() {
		return MATCH_PATTERNS;
	}
}
