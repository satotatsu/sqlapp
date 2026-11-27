/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ConstantReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Constant;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Schema global variables use the shared named-object model and native
 * specifics.
 */
public class Db2GlobalVariableReader extends ConstantReader {
	public Db2GlobalVariableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Constant> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		if (!(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110))
			throw new IllegalArgumentException("Db2 global variable reader requires LUW 11.1 or later");
		List<Constant> result = new ArrayList<>();
		execute(connection, getSqlNodeCache().getString("globalVariables.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.add(createVariable(rs));
			}
		});
		return result;
	}

	protected Constant createVariable(ExResultSet rs) throws SQLException {
		Constant variable = new Constant(getString(rs, "VARNAME"));
		variable.setSchemaName(getString(rs, "VARSCHEMA"));
		variable.setDialect(getDialect());
		variable.setRemarks(rs.getString("REMARKS"));
		variable.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
		variable.getSpecifics().put("DB2_OBJECT_TYPE", "VARIABLE");
		for (String key : List.of("TYPESCHEMA", "TYPENAME", "TYPEMODULENAME", "LENGTH", "SCALE", "CODEPAGE",
				"TYPESTRINGUNITS", "STRINGUNITSLENGTH", "SCOPE", "READONLY", "QUALIFIER", "FUNC_PATH",
				"COLLATIONSCHEMA", "COLLATIONNAME")) {
			String value = rs.getString(key);
			if (value != null)
				variable.getSpecifics().put(key, value);
		}
		String expression = rs.getString("DEFAULT_EXPRESSION");
		if (expression != null)
			variable.getStatement().add(expression);
		return variable;
	}
}
