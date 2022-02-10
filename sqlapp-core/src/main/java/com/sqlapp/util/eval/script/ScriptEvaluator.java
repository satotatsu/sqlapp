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

package com.sqlapp.util.eval.script;

import java.util.HashMap;
import java.util.Map;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.util.eval.AbstractEvalExecutor;

public class ScriptEvaluator extends AbstractEvalExecutor{

	public ScriptEvaluator(String expression, ScriptEngine engine) {
		super(expression);
		this.engine=engine;
	}
	private ScriptEngine engine=null;
	private CompiledScript compiledScript=null;

	private CompiledScript getCompiledScript(){
		if (compiledScript==null){
			synchronized (this){
				if (getEngine() instanceof Compilable){
					Compilable compilableEngile=(Compilable)engine;
					try {
						compiledScript=compilableEngile.compile(getExpression());
					} catch (ScriptException e) {
						throw new RuntimeException(e);
					}
				}
			}
		}
		return compiledScript;
	}
	
	public ScriptEngine getEngine() {
		return engine;
	}

	/**
	 * EVALの実行
	 * @param bindings 引数
	 * @return 結果
	 * @throws ScriptException 
	 */
	@Override
	public Object doEval(ParametersContext bindings) throws ScriptException {
		Object result=null;
		if (getCompiledScript()!=null){
			result=getCompiledScript().eval(bindings);				
		}else {
			result=engine.eval(getExpression(), bindings);
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.util.eval.AbstractEvalExecutor#doEval(com.sqlapp.data.parameter.ParametersContext, java.lang.Class)
	 */
	@Override
	public <T> T doEval(ParametersContext bindings, Class<T> clazz) throws ScriptException {
		T val=Converters.getDefault().convertObject(doEval(bindings), clazz);
		return val;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.util.eval.AbstractEvalExecutor#doEvalBoolean(com.sqlapp.data.parameter.ParametersContext)
	 */
	@Override
	public boolean doEvalBoolean(ParametersContext bindings) throws ScriptException {
		return (Boolean)doEval(bindings);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object doEval(Object val) throws ScriptException {
		if (val instanceof javax.script.Bindings){
			return evalScript((javax.script.Bindings)val);
		}
		if (val instanceof Map<?, ?>){
			BindingsMap bMap=new BindingsMap();
			bMap.putAll((Map<String, Object>)val);
			return evalScript(bMap);
		}
		throw new UnsupportedOperationException("ScriptEvaluator.eval(val)");
	}

	private Object evalScript(javax.script.Bindings val) throws ScriptException{
		return engine.eval(getExpression(), (javax.script.Bindings)val);
	}
	
	static class BindingsMap extends HashMap<String, Object> implements javax.script.Bindings{
		/** serialVersionUID */
		private static final long serialVersionUID = -6082931365315111168L;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.util.eval.AbstractEvalExecutor#doEvalBoolean(java.lang.Object)
	 */
	@Override
	public boolean doEvalBoolean(Object val) throws ScriptException {
		return (Boolean)doEval(val);
	}
}
