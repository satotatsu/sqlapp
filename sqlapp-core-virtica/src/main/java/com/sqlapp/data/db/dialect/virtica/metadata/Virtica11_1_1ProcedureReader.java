/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ProcedureReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads USER_PROCEDURES, available in Vertica 11.1.1 and later. */
public class Virtica11_1_1ProcedureReader extends ProcedureReader {

	private static final Pattern ARGUMENT_PATTERN = Pattern.compile(
			"\\s*(?:(INOUT|IN|OUT)\\s+)?(\\S+)\\s+(.+)", Pattern.CASE_INSENSITIVE);

	protected Virtica11_1_1ProcedureReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Procedure> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("procedures11_1_1.sql");
		List<Procedure> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				result.add(createProcedure(rs));
			}
		});
		return result;
	}

	protected Procedure createProcedure(ExResultSet rs) throws SQLException {
		Procedure obj = new Procedure(getString(rs, PROCEDURE_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		String arguments = getString(rs, "PROCEDURE_ARGUMENTS");
		obj.setSpecificName(obj.getName() + "(" + (arguments == null ? "" : arguments.trim()) + ")");
		obj.setLanguage(getString(rs, "LANGUAGE"));
		obj.setSqlSecurity(getString(rs, "SECURITY"));
		setSpecifics(rs, "OWNER", obj);
		VirticaFunctionReader splitter = new VirticaFunctionReader(this.getDialect());
		for (String argumentText : splitter.splitArguments(arguments)) {
			obj.getArguments().add(createArgument(argumentText));
		}
		return obj;
	}

	protected NamedArgument createArgument(String text) {
		NamedArgument argument = new NamedArgument();
		Matcher matcher = ARGUMENT_PATTERN.matcher(text);
		if (matcher.matches()) {
			argument.setName(matcher.group(2));
			if (matcher.group(1) != null) {
				argument.setDirection(matcher.group(1));
			}
			this.getDialect().setDbType(matcher.group(3), null, null, argument);
		}
		return argument;
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return null;
	}
}
