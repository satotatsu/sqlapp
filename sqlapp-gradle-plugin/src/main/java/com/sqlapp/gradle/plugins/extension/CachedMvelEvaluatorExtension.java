/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-gradle-plugin.
 *
 * sqlapp-gradle-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-gradle-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-gradle-plugin.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.gradle.plugins.extension;

import org.gradle.api.Action;
import org.gradle.api.tasks.Internal;
import org.mvel2.ParserContext;

import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;

public abstract class CachedMvelEvaluatorExtension {

	@Internal
	private CachedMvelEvaluator evaluator = new CachedMvelEvaluator();

	public CachedMvelEvaluator getEvaluator() {
		return this.evaluator;
	}

	/**
	 * @param parserContext the parserContext to set
	 */
	public void setParserContext(ParserContext parserContext) {
		this.getEvaluator().setParserContext(parserContext);
	}

	public void addImport(Class<?> clazz) {
		this.getEvaluator().addImport(clazz);
	}

	public void addPackageImports(Class<?> clazz) {
		this.getEvaluator().addPackageImports(clazz);
	}

	public void addPackageImports(String addPackageImports) {
		this.getEvaluator().addPackageImports(addPackageImports);
	}

	/**
	 * クラス内のstaticメソッドを一括でインポートします
	 * 
	 * @param parserContext
	 * @param clazz
	 */
	public void addAllStaticMethodsImport(Class<?> clazz) {
		this.getEvaluator().addAllStaticMethodsImport(clazz);
	}

	public void call(Action<CachedMvelEvaluatorExtension> cons) {
		cons.execute(this);
	}

}
