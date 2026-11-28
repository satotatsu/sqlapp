package com.sqlapp.data.db.dialect.defaultvalue;

import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens.Token;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens.Type;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TemporalKind;
import com.sqlapp.data.schemas.Column;

/**
 * Standard SQL recognition of basic literals. It recognizes no current
 * date/time expressions because their semantics are product specific.
 */
public class StandardDefaultExpressionSupport implements DefaultExpressionSupport {

	private final Dialect dialect;

	public StandardDefaultExpressionSupport(final Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	protected Dialect getDialect() {
		return dialect;
	}

	@Override
	public DefaultExpression parse(final String sql, final Column column) {
		final List<Token> all = DefaultExpressionTokens.tokenize(sql);
		if (all == null || all.isEmpty()) {
			return new DefaultExpression.Unrecognized(sql);
		}
		final DefaultExpression expression = recognize(sql, all, column);
		return expression instanceof DefaultExpression.Unrecognized ? new DefaultExpression.Unrecognized(sql)
				: expression;
	}

	@Override
	public DefaultExpression recognize(final String sql, final List<Token> range, final Column column) {
		if (range.isEmpty()) {
			return new DefaultExpression.Unrecognized("");
		}
		final List<Token> tokens = DefaultExpressionTokens.unwrap(range);
		final DefaultExpression literal = parseLiteral(tokens, column);
		if (literal != null) {
			return literal;
		}
		final DefaultExpression other = parseExpression(tokens, column);
		return other != null ? other : new DefaultExpression.Unrecognized(DefaultExpressionTokens.text(sql, range));
	}

	/**
	 * Recognizes NULL, numbers and string literals.
	 *
	 * @param tokens tokens without enclosing parentheses
	 * @param column column whose value the expression produces, or {@code null}
	 * @return the literal, or {@code null}
	 */
	protected DefaultExpression parseLiteral(final List<Token> tokens, final Column column) {
		if (tokens.size() == 1 && tokens.getFirst().isKeyword("NULL")) {
			return new DefaultExpression.NullValue();
		}
		if (tokens.size() == 1 && (tokens.getFirst().isKeyword("TRUE") || tokens.getFirst().isKeyword("FALSE"))) {
			return new DefaultExpression.BooleanLiteral(tokens.getFirst().isKeyword("TRUE"));
		}
		final String bits = bitString(tokens);
		if (bits != null) {
			return new DefaultExpression.BitStringLiteral(bits);
		}
		final String number = DefaultExpressionTokens.number(tokens);
		if (number != null) {
			return new DefaultExpression.NumericLiteral(number);
		}
		if (tokens.size() == 1
				&& (tokens.getFirst().type() == Type.STRING || tokens.getFirst().type() == Type.NATIONAL_STRING)) {
			return new DefaultExpression.StringLiteral(tokens.getFirst().text());
		}
		return null;
	}

	/**
	 * Recognizes product-specific expressions such as current date/time
	 * functions. Tokens have their enclosing parentheses removed.
	 *
	 * @param tokens tokens without enclosing parentheses
	 * @param column column whose value the expression produces, or {@code null}
	 * @return the expression, or {@code null} when not recognized
	 */
	protected DefaultExpression parseExpression(final List<Token> tokens, final Column column) {
		return null;
	}

	@Override
	public List<TemporalCandidate> temporalCandidates(final TemporalKind kind) {
		return List.of();
	}

	@Override
	public String stringLiteral(final String value, final Column column) {
		return "'" + value.replace("'", "''") + "'";
	}

	@Override
	public String booleanLiteral(final boolean value, final Column column) {
		return value ? "TRUE" : "FALSE";
	}

	@Override
	public boolean canPreserve(final Dialect source, final String sql, final DefaultExpression parsed) {
		return source != null && Objects.equals(source.getProductName(), dialect.getProductName());
	}

	/**
	 * Returns the digits of a bit string literal {@code B'0101'} (either case of
	 * the prefix), or {@code null}.
	 */
	protected static String bitString(final List<Token> tokens) {
		if (tokens.size() == 2 && (tokens.get(0).isKeyword("B")) && tokens.get(1).type() == Type.STRING
				&& tokens.get(0).end() == tokens.get(1).start() && tokens.get(1).text().matches("[01]*")) {
			return tokens.get(1).text();
		}
		return null;
	}

	/** Whether the column stores national character data. */
	protected static boolean isNationalCharacter(final Column column) {
		if (column == null) {
			return false;
		}
		final DataType type = column.getDataType();
		return type == DataType.NCHAR || type == DataType.NVARCHAR || type == DataType.LONGNVARCHAR
				|| type == DataType.NCLOB;
	}

	/**
	 * Matches {@code name ( )} or {@code name ( digits )}, returning the
	 * precision argument, {@code -1} for no argument, or {@code null} when the
	 * tokens do not match.
	 */
	protected static Integer functionCall(final List<Token> tokens, final String name, final boolean allowPrecision) {
		if (tokens.isEmpty() || !tokens.getFirst().isKeyword(name)) {
			return null;
		}
		if (tokens.size() == 3 && tokens.get(1).isSymbol("(") && tokens.get(2).isSymbol(")")) {
			return -1;
		}
		if (allowPrecision && tokens.size() == 4 && tokens.get(1).isSymbol("(")
				&& tokens.get(2).type() == Type.NUMBER && tokens.get(2).text().matches("\\d") && tokens.get(3).isSymbol(")")) {
			return Integer.valueOf(tokens.get(2).text());
		}
		return null;
	}
}
