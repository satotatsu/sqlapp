/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb.sql;

import com.sqlapp.data.db.dialect.mysql.sql.MySqlCreateTableFactory;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.schemas.Table;

/**
 * MariaDB CREATE TABLE generator preserving vendor table options.
 */
public class MariadbCreateTableFactory extends MySqlCreateTableFactory {

	@Override
	protected void addTableOption(Table table, MySqlSqlBuilder builder) {
		super.addTableOption(table, builder);
		Object value = table.getSpecifics().get("CREATE_OPTIONS");
		if (value == null) {
			return;
		}
		String options = value.toString().trim();
		if (options.isEmpty()) {
			return;
		}
		options = options.replaceAll("(?i)(^|\\s)partitioned(?=\\s|$)", " ").trim();
		if (!options.isEmpty()) {
			builder.space()._add(options);
		}
	}
}
