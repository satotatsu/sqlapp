/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.util;

import java.util.Objects;
import com.sqlapp.data.schemas.PartitionScheme;

/** Optional partition scheme settings stored in the shared Schema model. */
public final class SqlServerPartitionOptions {
	public static final String NEXT_USED = "NEXT_USED";

	private SqlServerPartitionOptions() {
	}

	public static PartitionScheme nextUsed(PartitionScheme scheme, String filegroup) {
		if (filegroup == null || filegroup.isBlank())
			throw new IllegalArgumentException("NEXT USED requires a filegroup name; use clearNextUsed to remove it");
		Objects.requireNonNull(scheme, "scheme").getSpecifics().put(NEXT_USED, filegroup);
		return scheme;
	}

	public static PartitionScheme clearNextUsed(PartitionScheme scheme) {
		Objects.requireNonNull(scheme, "scheme").getSpecifics().put(NEXT_USED, "");
		return scheme;
	}

	/** null means unspecified; an empty string explicitly clears NEXT USED. */
	public static String getNextUsed(PartitionScheme scheme) {
		if (!scheme.getSpecifics().containsKey(NEXT_USED))
			return null;
		String value = scheme.getSpecifics().get(NEXT_USED);
		if (value == null || !value.isEmpty() && value.isBlank())
			throw new IllegalArgumentException("Invalid NEXT_USED value; use clearNextUsed to remove it");
		return value;
	}
}
