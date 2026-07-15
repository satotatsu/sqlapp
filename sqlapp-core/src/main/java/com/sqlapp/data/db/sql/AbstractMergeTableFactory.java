/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;
import java.util.Objects;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * MERGE TABLE 生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractMergeTableFactory<S extends AbstractSqlBuilder<?>> extends AbstractTableFactory<S> {

	protected SqlType getSqlType() {
		return SqlType.MERGE_TABLE;
	}

	@Override
	public List<SqlOperation> createSql(final Table table) {
		final List<SqlOperation> sqlList = list();
		final S builder = createSqlBuilder();
		final SqlSignature sqlSignature = this.createSqlSignature(table);
		final ColumnSelectionStrategy columnSelectionStrategy = this.getTableOptions()
				.getMergeKeyColumnsMatchingStrategy().apply(table);
		sqlSignature.setColumnSelectionStrategy(columnSelectionStrategy);
		addMergeTable(table, sqlSignature, builder);
		addSql(sqlList, builder, getSqlType(), table);
		return sqlList;
	}

	protected void addMergeTable(final Table obj, final SqlSignature sqlSignature, final S builder) {
		final String targetTableAlias = "_target_";
		final String sourceTableAlias = "_source_";
		builder.merge().into();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		this.addTableComment(obj, builder);
		builder.as().space()._add(targetTableAlias);
		builder.lineBreak();
		addUsing(obj, sqlSignature, sourceTableAlias, builder);
		builder.lineBreak();
		addOn(obj, sqlSignature, targetTableAlias, sourceTableAlias, builder);
		addMergeTableWhenMatched(obj, sqlSignature, targetTableAlias, sourceTableAlias, builder);
		addMergeTableWhenNotMatched(obj, sqlSignature, targetTableAlias, sourceTableAlias, builder);
		addMergeTableWhenNotMatchedBySource(obj, sqlSignature, targetTableAlias, sourceTableAlias, builder);
		addMergeTableAfter(obj, sqlSignature, targetTableAlias, sourceTableAlias, builder);
	}

	protected void addUsing(final Table obj, final SqlSignature sqlSignature, String sourceTableAlias,
			final S builder) {
		final String sourceTableName = this.getTableOptions().getTempTableName().apply(obj);
		Table source = obj.getParent() != null ? obj.getParent().get(sourceTableName) : null;
		if (source == null) {
			source = obj.clone();
			source.setName(sourceTableName);
		}
		builder.using().name(source, this.getOptions().isDecorateSchemaName());
		builder.as().space()._add(sourceTableAlias);
		this.addTableComment(source, builder);
	}

	protected void addOn(final Table obj, final SqlSignature sqlSignature, String targetTableAlias,
			final String sourceTableAlias, final S builder) {
		final SqlSignature.ColumnsHolder columnsHolder = sqlSignature.getSelectedColumnsHolder();
		builder.on().space().brackets(() -> {
			builder.indent(() -> {
				columnsHolder.forEachKeyColumn((i, column) -> {
					builder.lineBreak();
					builder.and(i > 0).name(targetTableAlias + ".", column);
					builder.eq();
					builder.name(sourceTableAlias + ".", column);
				});
			});
			builder.lineBreak();
		});
	}

	protected void addMergeTableAfter(final Table obj, final SqlSignature sqlSignature, String targetTableAlias,
			final String sourceTableAlias, final S builder) {

	}

	protected void addMergeTableWhenMatched(final Table obj, final SqlSignature sqlSignature,
			final String targetTableAlias, final String sourceTableAlias, final S builder) {
		final SqlSignature.ColumnsHolder columnsHolder = sqlSignature.getSelectedColumnsHolder();
		builder.lineBreak();
		addWhenMatched(obj, sqlSignature, targetTableAlias, sourceTableAlias, builder);
		builder.indent(() -> {
			builder.lineBreak();
			builder.then().update();
			builder.indent(() -> {
				int i = 0;
				for (final Column column : obj.getColumns()) {
					if (!isUpdateable(column)) {
						continue;
					}
					if (columnsHolder.getKeyColumns().contains(column)) {
						continue;
					}
					if (columnsHolder.isUniqueKey()) {
						if (sqlSignature.getPrimaryKey().getKeyColumns().contains(column)) {
							continue;
						}
					}
					builder.lineBreak().set(i == 0).comma(i > 0);
					builder.name(targetTableAlias + ".", column).eq();
					final String value = this.getTableOptions().getUpdateTableColumnValue().apply(column);
					if (value != null && !Objects.equals(value, column.getName())) {
						builder._add(value);
					} else {
						builder.name(sourceTableAlias + ".", column);
					}
					final String comment = this.getTableOptions().getUpdateColumnComment().apply(column);
					if (!CommonUtils.isEmpty(comment) && !CommonUtils.eqIgnoreCase(comment, column.getName())) {
						builder.space().addComment(comment);
					}
					i++;
				}
				addMergeTableWhenMatchedWhere(obj, sqlSignature, targetTableAlias, sourceTableAlias, builder);
			});
		});
		builder.lineBreak();
	}

	protected void addWhenMatched(final Table obj, final SqlSignature sqlSignature, final String targetTableAlias,
			final String sourceTableAlias, final S builder) {
		builder.when().matched();
	}

	protected void addMergeTableWhenMatchedWhere(final Table obj, final SqlSignature sqlSignature,
			final String targetTableAlias, final String sourceTableAlias, final S builder) {
	}

	protected void addMergeTableWhenNotMatched(final Table obj, final SqlSignature sqlSignature,
			final String targetTableAlias, final String sourceTableAlias, final S builder) {
		addWhenNotMatched(obj, sqlSignature, targetTableAlias, sourceTableAlias, builder);
		final List<Column> insertColumns = CommonUtils.list();
		builder.indent(() -> {
			builder.lineBreak();
			builder.then().insert();
			builder.lineBreak();
			builder.brackets(() -> {
				builder.indent(() -> {
					int i = 0;
					for (final Column column : obj.getColumns()) {
						if (!isInsertable(column)) {
							continue;
						}
						final String comment = this.getTableOptions().getInsertColumnComment().apply(column);
						if (column.isIdentity()) {
							if (!CommonUtils.isEmpty(getDialect().getIdentityInsertString())) {
								insertColumns.add(column);
								builder.lineBreak().comma(i > 0).name(column);
								if (!CommonUtils.isEmpty(comment)
										&& !CommonUtils.eqIgnoreCase(comment, column.getName())) {
									builder.space().addComment(comment);
								}
								i++;
							}
						} else {
							insertColumns.add(column);
							builder.lineBreak().comma(i > 0).name(column);
							if (!CommonUtils.isEmpty(comment) && !CommonUtils.eqIgnoreCase(comment, column.getName())) {
								builder.space().addComment(comment);
							}
							i++;
						}
					}
				});
				builder.lineBreak();
			});
			builder.lineBreak();
			builder.values();
			builder.lineBreak();
			builder.brackets(() -> {
				builder.indent(() -> {
					int i = 0;
					for (final Column column : insertColumns) {
						builder.lineBreak().comma(i > 0);
						final String value = this.getTableOptions().getInsertTableColumnValue().apply(column);
						if (column.getDefaultValue() != null) {
							builder.coalesce(() -> {
								if (value != null && !Objects.equals(value, column.getName())) {
									builder._add(value);
								} else {
									builder.name(sourceTableAlias + ".", column);
								}
								builder.comma()._add(column.getDefaultValue());
							});
						} else {
							if (value != null && !Objects.equals(value, column.getName())) {
								builder._add(value);
							} else {
								builder.name(sourceTableAlias + ".", column);
							}
						}
						i++;
					}
				});
				builder.lineBreak();
			});
			addMergeTableWhenNotMatchedWhere(obj, targetTableAlias, sourceTableAlias, builder);
		});
	}

	protected void addWhenNotMatched(final Table obj, final SqlSignature sqlSignature, final String targetTableAlias,
			final String sourceTableAlias, final S builder) {
		builder.when().not().matched();
	}

	protected void addMergeTableWhenNotMatchedWhere(final Table obj, final String targetTableAlias,
			final String sourceTableAlias, final S builder) {
	}

	protected void addMergeTableWhenNotMatchedBySource(final Table obj, final SqlSignature sqlSignature,
			final String targetTableAlias, final String sourceTableAlias, final S builder) {
	}

}
