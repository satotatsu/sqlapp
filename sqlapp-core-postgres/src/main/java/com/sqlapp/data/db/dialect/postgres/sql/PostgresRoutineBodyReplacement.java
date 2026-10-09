/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.regex.Pattern;
import com.sqlapp.data.schemas.Function;

/** Replaces only a verified dollar-quoted body in a catalog-supplied definition. */
final class PostgresRoutineBodyReplacement {
	private static final Pattern BODY = Pattern.compile("(?m)^AS[ \\t]+(\\$(?:[A-Za-z_][A-Za-z_0-9]*)?\\$)");
	private PostgresRoutineBodyReplacement() { }

	static String replace(Function original, Function target, String qualifiedName, boolean procedure) {
		String encoded = original.getSpecifics().get("POSTGRES_ROUTINE_DDL_BASE64");
		String ddl = String.join("\n", original.getDefinition());
		if (encoded != null) {
			try { ddl = new String(java.util.Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8); }
			catch (IllegalArgumentException invalid) { throw unsupported(); }
		}
		String prefix = "CREATE OR REPLACE " + (procedure ? "PROCEDURE " : "FUNCTION ") + qualifiedName + "(";
		if (ddl == null || !ddl.startsWith(prefix)) throw unsupported();
		if (!"sql".equalsIgnoreCase(original.getLanguage()) && !"plpgsql".equalsIgnoreCase(original.getLanguage()))
			throw unsupported();
		if (original.getStatement() == null || target.getStatement() == null) throw unsupported();
		var match = BODY.matcher(ddl);
		while (match.find()) {
			String delimiter = match.group(1);
			int end = ddl.indexOf(delimiter, match.end());
			if (end < 0) continue;
			String suffix = ddl.substring(end + delimiter.length());
			if (!suffix.trim().isEmpty() && !suffix.trim().equals(";")) continue;
			String oldBody = ddl.substring(match.end(), end);
			if (!oldBody.strip().equals(String.join("\n", original.getStatement()).strip())) continue;
			String body = String.join("\n", target.getStatement());
			String tag = "$sqlapp_body$";
			for (int i = 0; body.contains(tag); i++) tag = "$sqlapp_body_" + i + "$";
			return ddl.substring(0, match.start(1)) + tag + body + tag + suffix;
		}
		throw unsupported();
	}

	static boolean sameHeader(Function original, Function target, String qualifiedName, boolean procedure) {
		// Both catalog snapshots must describe their own modeled body, not a stale clone template.
		Function marker = new Function().setStatement("sqlapp_body_comparison");
		String before = replace(original, marker, qualifiedName, procedure);
		String after = replace(target, marker, qualifiedName, procedure);
		if (!target.getDefinition().isEmpty()) {
			String encoded = target.getSpecifics().get("POSTGRES_ROUTINE_DDL_BASE64");
			if (encoded != null) {
				String ddl;
				try { ddl = new String(java.util.Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8); }
				catch (IllegalArgumentException invalid) { throw unsupported(); }
				if (!ddl.strip().equals(String.join("\n", target.getDefinition()).strip())) return false;
			}
		}
		return before.strip().equals(after.strip());
	}

	private static UnsupportedOperationException unsupported() {
		return new UnsupportedOperationException("Routine body replacement requires a matching catalog CREATE OR REPLACE DDL, SQL/PLpgSQL language and original statement; re-read metadata or provide an explicit migration");
	}
}
