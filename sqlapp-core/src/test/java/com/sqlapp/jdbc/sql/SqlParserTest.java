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

package com.sqlapp.jdbc.sql;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.exceptions.SqlParseException;
import com.sqlapp.jdbc.sql.node.Node;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.test.AbstractTest;
import com.sqlapp.util.SqlExecuter;

public class SqlParserTest extends AbstractTest {

	private final SqlParser parser = SqlParser.getInstance();

	@Test
	public void testParse() {
		final SqlExecuter sql = new SqlExecuter("select * from test");
		sql.addSqlLine("where 0=0");
		sql.addSqlLine("  and a=/*a*/1");
		sql.addSqlLine("  and b like  /*b*/1");
		sql.addSqlLine("  and c like   /*a*/1");
		sql.addSqlLine("  and d in /*d*/(1)");
		sql.addSqlLine("  /*query(resultSetType=TYPE_SCROLL_INSENSITIVE, fetchSize=50, resultSetConcurrency=CONCUR_UPDATABLE, resultSetHoldability=HOLD_CURSORS_OVER_COMMIT)*/");
		final SqlNode node = parser.parse(sql.toString());
		final ParametersContext context = new ParametersContext();
		context.put("a", 3);
		context.put("b", 2);
		context.put("d", list(2, 4));
		final SqlParameterCollection sqlParameters = node.eval(context);
		final List<BindParameter> parameters = sqlParameters.getBindParameters();
		assertEquals("a", parameters.get(0).getName());
		assertEquals("b", parameters.get(1).getName());
		assertEquals(ResultSetType.TYPE_SCROLL_INSENSITIVE,
				sqlParameters.getResultSetType());
		assertEquals(ResultSetConcurrency.CONCUR_UPDATABLE,
				sqlParameters.getResultSetConcurrency());
		assertEquals(ResultSetHoldability.HOLD_CURSORS_OVER_COMMIT,
				sqlParameters.getResultSetHoldability());
		assertEquals(Integer.valueOf(50), sqlParameters.getFetchSize());
		assertEquals(3, node.getParameters().size());
		System.out.println(sqlParameters.getSql());
	}
	
	@Test
	public void testParse2() {
		final SqlExecuter sql = new SqlExecuter("select * from test");
		sql.addSqlLine("where 0=0");
		sql.addSqlLine("  and a=/*a*/1");
		sql.addSqlLine("  and b like /*b_startsWith+'%'*/1");
		sql.addSqlLine("  and b like /*'%'+b_endsWith*/1");
		sql.addSqlLine("  and c like /*c_startsWith+\"%\"*/1");
		sql.addSqlLine("  and c like /*\"%\"+c_endsWith*/1");
		final SqlNode node = parser.parse(sql.toString());
		final Set<ParameterDefinition> defs=node.getParameters();
		assertTrue(getParameterDefinition(defs, "b_startsWith")!=null);
		assertTrue(getParameterDefinition(defs, "b_endsWith")!=null);
		assertTrue(getParameterDefinition(defs, "c_startsWith")!=null);
		assertTrue(getParameterDefinition(defs, "c_endsWith")!=null);
	}

	private ParameterDefinition getParameterDefinition(final Set<ParameterDefinition> defs, final String name){
		for(final ParameterDefinition def:defs){
			if (name.equals(def.getName())){
				return def;
			}
		}
		return null;
	}
	

	@Test
	public void testIf() {
		final SqlExecuter sql = new SqlExecuter("select * from test");
		sql.addSqlLine("where 0=0");
		sql.addSqlLine("  and a=/*a;type=INT*/1");
		sql.addSqlLine("  /*if isEmpty(b) */");
		sql.addSqlLine("  and b like  /*b*/1");
		sql.addSqlLine("  and c like   /*a*/1");
		sql.addSqlLine("  /*end*/");
		sql.addSqlLine("  and d in /*d*/(1)");
		sql.addSqlLine("ORDER BY /*$_orderBy;sqlKeywordCheck=true*/aaa,bbb ");
		final SqlNode node = parser.parse(sql.toString());
		final ParametersContext context = new ParametersContext();
		context.put("a", 1);
		context.put("b", 2);
		context.put("d", list(2, 4));
		context.put("_orderBy", "aaa,bbb");
		final SqlParameterCollection sqlParameters = node.eval(context);
		final List<BindParameter> parameters = sqlParameters.getBindParameters();
		assertTrue("a".equals(parameters.get(0).getName()));
		assertTrue("d".equals(parameters.get(1).getName()));
		assertEquals(4, node.getParameters().size());
		System.out.println(sqlParameters.getSql());
	}

	@Test
	public void testParseError() {
		final SqlExecuter sql = new SqlExecuter("select * from test");
		sql.addSqlLine("where 0=0");
		sql.addSqlLine("  /*if a==null*/");
		sql.addSqlLine("  and a=/*a*/1");
		try {
			parser.parse(sql.toString());
			assertTrue(false, "NG");
		} catch (final Exception e) {
			assertTrue(e instanceof SqlParseException, "OK");
		}
	}

	@Test
	public void testParseFor1() {
		final SqlExecuter sql = new SqlExecuter("/*for a:b*/");
		sql.addSqlLine("1");
		sql.addSqlLine("/*end*/");
		System.out.println(sql);
		final SqlNode node = parser.parse(sql.toString());
		final ParametersContext context = new ParametersContext();
		context.put("b", list(2, 4));
		final SqlParameterCollection sqlParameters = node.eval(context);
		assertEquals(1, node.getParameters().size());
		assertEquals(sqlParameters.getSql(), "1\n1\n");
	}

	@Test
	public void testParseFor2() {
		final SqlExecuter sql = new SqlExecuter("/*for a:range(3)*/");
		sql.addSqlLine("/*# a+1 */");
		sql.addSqlLine("/*end*/");
		System.out.println(sql);
		final Node node = parser.parse(sql.toString());
		final Map<String, Object> context = map();
		final SqlParameterCollection sqlParameters = node.eval(context);
		assertEquals("1\n2\n3\n", sqlParameters.getSql());
	}

	private static String testSql = "SELECT\n  current_database() AS rule_catalog\n, r.*\nFROM pg_rules r\nWHERE 0=0\n  /*if isNotEmpty(schemaName)*/ AND schemaname = /*schemaName*/'public' /*end*/\n  /*if isNotEmpty(dbRuleName)*/ AND rulename = /*dbRuleName*/'' /*end*/";

	@Test
	public void testParseTotal() {
		final SqlExecuter sql = new SqlExecuter(testSql);
		System.out.println(sql);
		final Node node = parser.parse(sql.toString());
		final ParametersContext context = new ParametersContext();
		context.put("schemaName", "public");
		context.put("dbRuleName", (String) null);
		final SqlParameterCollection sqlParameters = node.eval(context);
		System.out.println(sqlParameters.getSql());

	}
	
	@Test
	public void testSource() {
		final Node node = parser.parse(this.getResource("source.sql"));
		final ParametersContext context = new ParametersContext();
		context.put("schemaName", "public");
		context.put("objectType", new String[]{"type1", "type2"});
		context.put("objectName", new String[]{"objectName1", "objectName2"});
		final SqlParameterCollection sqlParameters = node.eval(context);
		assertEquals(this.getResource("source_result.sql"), sqlParameters.getSql());
	}

}
