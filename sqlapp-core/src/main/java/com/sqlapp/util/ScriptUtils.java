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

package com.sqlapp.util;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.util.eval.CachedEvaluator;
import com.sqlapp.util.eval.EvalExecutor;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;
/**
 * スクリプト実行クラス
 * @author SATOH
 *
 */
public class ScriptUtils {
	
	private static ScriptUtils instance=new ScriptUtils();

	public static ScriptUtils getInstance(){
		return instance;
	}

	private CachedEvaluator cachedEvaluator=CachedMvelEvaluator.getInstance();
	/**
	 * EVALの実行
	 * @param expression 式
	 * @param bindings 引数
	 * @return 結果
	 */
	public Object eval(String expression, ParametersContext bindings){
		EvalExecutor eval=cachedEvaluator.getEvalExecutor(expression);
		Object result=eval.eval(bindings);
		return result;
	}
}
