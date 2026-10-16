/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.Objects;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.Oracle11g;
import com.sqlapp.data.db.dialect.oracle.Oracle11gR2;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.UniqueConstraint;

final class OracleIndexRebuildOptions {
	private OracleIndexRebuildOptions() { }
	static boolean physicalChange(Index before, Index after) {
		return !Objects.equals(before.getTableSpaceName(), after.getTableSpaceName())
				|| compressionChange(before, after);
	}
	static boolean compressionChange(Index before, Index after) {
		return before.isCompression() != after.isCompression() || !Objects.equals(before.getCompressionType(), after.getCompressionType())
				|| !Objects.equals(before.getSpecifics().get("oracle.index.prefixLength"), after.getSpecifics().get("oracle.index.prefixLength"));
	}
	static void tablespace(String target, OracleSqlBuilder builder) {
		if (target == null || target.isBlank()) throw new IllegalArgumentException("Moving an index requires an explicit target tablespace");
		builder.space()._add("TABLESPACE").space().name(target);
	}
	static void compression(Index target, Dialect dialect, OracleSqlBuilder builder) {
		OracleIndexCompression.add(target, dialect, builder);
		if (!target.isCompression()) builder.space()._add("NOCOMPRESS");
	}
	static void online(Index index, Dialect dialect, boolean requested, boolean subpartition, boolean localHash, OracleSqlBuilder builder) {
		if (!requested) return;
		if ("true".equalsIgnoreCase(index.getSpecifics().get("oracle.index.join")))
			throw new IllegalArgumentException("Online rebuild is not supported for join indexes");
		if (subpartition && !(dialect instanceof Oracle11g) && !(dialect instanceof Oracle11gR2))
			throw new IllegalArgumentException("Online subpartition rebuild requires Oracle 11g or later");
		if (localHash) throw new IllegalArgumentException("Local hash segment rebuild supports only TABLESPACE options");
		if (index.getIndexType() != null && index.getIndexType().isBitMap()
				|| "true".equalsIgnoreCase(index.getSpecifics().get("oracle.index.bitmap")))
			throw new IllegalArgumentException("Online rebuild of bitmap indexes is not supported by this generator");
		if (index.getTable() != null) {
			for (var constraint : index.getTable().getConstraints()) {
				if (constraint instanceof UniqueConstraint unique && unique.getDeferrability() != null
						&& unique.getDeferrability() != Deferrability.NotDeferrable
						&& (Objects.equals(unique.getIndexName(), index.getName()) || unique.getColumns().equals(index.getColumns())))
					throw new IllegalArgumentException("Online rebuild cannot enforce a deferrable unique constraint: " + unique.getName());
			}
		}
		builder.space()._add("ONLINE");
	}
}
