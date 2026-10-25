/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.MySql800;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;

/** Validation shared by standalone, inline and ALTER index definitions. */
final class MySqlIndexValidation {
	private MySqlIndexValidation() {
	}

	static boolean isSpatial(IndexType type) {
		return type == IndexType.Spatial || type == IndexType.RTree;
	}

	static boolean isColumn(ReferenceColumn column, Table table) {
		return column.getColumn() != null || (table != null && table.getColumns().contains(column.getName()));
	}

	static void validate(Index index, Table table, Dialect dialect, boolean primary, boolean constraint) {
		boolean mysql8 = dialect instanceof MySql800 && "MySQL".equalsIgnoreCase(dialect.getProductName());
		boolean maria = "MariaDB".equalsIgnoreCase(dialect.getProductName());
		if (index.getColumns().isEmpty()) {
			throw new IllegalArgumentException("Index requires at least one key part: " + index.getName());
		}
		if (!index.getIncludes().isEmpty()) {
			throw new IllegalArgumentException(
					"MySQL/MariaDB indexes do not support INCLUDE columns: " + index.getName());
		}
		IndexType type = index.getIndexType();
		String parser = index.getSpecifics().get(MySqlCreateIndexFactory.PARSER);
		if (parser != null && (type != IndexType.FullText || parser.isBlank()
				|| parser.chars().anyMatch(Character::isISOControl))) {
			throw new IllegalArgumentException(
					"mysql_parser requires a FULLTEXT index and a nonblank plugin identifier without control characters");
		}
		if (type != null && type != IndexType.BTree && type != IndexType.Hash && type != IndexType.RTree
				&& type != IndexType.FullText && type != IndexType.Spatial) {
			throw new IllegalArgumentException("Unsupported MySQL/MariaDB index type: " + type);
		}
		if ((type == IndexType.FullText || isSpatial(type)) && (index.isUnique() || constraint)) {
			throw new IllegalArgumentException(
					"FULLTEXT/SPATIAL cannot be a unique or primary index: " + index.getName());
		}
		if (!index.isEnable()) {
			if (primary) {
				throw new IllegalArgumentException(
						"A primary index cannot be INVISIBLE or IGNORED: " + index.getName());
			}
			if (!mysql8 && !maria) {
				throw new IllegalArgumentException("Invisible indexes require MySQL 8.0 or later: " + index.getName());
			}
		}
		for (ReferenceColumn column : index.getColumns()) {
			if (column.isIncludedColumn() || column.getNullsOrder() != null) {
				throw new IllegalArgumentException(
						"MySQL/MariaDB index key parts do not support INCLUDE or NULLS FIRST/LAST: "
								+ column.getName());
			}
			if (column.getName() == null || column.getName().isBlank()) {
				throw new IllegalArgumentException(
						"Index key part requires a column or expression: " + index.getName());
			}
			if (column.getLength() != null && column.getLength() <= 0) {
				throw new IllegalArgumentException("Index prefix length must be positive: " + column.getName());
			}
			if (column.getLength() != null && (type == IndexType.FullText || isSpatial(type))) {
				throw new IllegalArgumentException(
						"FULLTEXT/SPATIAL key parts do not support prefix lengths: " + column.getName());
			}
			if (!isColumn(column, table)) {
				if (constraint) {
					throw new IllegalArgumentException(
							"Use a unique Index for functional key parts instead of a UniqueConstraint: "
									+ column.getName());
				}
				if (!mysql8) {
					throw new IllegalArgumentException(
							"Functional index key parts require MySQL 8.0.13 or later; use an indexed generated column on older MySQL or MariaDB");
				}
				if (column.getLength() != null || type == IndexType.FullText || isSpatial(type)) {
					throw new IllegalArgumentException(
							"Functional index key parts cannot have a prefix length or use FULLTEXT/SPATIAL: "
									+ column.getName());
				}
			}
		}
	}
}
