/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.Index;

/** Shared CREATE/REBUILD compression syntax and validation. */
final class OracleIndexCompression {
	private OracleIndexCompression() { }
	static void add(Index index, Dialect dialect, OracleSqlBuilder builder) {
		if (index.isCompression()) {
			if (index.getIndexType() != null && index.getIndexType().isBitMap() || "true".equalsIgnoreCase(index.getSpecifics().get("oracle.index.bitmap"))) throw new IllegalArgumentException("Bitmap indexes do not support these compression clauses: " + index.getName());
			String mode = index.getCompressionType();
			if (mode == null || "ENABLED".equals(mode)) {
				builder.space()._add("COMPRESS");
				String prefix = index.getSpecifics().get("oracle.index.prefixLength");
				if (prefix != null) {
					int count;
					try { count = Integer.parseInt(prefix); } catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid index compression prefix: " + index.getName(), error); }
					if (count < 1 || count > index.getColumns().size() || index.isUnique() && count == index.getColumns().size()) throw new IllegalArgumentException("Invalid index compression prefix: " + index.getName());
					builder.space()._add(count);
				}
			} else if ("ADVANCED LOW".equals(mode) || "ADVANCED HIGH".equals(mode)) {
				if (!(dialect instanceof com.sqlapp.data.db.dialect.oracle.Oracle12c) || "ADVANCED HIGH".equals(mode) && !(dialect instanceof com.sqlapp.data.db.dialect.oracle.Oracle18c)) throw new IllegalArgumentException("Advanced index compression is not supported by this dialect version: " + index.getName());
				if (index.getSpecifics().containsKey("oracle.index.prefixLength")) throw new IllegalArgumentException("Advanced compression cannot specify a prefix length: " + index.getName());
				builder.space()._add("COMPRESS ")._add(mode);
			} else throw new IllegalArgumentException("Unsupported index compression type: " + index.getName());
		} else if (index.getCompressionType() != null || index.getSpecifics().containsKey("oracle.index.prefixLength")) {
			throw new IllegalArgumentException("Index compression options require compression=true: " + index.getName());
		}
	}
}
