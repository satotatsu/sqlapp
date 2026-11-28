package com.sqlapp.data.db.dialect.defaultvalue;

import com.sqlapp.data.db.datatype.DataType;

/**
 * Semantics of a current date/time expression.
 *
 * @param point     when the value is evaluated
 * @param basis     clock or zone the value is expressed in
 * @param kind      kind of value produced
 * @param precision fractional second digits, or {@code null} when unknown
 */
public record TemporalSemantics(EvaluationPoint point, TimeBasis basis, TemporalKind kind, Integer precision) {

	public TemporalSemantics {
		java.util.Objects.requireNonNull(point, "point");
		java.util.Objects.requireNonNull(basis, "basis");
		java.util.Objects.requireNonNull(kind, "kind");
	}

	/** Returns a copy producing the given kind. */
	public TemporalSemantics withKind(final TemporalKind kind) {
		return new TemporalSemantics(point, basis, kind, kind == TemporalKind.DATE ? Integer.valueOf(0) : precision);
	}

	/** Returns a copy with the given precision. */
	public TemporalSemantics withPrecision(final Integer precision) {
		return new TemporalSemantics(point, basis, kind, precision);
	}

	/** When a current time value is evaluated. */
	public enum EvaluationPoint {
		/** Start of the current transaction (for example PostgreSQL CURRENT_TIMESTAMP). */
		TRANSACTION_START,
		/** Start of the current statement (for example SQL Server GETDATE()). */
		STATEMENT_START,
		/** Each evaluation (for example PostgreSQL clock_timestamp()). */
		CALL,
		/** Not known or product specific. */
		UNKNOWN
	}

	/** Clock or time zone the value is expressed in. */
	public enum TimeBasis {
		/** Local time in the session time zone. */
		SESSION_TIME_ZONE,
		/** Local time of the database server operating system clock. */
		SERVER_CLOCK,
		/** UTC without an offset. */
		UTC,
		/** An absolute instant (a value with time zone). */
		INSTANT,
		/** Not known. */
		UNKNOWN
	}

	/** Kind of temporal value. */
	public enum TemporalKind {
		DATE, TIME, TIME_WITH_TIME_ZONE, DATETIME, DATETIME_WITH_TIME_ZONE;

		/**
		 * Returns the kind stored by a column type, or {@code null} for types that
		 * are not handled as ordinary date/time values.
		 */
		public static TemporalKind of(final DataType dataType) {
			if (dataType == null) {
				return null;
			}
			return switch (dataType) {
			case DATE -> DATE;
			case TIME -> TIME;
			case TIME_WITH_TIMEZONE -> TIME_WITH_TIME_ZONE;
			case DATETIME, SMALLDATETIME, TIMESTAMP -> DATETIME;
			case TIMESTAMP_WITH_TIMEZONE -> DATETIME_WITH_TIME_ZONE;
			default -> null;
			};
		}

		/** Whether values of this kind include a time of day. */
		public boolean hasTime() {
			return this != DATE;
		}

		/** Whether values of this kind include a date. */
		public boolean hasDate() {
			return this == DATE || this == DATETIME || this == DATETIME_WITH_TIME_ZONE;
		}

		/** Whether values of this kind include a time zone offset. */
		public boolean hasTimeZone() {
			return this == TIME_WITH_TIME_ZONE || this == DATETIME_WITH_TIME_ZONE;
		}
	}
}
