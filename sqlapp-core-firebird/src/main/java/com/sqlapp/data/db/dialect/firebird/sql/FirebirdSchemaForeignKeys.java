/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.*;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;

/** Separates modeled foreign keys from whole-table create/drop operations. */
final class FirebirdSchemaForeignKeys {
	static Schema without(Schema source, List<ForeignKeyConstraint> keys) {
		var result = source.clone();
		var deferred = new HashSet<Dependency>();
		for (var key : keys) {
			var table = result.getTables().get(key.getTable().getName());
			table.getConstraints().remove(key.getName());
			String indexName = key.getSpecifics().get("firebird.indexName");
			table.getIndexes()
					.removeIf(index -> index.getName().equals(key.getName()) || index.getName().equals(indexName));
			deferred.add(new Dependency(new ObjectId(Kind.TABLE, key.getTable().getName()),
					new ObjectId(Kind.TABLE, key.getRelatedTable().getName())));
		}
		// Compiled enforcement triggers may expose the same table-to-table edge.
		// Preserve the distinction between unknown and recorded empty graphs.
		if (source.getSpecifics().get(FirebirdSchemaDependencies.SPECIFICS_KEY) != null)
			FirebirdSchemaDependencies.set(result,
					FirebirdSchemaDependencies.get(source).stream().filter(edge -> !deferred.contains(edge)).toList());
		return result;
	}

	static List<SqlOperation> create(SqlFactoryRegistry registry, List<ForeignKeyConstraint> keys) {
		var result = new ArrayList<SqlOperation>();
		for (var key : keys) {
			result.addAll(registry.createSql(key, SqlType.CREATE));
			String indexName = key.getSpecifics().get("firebird.indexName");
			for (var index : key.getTable().getIndexes())
				if ((index.getName().equals(key.getName()) || index.getName().equals(indexName))
						&& index.getRemarks() != null)
					result.addAll(registry.createSql(index, SqlType.SET_COMMENT));
		}
		return result;
	}

	private FirebirdSchemaForeignKeys() {
	}
}
