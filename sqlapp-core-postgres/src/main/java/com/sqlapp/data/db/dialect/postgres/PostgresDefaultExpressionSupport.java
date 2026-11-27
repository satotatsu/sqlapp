package com.sqlapp.data.db.dialect.postgres;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
 * PostgreSQL default expressions.
 * <p>
 * Recognizes the forms returned by {@code pg_get_expr}, for example
 * {@code CURRENT_TIMESTAMP}, {@code now()}, {@code 'a'::character varying},
 * {@code '-5'::integer} and {@code (statement_timestamp())::date}.
 * {@code CURRENT_TIMESTAMP}, {@code now()} and {@code LOCALTIMESTAMP} are the
 * start of the transaction; {@code statement_timestamp()} the start of the
 * statement; {@code clock_timestamp()} each call.
 * </p>
 */
public class PostgresDefaultExpressionSupport extends StandardDefaultExpressionSupport {

	private static final Set<String> CHARACTER_TYPES = Set.of("text", "character varying", "varchar", "character",
			"bpchar", "char", "name");

	private static final Set<String> NUMERIC_TYPES = Set.of("integer", "int", "int4", "bigint", "int8", "smallint",
			"int2", "numeric", "decimal", "real", "double precision", "float4", "float8");

	private static final int PRECISION = 6;

