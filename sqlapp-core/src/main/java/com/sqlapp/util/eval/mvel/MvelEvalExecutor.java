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

import org.mvel2.MVEL;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.util.eval.AbstractEvalExecutor;

public class MvelEvalExecutor extends AbstractEvalExecutor{
	
	public MvelEvalExecutor(String expression){
		super(expression);
	}
	
	@Override
	public Object doEval(ParametersContext bindings){
		return MVEL.eval(getExpression(), bindings);
	}

	@Override
	public <T> T doEval(ParametersContext bindings,
			Class<T> clazz) {
		return MVEL.eval(getExpression(), bindings, clazz);
	}

	@Override
	public boolean doEvalBoolean(ParametersContext bindings) {
		return MVEL.eval(getExpression(), bindings, boolean.class);
	}

	@Override
	public Object doEval(Object val) {
		return MVEL.eval(getExpression(), val);
	}

	@Override
	public boolean doEvalBoolean(Object val) {
		return MVEL.eval(getExpression(), val, boolean.class);
	}
}
