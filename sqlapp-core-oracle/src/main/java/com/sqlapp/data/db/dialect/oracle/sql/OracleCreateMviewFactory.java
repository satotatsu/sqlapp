/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.List;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateMviewFactory;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.Table;

public class OracleCreateMviewFactory extends AbstractCreateMviewFactory<OracleSqlBuilder> {

	@Override
	protected void addCreateObject(final Mview obj, OracleSqlBuilder builder) {
		if (!isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
		} else {
			if (isEmpty(obj.getStatement())) {
				throw new IllegalArgumentException("Materialized view query is required: " + obj.getName());
			}
			builder.create().materialized().view();
			builder.name(obj, this.getOptions().isDecorateSchemaName());
			if (!obj.getColumns().isEmpty()) {
				builder.space()._add("(");
				for (int i = 0; i < obj.getColumns().size(); i++) {
					builder.comma(i > 0).name(obj.getColumns().get(i).getName());
				}
				builder._add(")");
			}
			addMaterializedViewOptions(obj, builder);
			builder.lineBreak().as();
			builder.lineBreak()._add(obj.getStatement());
		}
	}

	private void addMaterializedViewOptions(Mview obj, OracleSqlBuilder builder) {
		String build = option(obj, "BUILD_MODE", "IMMEDIATE", "DEFERRED", "PREBUILT");
		String method = option(obj, "REFRESH_METHOD", "FAST", "COMPLETE", "FORCE", "NEVER");
		String mode = option(obj, "REFRESH_MODE", "COMMIT", "DEMAND", "NEVER", "STATEMENT");
		String rewrite = option(obj, "REWRITE_ENABLED", "Y", "N");
		String noIndex = option(obj, "USE_NO_INDEX", "Y", "N");
		if ("PREBUILT".equals(build) || "STATEMENT".equals(mode)) {
			throw new IllegalArgumentException("Materialized view PREBUILT/ON STATEMENT requires a complete definition: " + obj.getName());
		}
		boolean never = "NEVER".equals(method) || "NEVER".equals(mode);
		if (never && (method != null && !"NEVER".equals(method) || mode != null && !"NEVER".equals(mode))) {
			throw new IllegalArgumentException("Conflicting materialized view refresh options: " + obj.getName());
		}
		if (build != null) builder.lineBreak()._add("BUILD ")._add(build);
		if ("Y".equals(noIndex)) builder.lineBreak()._add("USING NO INDEX");
		if (never) {
			builder.lineBreak()._add("NEVER REFRESH");
		} else if (method != null || mode != null) {
			builder.lineBreak().refresh();
			if (method != null) builder.space()._add(method);
			if (mode != null) builder.space().on().space()._add(mode);
		}
		if (rewrite != null) builder.lineBreak()._add("Y".equals(rewrite) ? "ENABLE QUERY REWRITE" : "DISABLE QUERY REWRITE");
	}

	private String option(Mview obj, String key, String... supported) {
		String value = obj.getSpecifics().get(key);
		if (value == null) return null;
		String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
		if (!java.util.Arrays.asList(supported).contains(normalized)) {
			throw new IllegalArgumentException("Invalid materialized view " + key + ": " + obj.getName());
		}
		return normalized;
	}

	@Override
	protected void addOtherDefinitions(Mview table, List<SqlOperation> result) {
		if (table.getRemarks() != null) {
			OracleSqlBuilder builder = this.createSqlBuilder();
			builder.comment().on().materialized().view().space().name(table, this.getOptions().isDecorateSchemaName())
					.is().sqlChar(table.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, table);
		}
		table.getColumns().stream().filter(c -> c.getRemarks() != null).forEach(c -> {
			OracleSqlBuilder builder = this.createSqlBuilder();
			builder.comment().on().column().space().columnName(c, true, this.getOptions().isDecorateSchemaName()).is()
					.sqlChar(c.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, c);
		});
		table.getIndexes().stream().filter(c -> c.getRemarks() != null).forEach(c -> {
			OracleSqlBuilder builder = this.createSqlBuilder();
			builder.comment().on().index().space().name(c, this.getOptions().isDecorateSchemaName()).is()
					.sqlChar(c.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, c);
		});
	}

	/**
	 * 全インデックスを追加します
	 * 
	 * @param table
	 * @param builder
	 */
	protected void addIndexes(final Table table, OracleSqlBuilder builder) {
		for (Index index : table.getIndexes()) {
			if (!table.getConstraints().contains(index.getName())) {
				addIndex(index, builder);
			}
		}
	}

	/**
	 * インデックスを追加します
	 * 
	 * @param index
	 * @param builder
	 */
	protected void addIndex(final Index index, OracleSqlBuilder builder) {
		if (index == null) {
			return;
		}
		SqlFactory<Index> sqlFactory = getSqlFactoryRegistry().getSqlFactory(index, SqlType.CREATE);
		if (sqlFactory instanceof OracleCreateIndexFactory) {
			builder.lineBreak().comma();
			OracleCreateIndexFactory indexOperation = (OracleCreateIndexFactory) sqlFactory;
			indexOperation.addObjectDetail(index, null, builder);
		}
	}
}
