/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb.sql;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.db.dialect.mariadb.Mariadb11_50;

/**
 * MariaDB CREATE SEQUENCE generator.
 */
public class MariadbCreateSequenceFactory extends AbstractCreateSequenceFactory<MySqlSqlBuilder> {
	@Override
	protected void addCache(Sequence obj, MySqlSqlBuilder builder) {
		int size = obj.getCacheSize();
		if (size < 0 || (!obj.isCache() && size > 0)) {
			throw new IllegalArgumentException(
					"MariaDB sequence cache size must be nonnegative and cannot be positive when cache=false: "
							+ obj.getName());
		}
		if (size == 0) {
			builder.space()._add("NOCACHE");
		} else {
			super.addCache(obj, builder);
		}
	}

	@Override
	protected void addOptions(Sequence obj, MySqlSqlBuilder builder) {
		if (!obj.isCache() && obj.getCacheSize() == null) {
			builder.space()._add("NOCACHE");
		}
		String engine = obj.getSpecifics().get("ENGINE");
		if (engine != null) {
			if (!engine.matches("[A-Za-z][A-Za-z0-9_]*")) {
				throw new IllegalArgumentException("Invalid MariaDB sequence ENGINE token: " + engine);
			}
			builder.engine().eq()._add(engine);
		}
		if (obj.getRemarks() != null) {
			builder.comment().eq().sqlChar(obj.getRemarks());
		}
	}

	@Override
	protected void addDataType(Sequence obj, MySqlSqlBuilder builder) {
		String type = obj.getDataTypeName();
		if (type == null || type.isBlank()) {
			type = obj.getDataType().toString();
			type = switch (type) {
			case "UTINYINT" -> "TINYINT UNSIGNED";
			case "USMALLINT" -> "SMALLINT UNSIGNED";
			case "UMEDIUMINT" -> "MEDIUMINT UNSIGNED";
			case "UINT" -> "INT UNSIGNED";
			case "UBIGINT" -> "BIGINT UNSIGNED";
			default -> type;
			};
		}
		type = type.trim().replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
		if (!type.matches("(?:TINYINT|SMALLINT|MEDIUMINT|INT|INTEGER|BIGINT)(?: (?:SIGNED|UNSIGNED))?")) {
			throw new IllegalArgumentException("MariaDB sequence requires an integer type: " + type);
		}
		if (!(getDialect() instanceof Mariadb11_50)) {
			if (!type.equals("BIGINT") && !type.equals("BIGINT SIGNED")) {
				throw new IllegalArgumentException("MariaDB sequence AS type requires 11.5 or later: " + type);
			}
			return;
		}
		builder.as().space()._add(type);
	}

	@Override
	protected void addIfNotExists(Sequence obj, MySqlSqlBuilder builder) {
		builder.ifNotExists(getOptions().isCreateIfNotExists());
	}
}
