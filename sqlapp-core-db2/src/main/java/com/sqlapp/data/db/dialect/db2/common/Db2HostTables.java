/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

/**
 * Specifics keys for host table and column attributes that have no shared
 * Schema property. Values survive XML and clone like other specifics.
 */
public final class Db2HostTables {
	/** Table: z/OS database of the table space, or of an implicit table space. */
	public static final String DATABASE = "DB2_HOST_DATABASE";
	/** Table: z/OS encoding scheme EBCDIC, ASCII or UNICODE (CCSID clause). */
	public static final String ENCODING_SCHEME = "DB2_HOST_ENCODING_SCHEME";
	/** Table: IBM i system (short) name when it differs from the SQL name. */
	public static final String SYSTEM_NAME = "DB2_HOST_SYSTEM_NAME";
	/** Table: "Y" when versioning uses ON DELETE ADD EXTRA ROW. */
	public static final String VERSIONING_EXTRA_ROW = "DB2_HOST_VERSIONING_EXTRA_ROW";
	/** Table: "HISTORY" for a z/OS history table (SYSTABLES.TYPE H). */
	public static final String TABLE_KIND = "DB2_HOST_TABLE_KIND";
	/**
	 * Column: ROW_BEGIN, ROW_END or TRANSACTION_START_ID. DDL comes from the
	 * table's SYSTEM_TIME period and versioning; the marker guards against a model
	 * edit that would silently turn it into an ordinary column.
	 */
	public static final String GENERATION = "DB2_HOST_GENERATION";
	/** Column: row change timestamp, "A" (ALWAYS) or "D" (BY DEFAULT). */
	public static final String ROW_CHANGE = "DB2_HOST_ROW_CHANGE";
	/** Column: "Y" for IMPLICITLY HIDDEN. */
	public static final String HIDDEN = "DB2_HOST_HIDDEN";
	/** Column: IBM i explicit CCSID of a character, graphic or LOB column. */
	public static final String CCSID = "DB2_HOST_CCSID";
	/** Column: z/OS character subtype "S" (SBCS) or "M" (MIXED). */
	public static final String SUBTYPE = "DB2_HOST_SUBTYPE";

	/** Internal transient reader markers; never present on an assembled table. */
	public static final String TMP_PERIOD = "DB2_HOST_TMP_PERIOD";
	public static final String TMP_PARTITION_KEY_SEQ = "DB2_HOST_TMP_PARTKEY_SEQ";
	public static final String TMP_PARTITION_KEY_ORDER = "DB2_HOST_TMP_PARTKEY_ORDER";

	private Db2HostTables() {
	}
}
