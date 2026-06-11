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

import java.util.Map;

import org.mvel2.MVEL;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.util.eval.AbstractEvaluator;

public class MvelEvaluator extends AbstractEvaluator {

	protected MvelEvaluator(String expression) {
		super(expression);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T eval(ParametersContext bindings) {
		return (T) MVEL.eval(this.getExpression(), bindings);
	}

	@Override
	public <T> T eval(ParametersContext bindings, Class<T> clazz) {
		return MVEL.eval(this.getExpression(), bindings, clazz);
	}

	@Override
	public boolean evalBoolean(ParametersContext bindings) {
		return MVEL.eval(this.getExpression(), bindings, boolean.class);
	}

	@Override
	public boolean evalBoolean(Object val) {
		return MVEL.eval(this.getExpression(), val, boolean.class);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T eval(Object val) {
		return (T) MVEL.eval(this.getExpression(), val);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T eval(Map<?, ?> val) {
		return (T) MVEL.eval(this.getExpression(), val);
	}

	@Override
	public boolean evalBoolean(Map<?, ?> val) {
		return MVEL.eval(this.getExpression(), val, boolean.class);
	}
}
