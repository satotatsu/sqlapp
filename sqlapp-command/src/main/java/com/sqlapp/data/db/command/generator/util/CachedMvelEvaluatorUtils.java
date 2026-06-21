/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.generator.util;

import org.mvel2.ParserContext;

import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;
import com.sqlapp.util.eval.mvel.SqlappParserContextFactory;

public final class CachedMvelEvaluatorUtils {

	private static final ThreadLocal<CachedMvelEvaluator> evaluatorThreadLocal = ThreadLocal
			.withInitial(() -> createCachedMvelEvaluator());

	private static CachedMvelEvaluator createCachedMvelEvaluator() {
		CachedMvelEvaluator ceval = new CachedMvelEvaluator();
		ParserContext mvelParserContext = SqlappParserContextFactory.getInstance().getParserContext();
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
