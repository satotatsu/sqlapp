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

import static com.sqlapp.util.CommonUtils.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.SqlComparisonOperator;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.node.BindVariableNode;
import com.sqlapp.jdbc.sql.node.BindVariableNodeFactory;

public class BindVariableNodeFactoryTest {

	@Test
	public void testDate() {
		String sql=" aaa= /*aaa*/time '10:20' and bbb= /*bbb*/date '2011' and ccc= /*ccc*/datetime '20110101' and ddd= /*ddd*/timestamp '20110101'";
		BindVariableNodeFactory factory=new BindVariableNodeFactory();
		Map<Integer, BindVariableNode> map=factory.parseSql(sql);
		List<BindVariableNode> list=list(map.values());
		int i=0;
		BindVariableNode node=list.get(i++);
		assertEquals("aaa= /*aaa*/time '10:20'", node.getMatchText());
		assertEquals("aaa", node.getExpression());
		assertEquals("aaa= /*aaa*/time '10:20'", node.getSql());
		node=list.get(i++);
		assertEquals("bbb= /*bbb*/date '2011'", node.getMatchText());
		assertEquals("bbb", node.getExpression());
		assertEquals("bbb= /*bbb*/date '2011'", node.getSql());
		node=list.get(i++);
		assertEquals("ccc= /*ccc*/datetime '20110101'", node.getMatchText());
		assertEquals("ccc", node.getExpression());
		assertEquals("ccc= /*ccc*/datetime '20110101'", node.getSql());
		node=list.get(i++);
		assertEquals("ddd= /*ddd*/timestamp '20110101'", node.getMatchText());
		assertEquals("ddd", node.getExpression());
		assertEquals("ddd= /*ddd*/timestamp '20110101'", node.getSql());
	}

	@Test
	public void testInterval() {
		String sql="aaa= /*aaa*/interval '2-1' and bbb= /*bbb*/interval '2011' year to month and ";
		BindVariableNodeFactory factory=new BindVariableNodeFactory();
		Map<Integer, BindVariableNode> map=factory.parseSql(sql);
		List<BindVariableNode> list=list(map.values());
		int i=0;
		BindVariableNode node=list.get(i++);
		assertEquals("aaa= /*aaa*/interval '2-1'", node.getMatchText());
		assertEquals("aaa", node.getExpression());
		node=list.get(i++);
		assertEquals("bbb= /*bbb*/interval '2011' year to month", node.getMatchText());
		assertEquals("bbb", node.getExpression());
	}

	@Test
	public void testComma() {
		String sql=" aaa like /*aaa*/111,  ";
		BindVariableNodeFactory factory=new BindVariableNodeFactory();
		Map<Integer, BindVariableNode> map=factory.parseSql(sql);
		List<BindVariableNode> list=list(map.values());
		int i=0;
		BindVariableNode node=list.get(i++);
		assertEquals("aaa like /*aaa*/111", node.getMatchText());
		assertEquals("aaa", node.getExpression());
	}

	@Test
	public void testEval() {
		String sql=" aaa like /*aaa*/111 ";
		BindVariableNodeFactory factory=new BindVariableNodeFactory();
		Map<Integer, BindVariableNode> map=factory.parseSql(sql);
		List<BindVariableNode> list=list(map.values());
		int i=0;
		BindVariableNode node=list.get(i++);
		ParametersContext context=new ParametersContext();
		context.put("aval", 1);
		SqlParameterCollection sqlParameterCollection=node.eval(context);
		assertEquals(1, node.getIndex());
		assertEquals("aaa", node.getExpression());
		assertEquals("aaa like /*aaa*/111", node.getMatchText());
		assertEquals("aaa like ?", sqlParameterCollection.getSql());
		//
		context.putOperator("aaa", SqlComparisonOperator.EQ);
		sqlParameterCollection=node.eval(context);
		assertEquals("aaa = ?", sqlParameterCollection.getSql());
	}
	
	@Test
	public void testEval2() {
		String sql="(/*aaa*/111, /*bbb*/111)";
		BindVariableNodeFactory factory=new BindVariableNodeFactory();
		Map<Integer, BindVariableNode> map=factory.parseSql(sql);
		List<BindVariableNode> list=list(map.values());
		int i=0;
		BindVariableNode node=list.get(i++);
		ParametersContext context=new ParametersContext();
		context.put("aaa", 1);
		SqlParameterCollection sqlParameterCollection=node.eval(context);
		assertEquals(1, node.getIndex());
		assertEquals("aaa", node.getExpression());
		assertEquals("/*aaa*/111", node.getMatchText());
		assertEquals("?", sqlParameterCollection.getSql());
		//
		node=list.get(i++);
		context=new ParametersContext();
		context.put("bbb", 1);
		sqlParameterCollection=node.eval(context);
		assertEquals(12, node.getIndex());
		assertEquals("bbb", node.getExpression());
		assertEquals(" /*bbb*/111", node.getMatchText());
		assertEquals("?", sqlParameterCollection.getSql());
	}
	
	@Test
	public void testEval3() {
		String sql=" AND si.table_cat LIKE /*catalogName*/'%' )";
		BindVariableNodeFactory factory=new BindVariableNodeFactory();
		Map<Integer, BindVariableNode> map=factory.parseSql(sql);
		List<BindVariableNode> list=list(map.values());
		int i=0;
		BindVariableNode node=list.get(i++);
		ParametersContext context=new ParametersContext();
		context.put("aaa", 1);
		SqlParameterCollection sqlParameterCollection=node.eval(context);
		assertEquals(5, node.getIndex());
		assertEquals("catalogName", node.getExpression());
		assertEquals("si.table_cat LIKE /*catalogName*/'%'", node.getMatchText());
		assertEquals("si.table_cat LIKE ?", sqlParameterCollection.getSql());
	}
}
