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

package com.sqlapp.util.eval.mvel;

import static com.sqlapp.util.CommonUtils.list;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import org.mvel2.ParserContext;

import com.sqlapp.util.eval.AbstractCachedEvaluator;
import com.sqlapp.util.eval.Evaluator;

/**
 * キャッシュ機能付きのMVEL評価クラス
 * 
 * @author satoh
 *
 */
public class CachedMvelEvaluator extends AbstractCachedEvaluator {

	private ParserContext parserContext;

	private static final CachedMvelEvaluator cachedMvelEvaluator = new CachedMvelEvaluator(
			ParserContextFactory.getInstance().getParserContext());

	public static CachedMvelEvaluator getInstance() {
		return cachedMvelEvaluator;
	}

	public CachedMvelEvaluator() {
		this.parserContext = ParserContextFactory.getInstance().getParserContext();
	}

	public CachedMvelEvaluator(final ParserContext parserContext) {
		this.parserContext = parserContext;
	}

	/**
	 * @param parserContext the parserContext to set
	 */
	public void setParserContext(ParserContext parserContext) {
		this.parserContext = parserContext;
	}

	public void addImport(String clazzName) throws ClassNotFoundException {
		addImport(Class.forName(clazzName));
	}

	public void addImport(Class<?> clazz) {
		parserContext.addImport(clazz);
	}

	public void addPackageImports(Class<?> clazz) {
		addPackageImports(clazz.getPackage().getName());
	}

	public void addPackageImports(String packageName) {
		parserContext.addPackageImport(packageName);
	}

	/**
	 * クラス内のstaticメソッドを一括でインポートします
	 * 
	 * @param clazzName クラス名
	 * @throws ClassNotFoundException
	 */
	public void addAllStaticMethodsImport(String clazzName) throws ClassNotFoundException {
		addAllStaticMethodsImport(Class.forName(clazzName));
	}

	/**
	 * クラス内のstaticメソッドを一括でインポートします
	 * 
	 * @param clazz
	 */
	public void addAllStaticMethodsImport(Class<?> clazz) {
		List<Method> methods = getAllStaticMethods(clazz);
		for (Method method : methods) {
			addImport(parserContext, method);
		}
	}

	/**
	 * クラス内のstaticメソッドを全て取得します
	 * 
	 * @param clazz
	 */
	private List<Method> getAllStaticMethods(Class<?> clazz) {
		List<Method> list = list();
		for (Method method : clazz.getMethods()) {
			if ((method.getModifiers() & Modifier.STATIC) == 0) {
				continue;
			}
			if ((method.getModifiers() & Modifier.PUBLIC) == 0) {
				continue;
			}
			if (method.getName().equals("forName")) {
				continue;
			}
			list.add(method);
		}
		return list;
	}

	private void addImport(ParserContext parserContext, Method method) {
		parserContext.addImport(method.getName(), method);
	}

	@Override
	protected Evaluator createEvalExecutor(String expression) {
		return new MvelCompiledEvaluator(expression, this.parserContext);
	}

	@Override
	protected String getCacheKey(String expression, Object context) {
		StringBuilder builder = new StringBuilder(expression);
		builder.append(":");
		if (context != null) {
			builder.append(context.getClass().toGenericString());
		}
		return builder.toString();
	}

	@Override
	protected String getCacheKey(String expression, Map<?, ?> context) {
		StringBuilder builder = new StringBuilder(expression);
		builder.append(":");
		if (context != null) {
			builder.append(Map.class.toString());
		}
		return builder.toString();
	}
}
