/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.*;
import com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile;
import com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile.*;

import io.github.spannm.jackcess.DatabaseBuilder;
import io.github.spannm.jackcess.DateTimeType;

/** One-pass local scalar profiling. Only explicitly selected columns are decoded. */
final class MdbDataProfiler {
	private static final Set<String> TEXT = Set.of("TEXT", "MEMO", "GUID");
	private static final Set<String> NUMBER = Set.of("BYTE", "INT", "LONG", "BIG_INT", "MONEY", "NUMERIC", "FLOAT", "DOUBLE");
	private static final Set<String> DATE = Set.of("SHORT_DATE_TIME", "EXT_DATE_TIME");
	private MdbDataProfiler() { }

	record Result(MigrationDataProfile profile, List<Finding> findings) { }

	static Result scan(final Path file, final Schema schema) throws IOException {
		final var tables = new ArrayList<TableProfile>();
		final var findings = new ArrayList<Finding>();
		try (final var database = new DatabaseBuilder().withPath(file).withReadOnly(true).open()) {
			database.setDateTimeType(DateTimeType.LOCAL_DATE_TIME);
			database.setEvaluateExpressions(false);
			database.setLinkResolver((source, name) -> { throw new IOException("Linked sources must not be opened during data profiling"); });
			for (final var table : schema.getTables()) {
				final var metadata = database.getTableMetaData(table.getName());
				if (metadata == null || metadata.isLinked() || metadata.isSystem()) {
					throw new IOException("Source table changed or is not local: " + table.getName());
				}
				final var source = metadata.open(database);
				final var states = new ArrayList<ColumnState>();
				for (final var column : table.getColumns()) {
					states.add(new ColumnState(column, new ObjectId(schema.getCatalogName(), schema.getName(), "column",
							column.getName(), table.getName())));
				}
				final var selected = states.stream().filter(state -> state.supported).map(state -> state.column.getName()).toList();
				final var cursor = source.newCursor().toCursor();
				long rows = 0;
				io.github.spannm.jackcess.Row row;
				while ((row = cursor.getNextRow(selected)) != null) {
					rows++;
					for (final var state : states) {
						if (state.supported) { state.accept(row.get(state.column.getName())); }
					}
				}
				final var columns = new ArrayList<ColumnProfile>();
				for (final var state : states) {
					columns.add(state.finish());
					if (!state.supported) {
						findings.add(new Finding("access.data.column-not-scanned", Severity.REVIEW, Evidence.MANUAL_CHECK, state.id,
								"This column type was excluded from scalar profiling.", "Inspect binary, OLE, complex or unknown content separately.", null));
					} else if (state.column.isNotNull() && state.nulls > 0) {
						findings.add(new Finding("access.data.required-null", Severity.BLOCKER, Evidence.DATABASE, state.id,
								state.nulls + " NULL values were observed in a column marked NOT NULL.",
								"Resolve the values or explicitly revise the required-column mapping before loading.", null));
					}
					if (state.nonFinite > 0) {
						findings.add(new Finding("access.data.non-finite", Severity.WARNING, Evidence.DATABASE, state.id,
								state.nonFinite + " non-finite numeric values were observed and excluded from extrema.",
								"Define and test an explicit target representation for NaN and infinity.", null));
					}
				}
				tables.add(new TableProfile(new ObjectId(schema.getCatalogName(), schema.getName(), "table", table.getName()), rows, columns));
			}
		}
		return new Result(new MigrationDataProfile(tables), List.copyOf(findings));
	}

	private static final class ColumnState {
		private final Column column;
		private final ObjectId id;
		private final String type;
		private final boolean supported;
		private long nulls;
		private long empty;
		private Long maxCodePoints;
		private Long maxUtf16;
		private Long maxUtf8;
		private BigDecimal minimum;
		private BigDecimal maximum;
		private Integer maxIntegerDigits;
		private Integer maxScale;
		private long nonFinite;
		private LocalDateTime minDate;
		private LocalDateTime maxDate;
		private Integer fractionalDigits;

		ColumnState(final Column column, final ObjectId id) {
			this.column = column;
			this.id = id;
			final String nativeType = column.getSpecifics().get(MdbFileLoader.SOURCE_TYPE, String.class);
			type = nativeType == null ? "UNKNOWN" : nativeType;
			supported = TEXT.contains(type) || NUMBER.contains(type) || DATE.contains(type) || "BOOLEAN".equals(type);
		}

		void accept(final Object value) throws IOException {
			if (value == null) { nulls++; return; }
			if (TEXT.contains(type) && value instanceof String text) {
				if (text.isEmpty()) { empty++; }
				maxCodePoints = max(maxCodePoints, text.codePointCount(0, text.length()));
				maxUtf16 = max(maxUtf16, text.length());
				// Report malformed Unicode rather than silently replacing it in byte-length calculations.
				maxUtf8 = max(maxUtf8, StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(text)).remaining());
			} else if (NUMBER.contains(type) && value instanceof Number number) {
				if (number instanceof Double d && !Double.isFinite(d) || number instanceof Float f && !Float.isFinite(f)) {
					nonFinite++;
					return;
				}
				final BigDecimal decimal = "BYTE".equals(type) && number instanceof Byte b
						? BigDecimal.valueOf(Byte.toUnsignedInt(b))
						: number instanceof BigDecimal d ? d : new BigDecimal(number.toString());
				minimum = minimum == null || decimal.compareTo(minimum) < 0 ? decimal : minimum;
				maximum = maximum == null || decimal.compareTo(maximum) > 0 ? decimal : maximum;
				final BigDecimal normalized = decimal.stripTrailingZeros();
				final int integerDigits = normalized.signum() == 0 ? 0 : Math.max(0, normalized.precision() - normalized.scale());
				final int scale = Math.max(0, normalized.scale());
				maxIntegerDigits = maxIntegerDigits == null ? integerDigits : Math.max(maxIntegerDigits, integerDigits);
				maxScale = maxScale == null ? scale : Math.max(maxScale, scale);
			} else if (DATE.contains(type) && value instanceof LocalDateTime date) {
				minDate = minDate == null || date.isBefore(minDate) ? date : minDate;
				maxDate = maxDate == null || date.isAfter(maxDate) ? date : maxDate;
				int nanos = date.getNano();
				int digits = nanos == 0 ? 0 : 9;
				while (digits > 0 && nanos % 10 == 0) { nanos /= 10; digits--; }
				fractionalDigits = fractionalDigits == null ? digits : Math.max(fractionalDigits, digits);
			} else if (!("BOOLEAN".equals(type) && value instanceof Boolean)) {
				throw new IOException("Unexpected value type while profiling " + id + "; no raw value is included");
			}
		}

		ColumnProfile finish() {
			if (!supported) { return new ColumnProfile(id, type, Coverage.UNSUPPORTED, null, null, null, null); }
			return new ColumnProfile(id, type, Coverage.SCANNED, nulls,
					TEXT.contains(type) ? new TextStatistics(empty, maxCodePoints, maxUtf16, maxUtf8) : null,
					NUMBER.contains(type) ? new NumericStatistics(minimum, maximum, maxIntegerDigits, maxScale, nonFinite) : null,
					DATE.contains(type) ? new DateTimeStatistics(minDate == null ? null : minDate.toString(),
							maxDate == null ? null : maxDate.toString(), fractionalDigits) : null);
		}
		private static long max(final Long previous, final long value) { return previous == null ? value : Math.max(previous, value); }
	}
}
