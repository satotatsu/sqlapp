/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.verification;

import com.sqlapp.data.db.command.migration.Status;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.migration.MigrationFreshnessCheck;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.SqlParser;

/**
 * Evaluates watermark lag and verification recency without changing either
 * database.
 */
public final class MigrationCutoverAssessor {

	private MigrationCutoverAssessor() {
	}

	public static MigrationCutoverReport assess(final Connection source, final Connection target,
			final List<MigrationFreshnessCheck> checks, final Instant lastVerifiedAt,
			final Duration maximumVerificationAge, final Instant now) throws SQLException {
		final Instant assessedAt = java.util.Objects.requireNonNull(now, "now");
		final Duration verificationAge = lastVerifiedAt == null ? null : Duration.between(lastVerifiedAt, assessedAt);
		final List<MigrationCutoverReport.Freshness> results = new ArrayList<>();
		MigrationCutoverReport.Status overall = MigrationCutoverReport.Status.READY;
		for (final MigrationFreshnessCheck check : checks) {
			final Instant sourceValue = watermark(source, check, true);
			final Instant targetValue = watermark(target, check, false);
			final MigrationCutoverReport.Freshness result = result(check, sourceValue, targetValue);
			results.add(result);
			if (overall == MigrationCutoverReport.Status.READY
					&& result.status() != MigrationCutoverReport.Status.READY) {
				overall = result.status();
			}
		}
		if (maximumVerificationAge != null && (verificationAge == null || verificationAge.isNegative()
				|| verificationAge.compareTo(maximumVerificationAge) > 0)) {
			overall = MigrationCutoverReport.Status.NOT_READY_VERIFICATION_STALE;
		}
		return new MigrationCutoverReport(assessedAt, overall, verificationAge, results);
	}

	private static MigrationCutoverReport.Freshness result(final MigrationFreshnessCheck check, final Instant source,
			final Instant target) {
		if (source == null) {
			return new MigrationCutoverReport.Freshness(check.id(), null, target, null,
					MigrationCutoverReport.Status.NOT_READY_SOURCE_EMPTY);
		}
		if (target == null) {
			return new MigrationCutoverReport.Freshness(check.id(), source, null, null,
					MigrationCutoverReport.Status.NOT_READY_TARGET_EMPTY);
		}
		final Duration lag = Duration.between(target, source);
		final MigrationCutoverReport.Status status = lag.isNegative()
				? MigrationCutoverReport.Status.NOT_READY_TARGET_AHEAD
				: lag.compareTo(check.maximumLag()) > 0 ? MigrationCutoverReport.Status.NOT_READY_LAG_EXCEEDED
						: MigrationCutoverReport.Status.READY;
		return new MigrationCutoverReport.Freshness(check.id(), source, target, lag, status);
	}

	private static Instant watermark(final Connection connection, final MigrationFreshnessCheck check,
			final boolean source) throws SQLException {
		final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		final String sql = custom(check, source) ? source ? check.sourceSql() : check.targetSql()
				: "SELECT MAX(" + dialect.quote(check.watermarkColumn()) + ") FROM "
						+ dialect.getObjectFullName(check.catalogName(), check.schemaName(), check.tableName());
		final ParametersContext context = new ParametersContext();
		context.putAll(check.parameters());
		final Instant[] value = { null };
		final int[] rows = { 0 };
		new JdbcQueryHandler(SqlParser.getInstance().parse(dialect, sql), new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				if (rows[0]++ > 0) {
					throw new SQLException("Freshness query returned more than one row: " + check.id());
				}
				final Object raw = rs.getObject(1);
				if (raw instanceof Timestamp timestamp) {
					value[0] = timestamp.toInstant();
				} else if (raw instanceof Instant instant) {
					value[0] = instant;
				} else if (raw != null) {
					throw new SQLException("Freshness query must return a timestamp: " + check.id());
				}
			}
		}).execute(connection, context);
		if (rows[0] == 0) {
			throw new SQLException("Freshness query returned no row: " + check.id());
		}
		return value[0];
	}

	private static boolean custom(final MigrationFreshnessCheck check, final boolean source) {
		return source ? check.sourceSql() != null : check.targetSql() != null;
	}
}
