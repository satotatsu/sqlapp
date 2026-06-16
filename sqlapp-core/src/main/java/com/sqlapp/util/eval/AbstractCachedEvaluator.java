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
import java.util.concurrent.ConcurrentHashMap;

import com.sqlapp.data.parameter.ParametersContext;

public abstract class AbstractCachedEvaluator implements CachedEvaluator {

	private Map<String, Evaluator> evaluatorMap = new ConcurrentHashMap<String, Evaluator>();

	protected abstract String getCacheKey(String expression, Object context);

	protected abstract String getCacheKey(String expression, Map<?, ?> context);

	protected Map<String, Evaluator> getEvaluatorMap() {
		return evaluatorMap;
	}

	protected abstract Evaluator createEvalExecutor(String expression);

	@Override
	public <T> T eval(String expression, ParametersContext context) {
		final String key = getCacheKey(expression, context);
		Evaluator evaluator = getEvaluatorMap().get(key);
		if (evaluator == null) {
			return putIfAbsent(expression, key).eval(context);
		}
		return evaluator.eval(context);
	}

	protected Evaluator putIfAbsent(String expression, String cacheKey) {
		Evaluator evaluator = createEvalExecutor(expression);
		Evaluator oldEvaluator = getEvaluatorMap().putIfAbsent(cacheKey, evaluator);
		return oldEvaluator != null ? oldEvaluator : evaluator;
	}

	@Override
	public <T> T eval(String expression, ParametersContext context, Class<T> clazz) {
		final String key = getCacheKey(expression, context);
		Evaluator evaluator = getEvaluatorMap().get(key);
		if (evaluator == null) {
			return putIfAbsent(expression, key).eval(context, clazz);
		}
		return evaluator.eval(context, clazz);
	}

	@Override
	public <T> T eval(String expression, Object context) {
		final String key = getCacheKey(expression, context);
		Evaluator evaluator = getEvaluatorMap().get(key);
		if (evaluator == null) {
			return putIfAbsent(expression, key).eval(context);
		}
		return evaluator.eval(context);
	}

	@Override
	public boolean evalBoolean(String expression, Object context) {
		final String key = getCacheKey(expression, context);
		Evaluator evaluator = getEvaluatorMap().get(key);
		if (evaluator == null) {
			return putIfAbsent(expression, key).evalBoolean(context);
		}
		return evaluator.evalBoolean(context);
	}

	@Override
	public <T> T eval(String expression, Map<?, ?> context) {
		final String key = getCacheKey(expression, context);
		Evaluator evaluator = getEvaluatorMap().get(key);
		if (evaluator == null) {
			return putIfAbsent(expression, key).eval(context);
		}
		return evaluator.eval(context);
	}

	@Override
	public boolean evalBoolean(String expression, Map<?, ?> context) {
		final String key = getCacheKey(expression, context);
		Evaluator evaluator = getEvaluatorMap().get(key);
		if (evaluator == null) {
			return putIfAbsent(expression, expression).evalBoolean(context);
		}
		return evaluator.evalBoolean(context);
	}

	@Override
	public boolean evalBoolean(String expression, ParametersContext context) {
		final String key = getCacheKey(expression, context);
		Evaluator evaluator = getEvaluatorMap().get(key);
		if (evaluator == null) {
			return putIfAbsent(expression, key).evalBoolean(context);
		}
		return evaluator.evalBoolean(context);
	}
}
