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

import java.util.Map;

import javax.script.Compilable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.sqlapp.util.eval.AbstractCachedEvaluator;
import com.sqlapp.util.eval.Evaluator;

public class CachedScriptEvaluator extends AbstractCachedEvaluator {
	private final String engineName;
	private transient volatile ScriptEngine engine = null;

	private final boolean compilable;

	public CachedScriptEvaluator() {
		this("JavaScript");
	}

	public CachedScriptEvaluator(String engineName) {
		this.engineName = engineName;
		ScriptEngineManager manager = new ScriptEngineManager();
		engine = manager.getEngineByName(engineName);
		if (getEngine() instanceof Compilable) {
			this.compilable = true;
		} else {
			this.compilable = false;
		}
		getEngine();
	}

	public ScriptEngine getEngine() {
		if (engine == null) {
			ScriptEngineManager manager = new ScriptEngineManager();
			engine = manager.getEngineByName(engineName);
		}
		return engine;
	}

	public String getEngineName() {
		return engineName;
	}

	@Override
	protected Evaluator createEvalExecutor(String expression) {
		if (this.compilable) {
			return new CompiledScriptEvaluator(expression, getEngine());
		} else {
			return new ScriptEvaluator(expression, getEngine());
		}
	}

	@Override
	protected String getCacheKey(String expression, Object context) {
		return expression;
	}

	@Override
	protected String getCacheKey(String expression, Map<?, ?> context) {
		return expression;
	}
}
