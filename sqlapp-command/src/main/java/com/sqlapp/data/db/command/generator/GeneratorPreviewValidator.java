package com.sqlapp.data.db.command.generator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import static com.sqlapp.data.db.command.generator.GeneratorPreviewDiagnostic.Code.*;

/**
 * Conservative, advisory checks against the canonical Schema column
 * definitions.
 */
final class GeneratorPreviewValidator {
	private GeneratorPreviewValidator() {
	}

	static List<GeneratorPreviewDiagnostic> validate(Table table, GeneratorPreviewRow row) {
		final List<GeneratorPreviewDiagnostic> diagnostics = new ArrayList<>();
		for (Column column : table.getColumns()) {
			if (!row.values().containsKey(column.getName()) || column.isIdentity()
					|| (column.getFormula() != null && !column.getFormula().isBlank())
					|| column.getArrayDimension() > 0) {
				continue;
			}
			final Object value = row.values().get(column.getName());
			if (value == null) {
				if (column.isNotNull()) {
					diagnostics.add(new GeneratorPreviewDiagnostic(row, column.getName(), NULL_FOR_NOT_NULL,
							"Explicit null sample for a NOT NULL column; a column default does not imply replacement of explicit null."));
				}
				continue;
			}
			final DataType type = column.getDataType();
			if (type == null) {
				continue;
			}
			if (type.isCharacter() && column.getCharacterSemantics() == CharacterSemantics.Char
					&& column.getLength() != null && column.getLength() > 0 && value instanceof String text) {
				final int characters = text.codePointCount(0, text.length());
				if (characters > column.getLength()) {
					diagnostics.add(new GeneratorPreviewDiagnostic(row, column.getName(), CHARACTER_LENGTH_EXCEEDED,
							"Unicode code-point length=" + characters + " exceeds declared character length="
									+ column.getLength() + "."));
				}
			}
			if ((type == DataType.DECIMAL || type == DataType.NUMERIC) && column.getLength() != null
					&& column.getLength() > 0 && column.getScale() != null && column.getScale() >= 0) {
				final BigDecimal decimal = exactDecimal(value);
				if (decimal == null) {
					continue;
				}
				final long integerDigits = (long) decimal.precision() - decimal.scale();
				final long allowedIntegerDigits = column.getLength() - column.getScale();
				if (decimal.signum() != 0 && integerDigits > allowedIntegerDigits) {
					diagnostics
							.add(new GeneratorPreviewDiagnostic(row, column.getName(), NUMERIC_INTEGER_DIGITS_EXCEEDED,
									"Decimal position=" + integerDigits + " exceeds precision-scale="
											+ allowedIntegerDigits + " (precision=" + column.getLength() + ", scale="
											+ column.getScale() + ")."));
				}
				final int fractionalDigits = decimal.scale() <= 0 ? 0
						: Math.max(0, decimal.stripTrailingZeros().scale());
				if (fractionalDigits > column.getScale()) {
					diagnostics.add(new GeneratorPreviewDiagnostic(row, column.getName(), NUMERIC_SCALE_EXCEEDED,
							"Nonzero fractional digits=" + fractionalDigits + " exceed declared scale="
									+ column.getScale() + "; database rounding/truncation is not simulated."));
				}
			}
		}
		return List.copyOf(diagnostics);
	}

	private static BigDecimal exactDecimal(Object value) {
		if (value instanceof BigDecimal decimal)
			return decimal;
		if (value instanceof BigInteger integer)
			return new BigDecimal(integer);
		if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
			return BigDecimal.valueOf(((Number) value).longValue());
		}
		return null;
	}
}
