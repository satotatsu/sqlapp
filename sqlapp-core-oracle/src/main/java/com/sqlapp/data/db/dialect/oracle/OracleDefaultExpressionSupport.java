package com.sqlapp.data.db.dialect.oracle;

import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpression;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens.Token;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens.Type;
import com.sqlapp.data.db.dialect.defaultvalue.StandardDefaultExpressionSupport;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalCandidate;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.EvaluationPoint;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TemporalKind;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TimeBasis;
import com.sqlapp.data.schemas.Column;

/**
 * Oracle default expressions.
 * <p>
 * Oracle {@code DATE} includes the time of day and is read as DATETIME.
 * {@code SYSDATE} and {@code SYSTIMESTAMP} use the database server's operating
 * system clock; {@code CURRENT_DATE}, {@code CURRENT_TIMESTAMP} and
 * {@code LOCALTIMESTAMP} the session time zone. They are evaluated once per
 * statement. {@code SYSTIMESTAMP} stored in a column without a time zone keeps
 * the server's local time. Readers return national literals as {@code u'...'}.
 * </p>
 */
public class OracleDefaultExpressionSupport extends StandardDefaultExpressionSupport {

	private static final int PRECISION = 6;

	public OracleDefaultExpressionSupport(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected DefaultExpression parseLiteral(final List<Token> tokens, final Column column) {
		final DefaultExpression literal = super.parseLiteral(tokens, column);
		if (literal != null) {
			return literal;
		}
		// u'...' as returned for N'...'; escapes with a backslash are not interpreted.
		if (tokens.size() == 2 && tokens.get(0).isKeyword("u") && tokens.get(1).type() == Type.STRING
				&& tokens.get(0).end() == tokens.get(1).start() && tokens.get(1).text().indexOf('\\') < 0) {
			return new DefaultExpression.StringLiteral(tokens.get(1).text());
		}
		return null;
	}

	@Override
	protected DefaultExpression parseExpression(final List<Token> tokens, final Column column) {
		final TemporalSemantics semantics = currentTime(tokens, column);
		return semantics == null ? null : new DefaultExpression.CurrentTime(semantics);
	}

	private TemporalSemantics currentTime(final List<Token> raw, final Column column) {
		final List<Token> tokens = DefaultExpressionTokens.unwrap(raw);
		final var statement = EvaluationPoint.STATEMENT_START;
		if (tokens.size() == 1) {
			final Token token = tokens.getFirst();
			if (token.isKeyword("SYSDATE")) {
				return new TemporalSemantics(statement, TimeBasis.SERVER_CLOCK, TemporalKind.DATETIME, 0);
			}
			if (token.isKeyword("SYSTIMESTAMP")) {
				return systimestamp(column, PRECISION);
			}
			if (token.isKeyword("CURRENT_DATE")) {
				return new TemporalSemantics(statement, TimeBasis.SESSION_TIME_ZONE, TemporalKind.DATETIME, 0);
			}
			if (token.isKeyword("CURRENT_TIMESTAMP")) {
				return new TemporalSemantics(statement, TimeBasis.INSTANT, TemporalKind.DATETIME_WITH_TIME_ZONE,
						PRECISION);
			}
			if (token.isKeyword("LOCALTIMESTAMP")) {
				return new TemporalSemantics(statement, TimeBasis.SESSION_TIME_ZONE, TemporalKind.DATETIME, PRECISION);
			}
			return null;
		}
		Integer precision = functionCall(tokens, "SYSTIMESTAMP", true);
		if (precision != null && precision >= 0) {
			return systimestamp(column, precision);
		}
		precision = functionCall(tokens, "CURRENT_TIMESTAMP", true);
		if (precision != null && precision >= 0) {
			return new TemporalSemantics(statement, TimeBasis.INSTANT, TemporalKind.DATETIME_WITH_TIME_ZONE,
					precision);
		}
		precision = functionCall(tokens, "LOCALTIMESTAMP", true);
		if (precision != null && precision >= 0) {
			return new TemporalSemantics(statement, TimeBasis.SESSION_TIME_ZONE, TemporalKind.DATETIME, precision);
		}
		if (tokens.size() == 4 && tokens.get(1).isSymbol("(") && tokens.get(3).isSymbol(")")) {
			// TRUNC(SYSDATE) / TRUNC(CURRENT_DATE): the date at midnight.
			if (tokens.get(0).isKeyword("TRUNC") && (tokens.get(2).isKeyword("SYSDATE")
					|| tokens.get(2).isKeyword("CURRENT_DATE"))) {
				return new TemporalSemantics(statement, tokens.get(2).isKeyword("SYSDATE") ? TimeBasis.SERVER_CLOCK
						: TimeBasis.SESSION_TIME_ZONE, TemporalKind.DATE, 0);
			}
			// SYS_EXTRACT_UTC(SYSTIMESTAMP)
			if (tokens.get(0).isKeyword("SYS_EXTRACT_UTC") && (tokens.get(2).isKeyword("SYSTIMESTAMP")
					|| tokens.get(2).isKeyword("CURRENT_TIMESTAMP"))) {
				return new TemporalSemantics(statement, TimeBasis.UTC, TemporalKind.DATETIME, PRECISION);
			}
		}
		return null;
	}

	/** SYSTIMESTAMP: an instant, or server-local time where the column has no time zone. */
	private TemporalSemantics systimestamp(final Column column, final int precision) {
		final TemporalKind stored = column == null ? null : storedKind(column);
		if (stored != null && !stored.hasTimeZone()) {
			return new TemporalSemantics(EvaluationPoint.STATEMENT_START, TimeBasis.SERVER_CLOCK,
					stored == TemporalKind.DATE ? TemporalKind.DATE : TemporalKind.DATETIME,
					stored == TemporalKind.DATE ? 0 : precision);
		}
		return new TemporalSemantics(EvaluationPoint.STATEMENT_START, TimeBasis.INSTANT,
				TemporalKind.DATETIME_WITH_TIME_ZONE, precision);
	}

	@Override
	public List<TemporalCandidate> temporalCandidates(final TemporalKind kind) {
		final Dialect dialect = getDialect();
		final var statement = EvaluationPoint.STATEMENT_START;
		final List<TemporalCandidate> candidates = new ArrayList<>();
		if (kind == null) {
			return candidates;
		}
		switch (kind) {
		case DATETIME -> {
			candidates.add(new TemporalCandidate("SYSTIMESTAMP",
					new TemporalSemantics(statement, TimeBasis.SERVER_CLOCK, kind, PRECISION)));
			candidates.add(
					new TemporalCandidate("SYSDATE", new TemporalSemantics(statement, TimeBasis.SERVER_CLOCK, kind, 0)));
			candidates.add(new TemporalCandidate("LOCALTIMESTAMP",
					new TemporalSemantics(statement, TimeBasis.SESSION_TIME_ZONE, kind, PRECISION)));
			candidates.add(new TemporalCandidate("CURRENT_DATE",
					new TemporalSemantics(statement, TimeBasis.SESSION_TIME_ZONE, kind, 0)));
			candidates.add(new TemporalCandidate("SYS_EXTRACT_UTC(SYSTIMESTAMP)",
					new TemporalSemantics(statement, TimeBasis.UTC, kind, PRECISION)));
		}
		case DATETIME_WITH_TIME_ZONE -> {
			candidates.add(new TemporalCandidate(dialect.getCurrentTimestampWithTimeZoneFunction(),
					new TemporalSemantics(statement, TimeBasis.INSTANT, kind, PRECISION)));
		}
		case DATE -> {
			candidates.add(new TemporalCandidate(dialect.getCurrentDateFunction(),
					new TemporalSemantics(statement, TimeBasis.SESSION_TIME_ZONE, kind, 0)));
			candidates.add(new TemporalCandidate("TRUNC(SYSDATE)",
					new TemporalSemantics(statement, TimeBasis.SERVER_CLOCK, kind, 0)));
		}
		default -> {
		}
		}
		return candidates;
	}

	@Override
	public String stringLiteral(final String value, final Column column) {
		final String literal = super.stringLiteral(value, column);
		return isNationalCharacter(column) ? "N" + literal : literal;
	}

	/** BOOLEAN exists from Oracle 23ai; earlier versions store flags as numbers. */
	@Override
	public String booleanLiteral(final boolean value, final Column column) {
		if (getDialect() instanceof Oracle23ai) {
			return value ? "TRUE" : "FALSE";
		}
		return value ? "1" : "0";
	}
}
