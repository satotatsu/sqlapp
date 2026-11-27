/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.Routine;
import com.sqlapp.data.schemas.State;
import com.sqlapp.util.CommonUtils;

/**
 * Explicit full-definition replacement; never drops a routine or guesses its
 * body.
 */
public class OracleAlterRoutineFactory extends SimpleSqlFactory<Routine<?>, OracleSqlBuilder> {
	private static final String ID = "(?:\"(?:[^\"]|\"\")+\"|[\\p{L}][\\p{L}\\p{N}_$#]*)";
	private static final Pattern HEADER = Pattern
			.compile("(?is)^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?(FUNCTION|PROCEDURE)\\s+(?:(" + ID + ")\\s*\\.\\s*)?("
					+ ID + ")(?=\\s|\\(|$)");

	@Override
	public List<SqlOperation> createSql(Routine<?> routine) {
		throw new UnsupportedOperationException("Oracle routine ALTER requires original.diff(target)");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Unchanged)
			return List.of();
		if (!(difference.getOriginal() instanceof Routine<?> before)
				|| !(difference.getTarget() instanceof Routine<?> after) || before.getClass() != after.getClass()
				|| !(after instanceof Function || after instanceof Procedure))
			throw new IllegalArgumentException("Routine replacement requires original and target of the same kind");
		if (!Objects.equals(before.getName(), after.getName())
				|| !Objects.equals(before.getSchemaName(), after.getSchemaName()))
			throw new IllegalArgumentException("Routine replacement must preserve owner and name");
		if (CommonUtils.isEmpty(after.getDefinition()) || Objects.equals(before.getDefinition(), after.getDefinition()))
			throw new IllegalArgumentException("Routine replacement requires an updated complete definition");
		String ddl = String.join("\n", after.getDefinition());
		if (!ddl.stripLeading().matches("(?is)^CREATE\\s+.*"))
			ddl = "CREATE OR REPLACE " + ddl;
		var header = HEADER.matcher(ddl);
		if (!header.find() || !header.group(1).equalsIgnoreCase(after instanceof Function ? "FUNCTION" : "PROCEDURE")
				|| !identifier(header.group(3)).equals(after.getName()))
			throw new IllegalArgumentException("Routine definition header must match its modeled kind and name");
		if (header.group(2) != null && after.getSchemaName() != null
				&& !identifier(header.group(2)).equals(after.getSchemaName()))
			throw new IllegalArgumentException("Routine definition owner must match its modeled owner");
		if (after.getSchemaName() != null) {
			OracleSqlBuilder name = createSqlBuilder(getDialect());
			name.name(after, true);
			int start = header.group(2) == null ? header.start(3) : header.start(2);
			ddl = ddl.substring(0, start) + name.toString().trim() + ddl.substring(header.end(3));
		}
		if (!ddl.stripLeading().matches("(?is)^CREATE\\s+OR\\s+REPLACE\\s+.*"))
			ddl = ddl.replaceFirst("(?is)^\\s*CREATE\\s+", "CREATE OR REPLACE ");
		return List.of(createOperation(ddl, SqlType.ALTER, before, after));
	}

	private static String identifier(String name) {
		return name.startsWith("\"") ? name.substring(1, name.length() - 1).replace("\"\"", "\"")
				: name.toUpperCase(java.util.Locale.ROOT);
	}
}
