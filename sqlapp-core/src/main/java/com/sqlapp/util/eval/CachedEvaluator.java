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

package com.sqlapp.util.eval;

import java.util.Map;

import com.sqlapp.data.parameter.ParametersContext;

public interface CachedEvaluator {
	/**
	 * execute eval
	 * 
	 * @param expression expression
	 * @param context    context
	 * @return result
	 */
	<T> T eval(String expression, ParametersContext context);

	/**
	 * execute eval
	 * 
	 * @param expression expression
	 * @param context    context
	 * @param clazz      result class
	 * @return result
	 */
	<T> T eval(String expression, ParametersContext context, Class<T> clazz);

	/**
	 * execute eval
	 * 
	 * @param expression expression
	 * @param context    context
	 * @return result
	 */
	<T> T eval(String expression, Object context);

	/**
	 * execute eval
	 * 
	 * @param expression expression
	 * @param context    context
	 * @return result
	 */
	boolean evalBoolean(String expression, Object context);

	/**
	 * execute eval
	 * 
	 * @param expression expression
	 * @param context    context
	 * @return result
	 */
	<T> T eval(String expression, Map<?, ?> context);

	/**
	 * execute eval
	 * 
	 * @param expression expression
	 * @param context    context
	 * @return result
	 */
	boolean evalBoolean(String expression, Map<?, ?> context);

	/**
	 * execute eval
	 * 
	 * @param expression expression
	 * @param context    context
	 * @return result
	 */
	boolean evalBoolean(String expression, ParametersContext context);
}
