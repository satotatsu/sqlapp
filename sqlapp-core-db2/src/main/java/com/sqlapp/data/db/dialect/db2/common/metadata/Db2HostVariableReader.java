/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.common.AbstractDb2HostDialect;
import com.sqlapp.data.db.dialect.db2.common.Db2HostTables;
import com.sqlapp.data.db.dialect.db2.common.Db2HostVariables;
import com.sqlapp.data.db.metadata.ConstantReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Constant;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Global variables from SYSIBM.SYSVARIABLES (z/OS) or QSYS2.SYSVARIABLES (IBM
 * i). Variables whose definition cannot be recreated faithfully (z/OS string
 * constant defaults whose catalog quoting is undocumented, IBM i read-only
 * variables, unknown default codes) fail explicitly.
 */
final class Db2HostVariableReader extends ConstantReader {
	private static final Set<DataType> CHARACTER = Set.of(DataType.CHAR, DataType.VARCHAR, DataType.CLOB,
			DataType.NCHAR, DataType.NVARCHAR, DataType.NCLOB);
	private final Class<?> resources;

	Db2HostVariableReader(Dialect dialect, Class<?> resources) {
		super(dialect);
		this.resources = resources;
	}

	@Override
	protected List<Constant> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		List<Constant> result = new ArrayList<>();
		execute(connection, getSqlNodeCache(resources).getString("variables.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.add(variable(rs));
			}
		});
		return result;
	}

	private Constant variable(ExResultSet rs) throws SQLException {
		var variable = new Constant(required(rs, "VARIABLE_NAME"));
		variable.setSchemaName(required(rs, "SCHEMA_NAME"));
		variable.setDialect(getDialect());
		String unsupported = rs.getString("UNSUPPORTED_VARIABLE");
		if (unsupported != null && !unsupported.isBlank())
			throw new SQLException(
					"Unsupported Db2 host global variable " + variable.getName() + " (" + unsupported.strip() + ")");
		var fields = variable.getSpecifics();
		fields.put(Db2HostVariables.OBJECT_TYPE, Db2HostVariables.VARIABLE);
		String udtSchema = id(rs, "UDT_SCHEMA"), udtName = id(rs, "UDT_NAME");
		if (udtSchema != null || udtName != null) {
			if (udtSchema == null || udtName == null)
				throw new SQLException("Incomplete user-defined type of variable " + variable.getName());
			fields.put(Db2HostVariables.UDT_SCHEMA, udtSchema);
			fields.put(Db2HostVariables.UDT_NAME, udtName);
		} else {
			String type = id(rs, "TYPE_NAME");
			if (type == null)
				throw new SQLException("Missing data type of variable " + variable.getName());
			type = switch (type) {
			case "VARGRAPH" -> "VARGRAPHIC";
			case "VARBIN" -> "VARBINARY";
			case "TIMESTMP" -> "TIMESTAMP";
			default -> type;
			};
			fields.put(Db2HostVariables.TYPE_NAME, type);
			Long length = rs.getLongValue("TYPE_LENGTH");
			Integer scale = rs.getInteger("TYPE_SCALE");
			if (length != null)
				fields.put(Db2HostVariables.TYPE_LENGTH, length.toString());
			if (scale != null && !"TIMESTAMP".equals(type))
				fields.put(Db2HostVariables.TYPE_SCALE, scale.toString());
			DataType dataType;
			try {
				dataType = Db2HostVariables.column(variable, getDialect()).getDataType();
			} catch (IllegalArgumentException e) {
				throw new SQLException(e.getMessage(), e);
			}
			Integer ccsid = rs.getInteger("VARIABLE_CCSID");
			if (ccsid != null && ccsid == 65535 && CHARACTER.contains(dataType)) {
				if (dataType != DataType.CHAR && dataType != DataType.VARCHAR)
					throw new SQLException("CCSID 65535 requires CHAR or VARCHAR: " + variable.getName());
				fields.put("CODEPAGE", "0");
			} else if (ccsid != null && ccsid > 0 && CHARACTER.contains(dataType)
					&& ((AbstractDb2HostDialect) getDialect()).getPlatform() == AbstractDb2HostDialect.Platform.IBM_I)
				fields.put(Db2HostTables.CCSID, ccsid.toString());
		}
		String defaultSql = rs.getString("DEFAULT_SQL");
		if (defaultSql != null && !defaultSql.isBlank())
			variable.getStatement().add(defaultSql.strip());
		variable.setRemarks(rs.getString("REMARKS"));
		if (Db2HostSupplementalReaders.has(rs, "CREATED_AT"))
			variable.setCreatedAt(rs.getTimestamp("CREATED_AT"));
		return variable;
	}

	private static String required(ExResultSet rs, String name) throws SQLException {
		String value = id(rs, name);
		if (value == null)
			throw new SQLException("Missing host variable field: " + name);
		return value;
	}

	private static String id(ExResultSet rs, String name) throws SQLException {
		String value = rs.getString(name);
		return value == null || value.isBlank() ? null : value.stripTrailing();
	}
}
