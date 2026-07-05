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

package com.sqlapp.jdbc.sql;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractTest;
import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.exceptions.SqlParseException;
import com.sqlapp.jdbc.sql.node.Node;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.SqlExecuter;

public class SqlParserTest extends AbstractTest {

	private final SqlParser parser = SqlParser.getInstance();

	@Test
	public void testParse() {
		String sqlText = """
				select * from test
				where 0=0
				  and a=/*a*/1
				  and b like  /*b*/1
				  and c like   /*a*/1
				  and d in /*d*/(1)
				/*query(resultSetType=TYPE_SCROLL_INSENSITIVE, fetchSize=50, resultSetConcurrency=CONCUR_UPDATABLE, resultSetHoldability=HOLD_CURSORS_OVER_COMMIT)*/
				""";
		String resultSql = """
				select * from test
				where 0=0
				  and a = ?
				  and b like ?
				  and c like ?
				  and d IN (?,?)""";
		final SqlExecuter sql = new SqlExecuter(sqlText);
		final SqlNode node = parser.parse(sql.toString());
		final ParametersContext context = new ParametersContext();
		context.put("a", 3);
		context.put("b", 2);
		context.put("d", list(2, 4));
		final SqlParameterCollection sqlParameters = node.eval(context);
		final List<BindParameter> parameters = sqlParameters.getBindParameters().stream().map(p -> p.getBindParameter())
				.toList();
		assertEquals("a", parameters.get(0).getName());
		assertEquals("b", parameters.get(1).getName());
		assertEquals(ResultSetType.TYPE_SCROLL_INSENSITIVE, sqlParameters.getResultSetType());
		assertEquals(ResultSetConcurrency.CONCUR_UPDATABLE, sqlParameters.getResultSetConcurrency());
		assertEquals(ResultSetHoldability.HOLD_CURSORS_OVER_COMMIT, sqlParameters.getResultSetHoldability());
		assertEquals(Integer.valueOf(50), sqlParameters.getFetchSize());
		assertEquals(3, node.getParameters().size());
		assertEquals(resultSql, sqlParameters.getSql());
	}

	@Test
	public void testParse2() {
		String sqlText = """
				select * from test
				where 0=0
				  and a=/*a*/1
				  and b like /*b_startsWith+'%'*/1
				  and b like /*'%'+b_endsWith*/1
				  and c like /*c_startsWith+\"%\"*/1
				  and c like /*\"%\"+c_endsWith*/1
				""";
		String resultSql = """
				select * from test
				where 0=0
				  and a = ?
				  and b like ?
				  and b like ?
				  and c like ?
				  and c like ?""";
		final SqlExecuter sql = new SqlExecuter(sqlText);
		final SqlNode node = parser.parse(sql.toString());
		final ParametersContext context = new ParametersContext();
		context.put("b_startsWith", "abc");
		context.put("b_endsWith", "xyz");
		context.put("c_startsWith", "123");
		context.put("c_endsWith", "456");
		final SqlParameterCollection sqlParameters = node.eval(context);
		final List<BindParameter> parameters = sqlParameters.getBindParameters().stream().map(p -> p.getBindParameter())
				.toList();
		final Set<ParameterDefinition> defs = node.getParameters();
		assertTrue(getParameterDefinition(defs, "b_startsWith") != null);
		assertTrue(getParameterDefinition(defs, "b_endsWith") != null);
		assertTrue(getParameterDefinition(defs, "c_startsWith") != null);
		assertTrue(getParameterDefinition(defs, "c_endsWith") != null);
		int i = 0;
		BindParameter params = parameters.get(i++);
		assertEquals("a", params.getName());
		params = parameters.get(i++);
		assertEquals("b_startsWith+'%'", params.getName());
		assertEquals("?", params.getBindingName());
		assertEquals("abc%", params.getValue());
		params = parameters.get(i++);
		assertEquals("'%'+b_endsWith", params.getName());
		assertEquals("?", params.getBindingName());
		assertEquals("%xyz", params.getValue());
		params = parameters.get(i++);
		assertEquals("c_startsWith+\"%\"", params.getName());
		assertEquals("?", params.getBindingName());
		assertEquals("123%", params.getValue());
		params = parameters.get(i++);
		assertEquals("\"%\"+c_endsWith", params.getName());
		assertEquals("?", params.getBindingName());
		assertEquals("%456", params.getValue());
		assertEquals(resultSql, sqlParameters.getSql());
	}

