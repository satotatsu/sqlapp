package com.sqlapp.data.db.command.generator.util;

import org.mvel2.ParserContext;

import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;
import com.sqlapp.util.eval.mvel.ParserContextFactory;

public final class CachedMvelEvaluatorUtils {

	private static final ThreadLocal<CachedMvelEvaluator> evaluatorThreadLocal = ThreadLocal
			.withInitial(() -> createCachedMvelEvaluator());

	private static CachedMvelEvaluator createCachedMvelEvaluator() {
		CachedMvelEvaluator ceval = new CachedMvelEvaluator();
		ParserContext mvelParserContext = ParserContextFactory.getInstance().getParserContext();
		ceval.setParserContext(mvelParserContext);
		ceval.addAllStaticMethodsImport(GeneratorMvelUtils.class);
		return ceval;
	}

	public static void setCachedMvelEvaluator(CachedMvelEvaluator evaluator) {
		evaluatorThreadLocal.set(evaluator);
	}

	public static CachedMvelEvaluator getCachedMvelEvaluator() {
		return evaluatorThreadLocal.get();
	}
}
