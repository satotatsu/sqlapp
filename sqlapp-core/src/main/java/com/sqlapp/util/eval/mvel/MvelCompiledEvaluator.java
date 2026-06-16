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

package com.sqlapp.util.eval.mvel;

import java.io.Serializable;
import java.util.Map;

import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.util.eval.AbstractEvaluator;

/**
 * コンパイル済のMVEL実行クラス
 * 
 * @author satoh
 *
 */
public class MvelCompiledEvaluator extends AbstractEvaluator {

	private final ParserContext parserContext;

	private final Serializable compliedExpression;

	/**
	 * @return the compliedExpression
	 */
	protected Serializable getCompliedExpression() {
		return compliedExpression;
	}

	/**
	 * @return the parserContext
	 */
	public ParserContext getParserContext() {
		return parserContext;
	}

	public MvelCompiledEvaluator(String expression, ParserContext parserContext) {
		super(expression);
		this.parserContext = parserContext;
		if (parserContext == null) {
			compliedExpression = MVEL.compileExpression(getExpression());
		} else {
			compliedExpression = MVEL.compileExpression(getExpression(), parserContext);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.util.eval.mvel.MvelEvalExecutor#doEval(com.sqlapp.data.parameter.
	 * ParametersContext)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T> T eval(ParametersContext context) {
		final VariableResolverFactory userVars = createVariableResolverFactory(context);
		return (T) MVEL.executeExpression(getCompliedExpression(), context, userVars);
	}

	/**
	 * @param expression
	 * @param bindings
	 * @param db
	 */
	@SuppressWarnings("unchecked")
	public <T> T eval(String expression, ParametersContext context, Dialect db) {
		final VariableResolverFactory userVars = createVariableResolverFactory(context);
		context.put("db", db);
		return (T) MVEL.executeExpression(getCompliedExpression(), context, userVars);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public <T> T eval(Object context) {
		if (context instanceof Map) {
			final VariableResolverFactory factory = createVariableResolverFactory((Map) context);
			return (T) MVEL.executeExpression(getCompliedExpression(), context, factory);
		} else {
			return (T) MVEL.executeExpression(getCompliedExpression(), context);
		}
	}

	private VariableResolverFactory createVariableResolverFactory(Map<?, ?> map) {
		final VariableResolverFactory factory = new MapVariableResolverFactory(map);
		// final VariableResolverFactory factory = new
		// CachingMapVariableResolverFactory(map);
		return factory;
	}

	@Override
	public boolean evalBoolean(Object context) {
		if (context instanceof Map) {
			@SuppressWarnings("rawtypes")
			final VariableResolverFactory factory = createVariableResolverFactory((Map) context);
			return MVEL.executeExpression(getCompliedExpression(), context, factory, boolean.class);
		} else {
			return MVEL.executeExpression(getCompliedExpression(), context, boolean.class);
		}
	}

	@Override
	public boolean evalBoolean(ParametersContext context) {
		final VariableResolverFactory factory = createVariableResolverFactory(context);
		return MVEL.executeExpression(getCompliedExpression(), context, factory, boolean.class);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T eval(Map<?, ?> context) {
		final VariableResolverFactory factory = createVariableResolverFactory(context);
		return (T) MVEL.executeExpression(getCompliedExpression(), context, factory);
	}

	@Override
	public boolean evalBoolean(Map<?, ?> context) {
		final VariableResolverFactory factory = createVariableResolverFactory(context);
		return MVEL.executeExpression(getCompliedExpression(), context, factory, boolean.class);
	}

	@Override
	public <T> T eval(ParametersContext context, Class<T> clazz) {
		return MVEL.executeExpression(getCompliedExpression(), context, context, clazz);
	}

}
