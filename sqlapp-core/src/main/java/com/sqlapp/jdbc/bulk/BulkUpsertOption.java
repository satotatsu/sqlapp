/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;

/** Options shared by staging-table bulk upsert implementations. */
@Builder
@ToString
@EqualsAndHashCode
@Getter
public class BulkUpsertOption implements Serializable {
	private static final long serialVersionUID = 1L;

	public static BulkUpsertOption defaults() {
		return builder().build();
	}

	/** Match columns; the target primary key is used when empty. */
	@Singular("keyColumn")
	private final List<String> keyColumns;
	/** Columns to update; all non-key, non-generated columns when empty. */
	@Singular("updateColumn")
	private final List<String> updateColumns;
	@Builder.Default
	private final boolean updateWhenMatched = true;
	@Builder.Default
	private final boolean insertWhenNotMatched = true;
	@Builder.Default
	private final boolean useTransaction = true;
	/** Handling of duplicate match keys in the source rows. */
	@Builder.Default
	private final BulkUpsertDuplicateKeyStrategy duplicateKeyStrategy = BulkUpsertDuplicateKeyStrategy.ERROR;
	/** Selector required when duplicateKeyStrategy is CUSTOM. */
	private final BulkUpsertDuplicateRowSelector duplicateRowSelector;
	/** Stable identity of the custom selector logic, required for CUSTOM. */
	private final String duplicateRowSelectorFingerprint;
	/** Optional deterministic staging name, mainly for diagnostics/tests. */
	private final String stagingTableName;
	@Builder.Default
	private final BulkOption bulkOption = BulkOption.defaults();

	private BulkUpsertOption(final List<String> keyColumns,
			final List<String> updateColumns, final boolean updateWhenMatched,
			final boolean insertWhenNotMatched, final boolean useTransaction,
			final BulkUpsertDuplicateKeyStrategy duplicateKeyStrategy,
			final BulkUpsertDuplicateRowSelector duplicateRowSelector,
			final String duplicateRowSelectorFingerprint,
			final String stagingTableName, final BulkOption bulkOption) {
		this.keyColumns = columns(keyColumns, "keyColumns");
		this.updateColumns = columns(updateColumns, "updateColumns");
		this.updateWhenMatched = updateWhenMatched;
		this.insertWhenNotMatched = insertWhenNotMatched;
		this.useTransaction = useTransaction;
		this.duplicateKeyStrategy = duplicateKeyStrategy;
		this.duplicateRowSelector = duplicateRowSelector;
		this.duplicateRowSelectorFingerprint = duplicateRowSelectorFingerprint;
		this.stagingTableName = stagingTableName;
		this.bulkOption = java.util.Objects.requireNonNull(bulkOption, "bulkOption");
		if (!updateWhenMatched && !insertWhenNotMatched) {
			throw new IllegalArgumentException("At least one upsert action must be enabled");
		}
		validateDuplicateKeyStrategy();
	}

	private static List<String> columns(final List<String> values, final String role) {
		final List<String> columns = List.copyOf(values);
		if (columns.stream().anyMatch(value -> value.isBlank())
				|| new HashSet<>(columns).size() != columns.size()) {
			throw new IllegalArgumentException(
					role + " must contain unique non-empty column names");
		}
		return columns;
	}

	void validateDuplicateKeyStrategy() {
		if (duplicateKeyStrategy == null) {
			throw new IllegalArgumentException("duplicateKeyStrategy must not be null");
		}
		if (duplicateKeyStrategy == BulkUpsertDuplicateKeyStrategy.CUSTOM) {
			if (duplicateRowSelector == null) {
				throw new IllegalArgumentException(
						"duplicateRowSelector is required for CUSTOM duplicate keys");
			}
			if (duplicateRowSelectorFingerprint == null
					|| duplicateRowSelectorFingerprint.isBlank()) {
				throw new IllegalArgumentException(
						"duplicateRowSelectorFingerprint is required for CUSTOM duplicate keys");
			}
		} else if (duplicateRowSelector != null
				|| (duplicateRowSelectorFingerprint != null
						&& !duplicateRowSelectorFingerprint.isBlank())) {
			throw new IllegalArgumentException(
					"duplicateRowSelector and its fingerprint require CUSTOM duplicate keys");
		}
	}
}
