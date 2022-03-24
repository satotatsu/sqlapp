/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.h2.metadata;

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
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * H2のシーケンス読み込み
 * 
 * @author satoh
 * 
 */
public class H2SequenceReader extends SequenceReader {

	protected H2SequenceReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Sequence> doGetAll(Connection connection,
			ParametersContext context,
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

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("sequences.sql");
	}

	protected Sequence createSequence(ExResultSet rs) throws SQLException {
		Sequence obj = new Sequence(getString(rs, SEQUENCE_NAME));
		obj.setDialect(this.getDialect());
		obj.setCatalogName(getString(rs, "SEQUENCE_CATALOG"));
		obj.setSchemaName(getString(rs, "SEQUENCE_SCHEMA"));
		obj.setLastValue(rs.getBigDecimal("CURRENT_VALUE"));
		obj.setIncrementBy(rs.getBigDecimal("INCREMENT"));
		obj.setCacheSize(rs.getBigDecimal("CACHE"));
		this.setStatistics(rs, "IS_GENERATED", obj);
		return obj;
	}
}
