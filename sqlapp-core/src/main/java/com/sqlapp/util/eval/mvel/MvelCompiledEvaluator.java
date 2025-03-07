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

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;

import org.mvel2.MVEL;
import org.mvel2.ParserContext;
/**
 * コンパイル済のMVEL実行クラス
 * @author satoh
 *
 */
public class MvelCompiledEvaluator extends MvelEvalExecutor{
	private volatile Serializable compliedExpression=null;
	
	private ParserContext parserContext=null;
	
	public MvelCompiledEvaluator(String expression){
		super(expression);
	}

	public MvelCompiledEvaluator(String expression, ParserContext parserContext){
		super(expression);
		this.parserContext=parserContext;
	}

	
	/* (non-Javadoc)
	 * @see com.sqlapp.util.eval.mvel.MvelEvalExecutor#doEval(com.sqlapp.data.parameter.ParametersContext)
	 */
	@Override
	public Object doEval(ParametersContext bindings) {
		return MVEL.executeExpression(getCompliedExpression(), bindings);
	}

	/**
	 * @param expression
	 * @param bindings
	 * @param db
	 */
	public Object doEval(String expression, ParametersContext bindings, Dialect db){
		bindings.put("db", db);
		return MVEL.executeExpression(getCompliedExpression(), bindings);
	}

	@Override
	public Object doEval(Object val) {
		return MVEL.executeExpression(getCompliedExpression(), val);
	}

	@Override
	public boolean doEvalBoolean(Object val) {
		return MVEL.executeExpression(getCompliedExpression(), val, boolean.class);
	}

	@Override
	public boolean doEvalBoolean(ParametersContext bindings) {
		return MVEL.executeExpression(getCompliedExpression(), bindings, boolean.class);
	}
	
	private Serializable getCompliedExpression(){
		if (compliedExpression==null){
			synchronized (this){
				if (parserContext==null){
					compliedExpression=MVEL.compileExpression(getExpression());
				} else{
					compliedExpression=MVEL.compileExpression(getExpression(), parserContext);
				}
			}
		}
		return compliedExpression;
	}
}
