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

import java.util.List;
import java.util.Locale;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerIndexOptions;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.util.CommonUtils;

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
		if (table.isCompression() && table.getCompressionType() != null
				&& table.getCompressionType().toUpperCase().startsWith("COLUMNSTORE")) {
			throw new IllegalArgumentException("DATA_COMPRESSION=" + table.getCompressionType()
					+ " applies to columnstore indexes, not tables. Set DATA_COMPRESSION on the columnstore index of table "
					+ table.getName() + ".");
		}
		if (table.isCompression()) {
			builder.lineBreak();
			builder.with().space().brackets(() -> {
				builder.dataCompression().eq().space();
				if ("ROW".equalsIgnoreCase(table.getCompressionType())) {
					builder.row();
				} else if ("PAGE".equalsIgnoreCase(table.getCompressionType())) {
					builder.page();
				} else {
					builder.row();
				}
			});
		}
	}

	@Override
	public List<SqlOperation> createSql(final Table table) {
		if ((!table.getTemporalPeriods().isEmpty() || table.getSystemVersioning() != null)
				&& getDialect().compareTo(com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver
						.getInstance().getDialect(13, 0)) < 0)
			throw new IllegalArgumentException("Temporal tables require SQL Server 2016 or later");
		validateTableOptions(table);
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

	private void validateTableOptions(final Table table) {
		var specifics = table.getSpecifics();
		String ledger = specifics.get("ledger_type");
		if (SqlServerDdlSupport.isMemoryOptimized(table))
			throw new IllegalArgumentException(
					"Cannot recreate SQL Server memory-optimized table with equivalent indexes and constraints: "
							+ table.getName());
		if (Boolean.TRUE.equals(specifics.get("is_node", Boolean.class))
				|| Boolean.TRUE.equals(specifics.get("is_edge", Boolean.class)))
			throw new IllegalArgumentException(
					"SQL Server graph table recreation requires graph column and constraint support: "
							+ table.getName());
		if (ledger != null && !"0".equals(ledger) && !"NON_LEDGER_TABLE".equalsIgnoreCase(ledger))
			throw new IllegalArgumentException("SQL Server ledger table recreation is unsupported: " + table.getName());
		if (specifics.containsKey(SqlServerIndexOptions.COMPRESSION_DELAY.toString()))
			throw new IllegalArgumentException(
					"COMPRESSION_DELAY applies to columnstore indexes, not tables: " + table.getName());
	}
}
