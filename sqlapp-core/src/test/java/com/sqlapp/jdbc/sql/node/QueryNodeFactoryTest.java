package com.sqlapp.jdbc.sql.node;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.map;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

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
		String sql = "  /*query(fetchSize=10, resultSetType=TYPE_SCROLL_INSENSITIVE, resultSetConcurrency=CONCUR_READ_ONLY , resultSetHoldability=HOLD_CURSORS_OVER_COMMIT, generatedKey=NO_GENERATED_KEYS)*/ ";
		QueryNodeFactory factory = new QueryNodeFactory();
		Map<Integer, QueryNode> map = factory.parseSql(sql);
		List<QueryNode> list = list(map.values());
		int i = 0;
		QueryNode node = list.get(i++);
		Map<String, String> context = map();
		SqlParameterCollection sqlParameterCollection = new SqlParameterCollection();
		node.eval(context, sqlParameterCollection);
		assertEquals(ResultSetType.TYPE_SCROLL_INSENSITIVE, sqlParameterCollection.getResultSetType());
		assertEquals(ResultSetConcurrency.CONCUR_READ_ONLY, sqlParameterCollection.getResultSetConcurrency());
		assertEquals(ResultSetHoldability.HOLD_CURSORS_OVER_COMMIT, sqlParameterCollection.getResultSetHoldability());
		assertEquals(GeneratedKey.NO_GENERATED_KEYS, sqlParameterCollection.getGeneratedKey());
	}

}
