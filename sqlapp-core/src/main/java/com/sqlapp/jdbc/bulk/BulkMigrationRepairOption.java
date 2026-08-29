/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.io.Serializable;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** Options for replaying expected rows from mismatched verification chunks. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BulkMigrationRepairOption implements Serializable {
	private static final long serialVersionUID = 1L;
	@Builder.Default
	private final boolean verifyExpectedHashes = true;
	@Builder.Default
	private final BulkUpsertOption bulkUpsertOption = BulkUpsertOption.builder()
			.useTransaction(true).build();
}
