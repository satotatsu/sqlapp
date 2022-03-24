/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.firebird.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.trim;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SequenceReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.SqlExecuter;

public class FirebirdSequenceReader extends SequenceReader {

	protected FirebirdSequenceReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Sequence> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Sequence> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Sequence sequence = createSequence(rs);
				result.add(sequence);
			}
		});
		return result;
	}

	protected Sequence createSequence(ExResultSet rs) throws SQLException {
		String name = trim(getString(rs, "RDB$GENERATOR_NAME"));
		Sequence sequence = new Sequence(name);
		sequence.setCycle(false);
		return sequence;
	}

	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("sequences.sql");
	}

	/**
	 * メタデータの詳細情報を設定するためのメソッドです。子クラスでのオーバーライドを想定しています。
	 * 
	 * @param connection
	 * @param obj
	 */
	protected void setMetadataDetail(Connection connection, Sequence sequence) throws SQLException {
		BigDecimal lastNumber = getCurrentNumber(connection, sequence.getName());
		sequence.setLastValue(lastNumber);
	}

	/**
	 * シーケンスの現在値の取得
	 * 
	 * @param connection
	 * @param sequenceName
	 */
	protected BigDecimal getCurrentNumber(Connection connection, String sequenceName) {
		SqlExecuter sql = new SqlExecuter("SELECT GEN_ID(");
		sql.addSqlLine(sequenceName + ", 0) FROM RDB$DATABASE");
		return sql.executeScalar(connection, BigDecimal.class, 1);
	}
}
