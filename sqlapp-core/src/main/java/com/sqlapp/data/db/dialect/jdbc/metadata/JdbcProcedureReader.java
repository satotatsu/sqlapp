/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.jdbc.metadata;

import static com.sqlapp.util.CommonUtils.emptyToNull;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.DbUtils.close;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ProcedureReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.util.CommonUtils;

/**
 * 汎用のプロシージャ読み込みクラス
 * 
 * @author satoh
 * 
 */
public class JdbcProcedureReader extends ProcedureReader {

	public JdbcProcedureReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Procedure> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		ExResultSet rs = null;
		try {
			final DatabaseMetaData databaseMetaData = connection.getMetaData();
			rs = new ExResultSet(databaseMetaData.getProcedures(CommonUtils.coalesce(emptyToNull(this.getCatalogName(context)), emptyToNull(this.getCatalogName()))
					,CommonUtils.coalesce(emptyToNull(this.getSchemaName(context)), emptyToNull(this.getSchemaName()))
					,CommonUtils.coalesce(emptyToNull(this.getObjectName(context)), emptyToNull(this.getObjectName()))));
			final List<Procedure> result = list();
			while (rs.next()) {
				final Procedure obj = createProcedure(rs);
				result.add(obj);
			}
			return result;
		} catch (final SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(rs);
		}
	}

	protected Procedure createProcedure(final ExResultSet rs) throws SQLException {
		final String catalog_name = getString(rs, "PROCEDURE_CAT");
		final String schema_name = getString(rs, "PROCEDURE_SCHEM");
		final String procedure_name = getString(rs, "PROCEDURE_NAME");
		final String specific_name = getString(rs, "SPECIFIC_NAME");
		final Procedure obj = new Procedure(procedure_name);
		obj.setCatalogName(catalog_name);
		obj.setSchemaName(schema_name);
		obj.setSpecificName(specific_name);
		obj.setRemarks(getString(rs, "REMARKS"));
		return obj;
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new JdbcProcedureArgumentReader(this.getDialect());
	}
}
