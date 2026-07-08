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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.sql.ColumnSelectionStrategy;
import com.sqlapp.util.SeparatedStringBuilder;

/**
 * SQLコメントのROW_EQUALS(PRIMARY_KEY)ファクトリ
 * 
 * @author satoh
 *
 */
public class RowsEqualsBindVariableNodeFactory extends AbstractCommentNodeFactory<RowsEqualsBindVariableNode> {

	static {
		SeparatedStringBuilder builder = new SeparatedStringBuilder("|");
		for (ColumnSelectionStrategy enm : ColumnSelectionStrategy.values()) {
			builder.add(enm);
		}
		MATCH_PATTERNS = new Pattern[] { Pattern.compile(
				"(?<value>\\s*/\\*ROWS_EQUALS\\((?<columnSelectionStrategy>" + builder.toString() + ")\\)\\*/)") };
	}

	protected static Pattern[] MATCH_PATTERNS;

	@Override
	protected void setNodeValue(RowsEqualsBindVariableNode node, Matcher matcher) {
		node.setMatchText(matcher.group("value"));
		node.setExpression(matcher.group("columnSelectionStrategy"));
		node.setColumnSelectionStrategy(ColumnSelectionStrategy.valueOf(node.getExpression()));
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
