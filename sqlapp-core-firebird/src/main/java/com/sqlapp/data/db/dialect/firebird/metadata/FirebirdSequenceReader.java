/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
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
	 * @param connection Connection
	 * @param sequence   Sequence
	 */
	protected void setMetadataDetail(Connection connection, Sequence sequence) throws SQLException {
		// Current values are fetched in batches by the collection overload.
	}

	@Override
	protected void setMetadataDetail(Connection connection, ParametersContext context, List<Sequence> sequences)
			throws SQLException {
		// GEN_ID requires a literal identifier, so build one projection per requested
		// sequence, in bounded batches below the legacy row/column limits.
		for (int offset = 0; offset < sequences.size(); offset += 500) {
			var batch = sequences.subList(offset, Math.min(offset + 500, sequences.size()));
			StringBuilder sql = new StringBuilder("SELECT ");
			for (int i = 0; i < batch.size(); i++) {
				if (i > 0)
					sql.append(',');
				sql.append("GEN_ID(\"").append(batch.get(i).getName().replace("\"", "\"\"")).append("\",0) AS VALUE_")
						.append(i);
			}
			sql.append(" FROM RDB$DATABASE");
			boolean[] found = { false };
			execute(connection, com.sqlapp.jdbc.sql.SqlParser.getInstance().parse(getDialect(), sql.toString()),
					context, new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							found[0] = true;
							for (int i = 0; i < batch.size(); i++)
								batch.get(i).setLastValue(rs.getBigDecimal("VALUE_" + i));
						}
					});
			if (!found[0])
				throw new SQLException("Firebird sequence current-value query returned no row");
		}
	}

	/**
	 * シーケンスの現在値の取得
	 * 
	 * @param connection   Connection
	 * @param sequenceName sequenceName
	 */
	protected BigDecimal getCurrentNumber(Connection connection, String sequenceName) {
		SqlExecuter sql = new SqlExecuter("SELECT GEN_ID(");
		sql.addSqlLine("\"" + sequenceName.replace("\"", "\"\"") + "\", 0) FROM RDB$DATABASE");
		return sql.executeScalar(connection, BigDecimal.class, 1);
	}
}
