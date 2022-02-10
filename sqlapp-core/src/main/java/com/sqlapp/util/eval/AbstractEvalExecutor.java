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

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.exceptions.ExpressionExecutionException;
import com.sqlapp.util.MessageReader;

public abstract class AbstractEvalExecutor implements EvalExecutor {
	public AbstractEvalExecutor(String expression) {
		this.expression = expression;
	}

	/**
	 * 式
	 */
	private String expression = null;

	public String getExpression() {
		return expression;
	}

	protected void setExpression(String expression) {
		this.expression = expression;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.eval.EvalExecutor#eval(com.sqlapp.data.parameter.
	 * ParametersContext)
	 */
	@Override
	public Object eval(ParametersContext bindings) {
		try {
			return doEval(bindings);
		} catch (Exception e) {
			throw handleException(e);
		}
	}

	protected abstract Object doEval(ParametersContext bindings)
			throws Exception;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.eval.EvalExecutor#eval(com.sqlapp.data.parameter.
	 * ParametersContext, java.lang.Class)
	 */
	@Override
	public <T> T eval(ParametersContext bindings, Class<T> clazz) {
		try {
			return doEval(bindings, clazz);
		} catch (Exception e) {
			throw handleException(e);
		}
	}

	protected abstract <T> T doEval(ParametersContext bindings, Class<T> clazz)
			throws Exception;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.eval.EvalExecutor#eval(java.lang.Object)
	 */
	@Override
	public Object eval(Object val) {
		try {
			return doEval(val);
		} catch (Exception e) {
			throw handleException(e);
		}
	}

	protected abstract Object doEval(Object val) throws Exception;

	@Override
	public boolean evalBoolean(Object val) {
		try {
			return doEvalBoolean(val);
		} catch (Exception e) {
			throw handleException(e);
		}
	}

	public abstract boolean doEvalBoolean(Object val) throws Exception;

	public boolean evalBoolean(ParametersContext bindings) {
		try {
			return doEvalBoolean(bindings);
		} catch (Exception e) {
			throw handleException(e);
		}
	}

	public abstract boolean doEvalBoolean(ParametersContext bindings)
			throws Exception;

	private ExpressionExecutionException handleException(Exception e) {
		String message = MessageReader.getInstance().getMessage("E0000003",
				getExpression());
		throw new ExpressionExecutionException(message, e);
	}
}
