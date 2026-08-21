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
import com.sqlapp.data.db.metadata.ProcedureReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads Informix procedures and their complete CREATE text. */
public class InformixProcedureReader extends ProcedureReader {
	public InformixProcedureReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Procedure> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("procedures.sql");
		Map<Integer, RoutineText> routines = new LinkedHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				int id = rs.getInt("routine_id");
				RoutineText routineText = routines.get(id);
				if (routineText == null) {
					Procedure procedure = new Procedure(getString(rs, ROUTINE_NAME));
					procedure.setSpecificName(getString(rs, SPECIFIC_NAME));
					procedure.setCatalogName(getString(rs, CATALOG_NAME));
					procedure.setSchemaName(getString(rs, SCHEMA_NAME));
					routineText = new RoutineText(procedure);
					routines.put(id, routineText);
				}
				String text = getString(rs, "routine_definition");
				if (text != null) {
					routineText.text.append(text);
				}
			}
		});
		List<Procedure> result = list();
		routines.values().forEach(routineText -> {
			String text = routineText.text.toString();
			if (getReaderOptions().isReadDefinition()) {
				routineText.procedure.setDefinition(text);
			}
			if (getReaderOptions().isReadStatement()) {
				routineText.procedure.setStatement(text);
			}
			InformixRoutineUtils.setArguments(routineText.procedure, text);
			result.add(routineText.procedure);
		});
		return result;
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return null;
	}

	private static class RoutineText {
		private final Procedure procedure;
		private final StringBuilder text = new StringBuilder();

		private RoutineText(final Procedure procedure) {
			this.procedure = procedure;
		}
	}
}
