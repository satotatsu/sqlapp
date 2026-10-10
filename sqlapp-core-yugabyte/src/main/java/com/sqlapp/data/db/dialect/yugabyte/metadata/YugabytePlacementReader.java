/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;

/**
 * Catalog-backed placement settings, separate from live node/tablet identities.
 */
public final class YugabytePlacementReader {
	private YugabytePlacementReader() {
	}

	public static void read(Connection connection, Table table) throws SQLException {
		if (table.getId() == null || table.getPartitioning() != null)
			return;
		readRelation(connection, table.getId(), table.getSpecifics(), table::setTableSpaceName);
		try (var query = connection.prepareStatement(
				"SELECT i.indexrelid, c.relname, k.conname FROM pg_catalog.pg_index i JOIN pg_catalog.pg_class c ON c.oid=i.indexrelid LEFT JOIN pg_catalog.pg_constraint k ON k.conindid=i.indexrelid AND k.contype IN ('p','u') WHERE i.indrelid=?::oid")) {
			query.setString(1, table.getId());
			try (var rows = query.executeQuery()) {
				while (rows.next()) {
					var index = table.getIndexes().get(rows.getString(2));
					var constraint = table.getConstraints().get(rows.getString(3));
					Map<String, String> settings = new java.util.LinkedHashMap<>();
					final String[] tablespace = { null };
					readRelation(connection, rows.getString(1), settings, value -> tablespace[0] = value);
					// A YSQL primary-key index shares its table's DocDB storage.
					if (constraint instanceof UniqueConstraint unique && unique.isPrimaryKey()) {
						settings.clear();
						for (String key : java.util.List.of("YSQL_COLOCATION", "YSQL_HASH_COLUMNS", "YSQL_SPLIT_INTO",
								"YSQL_SPLIT_AT_BASE64")) {
							if (table.getSpecifics().containsKey(key))
								settings.put(key, table.getSpecifics().get(key));
						}
						if (tablespace[0] == null)
							tablespace[0] = table.getTableSpaceName();
					}
					if (index != null) {
						index.getSpecifics().putAll(settings);
						index.setTableSpaceName(tablespace[0]);
					}
					if (constraint instanceof UniqueConstraint unique) {
						unique.getSpecifics().putAll(settings);
						unique.getIndex().getSpecifics().putAll(settings);
						unique.getIndex().setTableSpaceName(tablespace[0]);
						if (tablespace[0] != null)
							unique.getSpecifics().put("YSQL_TABLESPACE", tablespace[0]);
					}
				}
			}
		}
	}

	private static void readRelation(Connection connection, String oid, Map<String, String> settings,
			java.util.function.Consumer<String> tablespace) throws SQLException {
		try (var query = connection.prepareStatement(
				"SELECT p.num_tablets,p.num_hash_key_columns,p.is_colocated,s.spcname FROM pg_catalog.pg_class c CROSS JOIN LATERAL yb_table_properties(c.oid) p LEFT JOIN pg_catalog.pg_tablespace s ON s.oid=c.reltablespace WHERE c.oid=?::oid")) {
			query.setString(1, oid);
			try (var rows = query.executeQuery()) {
				if (!rows.next())
					throw new SQLException("YSQL relation not found: " + oid);
				int tablets = rows.getInt(1), hash = rows.getInt(2);
				boolean colocated = rows.getBoolean(3);
				settings.put("YSQL_COLOCATION", Boolean.toString(colocated));
				settings.put("YSQL_HASH_COLUMNS", Integer.toString(hash));
				tablespace.accept(rows.getString(4));
				if (colocated)
					return;
				if (hash > 0)
					settings.put("YSQL_SPLIT_INTO", Integer.toString(tablets));
				else if (tablets > 1) {
					try (var splits = connection.prepareStatement("SELECT yb_get_range_split_clause(?::oid)")) {
						splits.setString(1, oid);
						try (var result = splits.executeQuery()) {
							if (!result.next() || result.getString(1) == null || result.getString(1).isBlank())
								throw new SQLException("YSQL range split boundaries unavailable for relation " + oid);
							settings.put("YSQL_SPLIT_AT_BASE64", Base64.getEncoder()
									.encodeToString(result.getString(1).getBytes(StandardCharsets.UTF_8)));
						}
					}
				}
			}
		}
	}
}
