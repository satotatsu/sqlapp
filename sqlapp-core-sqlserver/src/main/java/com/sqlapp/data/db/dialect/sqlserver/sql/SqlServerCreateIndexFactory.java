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

import java.util.Locale;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlserver.metadata.SqlServer2000IndexReader;
import com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerIndexOptions;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.db.sql.AddTableObjectDetailFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.OnOffType;

public class SqlServerCreateIndexFactory extends AbstractCreateIndexFactory<SqlServerSqlBuilder>
		implements AddTableObjectDetailFactory<Index, SqlServerSqlBuilder> {

	@Override
	public void addObjectDetail(final Index obj, final Table table, final SqlServerSqlBuilder builder) {
		if (isColumnStore(obj)) {
			addColumnStoreIndex(obj, table, builder);
			return;
		}
		builder.unique(obj.isUnique());
		addIndexType(obj, table, builder);
		builder.index().space();
		builder.name(obj, false);
		if (table != null) {
			builder.on();
			builder.name(table);
		}
		builder.space().brackets(() -> {
			builder.names(obj.getColumns());
			builder.space();
		});
		addObjectDetailAfter(obj, table, builder);
	}

	protected static boolean isColumnStore(final Index obj) {
		return obj.getIndexType() != null && obj.getIndexType().isColumnStore();
	}

	/**
	 * CREATE [NON]CLUSTERED COLUMNSTORE INDEX を追加します。
	 * クラスタ化列ストアは列リストを持たず、Indexの列はORDER句(SQL Server 2022以降)として扱います。
	 */
	protected void addColumnStoreIndex(final Index obj, final Table table, final SqlServerSqlBuilder builder) {
		validateColumnStoreIndex(obj);
		final boolean clustered = obj.getIndexType().isClusterd();
		if (clustered) {
			builder.clustered();
		} else {
			builder.nonclustered();
		}
		builder.columnstore().index().space();
		builder.name(obj, false);
		if (table != null) {
			builder.on();
			builder.name(table);
		}
		if (!clustered) {
			builder.space().brackets(() -> {
				builder.names(obj.getColumns());
				builder.space();
			});
		} else if (!obj.getColumns().isEmpty()) {
			builder.lineBreak().order().space().brackets(() -> {
				builder.space();
				builder.names(obj.getColumns());
				builder.space();
			});
		}
		addObjectDetailAfter(obj, table, builder);
	}

	private static final String COMPRESSION_DELAY = SqlServerIndexOptions.COMPRESSION_DELAY.toString();
	private static final String DATA_COMPRESSION = SqlServerIndexOptions.DATA_COMPRESSION.toString();

	private boolean isBefore(final int majorVersion) {
		final Dialect boundary = SqlServerDialectResolver.getInstance().getDialect(majorVersion, 0);
		return getDialect().compareTo(boundary) < 0;
	}

	/**
	 * 列ストアインデックスの定義がこのバージョンで作成できるかを検証します。作成できない場合は例外にします。
	 */
	protected void validateColumnStoreIndex(final Index obj) {
		final boolean clustered = obj.getIndexType().isClusterd();
		final String target = " index=" + obj.getName();
		if (isBefore(11)) {
			throw new IllegalArgumentException("Columnstore indexes require SQL Server 2012 or later." + target);
		}
		if (clustered && isBefore(12)) {
			throw new IllegalArgumentException(
					"Clustered columnstore indexes require SQL Server 2014 or later." + target);
		}
		if (obj.isUnique()) {
			throw new IllegalArgumentException("Columnstore indexes cannot be UNIQUE." + target);
		}
		if (!CommonUtils.isEmpty(obj.getIncludes())) {
			throw new IllegalArgumentException(
					"Columnstore indexes do not support INCLUDE. List the columns in the index column list instead."
							+ target);
		}
		for (final ReferenceColumn column : obj.getColumns()) {
			if (column.getOrder() == Order.Desc) {
				throw new IllegalArgumentException(
						"Columnstore index columns cannot be DESC. column=" + column.getName() + target);
			}
		}
		if (!clustered && obj.getColumns().isEmpty()) {
			throw new IllegalArgumentException(
					"Nonclustered columnstore indexes require at least one column." + target);
		}
		if (clustered && !obj.getColumns().isEmpty() && isBefore(16)) {
			throw new IllegalArgumentException(
					"Ordered clustered columnstore indexes (ORDER) require SQL Server 2022 or later." + target);
		}
		if (!CommonUtils.isEmpty(obj.getWhere())) {
			if (clustered) {
				throw new IllegalArgumentException(
						"Clustered columnstore indexes cannot have a filter (WHERE)." + target);
			}
			if (isBefore(13)) {
				throw new IllegalArgumentException(
						"Filtered nonclustered columnstore indexes require SQL Server 2016 or later." + target);
			}
		}
		if (obj.getSpecifics().get(COMPRESSION_DELAY) != null && isBefore(13)) {
			throw new IllegalArgumentException("COMPRESSION_DELAY requires SQL Server 2016 or later." + target);
		}
		final String compression = obj.getSpecifics().get(DATA_COMPRESSION);
		if (compression != null) {
			final String upper = compression.toUpperCase(Locale.ROOT);
			if (!"COLUMNSTORE".equals(upper) && !"COLUMNSTORE_ARCHIVE".equals(upper)) {
				throw new IllegalArgumentException(
						"DATA_COMPRESSION of a columnstore index must be COLUMNSTORE or COLUMNSTORE_ARCHIVE. value="
								+ compression + target);
			}
			if (isBefore(12)) {
				throw new IllegalArgumentException(
						"DATA_COMPRESSION on columnstore indexes requires SQL Server 2014 or later." + target);
			}
		}
	}

	/**
	 * 列ストアインデックスで指定できるWITHオプションだけを返します。
	 * (PAD_INDEX、FILLFACTOR、ALLOW_ROW_LOCKSなどの行ストア用オプションは列ストアでは指定できない)
	 */
	protected Map<String, String> createColumnStoreIndexWithOption(final Index obj, final Table table) {
		final Map<String, String> map = CommonUtils.linkedMap();
		for (final String key : new String[] { "DROP_EXISTING", "MAXDOP", "ONLINE", COMPRESSION_DELAY,
				DATA_COMPRESSION }) {
			final String val = obj.getSpecifics().get(key);
			if (val == null) {
				continue;
			}
			if ("DROP_EXISTING".equals(key) || "ONLINE".equals(key)) {
				map.put(key, OnOffType.parse(val).toString());
			} else if (DATA_COMPRESSION.equals(key)) {
				map.put(key, val.toUpperCase(Locale.ROOT));
			} else {
				map.put(key, val);
			}
		}
		return map;
	}

	protected void addIndexType(final Index obj, final Table table, final SqlServerSqlBuilder builder) {
		if (obj.getIndexType() != null && obj.getIndexType().isClusterd()) {
			builder.clustered();
		}
	}

	protected void addObjectDetailAfter(final Index obj, final Table table, final SqlServerSqlBuilder builder) {
		addIndexWithOption(obj, table, builder);
	}

	protected void addIndexWithOption(final Index obj, final Table table, final SqlServerSqlBuilder builder) {
		final Map<String, String> map = isColumnStore(obj) ? createColumnStoreIndexWithOption(obj, table)
				: createIndexWithOption(obj, table);
		if (!map.isEmpty()) {
			builder.lineBreak();
			builder.with().space().brackets(() -> {
				builder.indent(() -> {
					final boolean[] first = new boolean[] { true };
					map.forEach((k, v) -> {
						builder.lineBreak().comma(!first[0])._add(k).eq().space()._add(v);
						first[0] = false;
					});
				});
				builder.lineBreak();
			});
		}
	}

	protected Map<String, String> createIndexWithOption(final Index obj, final Table table) {
		final Map<String, String> map = CommonUtils.linkedMap();
		String key = SqlServerIndexOptions.PAD_INDEX.toString();
		String val = obj.getSpecifics().get(key);
		if (val != null) {
			map.put(key, val);
		}
		key = SqlServer2000IndexReader.FILL_FACTOR;
		val = obj.getSpecifics().get(key);
		if (val != null) {
			map.put(SqlServerIndexOptions.FILLFACTOR.toString(), val);
		} else {
			val = obj.getSpecifics().get(SqlServerIndexOptions.FILLFACTOR.toString());
			if (val != null) {
				map.put(SqlServerIndexOptions.FILLFACTOR.toString(), val);
			}
		}
		key = SqlServerIndexOptions.ALLOW_ROW_LOCKS.toString();
		val = obj.getSpecifics().get(key);
		if (val != null) {
			map.put(key, val);
		}
		key = SqlServerIndexOptions.ALLOW_PAGE_LOCKS.toString();
		val = obj.getSpecifics().get(key);
		if (val != null) {
			map.put(key, val);
		}
		return map;
	}

}
