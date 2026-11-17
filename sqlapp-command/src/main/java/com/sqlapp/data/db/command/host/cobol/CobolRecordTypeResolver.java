/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.cobol;

/**
 * Identifies the record type of a record for {@link CobolCsvExtractCommand}
 * when the configured byte ranges and expressions are not enough. The
 * implementation needs a public no-argument constructor.
 */
public interface CobolRecordTypeResolver {

	/**
	 * @param record       the record
	 * @param recordNumber one-based record number in the data set
	 * @return the COBOL record name, or {@code null} when the record is not known
	 */
	String resolve(CobolRecordView record, long recordNumber);
}
