/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.metadata;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.Postgres110FunctionReader;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.jdbc.ExResultSet;

/** Preserves executable SPL DDL, without per-routine type lookup SELECTs. */
public class EdbOracleFunctionReader extends Postgres110FunctionReader {
	protected EdbOracleFunctionReader(Dialect dialect) { super(dialect); }
	@Override protected Function createFunction(ExResultSet rs) throws SQLException {
		if (!"edbspl".equalsIgnoreCase(getString(rs,"lanname"))) return super.createFunction(rs);
		var function = new Function(getString(rs,"function_name"));
		function.setDialect(getDialect());
		function.setCatalogName(getString(rs,"function_catalog"));
		function.setSchemaName(getString(rs,"function_schema"));
		function.setLanguage("edbspl");
		function.setRemarks(getString(rs,"remarks"));
		function.setSpecificName(function.getName() + "(" + getString(rs,"function_identity_arguments") + ")");
		if ("p".equals(getString(rs,"prokind"))) function.getSpecifics().put("ROUTINE_KIND","PROCEDURE");
		if (getReaderOptions().isReadDefinition() || getReaderOptions().isReadStatement()) {
			String ddl = getString(rs,"functiondef");
			if (ddl == null || ddl.isBlank()) throw new SQLException("EPAS returned no executable SPL definition for " + function.getSpecificName());
			function.setDefinition(ddl);
		}
		return function;
	}
}
