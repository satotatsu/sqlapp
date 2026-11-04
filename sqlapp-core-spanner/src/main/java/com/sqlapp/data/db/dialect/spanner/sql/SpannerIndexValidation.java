/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;

final class SpannerIndexValidation {
	private SpannerIndexValidation() {
	}

	static boolean isManaged(final Index index) {
		final String value = index.getSpecifics().get(SpannerCreateIndexFactory.SPANNER_IS_MANAGED);
		if (value == null || "false".equalsIgnoreCase(value))
			return false;
		if ("true".equalsIgnoreCase(value))
			return true;
		throw new IllegalArgumentException("Spanner SPANNER_IS_MANAGED requires true or false: " + index.getName());
	}

	static void requireUserIndex(final Index index) {
		if (isManaged(index))
			throw new UnsupportedOperationException("Spanner managed index is controlled by foreign-key constraints; "
					+ "change the constraint instead: " + index.getName());
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
