/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
import static com.sqlapp.util.CommonUtils.map;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.jdbc.sql.FetchDirection;
import com.sqlapp.jdbc.sql.GeneratedKey;
import com.sqlapp.jdbc.sql.ResultSetConcurrency;
import com.sqlapp.jdbc.sql.ResultSetHoldability;
import com.sqlapp.jdbc.sql.ResultSetType;
import com.sqlapp.jdbc.sql.SqlParameterCollection;

/**
 * SQLに埋め込んでJDBCの挙動を変えるQueryNodeのテスト
 */
class QueryNodeFactoryTest {

	@Test
	void test() {
		String sql = "  /*query(fetchSize=10, resultSetType=TYPE_SCROLL_INSENSITIVE, resultSetConcurrency=CONCUR_READ_ONLY , resultSetHoldability=HOLD_CURSORS_OVER_COMMIT, fetchDirection=FETCH_UNKNOWN, generatedKey=NO_GENERATED_KEYS)*/ ";
		QueryNodeFactory factory = new QueryNodeFactory();
		Map<Integer, QueryNode> map = factory.parseSql(sql);
		List<QueryNode> list = list(map.values());
		int i = 0;
		QueryNode node = list.get(i++);
		Map<String, String> context = map();
		SqlParameterCollection sqlParameterCollection = new SqlParameterCollection();
		node.eval(context, sqlParameterCollection);
		assertEquals(Integer.valueOf(10), sqlParameterCollection.getFetchSize());
		assertEquals(ResultSetType.TYPE_SCROLL_INSENSITIVE, sqlParameterCollection.getResultSetType());
		assertEquals(ResultSetConcurrency.CONCUR_READ_ONLY, sqlParameterCollection.getResultSetConcurrency());
		assertEquals(ResultSetHoldability.HOLD_CURSORS_OVER_COMMIT, sqlParameterCollection.getResultSetHoldability());
		assertEquals(FetchDirection.FETCH_UNKNOWN, sqlParameterCollection.getFetchDirection());
		assertEquals(GeneratedKey.NO_GENERATED_KEYS, sqlParameterCollection.getGeneratedKey());
	}

}
