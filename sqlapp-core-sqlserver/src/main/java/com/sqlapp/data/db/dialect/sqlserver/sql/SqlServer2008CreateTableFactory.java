/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.sql;

import java.util.Map;
import java.util.List;
import java.util.Locale;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerIndexOptions;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.OnOffType;

/**
 * SQLServer2008用のテーブル作成
 * 
 * @author tatsuo satoh
 * 
 */
public class SqlServer2008CreateTableFactory extends SqlServer2005CreateTableFactory {

	@Override
	protected void addOption(final Table table, final SqlServerSqlBuilder builder) {
		super.addOption(table, builder);
		if (table.isCompression()) {
			builder.lineBreak();
			builder.with().space().brackets(() -> {
				builder.dataCompression().eq().space();
				if ("ROW".equalsIgnoreCase(table.getCompressionType())) {
					builder.row();
				} else if ("PAGE".equalsIgnoreCase(table.getCompressionType())) {
					builder.page();
				} else if ("COLUMNSTORE".equalsIgnoreCase(table.getCompressionType())) {
					builder.columnstore();
				} else if ("COLUMNSTORE_ARCHIVE".equalsIgnoreCase(table.getCompressionType())) {
					builder.columnstoreArchive();
				} else {
					builder.row();
				}
				addWithOption(table, builder);
			});
		} else {
			SqlServerSqlBuilder child = builder.newInstance();
			addWithOption(table, child);
			if (!child.toString().isEmpty()) {
				builder.with().space().brackets(() -> {
					builder.space();
					builder._add(child.toString());
				});
			}
		}
	}

	@Override
	public List<SqlOperation> createSql(final Table table) {
		if ((!table.getTemporalPeriods().isEmpty() || table.getSystemVersioning() != null)
				&& getDialect().compareTo(com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver
						.getInstance().getDialect(13, 0)) < 0)
			throw new IllegalArgumentException("Temporal tables require SQL Server 2016 or later");
		List<SqlOperation> result = super.createSql(table);
		String escalation = table.getSpecifics().get("LOCK_ESCALATION");
		if (escalation != null) {
			if (!List.of("AUTO", "TABLE", "DISABLE").contains(escalation.toUpperCase(Locale.ROOT))) {
				throw new IllegalArgumentException("Invalid LOCK_ESCALATION: " + escalation);
			}
			SqlServerSqlBuilder builder = createSqlBuilder();
			builder.alter().table().space().name(table)._add(" SET (LOCK_ESCALATION = ")
					._add(escalation.toUpperCase(Locale.ROOT))._add(")");
			add(result, createOperation(builder.toString(), SqlType.ALTER, table));
		}
		Boolean tracking = Converters.getDefault().convertObject(table.getSpecifics().get("HAS_CHANGE_TRACKING"),
				Boolean.class);
		if (Boolean.TRUE.equals(tracking)) {
			SqlServerSqlBuilder builder = createSqlBuilder();
			builder.alter().table().space().name(table).space();
			builder.enable().changeTracking();
			Boolean columns = table.getSpecifics().get("is_track_columns_updated_on", Boolean.class);
			if (columns != null)
				builder._add(" WITH (TRACK_COLUMNS_UPDATED = ")._add(columns ? "ON" : "OFF")._add(")");
			add(result, createOperation(builder.toString(), SqlType.ALTER, table));
		}
		String largeValues = table.getSpecifics().get("large_value_types_out_of_row");
		if (Boolean.TRUE.equals(Converters.getDefault().convertObject(largeValues, Boolean.class))) {
			addTableOption(table, result, "large value types out of row", "ON");
		}
		String textLimit = table.getSpecifics().get("text_in_row_limit");
		if (textLimit != null && Integer.parseInt(textLimit) > 0)
			addTableOption(table, result, "text in row", textLimit);
		return result;
	}

	private void addTableOption(Table table, List<SqlOperation> result, String option, String value) {
		SqlServerSqlBuilder name = createSqlBuilder();
		name.name(table, true);
		String sql = "EXEC sys.sp_tableoption N'" + name.toString().replace("'", "''") + "', N'" + option + "', N'"
				+ value.replace("'", "''") + "'";
		add(result, createOperation(sql, SqlType.ALTER, table));
	}

	protected void addWithOption(final Table table, final SqlServerSqlBuilder builder) {
		final Map<String, String> map = table.getSpecifics();
		boolean[] first = new boolean[] { true };
		map.forEach((k, v) -> {
			String key = k.toLowerCase(Locale.ROOT);
			if ((key.startsWith("is_") && ("false".equalsIgnoreCase(v) || "0".equals(v)))
					|| key.equals("temporal_type")
					|| (key.equals("ledger_type") && ("0".equals(v) || "NON_LEDGER_TABLE".equals(v))))
				return;
			if ("HAS_CHANGE_TRACKING".equalsIgnoreCase(k) || "LOCK_ESCALATION".equalsIgnoreCase(k)
					|| "text_in_row_limit".equalsIgnoreCase(k) || "is_track_columns_updated_on".equalsIgnoreCase(k)
					|| "large_value_types_out_of_row".equalsIgnoreCase(k) || "has_var_decimal".equalsIgnoreCase(k)) {
				return;
			}
			if (!first[0]) {
				builder.space().comma();
			}
			SqlServerIndexOptions enm = SqlServerIndexOptions.parse(k);
			if (enm != null) {
				builder._add(enm);
				first[0] = false;
				if (CommonUtils.isEmpty(v)) {
					return;
				}
				builder.space().eq().space();
				if (enm.isOnOff()) {
					OnOffType onOffType = OnOffType.parse(v, null);
					if (onOffType == null) {
						builder._add(enm.getDefaultValue());
					} else {
						builder._add(onOffType);
					}
				} else {
					builder._add(v);
				}
			} else {
				builder._add(k.toUpperCase());
				first[0] = false;
				if (CommonUtils.isEmpty(v)) {
					return;
				}
				builder.space().eq().space();
				builder._add(v);
			}
		});
	}
}
