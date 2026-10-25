/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/**
 * ACTIVE rebuilds both ordinary and constraint indexes without dropping them.
 */
public class FirebirdRebuildIndexFactory extends SimpleSqlFactory<Index, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Index index) {
		FirebirdPrivilege.identifier(index.getName(), "index name");
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, createSqlBuilder()._add("ALTER INDEX ").name(index.getName())._add(" ACTIVE"), SqlType.REBUILD,
				index);
		return result;
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		throw new IllegalArgumentException("Index REBUILD requires an explicit index, not a structural diff");
	}
}
