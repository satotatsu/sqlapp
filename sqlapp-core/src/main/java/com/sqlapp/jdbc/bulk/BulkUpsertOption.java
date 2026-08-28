/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.io.Serializable;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;

/** Options shared by staging-table bulk upsert implementations. */
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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
