/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/**
 * Recalculates index selectivity without activating or rebuilding the index.
 */
public class FirebirdAnalyzeIndexFactory extends SimpleSqlFactory<Index, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Index index) {
		FirebirdPrivilege.identifier(index.getName(), "index name");
		if (!index.isEnable())
			throw new IllegalArgumentException(
					"Cannot analyze an inactive Firebird index; activate it explicitly first");
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, createSqlBuilder()._add("SET STATISTICS INDEX ").name(index.getName()), SqlType.ANALYZE, index);
		return result;
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		throw new IllegalArgumentException("Index ANALYZE requires an explicit index, not a structural diff");
	}
}
