/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TemporalPeriod;
import com.sqlapp.data.schemas.TemporalPeriodType;

/**
 * MariaDB SQL builder.
 */
public class MariadbSqlBuilder extends MySqlSqlBuilder {

	private static final long serialVersionUID = 1L;

	public MariadbSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	@Override
	public MySqlSqlBuilder definition(Column column, boolean withRemarks) {
		super.definition(column, withRemarks);
		Table table = column.getTable();
		if (table != null) {
			for (TemporalPeriod period : table.getTemporalPeriods()) {
				if (period.getPeriodType() != TemporalPeriodType.SYSTEM_TIME) {
					continue;
				}
				if (column.getName().equalsIgnoreCase(period.getStartColumnName())) {
					space()._add("GENERATED ALWAYS AS ROW START");
				} else if (column.getName().equalsIgnoreCase(period.getEndColumnName())) {
					space()._add("GENERATED ALWAYS AS ROW END");
				}
			}
		}
		return this;
	}
}
