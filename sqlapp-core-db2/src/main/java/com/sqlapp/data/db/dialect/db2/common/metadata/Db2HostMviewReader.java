/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.MviewReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Materialized query tables. As in the LUW dialect the MQT is recreated from a
 * complete CREATE TABLE in {@code definition}: z/OS stores that statement in
 * SYSVIEWS.STATEMENT; IBM i stores only the query, so the statement is built
 * from the query, the catalog options and the loaded column names.
 */
class Db2HostMviewReader extends MviewReader {
	/** Transient marker for an IBM i MQT whose definition needs its columns. */
	private static final String PENDING_QUERY = "DB2_HOST_TMP_MQT_QUERY";
	private final Class<?> resources;

	Db2HostMviewReader(Dialect dialect, Class<?> resources) {
		super(dialect);
		this.resources = resources;
	}

	@Override
	protected List<Table> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		List<Table> result = new ArrayList<>();
		execute(connection, getSqlNodeCache(resources).getString("mviews.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				var mview = new Mview(id(rs, "TABLE_NAME"));
				mview.setSchemaName(id(rs, "SCHEMA_NAME"));
				mview.setRemarks(rs.getString("REMARKS"));
				String text = rs.getString("MQT_TEXT");
				if (text == null || text.isBlank() || text.indexOf('\0') >= 0)
					throw new SQLException("Missing or truncated MQT definition: " + mview.getName());
				String refresh = id(rs, "REFRESH"), maintenance = id(rs, "MAINTENANCE"), enable = id(rs, "ENABLE_FLAG");
				mview.getSpecifics().put("REFRESH", refresh);
				mview.getSpecifics().put("QUALIFIER", mview.getSchemaName());
				if ("D".equals(id(rs, "TEXT_KIND"))) {
					if (!text.stripLeading().matches("(?is)CREATE\\s+TABLE\\b.*"))
						throw new SQLException("MQT catalog text is not a complete CREATE TABLE: " + mview.getName());
					mview.setDefinition(text.strip());
					String path = rs.getString("SQL_PATH");
					if (path != null && !path.isBlank())
						mview.getSpecifics().put("FUNC_PATH", path);
				} else {
					// IBM i: only REFRESH DEFERRED and MAINTAINED BY USER exist.
					if (!"DEFERRED".equals(refresh) || !"USER".equals(maintenance)
							|| (!"YES".equals(enable) && !"NO".equals(enable)))
						throw new SQLException("Unsupported MQT options " + refresh + "/" + maintenance + "/" + enable
								+ ": " + mview.getName());
					mview.getSpecifics().put("MAINTENANCE", maintenance);
					mview.getSpecifics().put("ENABLE", enable);
					String isolation = id(rs, "ISOLATION");
					if (isolation != null && !isolation.isBlank())
						mview.getSpecifics().put("DB2_HOST_MQT_ISOLATION", isolation);
					mview.getSpecifics().put(PENDING_QUERY, text.strip());
				}
				result.add(mview);
			}
		});
		return result;
	}

	@Override
	protected void setMetadataDetail(Connection connection, ParametersContext context, List<Table> tableList)
			throws java.sql.SQLException {
		super.setMetadataDetail(connection, context, tableList);
		for (Table table : tableList) {
			String query = table.getSpecifics().get(PENDING_QUERY);
			if (query == null)
				continue;
			if (table.getColumns().isEmpty())
				throw new IllegalArgumentException("MQT columns not found: " + table.getName());
			var sql = new StringBuilder("CREATE TABLE ")
					.append(Db2CreationContext.qualified(table.getSchemaName(), table.getName())).append(" (");
			int count = 0;
			for (Column column : table.getColumns())
				sql.append(count++ > 0 ? ", " : "").append(Db2CreationContext.identifier(column.getName()));
			sql.append(") AS (").append(query).append(") DATA INITIALLY DEFERRED REFRESH DEFERRED ")
					.append("YES".equals(table.getSpecifics().get("ENABLE")) ? "ENABLE" : "DISABLE")
					.append(" QUERY OPTIMIZATION MAINTAINED BY USER");
			table.setDefinition(sql.toString());
			Db2HostSecurityReaders.removeSpecific(table, PENDING_QUERY);
		}
	}

	@Override
	protected ColumnReader newColumnReader() {
		return getDialect().getCatalogReader().getSchemaReader().getTableReader().getColumnReader();
	}

	@Override
	protected IndexReader newIndexReader() {
		return new Db2HostSupplementalReaders.Indexes(getDialect(), resources);
	}

	@Override
	protected ExcludeConstraintReader newExcludeConstraintReader() {
		return null;
	}

	private static String id(ExResultSet rs, String name) throws SQLException {
		String value = rs.getString(name);
		return value == null ? null : value.stripTrailing();
	}
}
