/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.metadata;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;

/** Two set-based queries, independent of package count. */
final class EdbOraclePackageSource {
	record Source(String schema, String name, String text) { }
	private EdbOraclePackageSource() { }
	static List<Source> read(Connection connection, ParametersContext context, boolean body) {
		try {
			String namespace = null;
			try (var sql = connection.prepareStatement("SELECT n.nspname FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE c.relname='all_source' AND c.relkind IN ('v','m') AND n.nspname IN ('sys','pg_catalog')"); var rows = sql.executeQuery()) {
				if (rows.next()) namespace = rows.getString(1);
				if (rows.next()) throw new SQLException("Ambiguous EPAS all_source catalog namespace");
			}
			if (namespace == null) return List.of();
			if (!Set.of("sys","pg_catalog").contains(namespace)) throw new SQLException("Unexpected EPAS source catalog namespace");
			var query = new StringBuilder("SELECT schema_name,name,string_agg(CASE WHEN right(text,1)=chr(10) THEN text ELSE text||chr(10) END,'' ORDER BY line) AS source, bool_or(text IS NULL) AS missing_source FROM \"" + namespace + "\".all_source WHERE type=?");
			var values = new ArrayList<Object>(); values.add(body ? "PACKAGE BODY" : "PACKAGE");
			filter(query,values,"schema_name",context.get("schemaName"));
			filter(query,values,"name",context.get("packageName"));
			query.append(" GROUP BY schema_name,name ORDER BY schema_name,name");
			var result = new ArrayList<Source>();
			try (var sql = connection.prepareStatement(query.toString())) {
				for (int i=0;i<values.size();i++) sql.setObject(i+1,values.get(i));
				try (var rows=sql.executeQuery()) {
					while (rows.next()) {
						if (rows.getBoolean("missing_source")) throw new SQLException("Missing EPAS package source line");
						result.add(new Source(rows.getString("schema_name"),rows.getString("name"),rows.getString("source")));
					}
				}
			}
			return result;
		} catch (SQLException e) { throw new IllegalStateException("Failed to read EPAS package sources",e); }
	}
	private static void filter(StringBuilder sql,List<Object> values,String column,Object value) {
		if (value == null) return;
		Collection<?> items = value instanceof Collection<?> c ? c : value instanceof Object[] array ? Arrays.asList(array) : List.of(value);
		if (items.isEmpty()) return;
		sql.append(" AND ").append(column).append(" IN (").append(String.join(",",Collections.nCopies(items.size(),"?"))).append(")");
		values.addAll(items);
	}
	static String definition(Dialect dialect,Source source,boolean body) {
		if (source.text() == null || source.text().isBlank()) throw new IllegalArgumentException("Missing EPAS package source for " + source.name());
		String id = "(?:\"(?:[^\"]|\"\")*\"|[\\p{L}_][\\p{L}\\p{N}_$]*)";
		var header = Pattern.compile("(?is)^\\s*(?:CREATE\\s+(?:OR\\s+REPLACE\\s+)?)?PACKAGE\\s+" + (body ? "BODY\\s+" : "") + id + "(?:\\s*\\.\\s*" + id + ")?(?=\\s)").matcher(source.text());
		if (!header.find()) throw new IllegalArgumentException("Unrecognized EPAS package source header for " + source.name());
		String tail = source.text().substring(header.end());
		if (!tail.stripLeading().matches("(?is)^(?:AUTHID\\s+(?:CURRENT_USER|DEFINER)\\s+)?(?:IS|AS)\\b.*")) throw new IllegalArgumentException("Unrecognized EPAS package declaration for " + source.name());
		return "CREATE OR REPLACE PACKAGE " + (body ? "BODY " : "") + dialect.quote(source.schema()) + "." + dialect.quote(source.name()) + tail;
	}
}
