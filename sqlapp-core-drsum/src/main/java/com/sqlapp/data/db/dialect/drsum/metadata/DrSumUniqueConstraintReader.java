/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-drsum.
 *
 * sqlapp-core-drsum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-drsum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-drsum.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.drsum.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Reads Dr.Sum single-column UNIQUE constraints from
 * {@code __all_tables__.column_unique}. Dr.Sum column constraints are unnamed,
 * so the constraint is named {@code <table>_<column>_UK}. COMPOUND KEY
 * definitions are not exposed by the documented system tables and are not
 * read.
 */
public class DrSumUniqueConstraintReader extends UniqueConstraintReader {

	protected DrSumUniqueConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<UniqueConstraint> result = list();
		execute(connection, getSqlNodeCache().getString("uniqueConstraints.sql"), context,
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(final ExResultSet rs) throws SQLException {
						final String table = getString(rs, "table_name");
						final String column = getString(rs, "column_name");
						if (getReaderOptions().isExcludeSystemObjects()
								&& DrSumMetadataSupport.isSystemTable(table)) {
							return;
						}
						if (!DrSumMetadataSupport.flag(getString(rs, "column_unique"), "column_unique", table,
								column)) {
							return;
						}
						final UniqueConstraint constraint = new UniqueConstraint(table + "_" + column + "_UK", false);
						constraint.setTableName(table);
						constraint.getColumns().add(column);
						result.add(constraint);
					}
				});
		return result;
	}
}
