/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-derby.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.derby.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Derbyの外部キー制約読み込みクラス
 * 
 * @author satoh
 * 
 */
public class DerbyForeignKeyConstraintReader extends ForeignKeyConstraintReader {

	public DerbyForeignKeyConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ForeignKeyConstraint> doGetAll(final Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<ForeignKeyConstraint> list = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				ForeignKeyConstraint c = createForeignKeyConstraint(connection, rs);
				list.add(c);
			}
		});
		return list;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("foreignKeyConstraints.sql");
	}

	protected ForeignKeyConstraint createForeignKeyConstraint(Connection connection, ExResultSet rs)
			throws SQLException {
		String pk_table_schema = getString(rs, "PKTABLE_SCHEMA");
		String pk_table_name = getString(rs, "PKTABLE_NAME");
		String fk_table_schema = getString(rs, "FKTABLE_SCHEMA");
		String fk_table_name = getString(rs, "FKTABLE_NAME");
		String fk_name = getString(rs, "FK_NAME");
		// String pk_name = getString(rs, "PK_NAME");
		Index pk_index = DerbyUtils.parseIndexDescriptor(connection, getDialect(), pk_table_schema, pk_table_name,
				"dummy", getString(rs, "pkcols"));
		Index fk_index = DerbyUtils.parseIndexDescriptor(connection, getDialect(), fk_table_schema, fk_table_name,
				"dummy", getString(rs, "fkcols"));
		ForeignKeyConstraint c = new ForeignKeyConstraint(fk_name);
		c.setSchemaName(pk_table_schema);
		c.setTableName(pk_table_name);
		c.setUpdateRule(DerbyUtils.getCascadeRule(getString(rs, UPDATE_RULE)));
		c.setDeleteRule(DerbyUtils.getCascadeRule(getString(rs, DELETE_RULE)));
		c.addColumns(pk_index.getColumns().toColumns());
		c.addRelatedColumns(fk_index.getColumns().toColumns());
		return c;
	}
}
