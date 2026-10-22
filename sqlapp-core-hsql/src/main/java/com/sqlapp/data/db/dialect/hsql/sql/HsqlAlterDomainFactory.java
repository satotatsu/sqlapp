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
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.DomainCheckConstraint;

/** HSQLDB 2.x domain changes without replacing dependent columns. */
public class HsqlAlterDomainFactory extends SimpleSqlFactory<Domain, HsqlSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Domain domain) {
		throw new IllegalArgumentException("HSQLDB domain ALTER requires original.diff(target): " + domain.getName());
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (!(difference.getOriginal() instanceof Domain original)
				|| !(difference.getTarget() instanceof Domain target)) {
			throw new IllegalArgumentException("HSQLDB domain ALTER requires two domains");
		}
		// Reuse CREATE validation for target check expressions and characteristics.
		getSqlFactoryRegistry().createSql(target, SqlType.CREATE);
		var oldShape = original.clone();
		var newShape = target.clone();
		oldShape.setName(newShape.getName());
		oldShape.setDefaultValue(null);
		newShape.setDefaultValue(null);
		oldShape.getChecks().clear();
		newShape.getChecks().clear();
		if (!oldShape.equals(newShape)) {
			throw new IllegalArgumentException("HSQLDB domain ALTER supports defaults and named checks; "
					+ "other changes require an explicit migration: " + original.getName());
		}
		List<SqlOperation> result = new ArrayList<>();
		Domain current = original;
		if (!Objects.equals(original.getName(), target.getName())) {
			if (target.getName() == null || target.getName().isBlank())
				throw new IllegalArgumentException("HSQLDB domain rename requires a target name");
			// Let the engine reject dependencies before applying defaults/check changes.
			var sql = alter(original).rename().to().name(target.getName());
			addSql(result, sql, SqlType.ALTER, target);
			current = target;
		}
		for (var check : original.getChecks()) {
			var replacement = target.getChecks().get(check.getName());
			if (replacement == null || !check.equals(replacement)) {
				requireName(original, check);
				var sql = alter(current).space()._add("DROP CONSTRAINT").space().name(check.getName());
				addSql(result, sql, SqlType.ALTER, target);
			}
		}
		if (!Objects.equals(original.getDefaultValue(), target.getDefaultValue())) {
			var sql = alter(current);
			if (target.getDefaultValue() == null) {
				sql.space()._add("DROP DEFAULT");
			} else {
				sql.space()._add("SET DEFAULT ")._add(target.getDefaultValue());
			}
			addSql(result, sql, SqlType.ALTER, target);
		}
		for (var check : target.getChecks()) {
			var previous = original.getChecks().get(check.getName());
			if (previous == null || !check.equals(previous)) {
				requireName(target, check);
				var sql = alter(current).space()._add("ADD CONSTRAINT").space().name(check.getName()).space()
						._add("CHECK (")._add(check.getExpression())._add(")");
				addSql(result, sql, SqlType.ALTER, target);
			}
		}
		return result;
	}

	private HsqlSqlBuilder alter(Domain domain) {
		return createSqlBuilder().alter().space()._add("DOMAIN").name(domain, getOptions().isDecorateSchemaName());
	}

	private void requireName(Domain domain, DomainCheckConstraint check) {
		if (check.getName() == null || check.getName().isBlank()) {
			throw new IllegalArgumentException("HSQLDB domain ALTER requires a check name: " + domain.getName());
		}
	}
}
