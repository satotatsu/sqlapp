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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import org.mvel2.ParserContext;
import org.mvel2.optimizers.OptimizerFactory;

import com.sqlapp.util.DateUtils;
import com.sqlapp.util.iterator.Iterators;
import static com.sqlapp.util.CommonUtils.*;

public class ParserContextFactory {
	
	private static final ParserContextFactory instance=new ParserContextFactory();
	
	static{
		OptimizerFactory.setDefaultOptimizer(OptimizerFactory.SAFE_REFLECTIVE);
	}

	public static ParserContextFactory getInstance(){
		return instance;
	}
	
	public ParserContext getParserContext(){
		ParserContext parserContext=new ParserContext();
		//parserContext.setStrictTypeEnforcement(true);
		//parserContext.setStrongTyping(false);
		try {
			addImports(parserContext);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return parserContext;
	}

	protected void addImports(ParserContext parserContext) throws SecurityException, NoSuchMethodException{
		addPackageImports(parserContext);
		addAllStaticMethodsImport(parserContext, MvelUtils.class);
		addDateUtilsImports(parserContext);
		addAllStaticMethodsImport(parserContext, Iterators.class);
	}

	protected void addPackageImports(ParserContext parserContext) throws SecurityException, NoSuchMethodException{
		//addPackageImports(parserContext, CommonUtils.class);
		//addPackageImports(parserContext, org.joda.time.DateTime.class);
	}

	protected void addPackageImports(ParserContext parserContext, Class<?> clazz){
		parserContext.addPackageImport(clazz.getPackage().getName());
	}

	/**
	 * クラス内のstaticメソッドを一括でインポートします
	 * @param parserContext
	 * @param clazz
	 */
	protected void addStaticMethodsImport(ParserContext parserContext, Class<?> clazz, String methodName, Class<?>... parameterTypes){
		Method method;
		try {
			method = clazz.getMethod(methodName, parameterTypes);
			addImport(parserContext, method);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * クラス内のstaticメソッドを一括でインポートします
	 * @param parserContext
	 * @param clazz
	 */
	protected void addStaticMethodsImport(ParserContext parserContext, Class<?> clazz, String methodName){
		List<Method> methods=getAllStaticMethods(clazz);
		for(Method method:methods){
			if (methodName.equals(method.getName())){
				addImport(parserContext, method);
			}
		}
	}

	/**
	 * クラス内のstaticメソッドを一括でインポートします
	 * @param parserContext
	 * @param clazz
	 */
	protected void addAllStaticMethodsImport(ParserContext parserContext, Class<?> clazz){
		List<Method> methods=getAllStaticMethods(clazz);
		for(Method method:methods){
			addImport(parserContext, method);
		}
	}

	private void addImport(ParserContext parserContext, Method method){
		parserContext.addImport(method.getName(), method);
	}
	
	/**
	 * クラス内のstaticメソッドを全て取得します
	 * @param clazz
	 */
	private List<Method> getAllStaticMethods(Class<?> clazz){
		List<Method> list=list();
		for(Method method:clazz.getMethods()){
			if((method.getModifiers()&Modifier.STATIC)==0){
				continue;
			}
			if((method.getModifiers()&Modifier.PUBLIC)==0){
				continue;
			}
			if(method.getName().equals("forName")){
				continue;
			}
			list.add(method);
		}
		return list;
	}
	
	private void addDateUtilsImports(ParserContext parserContext) throws SecurityException, NoSuchMethodException{
		String methodName=null;
		methodName="toDate";
		addStaticMethodsImport(parserContext, DateUtils.class, methodName);
		methodName="toSqlDate";
		addStaticMethodsImport(parserContext, DateUtils.class, methodName);
		methodName="toTime";
		addStaticMethodsImport(parserContext, DateUtils.class, methodName);
		methodName="toTimestamp";
		addStaticMethodsImport(parserContext, DateUtils.class, methodName);
		methodName="setDate";
		addStaticMethodsImport(parserContext, DateUtils.class, methodName);
		methodName="setMonth";
		addStaticMethodsImport(parserContext, DateUtils.class, methodName);
		methodName="setYear";
		addStaticMethodsImport(parserContext, DateUtils.class, methodName);
		methodName="currentDateTime";
		addStaticMethodsImport(parserContext, DateUtils.class, methodName);
		methodName="currentDate";
		addStaticMethodsImport(parserContext, DateUtils.class, methodName);
		methodName="currentTime";
		addStaticMethodsImport(parserContext, DateUtils.class, methodName);
		methodName="currentTimestamp";
		addStaticMethodsImport(parserContext, DateUtils.class, methodName);
	}
}
