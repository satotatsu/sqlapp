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

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;

/**
 * Postgresのユニーク制約読み込みクラス
 * 
 * @author satoh
 * 
 */
public class PostgresUniqueConstraintReader extends UniqueConstraintReader {

	public PostgresUniqueConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<UniqueConstraint> result = list();
		final TripleKeyMap<String, String, String, UniqueConstraint> map = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schema_name = getString(rs, "constraint_schema");
				String table_name = getString(rs, TABLE_NAME);
				String constraint_name = getString(rs, CONSTRAINT_NAME);
				UniqueConstraint c = map.get(schema_name, table_name, constraint_name);
				if (c == null) {
					boolean primary = !"unique".equalsIgnoreCase(getString(rs, "constraint_type"));
					c = new UniqueConstraint(constraint_name, primary);
					c.setSchemaName(schema_name);
					c.setTableName(table_name);
					c.setRemarks(getString(rs, "remarks"));
					c.setIndexName(getString(rs, "index_name"));
					if (rs.getBoolean("nulls_not_distinct")) {
						c.getSpecifics().put(com.sqlapp.data.db.dialect.postgres.sql.PostgresCreateIndexFactory.NULLS_NOT_DISTINCT, "true");
					}
					c.setDeferrability(Deferrability.getDeferrability(rs.getBoolean("is_deferrable"),
							rs.getBoolean("initially_deferred")));
					PostgresTemporalConstraintMetadata.apply(c, getString(rs, "consrc"));
					result.add(c);
					map.put(schema_name, table_name, constraint_name, c);
				}
				if (rs.getBoolean("is_included")) {
					c.getIndex().getIncludes().add(getString(rs, COLUMN_NAME));
					return;
				}
				Column column = new Column(getString(rs, COLUMN_NAME));
				column.setTableName(table_name);
				c.getColumns().add(column);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		if (productVersionInfo != null && productVersionInfo.getMajorVersion() != null) {
			if (productVersionInfo.getMajorVersion() >= 15) return getSqlNodeCache().getString("uniqueConstraints150.sql");
			if (productVersionInfo.getMajorVersion() >= 11) return getSqlNodeCache().getString("uniqueConstraints110.sql");
		}
		if (productVersionInfo != null && productVersionInfo.getMajorVersion() != null
				&& (productVersionInfo.getMajorVersion() > 8
						|| productVersionInfo.getMajorVersion() == 8 && productVersionInfo.getMinorVersion() != null
								&& productVersionInfo.getMinorVersion() >= 4)) {
			return getSqlNodeCache().getString("uniqueConstraints84.sql");
		}
		return getSqlNodeCache().getString("uniqueConstraints.sql");
	}

}
