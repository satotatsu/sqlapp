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
/**
 * カラムと比較条件のバインド変数のノードのファクトリ
 * @author satoh
 *
 */
public abstract class AbstractColumnNodeFactory<T extends AbstractColumnNode> extends AbstractCommentNodeFactory<T>{

	protected static final String OPEREATOR_PATTERN="((?<column>[^%{}\\s\\n=><^!]+)[\\s\n]*(?<operator>in|>=|<=|<>|>|<|=|like|REGEXP|^=|!=)){0,1}";

	@Override
	protected void setNodeValue(T node, Matcher matcher) {
		super.setNodeValue(node, matcher);
		node.setOperator(matcher.group("operator"));
		node.setColumn(matcher.group("column"));
		node.setMatchText(matcher.group(0));
	}
}
