/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.migration.MigrationModel;
import com.sqlapp.data.schemas.migration.MigrationModelSource;
import com.sqlapp.data.schemas.migration.MigrationModelUnitTest;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.SqlParser;

/**
 * Shared identifier, fixture and template handling for model executors. The
 * statements that create or replace a relation stay in the owning dialect.
 */
public abstract class AbstractMigrationModelExecutor implements MigrationModelExecutor {
	protected final Dialect dialect;

	protected AbstractMigrationModelExecutor(final Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	@Override
	public String relation(final MigrationModel model) {
		return qualified(model.schemaName(), model.name());
	}

	@Override
	public String sourceRelation(final MigrationModelSource source) {
		if (source.isFile()) {
			return fileRelation(source);
		}
		return qualified(source.schemaName(), source.tableName());
	}

	/** Relation expression that reads a data file; unsupported unless overridden. */
	protected String fileRelation(final MigrationModelSource source) {
		throw new UnsupportedOperationException(
				dialect.getProductName() + " cannot read a file source directly: " + source.name());
	}

	@Override
	public String fixtureRelation(final MigrationModelUnitTest.Fixture fixture) {
		if (fixture.sql() != null) {
			return "(" + fixture.sql().strip() + ")";
		}
		final List<String> columns = fixture.columns().stream().map(this::identifier).toList();
		final StringBuilder sql = new StringBuilder("(SELECT ");
		if (fixture.rows().isEmpty()) {
			for (int i = 0; i < columns.size(); i++) {
				sql.append(i == 0 ? "" : ", ").append("NULL AS ").append(columns.get(i));
			}
			return sql.append(" WHERE 1=0)").toString();
		}
		sql.append("* FROM (VALUES ");
		for (int r = 0; r < fixture.rows().size(); r++) {
			sql.append(r == 0 ? "(" : ", (");
			final List<Object> row = fixture.rows().get(r);
			for (int i = 0; i < row.size(); i++) {
				sql.append(i == 0 ? "" : ", ").append(literal(row.get(i)));
			}
			sql.append(')');
		}
		return sql.append(") AS fixture(").append(String.join(", ", columns)).append("))").toString();
	}

	/** SQL literal for a fixture value: NULL, number, boolean or quoted text. */
	protected String literal(final Object value) {
		if (value == null) {
			return "NULL";
		}
		if (value instanceof BigDecimal decimal) {
			return decimal.toPlainString();
		}
		if (value instanceof Number) {
			return value.toString();
		}
		if (value instanceof Boolean bool) {
			return bool ? "TRUE" : "FALSE";
		}
		return "'" + value.toString().replace("'", "''") + "'";
	}

	protected String qualified(final String schemaName, final String name) {
		return schemaName == null ? identifier(name) : identifier(schemaName) + "." + identifier(name);
	}

	/** Always-quoted identifier, so case and reserved words are preserved. */
	@Override
	public String identifier(final String name) {
		Objects.requireNonNull(name, "name");
		final String unquoted = dialect.unQuote(name);
		final String close = String.valueOf(dialect.getCloseQuote());
		return dialect.getOpenQuote() + unquoted.replace(close, close + close) + close;
	}

	/** Executes one statement through the sqlapp comment-template. */
	protected void execute(final Connection connection, final String sql, final Map<String, Object> parameters)
			throws SQLException {
		final ParametersContext context = new ParametersContext();
		context.putAll(parameters);
		new JdbcHandler(SqlParser.getInstance().parse(dialect, sql)).execute(connection, context);
	}
}
