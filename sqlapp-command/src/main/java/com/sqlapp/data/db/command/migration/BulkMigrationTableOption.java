/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.util.List;

import com.sqlapp.jdbc.bulk.BulkUpsertOption;

import lombok.Builder;
import lombok.Value;

/** Optional overrides for one table in the simple bulk migration facade. */
@Value
@Builder
public class BulkMigrationTableOption {
	String migrationId;
	Integer chunkSize;
	@Builder.Default
	List<String> keysetColumns = List.of();
	@Builder.Default
	List<String> verificationColumns = List.of();
	BulkUpsertOption upsertOption;

	public static BulkMigrationTableOption defaults() {
		return builder().build();
	}
}
