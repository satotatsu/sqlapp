/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.io.Serializable;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

/** Options for replaying expected rows from mismatched verification chunks. */
@Getter
@Builder
public class BulkMigrationRepairOption implements Serializable {
	private static final long serialVersionUID = 1L;

	public static BulkMigrationRepairOption defaults() {
		return builder().build();
	}

	@Builder.Default
	private final boolean verifyExpectedHashes = true;
	/** Maximum expected rows retained before writing; zero means unlimited. */
	@Builder.Default
	private final long maxBufferedRows = 0;
	/** Optional one-to-one source-to-target column renames. */
	@Builder.Default
	private final Map<String, String> columnMappings = Map.of();
	@Builder.Default
	private final BulkUpsertOption bulkUpsertOption = BulkUpsertOption.builder()
			.useTransaction(true).build();

	private BulkMigrationRepairOption(final boolean verifyExpectedHashes,
			final long maxBufferedRows, final Map<String, String> columnMappings,
			final BulkUpsertOption bulkUpsertOption) {
		if (maxBufferedRows < 0) {
			throw new IllegalArgumentException("maxBufferedRows must not be negative");
		}
		this.verifyExpectedHashes = verifyExpectedHashes;
		this.maxBufferedRows = maxBufferedRows;
		this.columnMappings = Map.copyOf(java.util.Objects.requireNonNull(columnMappings,
				"columnMappings"));
		this.bulkUpsertOption = java.util.Objects.requireNonNull(bulkUpsertOption,
				"bulkUpsertOption");
	}
}
