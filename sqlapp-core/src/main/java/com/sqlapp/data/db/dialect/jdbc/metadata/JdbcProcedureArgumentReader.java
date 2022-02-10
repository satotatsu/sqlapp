/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
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
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.util.CommonUtils;

public class JdbcProcedureArgumentReader extends
		RoutineArgumentReader<Procedure> {

	public JdbcProcedureArgumentReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<NamedArgument> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		ExResultSet rs = null;
		try {
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			rs = new ExResultSet(databaseMetaData.getProcedureColumns(CommonUtils.coalesce(emptyToNull(this.getCatalogName(context)), emptyToNull(this.getCatalogName()))
					,CommonUtils.coalesce(emptyToNull(this.getSchemaName(context)), emptyToNull(this.getSchemaName()))
					,CommonUtils.coalesce(emptyToNull(this.getObjectName(context)), emptyToNull(this.getObjectName())), null));
			List<NamedArgument> result = list();
			while (rs.next()) {
				NamedArgument obj = createNamedArgument(rs);
				result.add(obj);
			}
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(rs);
		}
	}

	protected NamedArgument createNamedArgument(ExResultSet rs)
			throws SQLException {
		String catalog_name = getString(rs, "PROCEDURE_CAT");
		String schema_name = getString(rs, "PROCEDURE_SCHEM");
		Procedure routine = new Procedure(getString(rs, "PROCEDURE_NAME"));
		routine.setDialect(this.getDialect());
		routine.setSpecificName(getString(rs, "SPECIFIC_NAME"));
		//
		Long length = rs.getLongValue("LENGTH");
		if (length==null) {
			length = rs.getLongValue("PRECISION");
		}
		Integer scale = rs.getInteger("SCALE");
		int nullable = rs.getInt("NULLABLE");
		int sqlType = rs.getInt("DATA_TYPE");
		NamedArgument obj = createObject(getString(rs, "COLUMN_NAME"));
		SchemaUtils.setRoutine(obj, routine);
		obj.setCatalogName(catalog_name);
		obj.setSchemaName(schema_name);
		String productDataType = getString(rs, "TYPE_NAME");
		boolean allowDBNull = false;
		if (nullable != DatabaseMetaData.functionNullableUnknown) {
			if (nullable == DatabaseMetaData.functionNullable) {
				allowDBNull = true;
			}
		}
		this.getDialect().setDbType(sqlType, productDataType, length,
				scale, obj);
		obj.setNullable(allowDBNull);
		obj.setRemarks(getString(rs, "REMARKS"));
		return obj;
	}
}
