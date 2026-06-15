/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.db.command.exceptions;

import com.sqlapp.exceptions.SqlappException;

/**
 * InvalidExpression error
 * 
 * @author SATOH
 *
 */
public class InvalidExpressionResultTypeException extends SqlappException {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -5297973457388901736L;

	private final String expressionPath;
	private final String expression;
	private final Object object;

	/**
	 * @return the expressionPath
	 */
	public String getExpressionPath() {
		return expressionPath;
	}

	/**
	 * @return the expression
	 */
	public String getExpression() {
		return expression;
	}

	/**
	 * @return the object
	 */
	public Object getObject() {
		return object;
	}

	public InvalidExpressionResultTypeException(String expressionPath, String expression, Object object, Class<?> clazz,
			String message) {
		super(createMessage(expressionPath, expression, object, clazz, message));
		this.object = object;
		this.expression = expression;
		this.expressionPath = expressionPath;
	}

	private static String createMessage(String expressionPath, String expression, Object object, Class<?> clazz,
			String message) {
		return message + " [expressionPath=" + expressionPath + ", expression=" + expression + ", resultClass="
				+ getClass(object) + ", class=" + clazz + ", object=" + object + "]";
	}

	private static Class<?> getClass(Object obj) {
		if (obj == null) {
			return null;
		}
		return obj.getClass();
	}
}
