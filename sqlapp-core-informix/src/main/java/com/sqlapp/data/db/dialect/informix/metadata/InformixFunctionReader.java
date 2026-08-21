/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.FunctionReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads Informix functions and their complete CREATE text. */
public class InformixFunctionReader extends FunctionReader {
	public InformixFunctionReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Function> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("functions.sql");
		Map<Integer, RoutineText> routines = new LinkedHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				int id = rs.getInt("routine_id");
				RoutineText routineText = routines.get(id);
				if (routineText == null) {
					Function function = new Function(getString(rs, ROUTINE_NAME));
					function.setSpecificName(getString(rs, SPECIFIC_NAME));
					function.setCatalogName(getString(rs, CATALOG_NAME));
					function.setSchemaName(getString(rs, SCHEMA_NAME));
					routineText = new RoutineText(function);
					routines.put(id, routineText);
				}
				String text = getString(rs, "routine_definition");
				if (text != null) {
					routineText.text.append(text);
				}
			}
		});
		List<Function> result = list();
		routines.values().forEach(routineText -> {
			String text = routineText.text.toString();
			if (getReaderOptions().isReadDefinition()) {
				routineText.function.setDefinition(text);
			}
			if (getReaderOptions().isReadStatement()) {
				routineText.function.setStatement(text);
			}
			InformixRoutineUtils.setArguments(routineText.function, text);
			result.add(routineText.function);
		});
		return result;
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return null;
	}

	private static class RoutineText {
		private final Function function;
		private final StringBuilder text = new StringBuilder();

		private RoutineText(final Function function) {
			this.function = function;
		}
	}
}
