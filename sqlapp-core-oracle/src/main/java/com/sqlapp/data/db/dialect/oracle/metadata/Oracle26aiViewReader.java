/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.SQLException;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.util.JsonUtils;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.sql.OracleJsonDualityViewUtils;
import com.sqlapp.data.schemas.View;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Oracle 26ai duality metadata including logical replication state. */
public class Oracle26aiViewReader extends Oracle23aiViewReader {
	protected Oracle26aiViewReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getDualitySqlNode() {
		return getSqlNodeCache().getString("jsonRelationalDualityViews26ai.sql");
	}

	@Override
	protected void setMetadataDetail(Connection connection, ParametersContext context, List<Table> views)
			throws SQLException {
		super.setMetadataDetail(connection, context, views);
		loadDualityDetails(connection, views, "jsonDualityTables26ai.sql", OracleJsonDualityViewUtils.TABLE_DETAILS,
				List.of("TABLE_OWNER", "TABLE_NAME", "TABLE_NUMBER", "PARENT_TABLE_NUMBER", "RELATIONSHIP",
						"WHERE_CLAUSE", "ROOT_TABLE", "HAS_FLEX_COL", "READ_ONLY", "ALLOW_INSERT", "ALLOW_UPDATE",
						"ALLOW_DELETE"));
		loadDualityDetails(connection, views, "jsonDualityColumns26ai.sql", OracleJsonDualityViewUtils.COLUMN_DETAILS,
				List.of("TABLE_OWNER", "TABLE_NAME", "TABLE_NUMBER", "COLUMN_NAME", "DATA_TYPE", "LENGTH",
						"JSON_KEY_NAME", "ROOT_TABLE", "READ_ONLY", "IS_FLEX_COL", "IS_GENERATED", "IS_HIDDEN",
						"PRIMARY_KEY_POS", "ETAG_POS", "ORDER_BY_POS", "ALLOW_INSERT", "ALLOW_UPDATE", "ALLOW_DELETE"));
	}

	protected void loadDualityDetails(Connection connection, List<Table> views, String query, String key,
			List<String> columns) {
		Map<List<String>, View> targets = new LinkedHashMap<>();
		for (Table table : views) {
			if (table instanceof View view && OracleJsonDualityViewUtils.isJsonRelationalDualityView(view)) {
				targets.put(List.of(view.getSchemaName(), view.getName()), view);
			}
		}
		if (targets.isEmpty())
			return;
		ParametersContext context = new ParametersContext();
		context.put("schemaName", targets.keySet().stream().map(k -> k.get(0)).distinct().toList());
		context.put("tableName", targets.keySet().stream().map(k -> k.get(1)).distinct().toList());
		Map<View, List<Map<String, Object>>> result = new LinkedHashMap<>();
		for (View view : targets.values())
			result.put(view, new ArrayList<>());
		try {
			execute(connection, getSqlNodeCache().getString(query), context, new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(ExResultSet rs) throws SQLException {
					View view = targets.get(List.of(getString(rs, "VIEW_OWNER"), getString(rs, "VIEW_NAME")));
					if (view == null)
						return;
					Map<String, Object> row = new LinkedHashMap<>();
					for (String column : columns)
						row.put(column, rs.getObject(column));
					result.get(view).add(row);
				}
			});
		} catch (RuntimeException e) {
			if (!Oracle23aiIndexReader.isVectorIndexDetailUnavailable(e))
				throw e;
			logger.warn("Oracle duality details unavailable or not permitted: " + query + ": " + e.getMessage());
			return;
		}
		result.forEach((view, rows) -> view.getSpecifics().put(key, JsonUtils.toJsonString(rows)));
	}

	@Override
	protected void setDualityMetadata(View view, ExResultSet rs) throws SQLException {
		super.setDualityMetadata(view, rs);
		final String state = getString(rs, "LOGICAL_REPLICATION");
		if (state == null) {
			return;
		}
		if (!"ENABLED".equals(state) && !"DISABLED".equals(state)) {
			throw new IllegalStateException("Unknown Oracle duality logical replication state: " + state);
		}
		view.getSpecifics().put(OracleJsonDualityViewUtils.LOGICAL_REPLICATION, "ENABLED".equals(state));
	}
}
