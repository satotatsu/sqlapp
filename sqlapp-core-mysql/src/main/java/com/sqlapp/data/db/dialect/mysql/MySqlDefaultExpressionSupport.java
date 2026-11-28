package com.sqlapp.data.db.dialect.mysql;

import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpression;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens.Token;
import com.sqlapp.data.db.dialect.defaultvalue.StandardDefaultExpressionSupport;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalCandidate;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.EvaluationPoint;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TemporalKind;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TimeBasis;
import com.sqlapp.data.schemas.Column;

/**
 * MySQL default expressions.
 * <p>
 * Recognizes the forms the MySQL readers produce, for example
 * {@code CURRENT_TIMESTAMP(3)}, {@code (curdate())}, {@code (utc_timestamp())}
 * and {@code b'1'}, and MariaDB's {@code current_timestamp(3)} and
 * {@code curdate()}. MySQL evaluates {@code NOW()} and its synonyms once per
 * statement in the session time zone; {@code SYSDATE()} at each call.
 * </p>
 * <p>
 * A DEFAULT {@code CURRENT_TIMESTAMP} must use the column's fractional
 * precision. Other expressions require expression defaults (MySQL 8.0.13,
 * MariaDB 10.2).
 * </p>
 */
public class MySqlDefaultExpressionSupport extends StandardDefaultExpressionSupport {

	/** Functions that may also be written without parentheses. */
	private static final java.util.Set<String> BARE_KEYWORDS = java.util.Set.of("CURRENT_TIMESTAMP", "LOCALTIME",
			"LOCALTIMESTAMP", "CURRENT_DATE", "CURRENT_TIME");

