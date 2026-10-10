/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;

/** Catalog-backed placement, fetched in one query for the requested table set. */
public final class YugabytePlacementReader {
	private YugabytePlacementReader() { }

	public static void read(Connection connection, Table table) throws SQLException {
		readAll(connection, List.of(table));
	}

	public static void readAll(Connection connection, List<Table> tables) throws SQLException {
		var selected = new java.util.LinkedHashMap<String, Table>();
		for (var table : tables)
			if (table.getId() != null && table.getPartitioning() == null) selected.put(table.getId(), table);
		if (selected.isEmpty()) return;
		String placeholders = String.join(",", java.util.Collections.nCopies(selected.size(), "?::oid"));
		String sql = "WITH targets AS (SELECT oid FROM pg_catalog.pg_class WHERE oid IN (" + placeholders + ")), "
				+ "relations AS (SELECT oid AS table_oid,oid AS relation_oid,NULL::text AS index_name,NULL::text AS constraint_name,0 AS kind FROM targets "
				+ "UNION ALL SELECT i.indrelid,i.indexrelid,c.relname,k.conname,1 FROM targets t JOIN pg_catalog.pg_index i ON i.indrelid=t.oid "
				+ "JOIN pg_catalog.pg_class c ON c.oid=i.indexrelid LEFT JOIN pg_catalog.pg_constraint k ON k.conindid=i.indexrelid AND k.contype IN ('p','u')) "
				+ "SELECT r.*,p.num_tablets,p.num_hash_key_columns,p.is_colocated,s.spcname, "
				+ "CASE WHEN NOT p.is_colocated AND p.num_hash_key_columns=0 AND p.num_tablets>1 THEN yb_get_range_split_clause(c.oid) END AS splits "
				+ "FROM relations r JOIN pg_catalog.pg_class c ON c.oid=r.relation_oid CROSS JOIN LATERAL yb_table_properties(c.oid) p "
				+ "LEFT JOIN pg_catalog.pg_tablespace s ON s.oid=c.reltablespace ORDER BY r.kind,r.table_oid,r.relation_oid";
		var seen = new java.util.HashSet<String>();
		try (var query = connection.prepareStatement(sql)) {
			int position = 1;
			for (var oid : selected.keySet()) query.setString(position++, oid);
			try (var rows = query.executeQuery()) {
				while (rows.next()) {
					String tableOid = rows.getString("table_oid");
					Table table = selected.get(tableOid);
					Map<String, String> settings = new java.util.LinkedHashMap<>();
					int tablets = rows.getInt("num_tablets"), hash = rows.getInt("num_hash_key_columns");
					boolean colocated = rows.getBoolean("is_colocated");
					settings.put("YSQL_COLOCATION", Boolean.toString(colocated));
					settings.put("YSQL_HASH_COLUMNS", Integer.toString(hash));
					String tablespace = rows.getString("spcname");
					if (!colocated && hash > 0) settings.put("YSQL_SPLIT_INTO", Integer.toString(tablets));
					else if (!colocated && tablets > 1) {
						String splits = rows.getString("splits");
						if (splits == null || splits.isBlank())
							throw new SQLException("YSQL range split boundaries unavailable for relation " + rows.getString("relation_oid"));
						settings.put("YSQL_SPLIT_AT_BASE64", Base64.getEncoder().encodeToString(splits.getBytes(StandardCharsets.UTF_8)));
					}
					if (rows.getInt("kind") == 0) {
						table.getSpecifics().putAll(settings);
						table.setTableSpaceName(tablespace);
						seen.add(tableOid);
						continue;
					}
					var index = table.getIndexes().get(rows.getString("index_name"));
					var constraint = table.getConstraints().get(rows.getString("constraint_name"));
					// A primary-key index shares its table's DocDB storage.
					if (constraint instanceof UniqueConstraint unique && unique.isPrimaryKey()) {
						settings.clear();
						for (String key : List.of("YSQL_COLOCATION", "YSQL_HASH_COLUMNS", "YSQL_SPLIT_INTO", "YSQL_SPLIT_AT_BASE64"))
							if (table.getSpecifics().containsKey(key)) settings.put(key, table.getSpecifics().get(key));
						if (tablespace == null) tablespace = table.getTableSpaceName();
					}
					if (index != null) {
						index.getSpecifics().putAll(settings);
						index.setTableSpaceName(tablespace);
					}
					if (constraint instanceof UniqueConstraint unique) {
						unique.getSpecifics().putAll(settings);
						unique.getIndex().getSpecifics().putAll(settings);
						unique.getIndex().setTableSpaceName(tablespace);
						if (tablespace != null) unique.getSpecifics().put("YSQL_TABLESPACE", tablespace);
					}
				}
			}
		}
		if (!seen.equals(selected.keySet())) throw new SQLException("YSQL placement missing for requested tables");
	}
}
