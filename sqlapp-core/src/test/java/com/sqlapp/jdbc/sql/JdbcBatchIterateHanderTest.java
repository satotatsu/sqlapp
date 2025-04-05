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

package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.CountIterable;

class JdbcBatchIterateHanderTest extends AbstractDbTest {

	/**
	 * INSERTして自動生成されたキーを使って、そのままUPDATEを行う
	 * 
	 * @throws SQLException
	 */
	@Test
	void testInsertUpdateWithGeneratedKey() throws SQLException {
		final String sql = this.getResource("create_table1.sql");
		SqlConverter con = new SqlConverter();
		final ParametersContext context = new ParametersContext();
		final String sql1 = this.getResource("insert_table1.sql");
		final String sql2 = this.getResource("update_table1.sql");
		final SqlNode sqlNode1 = con.parseSql(context, sql1);
		final SqlNode sqlNode2 = con.parseSql(context, sql2);
		List<SqlNode> sqlNodes = CommonUtils.list();
		sqlNodes.add(sqlNode1);
		sqlNodes.add(sqlNode2);
		testDb(connection -> {
			this.dropTables(connection, "TABA");
			executeSql(connection, sql);
			int gen = 125;
			final CountIterable<ParametersContext> iterable = new CountIterable<ParametersContext>(gen, l -> {
				ParametersContext ctx = new ParametersContext();
				ctx.put("TXT", "abc" + l);
				return ctx;
			});
			int[] counter = new int[1];
			JdbcBatchIterateHander handler = new JdbcBatchIterateHander(sqlNodes, 10, 10);
			handler.setBatchUpdateResultHandler(result -> {
				if (result.getSqlNode() == sqlNode1) {
					if (result.getValues().size() == 10) {
						assertEquals(10, result.getGeneratedKeys().size());
					} else {
						assertEquals(5, result.getGeneratedKeys().size());
					}
					GeneratedKeyInfo info = result.getGeneratedKeys().get(0);
					assertEquals("ID", info.getColumnName());
					assertEquals(1, result.getResult()[0]);
					for (int i = 0; i < result.getGeneratedKeys().size(); i++) {
						info = result.getGeneratedKeys().get(i);
						final ParametersContext ctx = (ParametersContext) result.getValues().get(i).value();
						ctx.put("ID", info.getValue());// INSERTした結果のIDを格納
					}
				} else {
					// GeneratedKeyInfo info = result.getGeneratedKeys().get(0);
					assertEquals(0, result.getGeneratedKeys().size());
					assertEquals(1, result.getResult()[0]);// INSERTした結果のIDをWHERE条件にして更新して結果が件
				}
				counter[0] = counter[0] + result.getValues().size();
				System.out.println("counter=" + result.getLastRowIndex() + ", generatedKeys.size="
						+ result.getGeneratedKeys().size() + ", result.result[0]=" + result.getResult()[0]);
			});
			handler.execute(connection, iterable);
			assertEquals(gen * 2, counter[0]);
		}, (connection) -> {
			this.dropTables(connection, "TABA");
		});
	}

