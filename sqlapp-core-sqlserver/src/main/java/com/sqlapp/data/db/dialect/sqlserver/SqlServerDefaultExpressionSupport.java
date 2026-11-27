package com.sqlapp.data.db.dialect.sqlserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpression;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens.Token;
import com.sqlapp.data.db.dialect.defaultvalue.StandardDefaultExpressionSupport;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalCandidate;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.EvaluationPoint;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TemporalKind;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TimeBasis;
import com.sqlapp.data.schemas.Column;

/**
 * SQL Server default expressions.
 * <p>
 * Recognizes the forms stored by SQL Server after the reader removes the
 * enclosing parentheses, for example {@code getdate()},
 * {@code CONVERT([date],sysdatetime())}, {@code N'abc'} and {@code -5}. SQL
 * Server evaluates current time functions once per statement on the server
 * clock; it has no session time zone.
 * </p>
 */
public class SqlServerDefaultExpressionSupport extends StandardDefaultExpressionSupport {

	private static final Map<String, TemporalSemantics> FUNCTIONS = Map.of("getdate",
			semantics(TimeBasis.SERVER_CLOCK, TemporalKind.DATETIME, 3), "sysdatetime",
			semantics(TimeBasis.SERVER_CLOCK, TemporalKind.DATETIME, 7), "getutcdate",
			semantics(TimeBasis.UTC, TemporalKind.DATETIME, 3), "sysutcdatetime",
			semantics(TimeBasis.UTC, TemporalKind.DATETIME, 7), "sysdatetimeoffset",
			semantics(TimeBasis.INSTANT, TemporalKind.DATETIME_WITH_TIME_ZONE, 7));

	public SqlServerDefaultExpressionSupport(final Dialect dialect) {
		super(dialect);
	}

	private static TemporalSemantics semantics(final TimeBasis basis, final TemporalKind kind, final int precision) {
		return new TemporalSemantics(EvaluationPoint.STATEMENT_START, basis, kind, precision);
	}

	@Override
	protected DefaultExpression parseExpression(final List<Token> tokens, final Column column) {
		final TemporalSemantics function = currentTime(tokens);
		if (function != null) {
			return new DefaultExpression.CurrentTime(function);
		}
		// CONVERT([date],f()) / CONVERT([time],f()), which is also how CAST is stored.
		if (tokens.size() >= 6 && tokens.get(0).isKeyword("CONVERT") && tokens.get(1).isSymbol("(")
				&& tokens.get(3).isSymbol(",") && tokens.getLast().isSymbol(")")
				&& DefaultExpressionTokens.closingIndex(tokens, 1) == tokens.size() - 1) {
			final TemporalKind kind = convertedKind(tokens.get(2));
			final TemporalSemantics inner = currentTime(tokens.subList(4, tokens.size() - 1));
			if (kind != null && inner != null) {
				return new DefaultExpression.CurrentTime(inner.withKind(kind));
			}
		}
		// CAST(f() AS date) as written in DDL.
		if (tokens.size() >= 6 && tokens.get(0).isKeyword("CAST") && tokens.get(1).isSymbol("(")
				&& tokens.get(tokens.size() - 3).isKeyword("AS") && tokens.getLast().isSymbol(")")
				&& DefaultExpressionTokens.closingIndex(tokens, 1) == tokens.size() - 1) {
			final TemporalKind kind = convertedKind(tokens.get(tokens.size() - 2));
			final TemporalSemantics inner = currentTime(tokens.subList(2, tokens.size() - 3));
			if (kind != null && inner != null) {
				return new DefaultExpression.CurrentTime(inner.withKind(kind));
			}
		}
		return null;
	}

	private static TemporalKind convertedKind(final Token type) {
		if (type.isName("date")) {
			return TemporalKind.DATE;
		}
		if (type.isName("time")) {
			return TemporalKind.TIME;
		}
		return null;
	}

	private static TemporalSemantics currentTime(final List<Token> candidate) {
		final List<Token> tokens = DefaultExpressionTokens.unwrap(candidate);
		if (tokens.size() == 1 && tokens.getFirst().isKeyword("CURRENT_TIMESTAMP")) {
			return FUNCTIONS.get("getdate");
		}
		if (tokens.size() == 3 && tokens.get(1).isSymbol("(") && tokens.get(2).isSymbol(")")
				&& tokens.getFirst().type() == DefaultExpressionTokens.Type.IDENTIFIER) {
			return FUNCTIONS.get(tokens.getFirst().lower());
		}
		return null;
	}

	@Override
	public List<TemporalCandidate> temporalCandidates(final Column column) {
		final TemporalKind kind = TemporalKind.of(column.getDataType());
		if (kind == null) {
			return List.of();
		}
		final Dialect dialect = getDialect();
		final List<TemporalCandidate> candidates = new ArrayList<>();
		if (!supportsDateTime2Functions()) {
			if (kind == TemporalKind.DATETIME) {
				candidates.add(new TemporalCandidate(dialect.getCurrentTimestampFunction(), FUNCTIONS.get("getdate")));
				candidates.add(new TemporalCandidate("GETUTCDATE()", FUNCTIONS.get("getutcdate")));
			}
			return candidates;
		}
		final TemporalSemantics local = FUNCTIONS.get("sysdatetime");
		final TemporalSemantics utc = FUNCTIONS.get("sysutcdatetime");
		switch (kind) {
		case DATETIME -> {
			candidates.add(new TemporalCandidate(dialect.getCurrentTimestampFunction(), local));
			candidates.add(new TemporalCandidate("SYSUTCDATETIME()", utc));
		}
		case DATETIME_WITH_TIME_ZONE ->
			candidates.add(new TemporalCandidate(dialect.getCurrentTimestampWithTimeZoneFunction(),
					FUNCTIONS.get("sysdatetimeoffset")));
		case DATE -> {
			candidates.add(new TemporalCandidate(dialect.getCurrentDateFunction(), local.withKind(TemporalKind.DATE)));
			candidates.add(new TemporalCandidate("CONVERT (date, SYSUTCDATETIME())", utc.withKind(TemporalKind.DATE)));
		}
		case TIME -> {
			candidates.add(new TemporalCandidate("CONVERT (time, SYSDATETIME())", local.withKind(TemporalKind.TIME)));
			candidates.add(new TemporalCandidate("CONVERT (time, SYSUTCDATETIME())", utc.withKind(TemporalKind.TIME)));
		}
		default -> {
		}
		}
		return candidates;
	}

	/** SYSDATETIME and related functions are available from SQL Server 2008. */
	protected boolean supportsDateTime2Functions() {
		return getDialect() instanceof SqlServer2008;
	}

	@Override
	public String stringLiteral(final String value, final Column column) {
		final String literal = super.stringLiteral(value, column);
		return isNationalCharacter(column) ? "N" + literal : literal;
	}

	@Override
	public String booleanLiteral(final boolean value, final Column column) {
		return value ? "1" : "0";
	}

	@Override
	public boolean canPreserve(final Dialect source, final String sql, final DefaultExpression parsed) {
		if (!super.canPreserve(source, sql, parsed)) {
			return false;
		}
		if (parsed instanceof DefaultExpression.CurrentTime && !supportsDateTime2Functions()) {
			final String lower = sql.toLowerCase(Locale.ROOT);
			return !lower.contains("sysdatetime") && !lower.contains("sysutcdatetime");
		}
		return true;
	}
}