	public MySqlDefaultExpressionSupport(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected DefaultExpression parseExpression(final List<Token> tokens, final Column column) {
		final TemporalSemantics semantics = currentTime(tokens);
		return semantics == null ? null : new DefaultExpression.CurrentTime(semantics);
	}

	private static TemporalSemantics currentTime(final List<Token> tokens) {
		final Integer timestamp = call(tokens, "CURRENT_TIMESTAMP", "NOW", "LOCALTIME", "LOCALTIMESTAMP");
		if (timestamp != null) {
			return semantics(EvaluationPoint.STATEMENT_START, TimeBasis.SESSION_TIME_ZONE, TemporalKind.DATETIME,
					timestamp);
		}
		final Integer date = call(tokens, "CURRENT_DATE", "CURDATE");
		if (date != null) {
			return semantics(EvaluationPoint.STATEMENT_START, TimeBasis.SESSION_TIME_ZONE, TemporalKind.DATE, 0);
		}
		final Integer time = call(tokens, "CURRENT_TIME", "CURTIME");
		if (time != null) {
			return semantics(EvaluationPoint.STATEMENT_START, TimeBasis.SESSION_TIME_ZONE, TemporalKind.TIME, time);
		}
		final Integer utc = call(tokens, "UTC_TIMESTAMP");
		if (utc != null) {
			return semantics(EvaluationPoint.STATEMENT_START, TimeBasis.UTC, TemporalKind.DATETIME, utc);
		}
		final Integer utcDate = call(tokens, "UTC_DATE");
		if (utcDate != null) {
			return semantics(EvaluationPoint.STATEMENT_START, TimeBasis.UTC, TemporalKind.DATE, 0);
		}
		final Integer utcTime = call(tokens, "UTC_TIME");
		if (utcTime != null) {
			return semantics(EvaluationPoint.STATEMENT_START, TimeBasis.UTC, TemporalKind.TIME, utcTime);
		}
		final Integer sysdate = call(tokens, "SYSDATE");
		if (sysdate != null) {
			return semantics(EvaluationPoint.CALL, TimeBasis.SESSION_TIME_ZONE, TemporalKind.DATETIME, sysdate);
		}
		return null;
	}

	/**
	 * Matches {@code name}, {@code name()} or {@code name(n)} for any of the
	 * names; returns the precision (0 without an argument) or {@code null}.
	 */
	private static Integer call(final List<Token> tokens, final String... names) {
		for (final String name : names) {
			if (tokens.size() == 1 && tokens.getFirst().isKeyword(name) && BARE_KEYWORDS.contains(name)) {
				return 0;
			}
			final Integer precision = functionCall(tokens, name, true);
			if (precision != null) {
				return precision < 0 ? 0 : precision;
			}
		}
		return null;
	}

	private static TemporalSemantics semantics(final EvaluationPoint point, final TimeBasis basis,
			final TemporalKind kind, final int precision) {
		return new TemporalSemantics(point, basis, kind, precision);
	}

	@Override
	public List<TemporalCandidate> temporalCandidates(final TemporalKind kind) {
		return candidates(kind, 0);
	}

	/** MySQL {@code TIMESTAMP} (read as TIMESTAMPVERSION) stores an instant. */
	@Override
	public TemporalKind storedKind(final Column column) {
		if (column.getDataType() == com.sqlapp.data.db.datatype.DataType.TIMESTAMPVERSION
				&& "TIMESTAMP".equalsIgnoreCase(column.getDataTypeName())) {
			return TemporalKind.DATETIME_WITH_TIME_ZONE;
		}
		return super.storedKind(column);
	}

	/** Candidates use the column's fractional precision, which MySQL requires. */
	@Override
	public List<TemporalCandidate> temporalCandidates(final Column column) {
		final TemporalKind kind = storedKind(column);
		if (kind == null) {
			return List.of();
		}
		final Long length = column.getLength();
		final int precision = length == null || length < 0 || length > 6 ? 0 : length.intValue();
		return candidates(kind, precision);
	}

	private List<TemporalCandidate> candidates(final TemporalKind kind, final int precision) {
		final String digits = precision > 0 ? "(" + precision + ")" : "";
		final var session = TimeBasis.SESSION_TIME_ZONE;
		final var statement = EvaluationPoint.STATEMENT_START;
		final List<TemporalCandidate> candidates = new ArrayList<>();
		final boolean expressions = supportsExpressionDefaults();
		switch (kind) {
		case DATETIME_WITH_TIME_ZONE -> candidates.add(new TemporalCandidate("CURRENT_TIMESTAMP" + digits,
				semantics(statement, TimeBasis.INSTANT, kind, precision)));
		case DATETIME -> {
			candidates.add(new TemporalCandidate("CURRENT_TIMESTAMP" + digits,
					semantics(statement, session, kind, precision)));
			if (expressions) {
				candidates.add(new TemporalCandidate("(UTC_TIMESTAMP" + (digits.isEmpty() ? "()" : digits) + ")",
						semantics(statement, TimeBasis.UTC, kind, precision)));
				candidates.add(new TemporalCandidate("(SYSDATE" + (digits.isEmpty() ? "()" : digits) + ")",
						semantics(EvaluationPoint.CALL, session, kind, precision)));
			}
		}
		case DATE -> {
			if (expressions) {
				candidates.add(new TemporalCandidate("(CURRENT_DATE)", semantics(statement, session, kind, 0)));
				candidates.add(new TemporalCandidate("(UTC_DATE())", semantics(statement, TimeBasis.UTC, kind, 0)));
			}
		}
		case TIME -> {
			if (expressions) {
				candidates.add(new TemporalCandidate("(CURRENT_TIME" + digits + ")",
						semantics(statement, session, kind, precision)));
				candidates.add(new TemporalCandidate("(UTC_TIME" + (digits.isEmpty() ? "()" : digits) + ")",
						semantics(statement, TimeBasis.UTC, kind, precision)));
			}
		}
		default -> {
		}
		}
		return candidates;
	}

	/** Expression defaults other than CURRENT_TIMESTAMP need MySQL 8.0.13 or later. */
	protected boolean supportsExpressionDefaults() {
		return getDialect() instanceof MySql8019;
	}

	/** MySQL treats a backslash as an escape character by default, so it is doubled. */
	@Override
	public String stringLiteral(final String value, final Column column) {
		return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
	}

	@Override
	public String booleanLiteral(final boolean value, final Column column) {
		return value ? "1" : "0";
	}

	@Override
	public String bitLiteral(final String bits, final Column column) {
		return "b'" + bits + "'";
	}
}
