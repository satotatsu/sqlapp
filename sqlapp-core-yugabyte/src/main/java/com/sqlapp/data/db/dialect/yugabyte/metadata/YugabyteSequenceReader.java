/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.PostgresSequenceReader;

/**
 * Uses the shared set-based PostgreSQL sequence reader on both YSQL engine majors.
 */
public class YugabyteSequenceReader extends PostgresSequenceReader {
	public YugabyteSequenceReader(Dialect dialect) {
		super(dialect);
	}

}
