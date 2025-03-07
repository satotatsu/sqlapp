/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;

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
import com.sqlapp.jdbc.sql.SqlParser;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Postgresのシーケンス読み込み
 * 
 * @author satoh
 * 
 */
public class PostgresSequenceReader extends SequenceReader {

	protected PostgresSequenceReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Sequence> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final Dialect dbDialact = this.getDialect();
		final List<Sequence> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Sequence sequence = new Sequence(getString(rs, SEQUENCE_NAME));
				sequence.setCatalogName(getString(rs, "SEQUENCE_CATALOG"));
				sequence.setSchemaName(getString(rs, "SEQUENCE_SCHEMA"));
				sequence.setDialect(dbDialact);
				result.add(sequence);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("sequences.sql");
	}

	/**
	 * PostgresでINFORMATION_SCHEMAから取得できない情報を設定
	 * 
	 * @param connection
	 *            DBコネクション
	 * @param sequence
	 *            シーケンスオブジェクト
	 */
	@Override
	protected void setMetadataDetail(Connection connection,
			final Sequence sequence) throws SQLException {
		StringBuilder tableName = new StringBuilder(64);
		if (!isEmpty(sequence.getSchemaName())) {
			if (!isEmpty(sequence.getCatalogName())) {
				tableName.append(sequence.getCatalogName());
				tableName.append(".");
				tableName.append(sequence.getSchemaName());
				tableName.append(".");
			} else {
				if (!"public".equalsIgnoreCase(sequence.getSchemaName())) {
					tableName.append(sequence.getSchemaName());
					tableName.append(".");
				}
			}
		}
		tableName.append(sequence.getName());
		StringBuilder sql = new StringBuilder("SELECT * ");
		sql.append("FROM " + tableName.toString());
		SqlNode node = SqlParser.getInstance().parse(sql.toString());
		ParametersContext context = newParametersContext(connection, null, null);
		final Dialect dialact = this.getDialect();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				sequence.setMinValue(rs.getBigDecimal("min_value"));
				sequence.setMaxValue(rs.getBigDecimal("max_value"));
				sequence.setStartValue(rs.getBigDecimal("start_value"));
				sequence.setLastValue(rs.getBigDecimal("last_value"));
				sequence.setIncrementBy(rs.getBigDecimal("increment_by"));
				sequence.setCacheSize(rs.getBigDecimal("cache_value"));
				sequence.setCycle("t".equalsIgnoreCase(getString(rs,
						"is_cycled")));
				sequence.setDialect(dialact);
			}
		});
	}

}
