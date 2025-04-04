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
			final CountIterable<ParametersContext> iterable = new CountIterable<ParametersContext>(125, l -> {
				ParametersContext ctx = new ParametersContext();
				ctx.put("TXT", "abc" + l);
				return ctx;
			});
			JdbcBatchIterateHander handler = new JdbcBatchIterateHander(sqlNodes, 10, 10, iterable);
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
						ParametersContext ctx = (ParametersContext) result.getValues().get(i);
						ctx.put("ID", info.getValue());// INSERTした結果のIDを格納
					}
				} else {
					// GeneratedKeyInfo info = result.getGeneratedKeys().get(0);
					assertEquals(0, result.getGeneratedKeys().size());
					assertEquals(1, result.getResult()[0]);// INSERTした結果のIDをWHERE条件にして更新して結果が件
				}
				System.out.println("counter=" + result.getCounter() + ", generatedKeys.size()="
						+ result.getGeneratedKeys().size() + ", result.getResult()[0]=" + result.getResult()[0]);
			});
			handler.execute(connection);
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
			final CountIterable<ParametersContext> iterable = new CountIterable<ParametersContext>(111, l -> {
				ParametersContext ctx = new ParametersContext();
				ctx.put("TXT", "abc" + l);
				return ctx;
			});
			JdbcBatchIterateHander handler = new JdbcBatchIterateHander(sqlNodes, 10, 10, iterable);
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
				System.out.println("counter=" + result.getCounter() + ", generatedKeys.size()="
						+ result.getGeneratedKeys().size() + ", result.getResult()[0]=" + result.getResult()[0]);
			});
			handler.execute(connection);
		}, (connection) -> {
			this.dropTables(connection, "TABA");
		});
	}

}
