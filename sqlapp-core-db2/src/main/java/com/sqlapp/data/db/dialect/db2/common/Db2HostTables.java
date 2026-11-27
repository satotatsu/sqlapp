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
	/**
	 * Table: "HISTORY" or "ARCHIVE" for a z/OS history (SYSTABLES.TYPE H) or
	 * archive (TYPE R) table.
	 */
	public static final String TABLE_KIND = "DB2_HOST_TABLE_KIND";
	/** Table: z/OS archive table schema of an archive-enabled table. */
	public static final String ARCHIVE_SCHEMA = "DB2_HOST_ARCHIVE_SCHEMA";
	/** Table: z/OS archive table name of an archive-enabled table. */
	public static final String ARCHIVE_TABLE = "DB2_HOST_ARCHIVE_TABLE";
	/** Table: "Y" for z/OS APPEND YES. */
	public static final String APPEND = "DB2_HOST_APPEND";
	/** Table: "Y" for a z/OS VOLATILE table. */
	public static final String VOLATILE = "DB2_HOST_VOLATILE";
	/** Table: "Y" for z/OS DATA CAPTURE CHANGES. */
	public static final String DATA_CAPTURE = "DB2_HOST_DATA_CAPTURE";
	/** Table: z/OS AUDIT option, "CHANGES" or "ALL". */
	public static final String AUDIT = "DB2_HOST_AUDIT";
	/** Table: z/OS validation procedure program name (VALIDPROC). */
	public static final String VALIDPROC = "DB2_HOST_VALIDPROC";
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
