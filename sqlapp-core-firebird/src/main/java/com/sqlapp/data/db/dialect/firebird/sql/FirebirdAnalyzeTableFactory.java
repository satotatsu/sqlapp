/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/**
 * Composes index ANALYZE for the active indexes in a fully read Schema table.
 */
public class FirebirdAnalyzeTableFactory extends SimpleSqlFactory<Table, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Table table) {
		FirebirdPrivilege.identifier(table.getName(), "table name");
		if (table instanceof View)
			throw new IllegalArgumentException("Firebird views do not have indexes to analyze");
		var names = new LinkedHashSet<String>();
		var inactive = new LinkedHashSet<String>();
		for (Index index : table.getIndexes()) {
			FirebirdPrivilege.identifier(index.getName(), "index name");
			if (index.isEnable())
				names.add(index.getName());
			else
				inactive.add(index.getName());
		}
		for (Constraint constraint : table.getConstraints()) {
			if (!(constraint instanceof UniqueConstraint || constraint instanceof ForeignKeyConstraint))
				continue;
			if (!constraint.isEnable())
				throw new IllegalArgumentException(
						"Firebird indexed constraints cannot be disabled: " + constraint.getName());
			String name = constraint.getSpecifics().get("firebird.indexName");
			if (name == null)
				name = constraint.getSpecifics().get("firebird.catalog.indexName");
			if (name == null || name.isBlank())
				throw new IllegalArgumentException(
						"Read full Firebird table metadata before ANALYZE, or provide firebird.indexName for constraint "
								+ constraint.getName());
			if (inactive.contains(name))
				throw new IllegalArgumentException("Constraint-backed indexes cannot be inactive: " + name);
			names.add(name);
		}
		if (names.isEmpty() && inactive.isEmpty())
			throw new IllegalArgumentException(
					"Table ANALYZE requires modeled index metadata; read an indexed table with getAllFull, or analyze an Index directly");
		List<SqlOperation> result = new ArrayList<>();
		for (String name : names)
			result.addAll(getSqlFactoryRegistry().createSql(new Index(name), SqlType.ANALYZE));
		return result;
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		throw new IllegalArgumentException("Table ANALYZE requires an explicit table, not a structural diff");
	}
}
