/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util.eval.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Reader;
import java.util.Map;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.SimpleBindings;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.util.eval.Evaluator;

class ScriptBooleanConversionTest {
	@Test
	void convertsResultsConsistentlyForEveryContextOverload() {
		for (Object value : new Object[] { 1, 0, "true", "false", true, false }) {
			ResultEngine engine = new ResultEngine(value);
			for (Evaluator evaluator : new Evaluator[] { new ScriptEvaluator("result", engine),
					new CompiledScriptEvaluator("result", engine) }) {
				boolean expected = evaluator.evalBoolean(Map.of());
				ParametersContext context = new ParametersContext();
				assertEquals(expected, evaluator.evalBoolean(context));
				assertEquals(expected, evaluator.evalBoolean((Map<?, ?>) context));
				assertEquals(expected, evaluator.evalBoolean((Object) context));
			}
		}
	}

	private static final class ResultEngine extends AbstractScriptEngine implements Compilable {
		private final Object result;

		ResultEngine(Object result) {
			this.result = result;
		}

		@Override
		public Object eval(String script, ScriptContext context) {
			return result;
		}

		@Override
		public Object eval(Reader reader, ScriptContext context) {
			return result;
		}

		@Override
		public Bindings createBindings() {
			return new SimpleBindings();
		}

		@Override
		public ScriptEngineFactory getFactory() {
			throw new UnsupportedOperationException();
		}

		@Override
		public CompiledScript compile(String script) {
			return new CompiledScript() {
				@Override
				public Object eval(ScriptContext context) {
					return result;
				}

				@Override
				public ScriptEngine getEngine() {
					return ResultEngine.this;
				}
			};
		}

		@Override
		public CompiledScript compile(Reader reader) {
			return compile("");
		}
	}
}
