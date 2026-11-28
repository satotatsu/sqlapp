package com.sqlapp.data.db.dialect.defaultvalue;

import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;

/**
 * Dialect-specific recognition and rendering of column default expressions.
 * <p>
 * A dialect returns its implementation from
 * {@link Dialect#getDefaultExpressionSupport()}. Recognition consumes the
 * representation produced by the dialect's metadata readers.
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
	 * Current date/time expressions this dialect can use as the default of the
	 * column. Each candidate's kind is the kind stored by the column. Candidates
	 * are listed in preference order, which breaks ties during selection.
	 *
	 * @param column target column after type conversion
	 * @return candidates legal for the column and this dialect version
	 */
	List<TemporalCandidate> temporalCandidates(Column column);

	/**
	 * Renders a character string literal for the column.
	 */
	String stringLiteral(String value, Column column);

	/**
	 * Renders a boolean literal for the column.
	 */
	String booleanLiteral(boolean value, Column column);

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
