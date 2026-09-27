/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration.assessment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.ObjectId;

/** Aggregate evidence for local tables only. No row samples or text values are retained.
 * Numeric and date extrema are aggregate values and may still be sensitive.
 * Integrity coverage is explicit; a scan does not certify a resolved target mapping. */
public record MigrationDataProfile(List<TableProfile> tables, List<IntegrityCheck> integrityChecks) {
	public MigrationDataProfile {
		tables = List.copyOf(tables);
		// Older version 2 reports have no integrityChecks property.
		integrityChecks = integrityChecks == null ? List.of() : List.copyOf(integrityChecks);
	}
	public MigrationDataProfile(final List<TableProfile> tables) { this(tables, List.of()); }

	public enum IntegrityKind { PRIMARY_KEY, UNIQUE_KEY, FOREIGN_KEY }
	public enum IntegrityCoverage { CHECKED, UNSUPPORTED, LIMIT_EXCEEDED }
	/** Counts apply only to completed checks. checkedRows excludes rows with any NULL
	 * key component; violationRows counts duplicate rows beyond the first or orphan rows.
	 * NULL semantics and comparison rules are explained by the provider in reason. */
	public record IntegrityCheck(ObjectId object, IntegrityKind kind, IntegrityCoverage coverage,
			List<String> columns, ObjectId relatedTable, List<String> relatedColumns,
			Long checkedRows, Long nullRows, Long violationRows, String reason) {
		public IntegrityCheck {
			Objects.requireNonNull(object, "object");
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(coverage, "coverage");
			Objects.requireNonNull(reason, "reason");
			columns = List.copyOf(columns);
			relatedColumns = List.copyOf(relatedColumns);
			if (coverage == IntegrityCoverage.CHECKED) {
				if (checkedRows == null || nullRows == null || violationRows == null
						|| checkedRows < 0 || nullRows < 0 || violationRows < 0 || violationRows > checkedRows) {
					throw new IllegalArgumentException("Completed integrity checks require valid nonnegative counts");
				}
			} else if (checkedRows != null || nullRows != null || violationRows != null) {
				throw new IllegalArgumentException("Incomplete integrity checks must not claim complete counts");
			}
		}
	}

	public enum Coverage { SCANNED, UNSUPPORTED }

	public record TableProfile(ObjectId table, long rowCount, List<ColumnProfile> columns) {
		public TableProfile {
			Objects.requireNonNull(table, "table");
			if (rowCount < 0) { throw new IllegalArgumentException("rowCount must not be negative"); }
			columns = List.copyOf(columns);
		}
	}

	/** Unsupported columns have null nullCount and no statistics, never an implied zero. */
	public record ColumnProfile(ObjectId column, String sourceType, Coverage coverage, Long nullCount,
			TextStatistics text, NumericStatistics numeric, DateTimeStatistics dateTime) {
		public ColumnProfile {
			Objects.requireNonNull(column, "column");
			Objects.requireNonNull(coverage, "coverage");
			if (coverage == Coverage.SCANNED && (nullCount == null || nullCount < 0)) {
				throw new IllegalArgumentException("Scanned columns require a nonnegative nullCount");
			}
			if (coverage == Coverage.UNSUPPORTED && (nullCount != null || text != null || numeric != null || dateTime != null)) {
				throw new IllegalArgumentException("Unsupported columns must not claim data statistics");
			}
		}
	}

	/** Maximum lengths are null when there are no non-null text values. UTF-8 is a
	 * reference encoding, not an assertion about the target database character set. */
	public record TextStatistics(long emptyCount, Long maximumCodePoints, Long maximumUtf16Units, Long maximumUtf8Bytes) { }
	/** Decimal digit requirements exclude insignificant trailing zeroes. Non-finite
	 * floating values are counted separately and excluded from the extrema. */
	public record NumericStatistics(BigDecimal minimum, BigDecimal maximum, Integer maximumIntegerDigits,
			Integer maximumScale, long nonFiniteCount) { }
	/** ISO local date/time extrema contain no inferred timezone; precision is observed. */
	public record DateTimeStatistics(String minimum, String maximum, Integer maximumFractionalDigits) { }
}
