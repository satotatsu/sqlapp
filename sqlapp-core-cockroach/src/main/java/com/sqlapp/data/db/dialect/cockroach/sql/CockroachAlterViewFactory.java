/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

public class CockroachAlterViewFactory extends SimpleSqlFactory<View, PostgresSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(View view) {
		throw new UnsupportedOperationException("View ALTER requires original.diff(target)");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var before = difference.getOriginal(View.class);
		var after = difference.getTarget(View.class);
		if (!Objects.equals(before.getName(), after.getName())
				|| !Objects.equals(before.getSchemaName(), after.getSchemaName()))
			throw new UnsupportedOperationException("View identity changes require an explicit CockroachDB migration");
		var changed = new HashSet<>(difference.getChangedProperties().keySet());
		changed.removeAll(Set.of("definition", "columns", "statement"));
		if (!changed.isEmpty() || !difference.getChangedProperties().containsKey("definition"))
			throw new UnsupportedOperationException(
					"CockroachDB view replacement requires a complete changed CREATE VIEW definition");
		String ddl = String.join("\n", after.getDefinition());
		if (!ddl.matches("(?is)^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\s+.*"))
			throw new IllegalArgumentException("CockroachDB view replacement requires CREATE VIEW definition");
		ddl = ddl.replaceFirst("(?is)^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\s+", "CREATE OR REPLACE VIEW ");
		var result = new ArrayList<SqlOperation>();
		addSql(result, createSqlBuilder()._add(ddl), SqlType.ALTER, after);
		return result;
	}
}
