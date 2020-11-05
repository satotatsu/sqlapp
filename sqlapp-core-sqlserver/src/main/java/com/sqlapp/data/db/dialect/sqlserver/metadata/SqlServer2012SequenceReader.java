/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

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
 * SqlServer2012のシーケンス読み込み
 * 
 * @author satoh
 * 
 */
public class SqlServer2012SequenceReader extends SequenceReader {

	protected SqlServer2012SequenceReader(Dialect dialect) {
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
		return getSqlNodeCache().getString("sequences2012.sql");
	}

	protected Sequence createSequence(ExResultSet rs) throws SQLException {
		Sequence obj = new Sequence(getString(rs, SEQUENCE_NAME));
		obj.setDialect(this.getDialect());
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setDataTypeName(getString(rs, TYPE_NAME));
		obj.setLastValue(rs.getBigDecimal("current_value"));
		obj.setStartValue(rs.getBigDecimal("start_value"));
		obj.setIncrementBy(rs.getBigDecimal("increment"));
		obj.setMinValue(rs.getBigDecimal("minimum_value"));
		obj.setMaxValue(rs.getBigDecimal("maximum_value"));
		obj.setCycle(rs.getBoolean("is_cycling"));
		obj.setCache(rs.getBoolean("is_cached"));
		obj.setPrecision(getInteger(rs, "precision"));
		obj.setScale(getInteger(rs, "scale"));
		obj.setCacheSize(rs.getBigDecimal("cache_size"));
		obj.setCreatedAt(rs.getTimestamp("create_date"));
		obj.setLastAlteredAt(rs.getTimestamp("modify_date"));
		return obj;
	}

}
