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
import java.util.AbstractMap;
import java.util.Set;

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

	private final Set<String> inputNames;

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
		final ParserContext compilationContext = parserContext == null ? new ParserContext()
				: parserContext.createSubcontext();
		compliedExpression = MVEL.compileExpression(getExpression(), compilationContext);
		inputNames = compilationContext.getInputs() == null ? Set.of()
				: Set.copyOf(compilationContext.getInputs().keySet());
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
		if (!(map instanceof ParametersContext parameters)) {
			return new MapVariableResolverFactory(map);
		}
		// ParametersContext intentionally resolves unspecified SQL parameters to null.
		// Expose real variables and the compiler's input names (including missing
		// SQL parameters), but not synthetic entries for classes or methods.
		final Map<String, Object> variables = new AbstractMap<>() {
			@Override
			public Set<Map.Entry<String, Object>> entrySet() {
				return parameters.entrySet();
			}

			@Override
			public boolean containsKey(final Object key) {
				return parameters.containsKeyInternal(key) || inputNames.contains(key);
			}

			@Override
			public Object get(final Object key) {
				return parameters.get(key);
			}

			@Override
			public Object put(final String key, final Object value) {
				return parameters.put(key, value);
			}
		};
		return new MapVariableResolverFactory(variables);
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
		return MVEL.executeExpression(getCompliedExpression(), context, createVariableResolverFactory(context), clazz);
	}

}
