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
		return SqlType.SELECT;
	}

	@Override
	public List<SqlOperation> createSql(final TableRelation obj) {
		final S builder = createSqlBuilder();
		final SqlSignature sqlSignature = this.createSqlSignature(obj);
		final ColumnSelectionStrategy columnSelectionStrategy = this.getTableOptions()
				.getUpdateKeyColumnsMatchingStrategy().apply(obj.getTable());
		sqlSignature.setColumnSelectionStrategy(columnSelectionStrategy);
		String alias = "a";
		addSelectFromTable(obj, alias, builder);
		addSelectConditionColumns(obj, alias, builder);
		final List<SqlOperation> sqlList = list();
		addSql(sqlList, builder, getSqlType(), obj);
		return sqlList;
	}

	protected void addSelectFromTable(final TableRelation obj, String alias, final S builder) {
		builder.select();
		addSelectAllColumns(obj, alias, builder);
		builder.lineBreak();
		builder.from();
		builder.nameAs(obj.getTable(), alias);
	}

	protected void addSelectAllColumns(final TableRelation obj, String alias, final S builder) {
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

	protected void addSelectConditionColumns(TableRelation obj, final String alias, S builder) {
		String[] previousAlias = new String[1];
		TableRelation[] previous = new TableRelation[1];
		previousAlias[0] = alias;
		previous[0] = obj;
		List<TableRelation> listParents = obj.getParentTableRelations();
		for (int i = 0; i < listParents.size(); i++) {
			TableRelation parent = listParents.get(i);
			String currentAlias = (alias + (i + 1));
			builder.lineBreak();
			builder.inner().join().nameAs(parent.getTable(), currentAlias);
			builder.lineBreak();
			builder.on().brackets(() -> {
				previous[0].forEach((j, c, rc) -> {
					builder.and(j > 0);
					builder.name(previousAlias[0] + ".", c);
					builder.eq();
					builder.name(currentAlias + ".", rc);
				});
			});
			previousAlias[0] = currentAlias;
			previous[0] = parent;
		}
		builder.lineBreak();
		builder.where().false_();
		builder.lineBreak();
		builder._add("/*PARENT_ROWS_EQUALS(");
		builder._add("ROOT," + alias + listParents.size() + ".");
		builder._add(")*/");
	}
}
