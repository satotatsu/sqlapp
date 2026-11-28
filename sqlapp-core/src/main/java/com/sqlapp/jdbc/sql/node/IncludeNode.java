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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.SqlRegistry;
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
		if (getSqlRegistry() == null) {
			throw new IllegalStateException("SQL include requires a SqlRegistry: " + getExpression());
		}
		Node registered = getSqlRegistry().get(getExpression(), sqlParameters.getDialect());
		if (registered == null) {
			throw new IllegalArgumentException("Included SQL not found: " + getExpression());
		}
		checkCircularInclude();
		Node node = registered.clone();
		node.setParent(this);
		return node.eval(context, sqlParameters);
	}

	private static final ThreadLocal<Set<String>> EXPANDING = ThreadLocal.withInitial(HashSet::new);

	private void checkCircularInclude() {
		for (Node parent = this.getParent(); parent != null; parent = parent.getParent()) {
			if (parent instanceof IncludeNode && Objects.equals(((IncludeNode) parent).getExpression(), getExpression())) {
				throw new IllegalStateException("Circular SQL include: " + getExpression());
			}
		}
	}

	@Override
	public void setExpression(String expression) {
		String value = CommonUtils.trim(expression);
		if (value == null || value.length() < 2
				|| !((value.startsWith("'") && value.endsWith("'"))
						|| (value.startsWith("\"") && value.endsWith("\"")))) {
			throw new IllegalArgumentException("SQL include requires a quoted SQL ID");
		}
		this.expression = value.substring(1, value.length() - 1);
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
		final SqlRegistry registry = this.getSqlRegistry();
		final Node node = registry != null ? registry.get(getExpression(), null) : null;
		final Set<String> expanding = EXPANDING.get();
		// Stop at a circular include instead of recursing forever.
		if (node != null && expanding.add(getExpression())) {
			try {
				builder.append(node.toString());
			} finally {
				expanding.remove(getExpression());
			}
		}
		return builder.toString();
	}
}
