/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.Oracle12c;
import com.sqlapp.data.db.dialect.oracle.Oracle18c;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.AbstractPartition;
import com.sqlapp.data.schemas.Index;

/** Compression clauses are partition defaults; subpartitions inherit them. */
final class OracleIndexPartitionCompression {
	private OracleIndexPartitionCompression() { }

	static String mode(AbstractPartition<?> partition, String inherited) {
		String mode = partition.getCompressionType();
		String explicit = partition.getSpecifics().get("oracle.index.compression");
		if (explicit != null) {
			if (!"DISABLED".equals(explicit) || mode != null && !explicit.equals(mode))
				throw new IllegalArgumentException("Conflicting index partition compression: " + partition.getName());
			mode = explicit;
		}
		if (mode == null) return partition.isCompression() ? "ENABLED" : inherited;
		if (partition.isCompression() == "DISABLED".equals(mode))
			throw new IllegalArgumentException("Conflicting index partition compression: " + partition.getName());
		return mode;
	}

	static String rootMode(Index index) {
		return index.isCompression() ? index.getCompressionType() == null ? "ENABLED" : index.getCompressionType() : "DISABLED";
	}

	static void add(Index index, AbstractPartition<?> partition, Dialect dialect, OracleSqlBuilder builder) {
		String mode = mode(partition, rootMode(index));
		if (!partition.isCompression() && partition.getCompressionType() == null
				&& !partition.getSpecifics().containsKey("oracle.index.compression")) return;
		if (index.getPartitioning().getPartitioningType() == com.sqlapp.data.schemas.PartitioningType.Hash) {
			if (!mode.equals(rootMode(index)))
				throw new IllegalArgumentException("Hash index partition compression must inherit the index setting: " + partition.getName());
			return;
		}
		if ("DISABLED".equals(mode)) {
			builder.space()._add("NOCOMPRESS");
			return;
		}
		if (index.getIndexType() != null && index.getIndexType().isBitMap()
				|| "true".equalsIgnoreCase(index.getSpecifics().get("oracle.index.bitmap")))
			throw new IllegalArgumentException("Bitmap index partitions cannot specify compression: " + partition.getName());
		if ("ENABLED".equals(mode)) {
			if (!"ENABLED".equals(rootMode(index)))
				throw new IllegalArgumentException("Partition prefix compression requires index prefix compression: " + partition.getName());
			builder.space()._add("COMPRESS");
		} else if ("ADVANCED LOW".equals(mode) || "ADVANCED HIGH".equals(mode)) {
			if (!(dialect instanceof Oracle12c) || "ADVANCED HIGH".equals(mode) && !(dialect instanceof Oracle18c))
				throw new IllegalArgumentException("Advanced partition compression is not supported by this dialect: " + partition.getName());
			builder.space()._add("COMPRESS ")._add(mode);
		} else throw new IllegalArgumentException("Unsupported index partition compression: " + mode);
	}

	static void validateSubpartition(Index index, AbstractPartition<?> parent, AbstractPartition<?> sub) {
		String inherited = mode(parent, rootMode(index));
		if (!inherited.equals(mode(sub, inherited)))
			throw new IllegalArgumentException("Index subpartition compression must inherit the partition setting: " + sub.getName());
	}
}
