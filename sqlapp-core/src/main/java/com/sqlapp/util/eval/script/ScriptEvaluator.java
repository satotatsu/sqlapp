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

package com.sqlapp.util.eval.script;

import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.exceptions.ExpressionExecutionException;
import com.sqlapp.util.eval.AbstractEvaluator;

public class ScriptEvaluator extends AbstractEvaluator {

	private final ScriptEngine engine;

	public ScriptEvaluator(String expression, ScriptEngine engine) {
		super(expression);
		this.engine = engine;
	}

	public ScriptEngine getEngine() {
		return engine;
	}

	/**
	 * EVALの実行
	 * 
	 * @param bindings 引数
	 * @return 結果
	 * @throws ScriptException
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T> T eval(ParametersContext bindings) {
		try {
			return (T) evalScript(bindings);
		} catch (ScriptException e) {
			throw new ExpressionExecutionException(e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T eval(javax.script.Bindings bindings) {
		try {
			return (T) evalScript(bindings);
		} catch (ScriptException e) {
			throw new ExpressionExecutionException(e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T eval(Object val) {
		if (val instanceof javax.script.Bindings) {
			return eval((javax.script.Bindings) val);
		}
		if (val instanceof Map<?, ?>) {
			BindingsMap bMap = new BindingsMap();
			bMap.putAll((Map<String, Object>) val);
			return eval(bMap);
		}
		throw new UnsupportedOperationException("ScriptEvaluator.eval(val)");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.util.eval.AbstractEvalExecutor#doEval(com.sqlapp.data.parameter.
	 * ParametersContext, java.lang.Class)
	 */
	@Override
	public <T> T eval(ParametersContext bindings, Class<T> clazz) {
		T val = Converters.getDefault().convertObject(eval(bindings), clazz);
		return val;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.eval.AbstractEvalExecutor#doEvalBoolean(com.sqlapp.data.
	 * parameter.ParametersContext)
	 */
	@Override
	public boolean evalBoolean(ParametersContext bindings) {
		return (Boolean) eval(bindings);
	}

	@SuppressWarnings("unchecked")
	private <T> T evalScript(javax.script.Bindings context) throws ScriptException {
		return (T) engine.eval(getExpression(), context);
	}

	static class BindingsMap extends HashMap<String, Object> implements javax.script.Bindings {
		/** serialVersionUID */
		private static final long serialVersionUID = -6082931365315111168L;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T eval(Map<?, ?> val) {
		BindingsMap bMap = new BindingsMap();
		bMap.putAll((Map<String, Object>) val);
		return eval(bMap);
	}

	@Override
	public boolean evalBoolean(Map<?, ?> context) {
		return Converters.getDefault().convertObject(eval(context), boolean.class);
	}

	@Override
	public boolean evalBoolean(Object context) {
		return Converters.getDefault().convertObject(eval(context), boolean.class);
	}
}
