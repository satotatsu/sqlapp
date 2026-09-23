/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util.eval.mvel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.parameter.ParametersContext;

class MvelEvaluatorTest {

	@Test
	void usesSharedClassResolverForEveryParametersContextOverload() {
		final ParametersContext context = new ParametersContext();
		context.put("path", "aaa.png");

		assertEquals(LocalDate.of(2026, 9, 23), evaluator("LocalDate.of(2026, 9, 23)").eval(context));
		assertEquals(LocalDate.of(2026, 9, 23),
				evaluator("LocalDate.of(2026, 9, 23)").eval(context, LocalDate.class));
		assertTrue(evaluator("LocalDate.of(2026, 9, 23).year == 2026").evalBoolean(context));
		assertEquals(new File("aaa.png"), evaluator("new File(path)").eval((Map<?, ?>) context));
	}

	private static MvelEvaluator evaluator(final String expression) {
		return new MvelEvaluator(expression);
	}
}
