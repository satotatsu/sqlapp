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

package com.sqlapp.jdbc.sql.node;

import static com.sqlapp.util.CommonUtils.list;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.SqlComparisonOperator;
import com.sqlapp.jdbc.sql.SqlParameterCollection;

public class BindVariableArrayNodeFactoryTest {

	@Test
	public void testDate() {
		final String sql=" aaa in /*aaa*/(1) and bbb in /*bbb*/('2') and ccc in /*ccc*/(1, 2) and ddd\nin /*ddd*/('a' , 'b')";
		final BindVariableArrayNodeFactory factory=new BindVariableArrayNodeFactory();
		final Map<Integer, BindVariableArrayNode> map=factory.parseSql(sql);
		final List<BindVariableArrayNode> list=list(map.values());
		int i=0;
		BindVariableArrayNode node=list.get(i++);
		assertEquals("aaa in /*aaa*/(1)", node.getMatchText());
		assertEquals("aaa", node.getExpression());
		assertEquals("in", node.getOperator());
		node=list.get(i++);
		assertEquals("bbb in /*bbb*/('2')", node.getMatchText());
		assertEquals("bbb", node.getExpression());
		assertEquals("in", node.getOperator());
		node=list.get(i++);
		assertEquals("ccc in /*ccc*/(1, 2)", node.getMatchText());
		assertEquals("ccc", node.getExpression());
		assertEquals("in", node.getOperator());
		node=list.get(i++);
		assertEquals("ddd\nin /*ddd*/('a' , 'b')", node.getMatchText());
		assertEquals("ddd", node.getExpression());
		assertEquals("in", node.getOperator());
	}

	@Test
	public void testComma() {
		final String sql=" aaa in /*aaa*/(111), ";
		final BindVariableArrayNodeFactory factory=new BindVariableArrayNodeFactory();
		final Map<Integer, BindVariableArrayNode> map=factory.parseSql(sql);
		final List<BindVariableArrayNode> list=list(map.values());
		int i=0;
		final BindVariableArrayNode node=list.get(i++);
		assertEquals("aaa in /*aaa*/(111)", node.getMatchText());
		assertEquals("aaa", node.getExpression());
	}

