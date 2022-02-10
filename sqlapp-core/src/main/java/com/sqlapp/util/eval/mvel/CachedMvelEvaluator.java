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

package com.sqlapp.util.eval.mvel;

import org.mvel2.ParserContext;

import com.sqlapp.util.eval.AbstractCachedEvaluator;
import com.sqlapp.util.eval.EvalExecutor;
/**
 * キャッシュ機能付きのMVEL評価クラス
 * @author satoh
 *
 */
public class CachedMvelEvaluator extends AbstractCachedEvaluator {

	private ParserContext parserContext=ParserContextFactory.getInstance().getParserContext();
	
	private static final CachedMvelEvaluator cachedMvelEvaluator=new CachedMvelEvaluator();
	
	public static CachedMvelEvaluator getInstance(){
		return cachedMvelEvaluator;
	}

	@Override
	protected EvalExecutor createEvalExecutor(String expression) throws Exception {
		MvelCompiledEvaluator evalExecutor=new MvelCompiledEvaluator(expression, parserContext);
		return evalExecutor;
	}

	/**
	 * @param parserContext the parserContext to set
	 */
	public void setParserContext(ParserContext parserContext) {
		this.parserContext = parserContext;
	}
}
