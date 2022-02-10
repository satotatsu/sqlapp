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

import static org.junit.jupiter.api.Assertions.*;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.jupiter.api.Test;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.optimizers.OptimizerFactory;

public class MvelBugTest {

	private ConcurrentMap<String, Serializable> map=new ConcurrentHashMap<String, Serializable>();
	
	static{
		OptimizerFactory.setDefaultOptimizer(OptimizerFactory.SAFE_REFLECTIVE);
	}
	
	@Test
	public void test() {
		for(int i=0;i<1;i++){
			testAll();
		}
	}

	@Test
	public void test10() {
		for(int i=0;i<10;i++){
			testAll();
		}
	}

	@Test
	public void test10000() {
		for(int i=0;i<10000;i++){
			testAll();
		}
	}

	private void testAll(){
		Map<String, Object> map=new HashMap<String, Object>();
		map.put("a", null);
		boolean val=doEvalBoolean("isEmpty(a)", map);
		assertEquals(Boolean.TRUE, val);
		//
		map.put("a", "");
		val=doEvalBoolean("isEmpty(a)", map);
		assertEquals(Boolean.TRUE, val);
	}

	public boolean doEvalBoolean(String expression, Object val) {
		return MVEL.executeExpression(getCompliedExpression(expression), val, Boolean.class);
	}

	private Serializable getCompliedExpression(String expression){
		Serializable compliedExpression=map.get(expression);
		if (compliedExpression==null){
			//compliedExpression=MVEL.compileGetExpression(expression, getParserContext());
			compliedExpression=MVEL.compileExpression(expression, getParserContext());
			Serializable org=map.putIfAbsent(expression, compliedExpression);
			return org!=null?org:compliedExpression;
		}
		return compliedExpression;
	}

	private ParserContext getParserContext(){
		ParserContext parserContext=new ParserContext();
		parserContext.setStrictTypeEnforcement(true);
		try {
			addAllStaticMethodsImport(parserContext, CommonUtils.class);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return parserContext;
	}

	/**
	 * クラス内のstaticメソッドを一括でインポートします
	 * @param parserContext
	 * @param clazz
	 */
	private static void addAllStaticMethodsImport(ParserContext parserContext, Class<?> clazz){
		List<Method> methods=getAllStaticMethods(clazz);
		for(Method method:methods){
			parserContext.addImport(method.getName(), method);
		}
	}

	/**
	 * クラス内のstaticメソッドを全て取得します
	 * @param clazz
	 */
	private static List<Method> getAllStaticMethods(Class<?> clazz){
		List<Method> list=new ArrayList<Method>();
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
	
	public static class CommonUtils{

		public static boolean isEmpty(final String obj){
			boolean ret= obj==null||obj.length()==0;
			if (ret){
				return ret;
			} else{
				return ret;
			}
		}
	}
}
