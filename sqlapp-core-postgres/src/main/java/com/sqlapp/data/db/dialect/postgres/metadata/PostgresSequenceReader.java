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
import com.sqlapp.data.db.datatype.DataType;
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
	protected List<Sequence> doGetAll(Connection connection, ParametersContext context,
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
				sequence.setRemarks(getString(rs, "remarks"));
				if (modern(productVersionInfo)) {
					sequence.setDataType(switch (getString(rs, "sequence_type")) {
					case "smallint" -> DataType.SMALLINT;
					case "integer" -> DataType.INT;
					default -> DataType.BIGINT;
					});
					sequence.setStartValue(rs.getBigDecimal("start_value"));
					sequence.setMinValue(rs.getBigDecimal("min_value"));
					sequence.setMaxValue(rs.getBigDecimal("max_value"));
					sequence.setIncrementBy(rs.getBigDecimal("increment_by"));
					sequence.setCacheSize(rs.getBigDecimal("cache_size"));
					sequence.setCycle(rs.getBoolean("cycle"));
					sequence.setLastValue(rs.getBigDecimal("last_value"));
				}
				result.add(sequence);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo version) {
		return getSqlNodeCache().getString(modern(version) ? "sequences100.sql" : "sequences.sql");
	}

	private static boolean modern(ProductVersionInfo version) {
		return version != null && version.getMajorVersion() != null && version.getMajorVersion() >= 10;
	}

	@Override
	protected void setMetadataDetail(Connection connection, ParametersContext context, List<Sequence> sequences)
			throws SQLException {
		if (sequences.isEmpty() || sequences.get(0).getStartValue() != null) return;
		// Pre-10 configuration lives in each sequence relation. Batch the dynamic
		// relation names into one query; never interpolate an unquoted identifier.
		var parts = new java.util.ArrayList<String>();
		for (int i = 0; i < sequences.size(); i++) {
			Sequence sequence = sequences.get(i);
			String name = getDialect().quote(sequence.getName());
			if (!isEmpty(sequence.getSchemaName())) name = getDialect().quote(sequence.getSchemaName()) + "." + name;
			parts.add("SELECT " + i + " AS position,min_value,max_value,start_value,last_value,increment_by,cache_value,is_cycled FROM " + name);
		}
		try (var sql = connection.createStatement(); var rows = sql.executeQuery(String.join(" UNION ALL ", parts))) {
			while (rows.next()) {
				Sequence sequence = sequences.get(rows.getInt("position"));
				sequence.setMinValue(rows.getBigDecimal("min_value"));
				sequence.setMaxValue(rows.getBigDecimal("max_value"));
				sequence.setStartValue(rows.getBigDecimal("start_value"));
				sequence.setLastValue(rows.getBigDecimal("last_value"));
				sequence.setIncrementBy(rows.getBigDecimal("increment_by"));
				sequence.setCacheSize(rows.getBigDecimal("cache_value"));
				sequence.setCycle(rows.getBoolean("is_cycled"));
			}
		}
	}

	@Override
	protected void setMetadataDetail(Connection connection, Sequence sequence) throws SQLException {
		// All detail is populated in the set-based read above.
	}
}
