/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.jdbc.ExResultSet;
import java.sql.SQLException;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Oracle10g以降のマテビュー作成クラス
 * 
 * @author satoh
 * 
 */
public class Oracle10gMviewReader extends OracleMviewReader {

	protected Oracle10gMviewReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("mviews10g.sql");
	}

	@Override
	protected List<Table> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		List<Table> views = super.doGetAll(connection, context, version);
		if (views.isEmpty()) return views;
		Map<List<String>, Table> targets = new LinkedHashMap<>();
		for (Table view : views) targets.put(List.of(view.getSchemaName(), view.getName()), view);
		ParametersContext parameters = new ParametersContext();
		parameters.put("schemaName", views.stream().map(Table::getSchemaName).distinct().toList());
		parameters.put("tableName", views.stream().map(Table::getName).distinct().toList());
		Map<List<String>, String> definitions = new LinkedHashMap<>();
		try {
			execute(connection, getSqlNodeCache().getString("mviewDefinitions10g.sql"), parameters, new ResultSetNextHandler() {
				@Override public void handleResultSetNext(ExResultSet rs) throws SQLException {
					var key = List.of(getString(rs, "OWNER"), getString(rs, "MVIEW_NAME"));
					if (!targets.containsKey(key)) return;
					String ddl = getString(rs, "MVIEW_DEFINITION");
					if (ddl == null || ddl.isBlank()) throw new IllegalStateException("Oracle materialized view DDL unavailable: " + key);
					definitions.put(key, ddl);
				}
			});
		} catch (RuntimeException error) {
			if (!OracleMetadataAccess.isDenied(error)) throw error;
			logger.warn("Oracle DBMS_METADATA access denied or object inaccessible; materialized view definitions are skipped, catalog metadata retained.");
			return views;
		}
		for (var entry : targets.entrySet()) {
			if (!definitions.containsKey(entry.getKey())) throw new IllegalStateException("Oracle materialized view DDL unavailable: " + entry.getKey());
			entry.getValue().setDefinition(definitions.get(entry.getKey()));
		}
		return views;
	}
}
