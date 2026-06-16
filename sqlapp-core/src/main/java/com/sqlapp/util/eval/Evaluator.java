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

public interface Evaluator {

	/**
	 * 
	 * @return Expression
	 */
	String getExpression();

	/**
	 * execute eval
	 * 
	 * @param context context
	 * @return result
	 */
	<T> T eval(ParametersContext context);

	/**
	 * execute eval
	 * 
	 * @param context context
	 * @param clazz   result class
	 * @return result
	 */
	<T> T eval(ParametersContext context, Class<T> clazz);

	/**
	 * execute eval
	 * 
	 * @param context context
	 * @return result
	 */
	<T> T eval(Object context);

	/**
	 * execute eval
	 * 
	 * @param context context
	 * @return result
	 */
	boolean evalBoolean(Object context);

	/**
	 * execute eval
	 * 
	 * @param context context
	 * @return result
	 */
	<T> T eval(Map<?, ?> context);

	/**
	 * execute eval
	 * 
	 * @param context context
	 * @return result
	 */
	boolean evalBoolean(Map<?, ?> context);

	/**
	 * execute eval
	 * 
	 * @param context context
	 * @return result
	 */
	boolean evalBoolean(ParametersContext context);
}
