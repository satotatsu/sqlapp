/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.Oracle10g;
import com.sqlapp.data.db.dialect.oracle.Oracle11g;
import com.sqlapp.data.db.dialect.oracle.Oracle11gR2;
import com.sqlapp.data.db.dialect.oracle.Oracle18c;
import com.sqlapp.data.schemas.PartitioningType;

/** Version boundaries shared by structured CREATE and composite maintenance. */
final class OraclePartitionVersions {
	private OraclePartitionVersions() {
	}

	static void validate(PartitioningType root, PartitioningType sub, Dialect dialect) {
		if (sub == null)
			return;
		if (sub != PartitioningType.Range && sub != PartitioningType.List && sub != PartitioningType.Hash)
			throw new IllegalArgumentException("Unsupported Oracle subpartition type: " + sub);
		if (root == PartitioningType.Hash) {
			// HASH-* was added in 12.2; this library's Oracle12c also represents 12.1.
			if (!(dialect instanceof Oracle18c))
				throw new IllegalArgumentException(
						"Composite HASH partitioning requires Oracle 18c or later in this dialect model");
		} else if (root == PartitioningType.List || sub == PartitioningType.Range) {
			if (!(dialect instanceof Oracle11g) && !(dialect instanceof Oracle11gR2))
				throw new IllegalArgumentException("LIST-* and RANGE-RANGE require Oracle 11g or later");
		} else if (sub == PartitioningType.List && !(dialect instanceof Oracle10g)) {
			throw new IllegalArgumentException("RANGE-LIST requires Oracle 10g or later in this dialect model");
		}
	}
}
