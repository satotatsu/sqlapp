package com.sqlapp.data.db.dialect.defaultvalue;

import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultExpressionTokens.Token;
import com.sqlapp.data.db.dialect.defaultvalue.TemporalSemantics.TemporalKind;
import com.sqlapp.data.schemas.Column;

/**
 * Dialect-specific recognition and rendering of column default expressions.
 * <p>
 * A dialect returns its implementation from
 * {@link Dialect#getDefaultExpressionSupport()}. Recognition consumes the
 * representation produced by the dialect's metadata readers.
 * </p>
 * <p>
 * Recognition of a token range ({@link #recognize(String, List, Column)}) and
 * candidates by expected kind ({@link #temporalCandidates(TemporalKind)}) do
 * not depend on a column default, so they can also serve expressions inside
 * CHECK constraints, computed columns or routine bodies, together with
 * {@link DefaultExpressionTokens#replace(String, List)} and
 * {@link TemporalTranslator}.
 * </p>
 */
public interface DefaultExpressionSupport {

	/**
	 * Recognizes a default expression read by this dialect.
	 *
	 * @param sql    default expression as stored in {@link Column#getDefaultValue()}
	 * @param column column owning the default
	 * @return recognized expression, or {@link DefaultExpression.Unrecognized}
	 */
	DefaultExpression parse(String sql, Column column);

	/**
	 * Recognizes the expression made of a contiguous token range of a longer
	 * source text.
	 *
	 * @param sql    source text the tokens were read from
	 * @param tokens contiguous tokens of the expression
	 * @param column column whose value the expression produces, or {@code null}
	 *               when there is no such column
	 * @return recognized expression, or {@link DefaultExpression.Unrecognized}
	 *         holding the source text of the range
	 */
	DefaultExpression recognize(String sql, List<Token> tokens, Column column);

	/**
	 * Current date/time expressions this dialect provides for a kind of value.
	 * Candidates are listed in preference order, which breaks ties during
	 * selection.
	 *
	 * @param kind expected kind of value
	 * @return candidates of that kind for this dialect version
	 */
	List<TemporalCandidate> temporalCandidates(TemporalKind kind);

	/**
	 * Current date/time expressions this dialect can use as the default of the
	 * column. The default returns the candidates for the kind the column stores.
	 * Override to apply column-specific restrictions.
	 *
	 * @param column target column after type conversion
	 * @return candidates legal for the column and this dialect version
	 */
	default List<TemporalCandidate> temporalCandidates(final Column column) {
		final TemporalKind kind = storedKind(column);
		return kind == null ? List.of() : temporalCandidates(kind);
	}

	/**
	 * Kind of date/time value the column stores in this dialect, or {@code null}
	 * when it is not an ordinary date/time column. The default follows the shared
	 * data type; dialects override it for product types the shared type cannot
	 * describe (for example MySQL {@code TIMESTAMP}, which stores an instant).
	 */
	default TemporalKind storedKind(final Column column) {
		return TemporalKind.of(column.getDataType());
	}

	/**
	 * Renders a character string literal for the column.
	 */
	String stringLiteral(String value, Column column);

	/**
	 * Renders a boolean literal for the column.
	 */
	String booleanLiteral(boolean value, Column column);

	/**
	 * Renders a bit string literal for the column.
	 *
	 * @param bits digits {@code 0} and {@code 1}
	 */
	default String bitLiteral(final String bits, final Column column) {
		return "B'" + bits + "'";
	}

	/**
	 * Whether a default expression read by the source dialect can be retained
	 * as is. The default accepts expressions from the same product.
	 *
	 * @param source source dialect
	 * @param sql    source default expression
	 * @param parsed source expression as recognized by the source dialect
	 * @return {@code true} to retain the expression
	 */
	boolean canPreserve(Dialect source, String sql, DefaultExpression parsed);

	/**
	 * Whether the default is part of an identity or sequence generator modeled
	 * on the column (for example PostgreSQL {@code nextval} of a serial column).
	 */
	default boolean isGeneratorDefault(final String sql, final Column column) {
		return false;
	}
}
