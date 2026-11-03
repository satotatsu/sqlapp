/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;

final class SpannerIndexValidation {
	private SpannerIndexValidation() {
	}

	static void identity(final Index index) {
		if (index.getName() == null || index.getName().isBlank()) {
			throw new IllegalArgumentException("Cloud Spanner index requires a name");
		}
		final IndexType type = index.getIndexType();
		if (type != null && type != IndexType.BTree && type != IndexType.FullText && type != IndexType.Vector) {
			throw new UnsupportedOperationException("Unsupported Cloud Spanner index type: " + type);
		}
	}
}
