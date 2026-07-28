/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb.sql;

import com.sqlapp.data.db.dialect.mysql.sql.MySqlCreateTableFactory;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TemporalPeriod;

/**
 * MariaDB CREATE TABLE generator preserving vendor table options.
 */
public class MariadbCreateTableFactory extends MySqlCreateTableFactory {

	@Override
	protected void addConstraintDefinitions(Table table, MySqlSqlBuilder builder) {
		super.addConstraintDefinitions(table, builder);
		for (TemporalPeriod period : table.getTemporalPeriods()) {
			if (period.getStartColumnName() == null || period.getEndColumnName() == null) {
				continue;
			}
			builder.lineBreak().comma()._add("PERIOD FOR ").name(period.getName())
					.space()._add("(").name(period.getStartColumnName())
					.comma().space().name(period.getEndColumnName())._add(")");
		}
	}

	@Override
	protected void addOption(Table table, MySqlSqlBuilder builder) {
		addTableOption(table, builder);
		if (table.getSystemVersioning() != null && table.getSystemVersioning().isEnable()) {
			builder.space()._add("WITH SYSTEM VERSIONING");
		}
		addPartitionByDefinition(table, builder);
	}

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
