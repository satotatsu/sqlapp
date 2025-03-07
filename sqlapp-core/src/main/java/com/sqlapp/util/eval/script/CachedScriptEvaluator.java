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


import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.sqlapp.util.eval.AbstractCachedEvaluator;
import com.sqlapp.util.eval.EvalExecutor;

public class CachedScriptEvaluator extends AbstractCachedEvaluator{
	private String engineName="JavaScript";
	private transient volatile ScriptEngine engine=null;

	public CachedScriptEvaluator(){
		getEngine();
	}
	
	public CachedScriptEvaluator(String engineName){
		this.engineName=engineName;
		getEngine();
	}
	
	public ScriptEngine getEngine() {
		if (engine==null){
			ScriptEngineManager manager = new ScriptEngineManager();
			engine = manager.getEngineByName(engineName);
		}
		return engine;
	}

	public String getEngineName() {
		return engineName;
	}

	@Override
	protected EvalExecutor createEvalExecutor(String expression) throws Exception {
		ScriptEvaluator scriptEvaluator=new ScriptEvaluator(expression, getEngine());
		return scriptEvaluator;
	}
}
