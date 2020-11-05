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
package com.sqlapp.jdbc.sql.node;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.SqlParser;
import com.sqlapp.jdbc.sql.node.Node;
import com.sqlapp.util.CommonUtils;

/**
 * For Nodeのテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class ForNodeTest {

	/**
	 * ノード評価テスト
	 */
	@Test
	public void testEval() {
		Node node = SqlParser.getInstance().parse("/*for a:b */\na/*end*/");
		Map<String, Object> context = CommonUtils.map();
		context.put("b", new int[] { 1, 2, 3, 4 });
		SqlParameterCollection sqlParameterCollection = node.eval(context);
		assertEquals("aaaa", sqlParameterCollection.getSql());
	}
	
	/**
	 * ノード評価テスト
	 */
	@Test
	public void testEval2() {
		Node node = SqlParser.getInstance().parse("/*for(a:b)*/\na/*end*/");
		Map<String, Object> context = CommonUtils.map();
		context.put("b", new int[] { 1, 2, 3, 4 });
		SqlParameterCollection sqlParameterCollection = node.eval(context);
		assertEquals("aaaa", sqlParameterCollection.getSql());
	}

}
