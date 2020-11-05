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
package com.sqlapp.util.eval;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class AbstractCachedEvaluator implements CachedEvaluator {

	private Map<String, EvalExecutor> evaluatorMap = new ConcurrentHashMap<String, EvalExecutor>();

	/**
	 * 式に対応したコンパイル済みスクリプトの取得
	 * 
	 * @param expression
	 */
	public EvalExecutor getEvalExecutor(String expression) {
		EvalExecutor evaluator = getEvaluatorMap().get(expression);
		if (evaluator == null) {
			try {
				evaluator = createEvalExecutor(expression);
				EvalExecutor oldValue = putIfAbsent(expression, evaluator);
				return oldValue == null ? evaluator : oldValue;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return evaluator;
	}

	protected EvalExecutor putIfAbsent(String expression, EvalExecutor evaluator) {
		if (getEvaluatorMap() instanceof ConcurrentMap) {
			return ((ConcurrentMap<String, EvalExecutor>) getEvaluatorMap())
					.putIfAbsent(expression, evaluator);
		}
		getEvaluatorMap().put(expression, evaluator);
		return evaluator;
	}

	public void clearEvalExecutor(String expression) {
		if (getEvaluatorMap().containsKey(expression)) {
			getEvaluatorMap().remove(expression);
		}
	}

	/**
	 * Eval実行クラスの取得
	 * 
	 * @param expression
	 * @throws Exception
	 */
	protected abstract EvalExecutor createEvalExecutor(String expression)
			throws Exception;

	public Map<String, EvalExecutor> getEvaluatorMap() {
		return evaluatorMap;
	}

	/**
	 * @param evaluatorMap
	 *            the evaluatorMap to set
	 */
	public void setEvaluatorMap(ConcurrentMap<String, EvalExecutor> evaluatorMap) {
		this.evaluatorMap = evaluatorMap;
	}

}
