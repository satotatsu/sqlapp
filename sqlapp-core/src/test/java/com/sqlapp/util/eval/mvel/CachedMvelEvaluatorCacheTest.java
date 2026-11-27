/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util.eval.mvel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mvel2.ParserContext;

import com.sqlapp.util.eval.Evaluator;

class CachedMvelEvaluatorCacheTest {

	@Test
	void booleanMapEvaluationReusesItsCompiledExpression() {
		final AtomicInteger compilations = new AtomicInteger();
		final CachedMvelEvaluator evaluator = new CachedMvelEvaluator() {
			@Override
			protected Evaluator createEvalExecutor(final String expression) {
				compilations.incrementAndGet();
				return super.createEvalExecutor(expression);
			}
		};

		assertTrue(evaluator.evalBoolean("value == 1", Map.of("value", 1)));
		assertTrue(evaluator.evalBoolean("value == 1", Map.of("value", 1)));
		assertEquals(1, compilations.get());
	}

	@Test
	void replacingParserContextInvalidatesCompiledExpressions() {
		final ParserContext first = SqlappParserContextFactory.getInstance().getParserContext();
		first.addImport("Value", FirstValue.class);
		final CachedMvelEvaluator evaluator = new CachedMvelEvaluator(first);
		assertEquals("first", evaluator.eval("Value.get()", Map.of()));

		final ParserContext second = SqlappParserContextFactory.getInstance().getParserContext();
		second.addImport("Value", SecondValue.class);
		evaluator.setParserContext(second);

		assertEquals("second", evaluator.eval("Value.get()", Map.of()));
	}

	public static final class FirstValue {
		public static String get() { return "first"; }
	}

	public static final class SecondValue {
		public static String get() { return "second"; }
	}

	@Test
	void invokesImportedStaticOverloadsWithFixedArgumentTypes() throws Exception {
		var evaluator = new CachedMvelEvaluator();
		evaluator.addAllStaticMethodsImport(StaticFunctions.class.getName());
		assertEquals("no-arguments", evaluator.eval("customValue()", Map.of()));
		assertEquals("number-7", evaluator.eval("customValue(value)", Map.of("value", 7)));
		assertEquals("text-seven", evaluator.eval("customValue(text)", Map.of("text", "seven")));
		assertEquals("number-8", evaluator.eval("customValue(value)", Map.of("value", 8)));
	}

	@Test
	void registeringStaticMethodsInvalidatesCachedCalls() {
		var evaluator = new CachedMvelEvaluator();
		evaluator.addAllStaticMethodsImport(FirstValue.class);
		assertEquals("first", evaluator.eval("get()", Map.of()));
		evaluator.addAllStaticMethodsImport(StaticFunctions.class);
		assertEquals("no-arguments", evaluator.eval("customValue()", Map.of()));
		evaluator.addAllStaticMethodsImport(SecondValue.class);
		assertEquals("second", evaluator.eval("get()", Map.of()));
		assertEquals("no-arguments", evaluator.eval("customValue()", Map.of()));
	}

	public static final class StaticFunctions {
		public static String customValue() { return "no-arguments"; }
		public static String customValue(int value) { return "number-" + value; }
		public static String customValue(String value) { return "text-" + value; }
	}
}
