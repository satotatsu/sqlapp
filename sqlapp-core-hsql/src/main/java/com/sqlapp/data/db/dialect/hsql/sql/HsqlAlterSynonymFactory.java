/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.sqlapp.data.db.dialect.hsql.util.HsqlSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Synonym;

/** Replaces a synonym without dropping its target or cascading dependencies. */
public class HsqlAlterSynonymFactory extends SimpleSqlFactory<Synonym, HsqlSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Synonym synonym) {
		throw new IllegalArgumentException("HSQLDB synonym ALTER requires original.diff(target)");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (!(difference.getOriginal() instanceof Synonym original)
				|| !(difference.getTarget() instanceof Synonym target)) {
			throw new IllegalArgumentException("HSQLDB synonym ALTER requires two synonyms");
		}
		if (original.equals(target))
			return List.of();
		if (!Objects.equals(original.getSchemaName(), target.getSchemaName())
				|| !Objects.equals(original.getCatalogName(), target.getCatalogName())) {
			throw new IllegalArgumentException("HSQLDB synonym schema/catalog changes require an explicit migration");
		}
		if (target.getName() == null || target.getName().isBlank())
			throw new IllegalArgumentException("HSQLDB synonym requires a target name");
		var replacement = getSqlFactoryRegistry().createSql(target, SqlType.CREATE);
		List<SqlOperation> result = new ArrayList<>();
		var sql = createSqlBuilder().drop().space()._add("SYNONYM").name(original, getOptions().isDecorateSchemaName());
		addSql(result, sql, SqlType.DROP, original);
		result.addAll(replacement);
		return result;
	}
}
