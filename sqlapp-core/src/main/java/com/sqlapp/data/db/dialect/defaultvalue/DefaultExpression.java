package com.sqlapp.data.db.dialect.defaultvalue;

/**
 * Conversion-local representation of a column default expression.
 * <p>
 * Only a bounded set of patterns is recognized; anything else is kept as
 * {@link Unrecognized} with its original SQL. This model is not serialized and
 * is not part of the Schema XML contract.
 * </p>
 */
public sealed interface DefaultExpression {

	/** SQL {@code NULL}. Distinct from no default and from the string {@code 'NULL'}. */
	record NullValue() implements DefaultExpression {
	}

	/**
	 * Character string literal.
	 *
	 * @param value unescaped value
	 */
	record StringLiteral(String value) implements DefaultExpression {
	}

	/**
	 * Boolean literal such as {@code TRUE}.
	 *
	 * @param value value
	 */
	record BooleanLiteral(boolean value) implements DefaultExpression {
	}

	/**
	 * Bit string literal such as {@code B'101'}.
	 *
	 * @param bits digits {@code 0} and {@code 1}
	 */
	record BitStringLiteral(String bits) implements DefaultExpression {
		public BitStringLiteral {
			if (bits == null || !bits.matches("[01]*")) {
				throw new IllegalArgumentException("Bit string literal must contain only 0 and 1.");
			}
		}
	}

	/**
	 * Numeric literal.
	 *
	 * @param text SQL text of the number including an optional leading minus sign
	 */
	record NumericLiteral(String text) implements DefaultExpression {
	}

	/**
	 * A current date/time expression with known semantics.
	 *
	 * @param semantics evaluation point, time basis, result kind and precision
	 */
	record CurrentTime(TemporalSemantics semantics) implements DefaultExpression {
	}

	/**
	 * An expression that is not recognized and must not be interpreted.
	 *
	 * @param sql original SQL
	 */
	record Unrecognized(String sql) implements DefaultExpression {
	}
}