	public PostgresDefaultExpressionSupport(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected DefaultExpression parseLiteral(final List<Token> tokens, final Column column) {
		final DefaultExpression literal = super.parseLiteral(tokens, column);
		if (literal != null) {
			return literal;
		}
		if (tokens.size() == 1 && (tokens.getFirst().isKeyword("true") || tokens.getFirst().isKeyword("false"))) {
			return new DefaultExpression.BooleanLiteral(tokens.getFirst().isKeyword("true"));
		}
		final int cast = castIndex(tokens);
		if (cast < 0) {
			return null;
		}
		final List<Token> value = DefaultExpressionTokens.unwrap(tokens.subList(0, cast));
		final String type = typeName(tokens.subList(cast + 1, tokens.size()));
		if (type == null) {
			return null;
		}
		if (value.size() == 1 && value.getFirst().isKeyword("NULL")) {
			return new DefaultExpression.NullValue();
		}
		if (value.size() == 1 && value.getFirst().type() == Type.STRING) {
			final String text = value.getFirst().text();
			if (CHARACTER_TYPES.contains(type)) {
				return new DefaultExpression.StringLiteral(text);
			}
			if (NUMERIC_TYPES.contains(type) && text.matches("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?")) {
				return new DefaultExpression.NumericLiteral(text.startsWith("+") ? text.substring(1) : text);
			}
			if ("boolean".equals(type) && (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false"))) {
				return new DefaultExpression.BooleanLiteral(text.equalsIgnoreCase("true"));
			}
		}
		final String number = DefaultExpressionTokens.number(value);
		if (number != null && NUMERIC_TYPES.contains(type)) {
			return new DefaultExpression.NumericLiteral(number);
		}
		return null;
	}

	@Override
	protected DefaultExpression parseExpression(final List<Token> tokens, final Column column) {
		final TemporalSemantics function = currentTime(tokens);
		if (function != null) {
			return new DefaultExpression.CurrentTime(function);
		}
		final int cast = castIndex(tokens);
		if (cast > 0) {
			final TemporalSemantics inner = currentTime(DefaultExpressionTokens.unwrap(tokens.subList(0, cast)));
			final String type = typeName(tokens.subList(cast + 1, tokens.size()));
			if (inner != null && type != null) {
				final TemporalKind kind = switch (type) {
				case "date" -> TemporalKind.DATE;
				case "timestamp", "timestamp without time zone" -> TemporalKind.DATETIME;
				case "time", "time without time zone" -> TemporalKind.TIME;
				case "timestamp with time zone", "timestamptz" -> TemporalKind.DATETIME_WITH_TIME_ZONE;
				default -> null;
				};
				if (kind != null) {
					return new DefaultExpression.CurrentTime(local(inner, kind));
				}
			}
		}
		// f() AT TIME ZONE 'UTC'
		final int at = atTimeZoneIndex(tokens);
		if (at > 0) {
			final TemporalSemantics inner = currentTime(DefaultExpressionTokens.unwrap(tokens.subList(0, at)));
			final List<Token> zone = tokens.subList(at + 3, tokens.size());
			if (inner != null && inner.kind() == TemporalKind.DATETIME_WITH_TIME_ZONE && isUtc(zone)) {
				return new DefaultExpression.CurrentTime(
						new TemporalSemantics(inner.point(), TimeBasis.UTC, TemporalKind.DATETIME, inner.precision()));
			}
		}
		return null;
	}

	private static TemporalSemantics local(final TemporalSemantics instant, final TemporalKind kind) {
		if (instant.kind() != TemporalKind.DATETIME_WITH_TIME_ZONE || kind == TemporalKind.DATETIME_WITH_TIME_ZONE) {
			return instant.withKind(kind);
		}
		return new TemporalSemantics(instant.point(), TimeBasis.SESSION_TIME_ZONE, kind,
				kind == TemporalKind.DATE ? Integer.valueOf(0) : instant.precision());
	}

	private static boolean isUtc(final List<Token> zone) {
		if (zone.isEmpty() || zone.getFirst().type() != Type.STRING) {
			return false;
		}
		final String name = zone.getFirst().text();
		if (!name.equalsIgnoreCase("UTC") && !name.equalsIgnoreCase("Etc/UTC")) {
			return false;
		}
		return zone.size() == 1 || zone.size() == 3 && zone.get(1).isSymbol("::") && zone.get(2).isKeyword("text");
	}

	private static int atTimeZoneIndex(final List<Token> tokens) {
		for (int i = 1; i + 2 < tokens.size(); i++) {
			if (tokens.get(i).isKeyword("AT") && tokens.get(i + 1).isKeyword("TIME")
					&& tokens.get(i + 2).isKeyword("ZONE")) {
				return i;
			}
		}
		return -1;
	}

	/** Index of a top-level {@code ::} cast, or -1. */
	private static int castIndex(final List<Token> tokens) {
		int depth = 0;
		for (int i = 0; i < tokens.size(); i++) {
			final Token token = tokens.get(i);
			if (token.isSymbol("(")) {
				depth++;
			} else if (token.isSymbol(")")) {
				depth--;
			} else if (depth == 0 && token.isSymbol("::")) {
				return i;
			}
		}
		return -1;
	}

	/** Lower case type name of a cast target without modifiers, or {@code null}. */
	private static String typeName(final List<Token> tokens) {
		if (tokens.isEmpty()) {
			return null;
		}
		final StringBuilder builder = new StringBuilder();
		for (final Token token : tokens) {
			if (token.type() != Type.IDENTIFIER && token.type() != Type.QUOTED_IDENTIFIER) {
				return null;
			}
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			builder.append(token.text().toLowerCase(Locale.ROOT));
		}
		return builder.toString();
	}

	private static TemporalSemantics currentTime(final List<Token> tokens) {
		if (tokens.isEmpty()) {
			return null;
		}
		if (tokens.size() == 1) {
			final Token token = tokens.getFirst();
			if (token.isKeyword("CURRENT_TIMESTAMP")) {
				return instant(EvaluationPoint.TRANSACTION_START, PRECISION);
			}
			if (token.isKeyword("LOCALTIMESTAMP")) {
				return session(TemporalKind.DATETIME, PRECISION);
			}
			if (token.isKeyword("CURRENT_DATE")) {
				return session(TemporalKind.DATE, 0);
			}
			if (token.isKeyword("LOCALTIME")) {
				return session(TemporalKind.TIME, PRECISION);
			}
			if (token.isKeyword("CURRENT_TIME")) {
				return new TemporalSemantics(EvaluationPoint.TRANSACTION_START, TimeBasis.SESSION_TIME_ZONE,
						TemporalKind.TIME_WITH_TIME_ZONE, PRECISION);
			}
			return null;
		}
		Integer precision = functionCall(tokens, "CURRENT_TIMESTAMP", true);
		if (precision != null && precision >= 0) {
			return instant(EvaluationPoint.TRANSACTION_START, precision);
		}
		precision = functionCall(tokens, "LOCALTIMESTAMP", true);
		if (precision != null && precision >= 0) {
			return session(TemporalKind.DATETIME, precision);
		}
		if (functionCall(tokens, "now", false) != null
				|| functionCall(tokens, "transaction_timestamp", false) != null) {
			return instant(EvaluationPoint.TRANSACTION_START, PRECISION);
		}
		if (functionCall(tokens, "statement_timestamp", false) != null) {
			return instant(EvaluationPoint.STATEMENT_START, PRECISION);
		}
		if (functionCall(tokens, "clock_timestamp", false) != null) {
			return instant(EvaluationPoint.CALL, PRECISION);
		}
		return null;
	}

	private static TemporalSemantics instant(final EvaluationPoint point, final int precision) {
		return new TemporalSemantics(point, TimeBasis.INSTANT, TemporalKind.DATETIME_WITH_TIME_ZONE, precision);
	}

	private static TemporalSemantics session(final TemporalKind kind, final int precision) {
		return new TemporalSemantics(EvaluationPoint.TRANSACTION_START, TimeBasis.SESSION_TIME_ZONE, kind, precision);
	}

	private static TemporalSemantics at(final EvaluationPoint point, final TimeBasis basis, final TemporalKind kind) {
		return new TemporalSemantics(point, basis, kind, kind == TemporalKind.DATE ? 0 : PRECISION);
	}

	@Override
	public List<TemporalCandidate> temporalCandidates(final Column column) {
		final TemporalKind kind = TemporalKind.of(column.getDataType());
		if (kind == null) {
			return List.of();
		}
		final Dialect dialect = getDialect();
		final var transaction = EvaluationPoint.TRANSACTION_START;
		final var statement = EvaluationPoint.STATEMENT_START;
		final var call = EvaluationPoint.CALL;
		final var session = TimeBasis.SESSION_TIME_ZONE;
		final var utc = TimeBasis.UTC;
		final List<TemporalCandidate> candidates = new ArrayList<>();
		switch (kind) {
		case DATETIME_WITH_TIME_ZONE -> {
			candidates.add(new TemporalCandidate(dialect.getCurrentTimestampWithTimeZoneFunction(),
					at(transaction, TimeBasis.INSTANT, kind)));
			candidates.add(new TemporalCandidate("statement_timestamp()", at(statement, TimeBasis.INSTANT, kind)));
			candidates.add(new TemporalCandidate("clock_timestamp()", at(call, TimeBasis.INSTANT, kind)));
		}
		case DATETIME -> {
			candidates.add(new TemporalCandidate("LOCALTIMESTAMP", at(transaction, session, kind)));
			candidates.add(
					new TemporalCandidate("CAST(statement_timestamp() AS timestamp)", at(statement, session, kind)));
			candidates.add(new TemporalCandidate("CAST(clock_timestamp() AS timestamp)", at(call, session, kind)));
			candidates.add(new TemporalCandidate("(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')", at(transaction, utc, kind)));
			candidates
					.add(new TemporalCandidate("(statement_timestamp() AT TIME ZONE 'UTC')", at(statement, utc, kind)));
			candidates.add(new TemporalCandidate("(clock_timestamp() AT TIME ZONE 'UTC')", at(call, utc, kind)));
		}
		case DATE -> {
			candidates.add(new TemporalCandidate("CURRENT_DATE", at(transaction, session, kind)));
			candidates.add(new TemporalCandidate("CAST(statement_timestamp() AS date)", at(statement, session, kind)));
			candidates.add(new TemporalCandidate("CAST(clock_timestamp() AS date)", at(call, session, kind)));
			candidates.add(new TemporalCandidate("CAST(CURRENT_TIMESTAMP AT TIME ZONE 'UTC' AS date)",
					at(transaction, utc, kind)));
			candidates.add(new TemporalCandidate("CAST(statement_timestamp() AT TIME ZONE 'UTC' AS date)",
					at(statement, utc, kind)));
		}
		case TIME -> {
			candidates.add(new TemporalCandidate("LOCALTIME", at(transaction, session, kind)));
			candidates.add(new TemporalCandidate("CAST(statement_timestamp() AS time)", at(statement, session, kind)));
			candidates.add(new TemporalCandidate("CAST(clock_timestamp() AS time)", at(call, session, kind)));
			candidates.add(new TemporalCandidate("CAST(statement_timestamp() AT TIME ZONE 'UTC' AS time)",
					at(statement, utc, kind)));
		}
		case TIME_WITH_TIME_ZONE ->
			candidates.add(new TemporalCandidate("CURRENT_TIME", at(transaction, session, kind)));
		}
		return candidates;
	}

	@Override
	public boolean isGeneratorDefault(final String sql, final Column column) {
		final List<Token> tokens = DefaultExpressionTokens.tokenize(sql);
		return column.isIdentity() && tokens != null && !tokens.isEmpty() && tokens.getFirst().isKeyword("nextval");
	}
}
