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
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.DataTypeSetProperties;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.util.CommonUtils;

public class JdbcFunctionArgumentReader extends RoutineArgumentReader<Function> {

	private final List<NamedArgument> returning = list();
	private final List<NamedArgument> resultColumns = list();

	public JdbcFunctionArgumentReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<NamedArgument> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		ExResultSet rs = null;
		returning.clear();
		resultColumns.clear();
		try {
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			rs = new ExResultSet(databaseMetaData.getFunctionColumns(
					CommonUtils.coalesce(emptyToNull(this.getCatalogName(context)), emptyToNull(this.getCatalogName())),
					CommonUtils.coalesce(emptyToNull(this.getSchemaName(context)), emptyToNull(this.getSchemaName())),
					CommonUtils.coalesce(emptyToNull(this.getObjectName(context)), emptyToNull(this.getObjectName())),
					null));
			List<NamedArgument> result = list();
			while (rs.next()) {
				NamedArgument obj = createNamedArgument(rs);
				int type = rs.getInt("COLUMN_TYPE");
				if (type == DatabaseMetaData.functionReturn) {
					returning.add(obj);
				} else if (type == DatabaseMetaData.functionColumnResult) {
					resultColumns.add(obj);
				} else {
					result.add(obj);
				}
			}
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(rs);
		}
	}

	protected NamedArgument createNamedArgument(ExResultSet rs) throws SQLException {
		String catalog_name = getString(rs, "FUNCTION_CAT");
		String schema_name = getString(rs, "FUNCTION_SCHEM");
		Function routine = new Function(getString(rs, FUNCTION_NAME));
		routine.setDialect(this.getDialect());
		routine.setSpecificName(getString(rs, SPECIFIC_NAME));
		//
		Long length = rs.getLongValue("PRECISION");
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
		this.getDialect().setDbType(sqlType, productDataType, length, scale, obj);
		obj.setNullable(allowDBNull);
		obj.setOctetLength(rs.getLongValue("CHAR_OCTET_LENGTH"));
		obj.setDirection(switch (rs.getInt("COLUMN_TYPE")) {
		case DatabaseMetaData.functionColumnIn -> ParameterDirection.Input;
		case DatabaseMetaData.functionColumnOut -> ParameterDirection.Output;
		case DatabaseMetaData.functionColumnInOut -> ParameterDirection.Inout;
		case DatabaseMetaData.functionReturn, DatabaseMetaData.functionColumnResult -> ParameterDirection.Output;
		default -> throw new SQLException("Unknown JDBC function column kind for " + routine.getName());
		});
		obj.setRemarks(getString(rs, "REMARKS"));
		return obj;
	}

	@Override
	public void loadFull(Connection connection, Function function) {
		super.loadFull(connection, function);
		applyReturning(function);
	}

	@Override
	public void load(Connection connection, Function function) {
		super.load(connection, function);
		applyReturning(function);
	}

	void applyReturning(Function function) {
		for (NamedArgument value : returning) {
			if (belongsTo(value, function)) {
				copyType(value, function.getReturning());
			}
		}
		for (NamedArgument value : resultColumns) {
			if (belongsTo(value, function)) {
				function.setFunctionType(FunctionType.Table);
				function.getReturning().toTable();
				Column column = new Column(value.getName());
				copyType(value, column);
				column.setNotNull(value.isNotNull());
				column.setRemarks(value.getRemarks());
				function.getReturning().getTable().getColumns().add(column);
			}
		}
	}

	private boolean belongsTo(NamedArgument argument, Function function) {
		return CommonUtils.eq(argument.getCatalogName(), function.getCatalogName())
				&& CommonUtils.eq(argument.getSchemaName(), function.getSchemaName())
				&& CommonUtils.eq(argument.getRoutine().getName(), function.getName())
				&& CommonUtils.eq(argument.getRoutine().getSpecificName(), function.getSpecificName());
	}

	private void copyType(NamedArgument source, DataTypeSetProperties<?> target) {
		target.setDataType(source.getDataType());
		target.setDataTypeName(source.getDataTypeName());
		target.setLength(source.getLength());
		target.setScale(source.getScale());
		target.setOctetLength(source.getOctetLength());
		target.setArrayDimension(source.getArrayDimension());
		target.setArrayDimensionLowerBound(source.getArrayDimensionLowerBound());
		target.setArrayDimensionUpperBound(source.getArrayDimensionUpperBound());
	}

}
