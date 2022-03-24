/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-derby.
 *
 * sqlapp-core-derby is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-derby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-derby.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.derby.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Derbyのユニーク制約読み込みクラス
 * 
 * @author satoh
 * 
 */
public class DerbyUniqueConstraintReader extends UniqueConstraintReader {

	public DerbyUniqueConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(final Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<UniqueConstraint> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				UniqueConstraint uc = createUniqueConstraint(connection, rs);
				result.add(uc);
			}
		});
		return result;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("uniqueConstraints.sql");
	}

	protected UniqueConstraint createUniqueConstraint(Connection connection,
			ExResultSet rs) throws SQLException {
		String catalogName = null;
		String schemaName = getString(rs, SCHEMA_NAME);
		String constraintName = getString(rs, CONSTRAINT_NAME);
		String tableName = getString(rs, TABLE_NAME);
		String type = getString(rs, "TYPE");
		String indexName = getString(rs, INDEX_NAME);
		String columnInfo = getString(rs, "index_info");
		Index index = DerbyUtils.parseIndexDescriptor(connection, getDialect(),
				schemaName, tableName, indexName, columnInfo);
		UniqueConstraint uc = new UniqueConstraint(constraintName);
		if ("P".equalsIgnoreCase(type)) {
			uc.setPrimaryKey(true);
		}
		uc.setEnable("E".equalsIgnoreCase(getString(rs, "state")));
		uc.setCatalogName(catalogName);
		uc.setSchemaName(schemaName);
		uc.getColumns().addAll(index.getColumns());
		return uc;
	}
}