	private ParameterDefinition getParameterDefinition(final Set<ParameterDefinition> defs, final String name) {
		for (final ParameterDefinition def : defs) {
			if (name.equals(def.getName())) {
				return def;
			}
		}
		return null;
	}

	@Test
	public void testIf() {
		String sqlText = """
				select * from test
				where 0=0
				  and a=/*a;type=INT*/1
				  /*if isEmpty(b) */
				  and b like  /*b*/1
				  and c like   /*a*/1
				  /*end*/
				  and d in /*d*/(1)
				  ORDER BY /*$_orderBy;sqlKeywordCheck=true*/aaa,bbb
				""";
		String resultSql = """
				select * from test
				where 0=0
				  and a = ?\s
				  and d IN (?,?)
				  ORDER BY aaa,bbb""";
		final SqlExecuter sql = new SqlExecuter(sqlText);
		final SqlNode node = parser.parse(sql.toString());
		final ParametersContext context = new ParametersContext();
		context.put("a", 1);
		context.put("b", 2);
		context.put("d", list(2, 4));
		context.put("_orderBy", "aaa,bbb");
		final SqlParameterCollection sqlParameters = node.eval(context);
		final List<BindParameter> parameters = sqlParameters.getBindParameters().stream().map(p -> p.getBindParameter())
				.toList();
		int i = 0;
		BindParameter params = parameters.get(i++);
		assertEquals("a", params.getName());
		assertEquals("?", params.getBindingName());
		params = parameters.get(i++);
		assertEquals("d", params.getName());
		assertEquals("?", params.getBindingName());
		params = parameters.get(i++);
		assertEquals("d", params.getName());
		assertEquals("?", params.getBindingName());
		assertEquals(4, node.getParameters().size());
		assertEquals(3, parameters.size());
		assertEquals(resultSql, sqlParameters.getSql());
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
		String sqlText = """
				/*for a:b*/
				1
				/*end*/
				""";
		String resultSql = """
				1
				1
				""";
		final SqlExecuter sql = new SqlExecuter(sqlText);
		System.out.println(sql);
		final SqlNode node = parser.parse(sql.toString());
		final ParametersContext context = new ParametersContext();
		context.put("b", list(2, 4));
		final SqlParameterCollection sqlParameters = node.eval(context);
		assertEquals(1, node.getParameters().size());
		assertEquals(resultSql, sqlParameters.getSql());
	}

	@Test
	public void testParseFor2() {
		String sqlText = """
				/*for a:range(3)*/
				/*# a+1 */
				/*end*/
				""";
		String resultSql = """
				1
				2
				3
				""";
		final SqlExecuter sql = new SqlExecuter(sqlText);
		System.out.println(sql);
		final Node node = parser.parse(sql.toString());
		final Map<String, Object> context = map();
		final SqlParameterCollection sqlParameters = node.eval(context);
		assertEquals(resultSql, sqlParameters.getSql());
	}

	@Test
	public void testParseTotal() {
		String testSql = """
				SELECT
				  current_database() AS rule_catalo
				  , r.*
				FROM pg_rules
				WHERE 0=0
				  /*if isNotEmpty(schemaName)*/ AND schemaname = /*schemaName*/'public' /*end*/
				  /*if isNotEmpty(dbRuleName)*/ AND rulename = /*dbRuleName*/'' /*end*/
				""";
		String resultSql = """
				SELECT
				  current_database() AS rule_catalo
				  , r.*
				FROM pg_rules
				WHERE 0=0
				   AND schemaname = ?""";
		final SqlExecuter sql = new SqlExecuter(testSql);
		System.out.println(sql);
		final Node node = parser.parse(sql.toString());
		final ParametersContext context = new ParametersContext();
		context.put("schemaName", "public");
		context.put("dbRuleName", (String) null);
		final SqlParameterCollection sqlParameters = node.eval(context);
		System.out.println(sqlParameters.getSql());
		assertEquals(resultSql, sqlParameters.getSql().trim());
	}

	@Test
	public void testSource() {
		final Node node = parser.parse(FileUtils.getResource(this, "source.sql"));
		final ParametersContext context = new ParametersContext();
		context.put("schemaName", "public");
		context.put("objectType", new String[] { "type1", "type2" });
		context.put("objectName", new String[] { "objectName1", "objectName2" });
		final SqlParameterCollection sqlParameters = node.eval(context);
		assertEquals(FileUtils.getResource(this, "source_result.sql"), sqlParameters.getSql());
	}

}
