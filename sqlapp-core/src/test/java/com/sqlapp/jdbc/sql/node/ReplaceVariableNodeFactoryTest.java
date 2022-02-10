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

import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.node.ReplaceVariableNode;
import com.sqlapp.jdbc.sql.node.ReplaceVariableNodeFactory;

public class ReplaceVariableNodeFactoryTest {

	@Test
	public void test1() {
		String sql="  /*$aaa*/all_db_links  ";
		ReplaceVariableNodeFactory factory=new ReplaceVariableNodeFactory();
		Map<Integer, ReplaceVariableNode> map=factory.parseSql(sql);
		List<ReplaceVariableNode> list=list(map.values());
		int i=0;
		ReplaceVariableNode node=list.get(i++);
		Map<String, String> context=map();
		context.put("aaa", "dba");
		SqlParameterCollection sqlParameterCollection=new SqlParameterCollection();
		node.eval(context, sqlParameterCollection);
		assertEquals("dba", sqlParameterCollection.getSql());
	}

	@Test
	public void test2() {
		String sql="  /*$aaa; length=3*/all_db_links  ";
		ReplaceVariableNodeFactory factory=new ReplaceVariableNodeFactory();
		Map<Integer, ReplaceVariableNode> map=factory.parseSql(sql);
		List<ReplaceVariableNode> list=list(map.values());
		int i=0;
		ReplaceVariableNode node=list.get(i++);
		Map<String, String> context=map();
		context.put("aaa", "dba");
		SqlParameterCollection sqlParameterCollection=new SqlParameterCollection();
		node.eval(context, sqlParameterCollection);
		assertEquals("dba_db_links", sqlParameterCollection.getSql());
	}

	@Test
	public void test3() {
		String sql="  ORDER BY /*$_orderBy;sqlKeywordCheck=true*/aaa,bbb";
		ReplaceVariableNodeFactory factory=new ReplaceVariableNodeFactory();
		Map<Integer, ReplaceVariableNode> map=factory.parseSql(sql);
		List<ReplaceVariableNode> list=list(map.values());
		int i=0;
		ReplaceVariableNode node=list.get(i++);
		Map<String, String> context=map();
		context.put("_orderBy", "aa,bb");
		SqlParameterCollection sqlParameterCollection=new SqlParameterCollection();
		node.eval(context, sqlParameterCollection);
		assertEquals("aa,bb", sqlParameterCollection.getSql());
	}
}
