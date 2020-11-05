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

import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.util.CommonUtils;

/**
 * SQLコメントのinclude要素
 * 
 */
public class IncludeNode extends CommentNode {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2951149147210439845L;

	@Override
	public boolean eval(Object context, SqlParameterCollection sqlParameters) {
		Node node = this.getSqlRegistry()
				.get(getExpression(), sqlParameters.getDialect()).clone();
		node.setParent(this);
		return node.eval(context, sqlParameters);
	}

	@Override
	public void setExpression(String expression) {
		this.expression = CommonUtils.trim(CommonUtils.substring(
				CommonUtils.trim(expression), 1, expression.length() - 1));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public IncludeNode clone() {
		return (IncludeNode) super.clone();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(super.toString());
		Node node = this.getSqlRegistry().get(getExpression(), null);
		builder.append(node.toString());
		return builder.toString();
	}
}