	@Test
	public void testEval() {
		final String sql=" aaa in /*aval*/(111)  ";
		final BindVariableArrayNodeFactory factory=new BindVariableArrayNodeFactory();
		final Map<Integer, BindVariableArrayNode> map=factory.parseSql(sql);
		final List<BindVariableArrayNode> list=list(map.values());
		int i=0;
		final BindVariableArrayNode node=list.get(i++);
		final ParametersContext context=new ParametersContext();
		context.put("aval", new int[] {2,1});
		SqlParameterCollection sqlParameterCollection=node.eval(context);
		assertEquals(1, node.getIndex());
		assertEquals("aaa IN (?,?)", sqlParameterCollection.getSql());
		assertEquals(1, sqlParameterCollection.getBindParameters().get(0).getValue());
		assertEquals(2, sqlParameterCollection.getBindParameters().get(1).getValue());
		//
		context.put("aval", 1);
		context.putOperator("aval", SqlComparisonOperator.EQ);
		sqlParameterCollection=node.eval(context);
		assertEquals("aaa = ?", sqlParameterCollection.getSql());
		//
		context.putOperator("aval", SqlComparisonOperator.NEQ);
		sqlParameterCollection=node.eval(context);
		assertEquals("aaa <> ?", sqlParameterCollection.getSql());
		//
		context.put("aval", "a");
		context.putOperator("aval", SqlComparisonOperator.NOT_LIKE);
		sqlParameterCollection=node.eval(context);
		assertEquals("aaa NOT LIKE ?", sqlParameterCollection.getSql());
		//
		context.put("aval", "a");
		context.putOperator("aval", SqlComparisonOperator.LIKE);
		sqlParameterCollection=node.eval(context);
		assertEquals("aaa LIKE ?", sqlParameterCollection.getSql());
		assertEquals("a", sqlParameterCollection.getBindParameters().get(0).getValue());
		//
		context.put("aval", "a");
		context.putOperator("aval", SqlComparisonOperator.STARTS_WITH);
		sqlParameterCollection=node.eval(context);
		assertEquals("aaa LIKE ?", sqlParameterCollection.getSql());
		assertEquals("a%", sqlParameterCollection.getBindParameters().get(0).getValue());
		//
		context.put("aval", "a");
		context.putOperator("aval", SqlComparisonOperator.ENDS_WITH);
		sqlParameterCollection=node.eval(context);
		assertEquals("aaa LIKE ?", sqlParameterCollection.getSql());
		assertEquals("%a", sqlParameterCollection.getBindParameters().get(0).getValue());
		//
		context.put("aval", "a");
		context.putOperator("aval", SqlComparisonOperator.CONTAINS);
		sqlParameterCollection=node.eval(context);
		assertEquals("aaa LIKE ?", sqlParameterCollection.getSql());
		assertEquals("%a%", sqlParameterCollection.getBindParameters().get(0).getValue());
		//
		context.put("aval", new int[]{1,2});
		context.putOperator("aval", SqlComparisonOperator.EQ);
		sqlParameterCollection=node.eval(context);
		assertEquals("aaa IN (?,?)", sqlParameterCollection.getSql());
		//
		context.put("aval", new int[]{1,2});
		context.putOperator("aval", SqlComparisonOperator.NEQ);
		sqlParameterCollection=node.eval(context);
		assertEquals("aaa NOT IN (?,?)", sqlParameterCollection.getSql());
		//
		context.put("aval", new int[]{1,2});
		context.putOperator("aval", SqlComparisonOperator.LIKE);
		sqlParameterCollection=node.eval(context);
		assertEquals(" ( aaa LIKE ? OR aaa LIKE ? ) ", sqlParameterCollection.getSql());
		//
		context.put("aval", new int[]{1,2});
		context.putOperator("aval", SqlComparisonOperator.NOT_LIKE);
		sqlParameterCollection=node.eval(context);
		assertEquals(" NOT ( aaa LIKE ? OR aaa LIKE ? ) ", sqlParameterCollection.getSql());
		//
		context.put("aval", new int[]{1,2});
		context.putOperator("aval", SqlComparisonOperator.GT_AND_LT);
		sqlParameterCollection=node.eval(context);
		assertEquals(" ( aaa > ? AND aaa < ? ) ", sqlParameterCollection.getSql());
		//
		context.put("aval", new int[]{1,2});
		context.putOperator("aval", SqlComparisonOperator.GT_AND_LT.reverse());
		sqlParameterCollection=node.eval(context);
		assertEquals(" ( aaa <= ? OR aaa >= ? ) ", sqlParameterCollection.getSql());
		//
		context.put("aval", new int[]{1,2});
		context.putOperator("aval", SqlComparisonOperator.GTE_AND_LT);
		sqlParameterCollection=node.eval(context);
		assertEquals(" ( aaa >= ? AND aaa < ? ) ", sqlParameterCollection.getSql());
		//
		context.put("aval", new int[]{1,2});
		context.putOperator("aval", SqlComparisonOperator.GTE_AND_LT.reverse());
		sqlParameterCollection=node.eval(context);
		assertEquals(" ( aaa < ? OR aaa >= ? ) ", sqlParameterCollection.getSql());
		//
		context.put("aval", new int[]{1,2});
		context.putOperator("aval", SqlComparisonOperator.GT_AND_LTE);
		sqlParameterCollection=node.eval(context);
		assertEquals(" ( aaa > ? AND aaa <= ? ) ", sqlParameterCollection.getSql());
		//
		context.put("aval", new int[]{1,2});
		context.putOperator("aval", SqlComparisonOperator.GT_AND_LTE.reverse());
		sqlParameterCollection=node.eval(context);
		assertEquals(" ( aaa <= ? OR aaa > ? ) ", sqlParameterCollection.getSql());
		//
		context.put("aval", new int[]{1,2});
		context.putOperator("aval", SqlComparisonOperator.GTE_AND_LTE);
		sqlParameterCollection=node.eval(context);
		assertEquals(" ( aaa >= ? AND aaa <= ? ) ", sqlParameterCollection.getSql());
		//
		context.put("aval", new int[]{1,2});
		context.putOperator("aval", SqlComparisonOperator.GTE_AND_LTE.reverse());
		sqlParameterCollection=node.eval(context);
		assertEquals(" ( aaa < ? OR aaa > ? ) ", sqlParameterCollection.getSql());
		//
		context.put("aval", new int[]{1,2});
		context.putOperator("aval", SqlComparisonOperator.BETWEEN);
		sqlParameterCollection=node.eval(context);
		assertEquals("aaa BETWEEN ? AND ?", sqlParameterCollection.getSql());
		//
		context.put("aval", new int[]{1,2});
		context.putOperator("aval", SqlComparisonOperator.BETWEEN.reverse());
		sqlParameterCollection=node.eval(context);
		assertEquals("aaa NOT BETWEEN ? AND ?", sqlParameterCollection.getSql());
	}


	@Test
	public void testEval2() {
		final String sql="AND SCHEMA_NAME(t.schema_id) IN /*schemaName;type=NVARCHAR*/('%')";
		final BindVariableArrayNodeFactory factory=new BindVariableArrayNodeFactory();
		final Map<Integer, BindVariableArrayNode> map=factory.parseSql(sql);
		final List<BindVariableArrayNode> list=list(map.values());
		int i=0;
		final BindVariableArrayNode node=list.get(i++);
		final ParametersContext context=new ParametersContext();
		context.put("schemaName", 1);
		final SqlParameterCollection sqlParameterCollection=node.eval(context);
		assertEquals("SCHEMA_NAME(t.schema_id) IN (?)", sqlParameterCollection.getSql());
	}
}
