/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Native comments and verified SQL body replacement. */
abstract class Db2AlterRoutineFactory<T extends Routine<T>> extends AbstractSqlFactory<T, Db2SqlBuilder> {
	@Override
	public List<SqlOperation> createSql(T routine) {
		throw new IllegalArgumentException("Db2 routine ALTER requires original and target models; use createDiffSql");
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Unchanged)
			return List.of();
		T before = (T) difference.getOriginal(), after = (T) difference.getTarget();
		if (difference.getState() == State.Added || difference.getState() == State.Deleted) {
			T routine = after == null ? before : after;
			SqlFactory<T> factory = getSqlFactoryRegistry().getSqlFactory(routine,
					after == null ? SqlType.DROP : SqlType.CREATE);
			return factory.createSql(routine);
		}
		var changed = new java.util.HashSet<>(difference.getChangedProperties().keySet());
		var beforeSpecifics = new java.util.HashMap<>(before.getSpecifics());
		var afterSpecifics = new java.util.HashMap<>(after.getSpecifics());
		beforeSpecifics.remove("DB2_ROUTINE_DDL_BASE64");
		afterSpecifics.remove("DB2_ROUTINE_DDL_BASE64");
		boolean refreshed = !java.util.Objects.equals(before.getSpecifics().get("DB2_ROUTINE_DDL_BASE64"),
				after.getSpecifics().get("DB2_ROUTINE_DDL_BASE64"));
		if (beforeSpecifics.equals(afterSpecifics))
			changed.remove("specifics");
		if (refreshed)
			Db2RoutineReplacement.replaceStatement(before, after);
		if (changed.isEmpty())
			return List.of();
		boolean definitionChange = changed.contains("definition")
				&& Set.of("definition", "remarks").containsAll(changed);
		boolean statementChange = changed.contains("statement")
				&& Set.of("statement", "definition", "remarks").containsAll(changed);
		if (difference.getState() == State.Modified && (definitionChange || statementChange)) {
			String replacement = definitionChange ? Db2RoutineReplacement.replace(before, after)
					: Db2RoutineReplacement.replaceStatement(before, after);
			List<SqlOperation> result = new ArrayList<>();
			for (String context : Db2CreationContext.sql(after.getSpecifics().get("QUALIFIER"),
					after.getSpecifics().get("FUNC_PATH")))
				result.add(new SqlOperation(context, SqlType.SET_SEARCH_PATH_TO_SCHEMA, after));
			result.add(new SqlOperation(replacement, SqlType.ALTER, before, after));
			result.add(comment(before, after));
			return result;
		}
		if (difference.getState() != State.Modified || !Set.of("remarks").containsAll(changed))
			throw new IllegalArgumentException(
					"Db2 routine body, signature, identity or option changes require an explicit "
							+ "native replacement plan preserving SPECIFIC identity, grants and dependencies: "
							+ before.getName());
		if (after.getSpecificName() == null || after.getSpecificName().isBlank() || after.getSchemaName() == null
				|| after.getSchemaName().isBlank())
			throw new IllegalArgumentException(
					"Db2 routine comment requires schemaName and specificName to identify the overload: "
							+ after.getName());
		return List.of(comment(before, after));
	}

	private SqlOperation comment(T before, T after) {
		String designator = "SPECIFIC " + (after instanceof Function ? "FUNCTION " : "PROCEDURE ")
				+ Db2CreationContext.qualified(after.getSchemaName(), after.getSpecificName());
		return new SqlOperation(Db2AlterCommentSql.comment(getDialect(), designator, after.getRemarks()),
				SqlType.SET_COMMENT, before, after);
	}
}
