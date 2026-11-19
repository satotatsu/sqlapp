package com.sqlapp.data.db.dialect;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.Objects;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.db.datatype.DataType;

/** Explicit conversion of a source column definition for a target dialect. */
public final class ColumnMigration {
	private ColumnMigration() {
	}

	public static Result convert(final Column source, final Dialect target) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(target, "target");
		return target.convertColumnForMigration(source);
	}

	private static final Pattern CURRENT_TIMESTAMP =
			Pattern.compile("CURRENT_TIMESTAMP(?:\\s*\\(\\s*([0-6])?\\s*\\))?", Pattern.CASE_INSENSITIVE);

	static Result mapNativeTimestamp(final Column source, final Dialect target, final int maxPrecision,
			final boolean fixedPrecision, final String currentTimeDefault) {
		final long precision = source.getLength() == null ? 0 : source.getLength();
		if (precision < 0 || precision > maxPrecision) {
			return new Result(null, List.of("Unsupported TIMESTAMP precision on "
					+ target.getProductName() + ": " + precision));
		}
		final var type = target.getDbDataTypes().getDbType(DataType.TIMESTAMP_WITH_TIMEZONE);
		if (type == null || type.getDataType() != DataType.TIMESTAMP_WITH_TIMEZONE) {
			return new Result(null, List.of("No native instant type on " + target.getProductName()));
		}
		final var issues = new ArrayList<String>();
		final Column candidate = source.clone().setDataType(DataType.TIMESTAMP_WITH_TIMEZONE)
				.setDataTypeName(type.getTypeName()).setLength(fixedPrecision ? maxPrecision : precision)
				.setDefaultValue(null).setOnUpdate(null);
		if (fixedPrecision && precision != maxPrecision) {
			issues.add(target.getProductName() + " uses fixed fractional precision " + maxPrecision
					+ " instead of " + precision);
		}
		final String defaultValue = source.getDefaultValue();
		if (defaultValue != null && !defaultValue.isBlank()) {
			final var match = CURRENT_TIMESTAMP.matcher(defaultValue.strip());
			if ("NULL".equalsIgnoreCase(defaultValue.strip()) && !source.isNotNull()) {
				candidate.setDefaultValue("NULL");
			} else if (currentTimeDefault != null && match.matches()
					&& (match.group(1) == null || Long.parseLong(match.group(1)) == precision)) {
				candidate.setDefaultValue(currentTimeDefault);
			} else {
				issues.add("Review TIMESTAMP DEFAULT on " + target.getProductName() + ": " + defaultValue);
			}
		}
		if (source.getOnUpdate() != null && !source.getOnUpdate().isBlank()) {
			issues.add(target.getProductName() + " requires manual handling for ON UPDATE: " + source.getOnUpdate());
		}
		return new Result(candidate, issues);
	}

	/** A proposed definition and any attributes requiring a migration decision. */
	public record Result(Column candidate, List<String> issues) {
		public Result {
			issues = List.copyOf(issues);
		}

		/** Returns a usable definition only when no decisions remain. */
		public Column requireComplete() {
			if (!issues.isEmpty()) {
				throw new IllegalStateException(String.join("; ", issues));
			}
			return Objects.requireNonNull(candidate, "candidate");
		}
	}
}

