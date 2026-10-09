/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.db.dialect.postgres.Postgres110;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.data.schemas.OnNullCall;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SqlSecurity;

/** Changes attributes or a verified catalog body without dropping the routine or its ACL. */
public class PostgresAlterFunctionFactory extends SimpleSqlFactory<Function, PostgresSqlBuilder> {
	private static final Set<String> SUPPORTED = Set.of(
			SchemaProperties.DETERMINISTIC.getLabel(), SchemaProperties.STABLE.getLabel(),
			SchemaProperties.ON_NULL_CALL.getLabel(), SchemaProperties.SQL_SECURITY.getLabel(),
			SchemaProperties.REMARKS.getLabel(), SchemaProperties.STATEMENT.getLabel());

	protected boolean supportsProcedureSecurityAlter() { return true; }

	@Override
	public List<SqlOperation> createSql(Function function) {
		throw new UnsupportedOperationException("Routine ALTER requires original.diff(target)");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (!(difference.getOriginal() instanceof Function original)
				|| !(difference.getTarget() instanceof Function target))
			throw new IllegalArgumentException("Routine ALTER requires both original and target functions");
		var changed = difference.getChangedProperties();
		// A refreshed catalog template is supplemental, not an independently alterable attribute.
		var originalSpecifics = new java.util.HashMap<>(original.getSpecifics());
		var targetSpecifics = new java.util.HashMap<>(target.getSpecifics());
		originalSpecifics.remove("POSTGRES_ROUTINE_DDL_BASE64");
		targetSpecifics.remove("POSTGRES_ROUTINE_DDL_BASE64");
		if (originalSpecifics.equals(targetSpecifics)) changed.remove(SchemaProperties.SPECIFICS.getLabel());
		boolean body = changed.containsKey(SchemaProperties.STATEMENT.getLabel());
		boolean definition = changed.containsKey(SchemaProperties.DEFINITION.getLabel());
		boolean refreshedTemplate = !java.util.Objects.equals(
				original.getSpecifics().get("POSTGRES_ROUTINE_DDL_BASE64"),
				target.getSpecifics().get("POSTGRES_ROUTINE_DDL_BASE64"));
		if (body && (definition || refreshedTemplate)) {
			PostgresSqlBuilder name = createSqlBuilder(); name.name(original, true);
			if (!PostgresRoutineBodyReplacement.sameHeader(original, target, name.toString().trim(),
					"PROCEDURE".equals(original.getSpecifics().get("ROUTINE_KIND"))))
				throw new UnsupportedOperationException("Routine body snapshots differ outside their body; migrate definition attributes separately");
			changed.remove(SchemaProperties.DEFINITION.getLabel());
		}
		var unsupported = changed.keySet().stream().filter(key -> !SUPPORTED.contains(key)).toList();
		if (!unsupported.isEmpty()) throw new UnsupportedOperationException(
				"Routine ALTER does not support changes to " + unsupported
				+ "; provide an explicit migration for body, identity or signature changes");
		if (changed.isEmpty()) return List.of();
		String kind = original.getSpecifics().get("ROUTINE_KIND");
		boolean procedure = "PROCEDURE".equals(kind);
		if (kind != null && !procedure && !"FUNCTION".equals(kind))
			throw new IllegalArgumentException("Unsupported routine kind: " + kind);
		if (procedure && !(getDialect() instanceof Postgres110))
			throw new UnsupportedOperationException("Procedures require PostgreSQL 11 or a compatible dialect");
		if (original.getFunctionType() == FunctionType.Aggregate)
			throw new UnsupportedOperationException("Aggregate ALTER requires an explicit migration");
		boolean volatility = body && !procedure || changed.containsKey(SchemaProperties.DETERMINISTIC.getLabel())
				|| changed.containsKey(SchemaProperties.STABLE.getLabel());
		boolean nullCall = body && !procedure || changed.containsKey(SchemaProperties.ON_NULL_CALL.getLabel());
		boolean security = body || changed.containsKey(SchemaProperties.SQL_SECURITY.getLabel());
		if (procedure && (volatility || nullCall))
			throw new IllegalArgumentException("Procedures do not support volatility or null-input attributes");
		if (procedure && security && !supportsProcedureSecurityAlter())
			throw new UnsupportedOperationException("This dialect does not support ALTER PROCEDURE SECURITY; provide an explicit migration");
		List<SqlOperation> result = new ArrayList<>();
		if (body) {
			PostgresSqlBuilder name = createSqlBuilder();
			name.name(original, true);
			String ddl = PostgresRoutineBodyReplacement.replace(original, target, name.toString().trim(), procedure);
			addSql(result, createSqlBuilder()._add(ddl), SqlType.ALTER, target);
		}
		if (volatility || nullCall || security) {
			PostgresSqlBuilder builder = createSqlBuilder();
			builder.alter()._add(procedure ? " PROCEDURE " : " FUNCTION ")
					.specificName(original, getOptions().isDecorateSchemaName());
			if (volatility) builder.space()._add(Boolean.TRUE.equals(target.getDeterministic()) ? "IMMUTABLE"
					: target.getDeterministic() == null && Boolean.TRUE.equals(target.getStable()) ? "STABLE" : "VOLATILE");
			if (nullCall) builder.space()._add(target.getOnNullCall() == null
					? OnNullCall.CalledOnNullInput : target.getOnNullCall());
			if (security) builder.space()._add(target.getSqlSecurity() == null
					? SqlSecurity.Invoker : target.getSqlSecurity());
			addSql(result, builder, SqlType.ALTER, target);
		}
		if (changed.containsKey(SchemaProperties.REMARKS.getLabel())) {
			PostgresSqlBuilder builder = createSqlBuilder();
			builder.comment().on()._add(procedure ? " PROCEDURE " : " FUNCTION ")
					.specificName(original, getOptions().isDecorateSchemaName()).is().space();
			if (target.getRemarks() == null) builder._add("NULL");
			else builder.sqlChar(target.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, target);
		}
		return result;
	}
}
