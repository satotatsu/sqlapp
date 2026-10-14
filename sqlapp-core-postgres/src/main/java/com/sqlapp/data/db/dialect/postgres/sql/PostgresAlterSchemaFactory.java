/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.List;
import java.util.Map;
import com.sqlapp.data.db.sql.AlterSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Schema;

/** Composes shared schema changes with PostgreSQL-only object changes. */
public class PostgresAlterSchemaFactory extends AlterSchemaFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		return createDiffSql(difference, Map.of());
	}

	/** External secret values use the same object-id keys as CREATE. */
	public List<SqlOperation> createDiffSql(DbObjectDifference difference, Map<String, String> externalValues) {
		var plan = new PostgresSchemaObjectDiff((Schema) difference.getOriginal(), (Schema) difference.getTarget(),
				externalValues, getDialect());
		var original = ((Schema) difference.getOriginal()).clone();
		var target = ((Schema) difference.getTarget()).clone();
		original.setDialect(getDialect());
		target.setDialect(getDialect());
		stripExtensionMembers(original);
		stripExtensionMembers(target);
		return compose(plan, super.createDiffSql(original.diff(target)));
	}

	static void stripExtensionMembers(Schema schema) {
		schema.getSpecifics().remove(com.sqlapp.data.db.dialect.postgres.PostgresSchemaObjects.SPECIFICS_KEY);
		for (var value : schema.toMap().values()) {
			if (value instanceof com.sqlapp.data.schemas.AbstractSchemaObjectCollection<?> collection) {
				collection.removeIf(object -> object.getSpecifics().containsKey("postgres.extension"));
			}
		}
	}

	static List<SqlOperation> compose(PostgresSchemaObjectDiff plan, List<SqlOperation> nativeSql) {
		var result = new java.util.ArrayList<SqlOperation>();
		var remaining = new java.util.ArrayList<>(nativeSql);
		move(remaining, result,
				operation -> operation.getSqlType() == com.sqlapp.data.db.sql.SqlType.DROP && isView(operation));
		var prerequisites = new java.util.ArrayList<SqlOperation>();
		for (var operation : plan.drops) {
			String sql = operation.getSqlText();
			if (sql.startsWith("DROP COLLATION") || sql.startsWith("DROP EXTENSION") || sql.startsWith("DROP SERVER")
					|| sql.startsWith("DROP USER MAPPING") || sql.startsWith("DROP FOREIGN DATA WRAPPER"))
				prerequisites.add(operation);
			else
				result.add(operation);
		}
		move(remaining, result, operation -> operation.getSqlType() == com.sqlapp.data.db.sql.SqlType.DROP
				&& !operation.getSqlText().stripLeading().startsWith("DROP SCHEMA "));
		result.addAll(prerequisites);
		move(remaining, result, operation -> operation.getSqlText().stripLeading().startsWith("DROP SCHEMA "));
		move(remaining, result,
				operation -> operation.getSqlText().stripLeading().startsWith("CREATE SCHEMA ")
						|| operation.getOriginal() instanceof com.sqlapp.data.schemas.Role
						|| operation.getOriginal() instanceof com.sqlapp.data.schemas.User);
		var before = new java.util.ArrayList<>(plan.before);
		move(before, result,
				operation -> operation.getSqlText().startsWith("CREATE EXTENSION")
						|| operation.getSqlText().startsWith("ALTER EXTENSION")
						|| operation.getSqlText().startsWith("CREATE COLLATION")
						|| operation.getSqlText().startsWith("ALTER COLLATION"));
		move(remaining, result, operation -> {
			var object = operation.getOriginal() != null ? operation.getOriginal() : operation.getTarget();
			return object instanceof com.sqlapp.data.schemas.Type || object instanceof com.sqlapp.data.schemas.Domain
					|| object instanceof com.sqlapp.data.schemas.Sequence
					|| object instanceof com.sqlapp.data.schemas.Function
					|| object instanceof com.sqlapp.data.schemas.Procedure;
		});
		result.addAll(before);
		var views = new java.util.ArrayList<SqlOperation>();
		for (var operation : remaining) {
			if (isView(operation))
				views.add(operation);
			else
				result.add(operation);
		}
		result.addAll(plan.foreignTables);
		result.addAll(views);
		result.addAll(plan.after);
		return result;
	}

	private static boolean isView(SqlOperation operation) {
		return operation.getOriginal() instanceof com.sqlapp.data.schemas.View
				|| operation.getTarget() instanceof com.sqlapp.data.schemas.View;
	}

	private static void move(List<SqlOperation> source, List<SqlOperation> target,
			java.util.function.Predicate<SqlOperation> predicate) {
		var iterator = source.iterator();
		while (iterator.hasNext()) {
			var operation = iterator.next();
			if (predicate.test(operation)) {
				target.add(operation);
				iterator.remove();
			}
		}
	}
}