	/**
	 * INSERTして自動生成されたキーを使って、そのままUPDATEを行う
	 * 
	 * @throws SQLException
	 */
	@Test
	void testInsertUpdateWithoutGeneratedKey() throws SQLException {
		final String sql = this.getResource("create_table1.sql");
		SqlConverter con = new SqlConverter();
		final ParametersContext context = new ParametersContext();
		final String sql1 = this.getResource("insert_table1.sql");
		final String sql2 = this.getResource("update_table1.sql");
		final SqlNode sqlNode1 = con.parseSql(context, sql1);
		final SqlNode sqlNode2 = con.parseSql(context, sql2);
		List<SqlNode> sqlNodes = CommonUtils.list();
		sqlNodes.add(sqlNode1);
		sqlNodes.add(sqlNode2);
		testDb(connection -> {
			this.dropTables(connection, "TABA");
			executeSql(connection, sql);
			int gen = 111;
			final CountIterable<ParametersContext> iterable = new CountIterable<ParametersContext>(gen, l -> {
				ParametersContext ctx = new ParametersContext();
				ctx.put("TXT", "abc" + l);
				return ctx;
			});
			JdbcBatchIterateHander handler = new JdbcBatchIterateHander(sqlNodes, 10, 10);
			int[] counter = new int[1];
			handler.setBatchUpdateResultHandler(result -> {
				if (result.getSqlNode() == sqlNode1) {
					if (result.getValues().size() == 10) {
						assertEquals(10, result.getGeneratedKeys().size());
					} else {
						assertEquals(1, result.getGeneratedKeys().size());
					}
					GeneratedKeyInfo info = result.getGeneratedKeys().get(0);
					assertEquals("ID", info.getColumnName());
					assertEquals(1, result.getResult()[0]);
					for (int i = 0; i < result.getGeneratedKeys().size(); i++) {
						info = result.getGeneratedKeys().get(i);
						// ctx.put("ID", info.getValue());// INSERTした結果のIDを格納
					}
				} else {
					// GeneratedKeyInfo info = result.getGeneratedKeys().get(0);
					assertEquals(0, result.getGeneratedKeys().size());
					assertEquals(0, result.getResult()[0]);// IDが指定されていないので更新結果が0件
				}
				counter[0] = counter[0] + result.getValues().size();
				System.out.println("counter=" + result.getLastRowIndex() + ", generatedKeys.size="
						+ result.getGeneratedKeys().size() + ", result.result[0]=" + result.getResult()[0]);
			});
			handler.execute(connection, iterable);
			assertEquals(gen * 2, counter[0]);
		}, (connection) -> {
			this.dropTables(connection, "TABA");
		});
	}

	/**
	 * INSERTして自動生成されたキーを使って、そのままUPDATEを行う
	 * 
	 * @throws SQLException
	 */
	@Test
	void testInsertUpdateBatchSize1() throws SQLException {
		final String sql = this.getResource("create_table1.sql");
		SqlConverter con = new SqlConverter();
		final ParametersContext context = new ParametersContext();
		final String sql1 = this.getResource("insert_table1.sql");
		final String sql2 = this.getResource("update_table1.sql");
		final SqlNode sqlNode1 = con.parseSql(context, sql1);
		final SqlNode sqlNode2 = con.parseSql(context, sql2);
		List<SqlNode> sqlNodes = CommonUtils.list();
		sqlNodes.add(sqlNode1);
		sqlNodes.add(sqlNode2);
		testDb(connection -> {
			this.dropTables(connection, "TABA");
			executeSql(connection, sql);
			int gen = 99;
			final CountIterable<ParametersContext> iterable = new CountIterable<ParametersContext>(gen, l -> {
				ParametersContext ctx = new ParametersContext();
				ctx.put("TXT", "abc" + l);
				return ctx;
			});
			JdbcBatchIterateHander handler = new JdbcBatchIterateHander(sqlNodes, 1, 10);
			int[] counter = new int[1];
			handler.setBatchUpdateResultHandler(result -> {
				if (result.getSqlNode() == sqlNode1) {
					assertEquals(1, result.getGeneratedKeys().size());
					GeneratedKeyInfo info = result.getGeneratedKeys().get(0);
					assertEquals("ID", info.getColumnName());
					assertEquals(1, result.getResult()[0]);
					for (int i = 0; i < result.getGeneratedKeys().size(); i++) {
						info = result.getGeneratedKeys().get(i);
						// ctx.put("ID", info.getValue());// INSERTした結果のIDを格納
					}
				} else {
					// GeneratedKeyInfo info = result.getGeneratedKeys().get(0);
					assertEquals(0, result.getGeneratedKeys().size());
					assertEquals(0, result.getResult()[0]);// IDが指定されていないので更新結果が0件
				}
				counter[0] = counter[0] + result.getValues().size();
				System.out.println("counter=" + result.getLastRowIndex() + ", generatedKeys.size="
						+ result.getGeneratedKeys().size() + ", result.result[0]=" + result.getResult()[0]);
			});
			handler.execute(connection, iterable);
			assertEquals(gen * 2, counter[0]);
		}, (connection) -> {
			this.dropTables(connection, "TABA");
		});
	}
}
