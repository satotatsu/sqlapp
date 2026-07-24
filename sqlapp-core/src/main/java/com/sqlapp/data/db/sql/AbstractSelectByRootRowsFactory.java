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

import com.sqlapp.data.db.sql.SqlSignature.ColumnsHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * SELECT BY Root Rows
 * 
 * @author satoh
 * 
 */
public abstract class AbstractSelectByRootRowsFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractTableRelationFactory<S> {

	protected SqlType getSqlType() {
		return SqlType.SELECT_BY_ROOT_ROWS;
	}

	@Override
	public List<SqlOperation> createSql(final TableRelation obj) {
		final S builder = createSqlBuilder();
		final SqlSignature sqlSignature = this.createSqlSignature(obj);
		String alias = "a";
		addSelectFromTable(obj, sqlSignature, alias, builder);
		addSelectConditionColumns(obj, sqlSignature, alias, builder);
		addSelectOrderby(obj, sqlSignature, alias, builder);
		final List<SqlOperation> sqlList = list();
		addSql(sqlList, builder, getSqlType(), obj);
		return sqlList;
	}

	protected void addSelectFromTable(final TableRelation obj, final SqlSignature sqlSignature, String alias,
			final S builder) {
		builder.select();
		addSelectAllColumns(obj, sqlSignature, alias, builder);
		builder.lineBreak();
		builder.from();
		builder.nameAs(obj.getTable(), alias);
	}

	protected void addSelectAllColumns(final TableRelation obj, final SqlSignature sqlSignature, String alias,
			final S builder) {
		builder.indent(() -> {
			boolean first = true;
			for (final Column column : obj.getTable().getColumns()) {
				builder.lineBreak();
				builder.comma(!first);
				builder.name(alias + ".", column);
				this.addSelectColumnComment(column, builder);
				first = false;
			}
		});
	}

	protected void addSelectOrderby(final TableRelation obj, final SqlSignature sqlSignature, String alias, S builder) {
		ColumnSelectionStrategy strategy = this.getTableOptions().getSelectOrderByColumnsStrategy()
				.apply(obj.getTable());
		ColumnsHolder columnsHolder = strategy.getWithoutCheck(sqlSignature);
		builder.lineBreak();
		builder.orderBy();
		columnsHolder.forEachKeyColumn((i, column) -> {
			builder.comma(i > 0).name(alias + ".", column);
		});
	}

	protected void addSelectConditionColumns(TableRelation obj, final SqlSignature sqlSignature, final String alias,
			S builder) {
		String previousAlias = null;
		TableRelation previous = null;
		previousAlias = alias;
		previous = obj;
		List<TableRelation> listParents = obj.getParentTableRelations();
		for (int i = 0; i < listParents.size(); i++) {
			TableRelation parent = listParents.get(i);
			String currentAlias = (alias + (i + 1));
			previous.addJoin(previousAlias, currentAlias, builder);
			previousAlias = currentAlias;
			previous = parent;
		}
		builder.lineBreak();
		builder.where().true_();
		builder.lineBreak();
		builder._add("/*ROWS_EQUALS(");
		builder._add("target=ROOT;prefix=" + alias + listParents.size() + ".");
		builder._add(")*/");
	}
}
