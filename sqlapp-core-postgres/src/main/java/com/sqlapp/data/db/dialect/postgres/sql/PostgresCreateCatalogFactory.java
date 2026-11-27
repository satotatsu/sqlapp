/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.sqlapp.data.db.sql.CreateCatalogFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObject.Kind;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObjects;

/**
 * Database-wide PostgreSQL-only definitions around the existing shared model
 * factories.
 */
public class PostgresCreateCatalogFactory extends CreateCatalogFactory {
	@Override
	public List<SqlOperation> createSql(Catalog catalog) {
		return createSql(catalog, Map.of());
	}

	/** Subscription connection overrides are keyed by PostgresSchemaObject.id(). */
	public List<SqlOperation> createSql(Catalog catalog, Map<String, String> connections) {
		if (PostgresSchemaObjects.get(catalog).isEmpty()) {
			return super.createSql(catalog);
		}
		// Resolve all required external values before returning a partial plan.
		PostgresSchemaObjects.createSql(catalog, connections);
		var remaining = new ArrayList<>(super.createSql(catalog));
		var sql = new ArrayList<SqlOperation>();
		// Roles and schemas must exist before extensions in non-public schemas.
		move(remaining, sql,
				operation -> operation.getOriginal() instanceof com.sqlapp.data.schemas.Role
						|| operation.getOriginal() instanceof com.sqlapp.data.schemas.User
						|| operation.getOriginal() instanceof com.sqlapp.data.schemas.TableSpace
						|| operation.getSqlText().stripLeading().startsWith("CREATE SCHEMA "));
		append(catalog, Kind.EXTENSION, connections, sql);
		var collations = new java.util.HashSet<String>();
		for (var schema : catalog.getSchemas()) {
			for (var object : PostgresSchemaObjects.get(schema)) {
				if (object.kind() == Kind.COLLATION) {
					collations.addAll(object.definition());
				}
			}
		}
		move(remaining, sql, operation -> collations.contains(operation.getSqlText()));
		// Custom FDW handler/validator functions may depend on user-defined types.
		move(remaining, sql,
				operation -> operation.getOriginal() instanceof com.sqlapp.data.schemas.Type
						|| operation.getOriginal() instanceof com.sqlapp.data.schemas.Domain
						|| operation.getOriginal() instanceof com.sqlapp.data.schemas.Sequence
						|| operation.getOriginal() instanceof com.sqlapp.data.schemas.Function
						|| operation.getOriginal() instanceof com.sqlapp.data.schemas.Procedure);
		append(catalog, Kind.FOREIGN_DATA_WRAPPER, connections, sql);
		append(catalog, Kind.SERVER, connections, sql);
		append(catalog, Kind.USER_MAPPING, connections, sql);
		sql.addAll(remaining);
		append(catalog, Kind.PUBLICATION, connections, sql);
		append(catalog, Kind.SUBSCRIPTION, connections, sql);
		return sql;
	}

	private void move(List<SqlOperation> remaining, List<SqlOperation> target,
			java.util.function.Predicate<SqlOperation> predicate) {
		var iterator = remaining.iterator();
		while (iterator.hasNext()) {
			var operation = iterator.next();
			if (predicate.test(operation)) {
				target.add(operation);
				iterator.remove();
			}
		}
	}

	private void append(Catalog catalog, Kind kind, Map<String, String> connections, List<SqlOperation> sql) {
		for (var object : PostgresSchemaObjects.get(catalog)) {
			if (object.kind() != kind) {
				continue;
			}
			for (String definition : PostgresSchemaObjects.createSql(object, connections)) {
				sql.add(new SqlOperation(definition, SqlType.CREATE, catalog));
			}
		}
	}
}
