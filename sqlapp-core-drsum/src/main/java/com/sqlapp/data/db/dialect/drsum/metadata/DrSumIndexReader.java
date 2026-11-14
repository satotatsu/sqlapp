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
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Reads Dr.Sum search indexes from {@code __all_indexes__} (one column each).
 * The documented status ({@code 0} disabled, {@code 1} enabled) is kept in the
 * {@value #STATUS} specific. Join indexes ({@code __all_join_indexes__}) are not
 * modeled by this dialect.
 */
public class DrSumIndexReader extends IndexReader {

	/** Specifics key holding the raw {@code __all_indexes__.status} value. */
	public static final String STATUS = "drsum.status";

	protected DrSumIndexReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Index> result = list();
		execute(connection, getSqlNodeCache().getString("indexes.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Index index = new Index(getString(rs, "index_name"));
				index.setTableName(getString(rs, "table_name"));
				index.getColumns().add(getString(rs, "column_name"));
				final String status = getString(rs, "status");
				if (status != null) {
					index.getSpecifics().put(STATUS, status);
				}
				result.add(index);
			}
		});
		return result;
	}
}
