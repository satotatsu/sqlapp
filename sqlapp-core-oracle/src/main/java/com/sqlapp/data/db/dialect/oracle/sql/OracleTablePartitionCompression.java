/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.Oracle11gR2;
import com.sqlapp.data.schemas.AbstractPartition;

/** Explicit row-store compression for table partitions and subpartitions. */
final class OracleTablePartitionCompression {
	private OracleTablePartitionCompression() {
	}

	static String clause(AbstractPartition<?> partition, Dialect dialect) {
		if (partition.isCompression() && !(dialect instanceof Oracle11gR2))
			throw new IllegalArgumentException(
					"Explicit table subpartition compression requires Oracle11gR2 or later in this dialect model");
		String mode = partition.getCompressionType(), catalog = partition.getSpecifics().get("COMPRESS_FOR");
		if (mode != null && catalog != null && !mode.equals(catalog))
			throw new IllegalArgumentException("Conflicting table partition compression modes");
		if (mode == null)
			mode = catalog;
		if (!partition.isCompression()) {
			if (mode != null)
				throw new IllegalArgumentException("Compression mode requires table partition compression");
			return " NOCOMPRESS";
		}
		if (mode == null || "BASIC".equals(mode)) {
			String pctFree = partition.getSpecifics().get("PCT_FREE");
			if (pctFree != null && !"0".equals(pctFree))
				throw new IllegalArgumentException(
						"BASIC compression requires explicit PCT_FREE=0; arbitrary child PCTFREE cannot be recreated");
			return " COMPRESS";
		}
		if (("OLTP".equals(mode) || "ADVANCED".equals(mode)) && dialect instanceof Oracle11gR2)
			return " COMPRESS FOR OLTP";
		throw new IllegalArgumentException("Unsupported table partition compression mode or version: " + mode);
	}
}
