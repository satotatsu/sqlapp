/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

public class CockroachAlterIndexFactory extends SimpleSqlFactory<Index,PostgresSqlBuilder> {
	@Override public List<SqlOperation> createSql(Index index) {throw new UnsupportedOperationException("Index ALTER requires original.diff(target)");}
	@Override public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var before=difference.getOriginal(Index.class);var after=difference.getTarget(Index.class);
		var result=new ArrayList<SqlOperation>();
		var changed=new HashSet<>(difference.getChangedProperties().keySet());
		changed.remove(SchemaProperties.REMARKS.getLabel());
		if(!changed.isEmpty()) {
			if(!changed.contains("definition") && !after.getDefinition().isEmpty()) throw new UnsupportedOperationException("Update or clear the CockroachDB index definition before changing modeled keys/includes/predicate");
			if(before.getTable()!=null && before.getTable().getConstraints().getUniqueConstraints().stream().anyMatch(c->c.getIndex()!=null && c.getIndex().getName().equals(before.getName())))
				throw new UnsupportedOperationException("Alter the owning constraint rather than its backing CockroachDB index");
			result.addAll(getSqlFactoryRegistry().createSql(before,SqlType.DROP));
			result.addAll(getSqlFactoryRegistry().createSql(after,SqlType.CREATE));
		} else if(!difference.getChangedProperties().isEmpty()) {
			var builder=createSqlBuilder().comment().on().index().space();
			CockroachDropIndexFactory.name(after,builder,getOptions().isDecorateSchemaName());builder.is().space();
			if(after.getRemarks()==null) builder._add("NULL");else builder.sqlChar(after.getRemarks());
			addSql(result,builder,SqlType.SET_COMMENT,after);
		}
		return result;
	}
}
