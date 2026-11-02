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
