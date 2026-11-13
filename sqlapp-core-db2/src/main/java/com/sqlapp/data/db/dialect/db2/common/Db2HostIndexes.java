/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

/**
 * Specifics keys for host index attributes that have no shared Index property.
 * Values survive XML and clone like other specifics.
 */
public final class Db2HostIndexes {
	/** "Y" for UNIQUE WHERE NOT NULL (z/OS UNIQUERULE N, IBM i IS_UNIQUE V). */
	public static final String UNIQUE_WHERE_NOT_NULL = "DB2_HOST_UNIQUE_WHERE_NOT_NULL";
	/** "Y" for a z/OS clustering index (CLUSTER). */
	public static final String CLUSTER = "DB2_HOST_CLUSTER";
	/**
	 * "Y" for PARTITIONED, "N" for an explicitly nonpartitioned index on a
	 * partitioned table.
	 */
	public static final String PARTITIONED = "DB2_HOST_PARTITIONED";

	/**
	 * "Y" when a z/OS unique index, primary key or unique constraint ends with
	 * BUSINESS_TIME WITHOUT OVERLAPS. The period columns are not repeated in the
	 * key column list.
	 */
	public static final String BUSINESS_TIME_WITHOUT_OVERLAPS = "DB2_HOST_BUSINESS_TIME_WITHOUT_OVERLAPS";

	private Db2HostIndexes() {
	}
}
