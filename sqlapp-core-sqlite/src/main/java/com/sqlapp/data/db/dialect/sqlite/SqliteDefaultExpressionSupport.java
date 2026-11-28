package com.sqlapp.data.db.dialect.sqlite;

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
 * SQLite default expressions.
 * <p>
 * SQLite's {@code CURRENT_TIMESTAMP} is UTC text {@code YYYY-MM-DD HH:MM:SS};
 * {@code 'localtime'} uses the clock of the process running SQLite. Candidates
 * produce the {@code YYYY-MM-DD HH:MM:SS[.SSS]} text that sqlapp writes for
 * date/time values, with {@code +00:00} for values with a time zone. Expression
 * defaults are enclosed in parentheses as SQLite requires.
 * </p>
 */
public class SqliteDefaultExpressionSupport extends StandardDefaultExpressionSupport {

	private static final String MILLIS = "%Y-%m-%d %H:%M:%f";
	private static final String SECONDS = "%Y-%m-%d %H:%M:%S";

	public SqliteDefaultExpressionSupport(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected DefaultExpression parseExpression(final List<Token> tokens, final Column column) {
		final TemporalSemantics semantics = currentTime(tokens);
		return semantics == null ? null : new DefaultExpression.CurrentTime(semantics);
	}

	private static TemporalSemantics currentTime(final List<Token> raw) {
		final List<Token> tokens = DefaultExpressionTokens.unwrap(raw);
		if (tokens.size() == 1) {
			if (tokens.getFirst().isKeyword("CURRENT_TIMESTAMP")) {
				return semantics(TimeBasis.UTC, TemporalKind.DATETIME, 0);
			}
			if (tokens.getFirst().isKeyword("CURRENT_DATE")) {
				return semantics(TimeBasis.UTC, TemporalKind.DATE, 0);
			}
			if (tokens.getFirst().isKeyword("CURRENT_TIME")) {
				return semantics(TimeBasis.UTC, TemporalKind.TIME, 0);
			}
			return null;
		}
		// strftime(...) || '+00:00'
		if (tokens.size() >= 4 && tokens.get(tokens.size() - 3).isSymbol("|")
				&& tokens.get(tokens.size() - 2).isSymbol("|") && tokens.getLast().type() == Type.STRING
				&& "+00:00".equals(tokens.getLast().text())) {
			final TemporalSemantics inner = currentTime(tokens.subList(0, tokens.size() - 3));
			if (inner != null && inner.basis() == TimeBasis.UTC && inner.kind() == TemporalKind.DATETIME) {
				return new TemporalSemantics(inner.point(), TimeBasis.INSTANT, TemporalKind.DATETIME_WITH_TIME_ZONE,
						inner.precision());
			}
			return null;
		}
		final List<String> arguments = stringArguments(tokens);
		if (arguments == null || tokens.getFirst().type() != Type.IDENTIFIER) {
			return null;
		}
		final String name = tokens.getFirst().lower();
		final int now = "strftime".equals(name) ? 1 : 0;
		if (arguments.size() <= now || !"now".equalsIgnoreCase(arguments.get(now))) {
			return null;
		}
		final List<String> modifiers = arguments.subList(now + 1, arguments.size());
		final TimeBasis basis;
		if (modifiers.isEmpty() || modifiers.size() == 1 && "utc".equalsIgnoreCase(modifiers.getFirst())) {
			basis = TimeBasis.UTC;
		} else if (modifiers.size() == 1 && "localtime".equalsIgnoreCase(modifiers.getFirst())) {
			basis = TimeBasis.SERVER_CLOCK;
		} else {
			return null;
		}
		return switch (name) {
		case "datetime" -> semantics(basis, TemporalKind.DATETIME, 0);
		case "date" -> semantics(basis, TemporalKind.DATE, 0);
		case "time" -> semantics(basis, TemporalKind.TIME, 0);
		case "strftime" -> switch (arguments.getFirst()) {
			case MILLIS -> semantics(basis, TemporalKind.DATETIME, 3);
			case SECONDS -> semantics(basis, TemporalKind.DATETIME, 0);
			default -> null;
			};
		default -> null;
		};
	}

	/** Arguments of {@code name('a', 'b', ...)} when all are string literals, otherwise {@code null}. */
	private static List<String> stringArguments(final List<Token> tokens) {
		if (tokens.size() < 3 || !tokens.get(1).isSymbol("(") || !tokens.getLast().isSymbol(")")) {
			return null;
		}
		final List<String> arguments = new ArrayList<>();
		for (int i = 2; i < tokens.size() - 1; i++) {
			final Token token = tokens.get(i);
			if ((i - 2) % 2 == 0) {
				if (token.type() != Type.STRING) {
					return null;
				}
				arguments.add(token.text());
			} else if (!token.isSymbol(",")) {
				return null;
			}
		}
		return arguments;
	}

	private static TemporalSemantics semantics(final TimeBasis basis, final TemporalKind kind, final int precision) {
		return new TemporalSemantics(EvaluationPoint.STATEMENT_START, basis, kind, precision);
	}

	/** SQLite has no bit string literal; BIT values are stored as text. */
	@Override
	public String bitLiteral(final String bits, final Column column) {
		return "'" + bits + "'";
	}

	@Override
	public List<TemporalCandidate> temporalCandidates(final TemporalKind kind) {
		final Dialect dialect = getDialect();
		final var local = TimeBasis.SERVER_CLOCK;
		final var utc = TimeBasis.UTC;
		final List<TemporalCandidate> candidates = new ArrayList<>();
		if (kind == null) {
			return candidates;
		}
		switch (kind) {
		case DATETIME -> {
			candidates.add(new TemporalCandidate("(strftime('" + MILLIS + "','now','localtime'))",
					semantics(local, kind, 3)));
			candidates.add(new TemporalCandidate("(datetime('now','localtime'))", semantics(local, kind, 0)));
			candidates.add(new TemporalCandidate(dialect.getCurrentTimestampFunction(), semantics(utc, kind, 0)));
			candidates.add(new TemporalCandidate("(strftime('" + MILLIS + "','now'))", semantics(utc, kind, 3)));
		}
		case DATE -> {
			candidates.add(new TemporalCandidate("(date('now','localtime'))", semantics(local, kind, 0)));
			candidates.add(new TemporalCandidate(dialect.getCurrentDateFunction(), semantics(utc, kind, 0)));
		}
		case TIME -> {
			candidates.add(new TemporalCandidate("(time('now','localtime'))", semantics(local, kind, 0)));
			candidates.add(new TemporalCandidate(dialect.getCurrentTimeFunction(), semantics(utc, kind, 0)));
		}
		case DATETIME_WITH_TIME_ZONE -> candidates.add(new TemporalCandidate(
				"(strftime('" + MILLIS + "','now') || '+00:00')",
				new TemporalSemantics(EvaluationPoint.STATEMENT_START, TimeBasis.INSTANT, kind, 3)));
		default -> {
		}
		}
		return candidates;
	}
}
