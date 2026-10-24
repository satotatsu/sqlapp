/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.Set;
import com.sqlapp.data.schemas.DbObjectDifference;

final class FirebirdAlterSql {
	static <T> T target(DbObjectDifference difference, Class<T> type, Set<String> allowed) {
		if (!type.isInstance(difference.getOriginal()) || !type.isInstance(difference.getTarget()))
			throw new IllegalArgumentException("Firebird ALTER requires original and target " + type.getSimpleName());
		var unsupported = difference.getChangedProperties().keySet().stream().filter(key -> !allowed.contains(key))
				.toList();
		if (!unsupported.isEmpty())
			throw new UnsupportedOperationException(
					"Firebird " + type.getSimpleName() + " ALTER does not support " + unsupported);
		return type.cast(difference.getTarget());
	}

	private FirebirdAlterSql() {
	}
}
