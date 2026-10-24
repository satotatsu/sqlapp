/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Index state changes do not rebuild or drop the index. */
public class FirebirdAlterIndexFactory extends SimpleSqlFactory<Index, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Index index) {
		if (index.getTable() != null) {
			for (var constraint : index.getTable().getConstraints())
				if (index.getName().equals(constraint.getName())
						|| index.getName().equals(constraint.getSpecifics().get("firebird.indexName")))
					throw new IllegalArgumentException(
							"Constraint-backed indexes cannot be activated or deactivated independently");
		}
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, createSqlBuilder()._add("ALTER INDEX ").name(index.getName())
				._add(index.isEnable() ? " ACTIVE" : " INACTIVE"), SqlType.ALTER, index);
		return result;
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Index target = FirebirdAlterSql.target(difference, Index.class, Set.of("enable", "remarks"));
		List<SqlOperation> result = new ArrayList<>();
		if (difference.getChangedProperties().containsKey("enable"))
			result.addAll(createSql(target));
		if (difference.getChangedProperties().containsKey("remarks"))
			result.addAll(getSqlFactoryRegistry().createSql(target, SqlType.SET_COMMENT));
		return result;
	}
}
