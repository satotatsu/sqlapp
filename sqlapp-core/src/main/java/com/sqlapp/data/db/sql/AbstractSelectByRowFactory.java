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
import com.sqlapp.util.CommonUtils;

/**
 * SELECT BY Root Rows
 * 
 * @author satoh
 * 
 */
public abstract class AbstractSelectByRowFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractTableRelationFactory<S> {

	protected SqlType getSqlType() {
		return SqlType.SELECT_BY_ROW;
	}

	@Override
	public List<SqlOperation> createSql(final TableRelation obj) {
		final S builder = createSqlBuilder();
		final SqlSignature sqlSignature = this.createSqlSignature(obj);
		String alias = "a";
		final ColumnSelectionStrategy strategy = ColumnSelectionStrategy.PRIMARY_KEY_OR_UNIQUE_KEY_OR_NOT_NULL_UNIQUE_INDEX;
		ColumnsHolder columnsHolder = strategy.getWithoutCheck(sqlSignature);
		addSelectFromTable(obj, columnsHolder, alias, builder);
		addSelectConditionColumns(obj, columnsHolder, alias, builder);
		addSelectOrderby(obj, columnsHolder, alias, builder);
		final List<SqlOperation> sqlList = list();
		addSql(sqlList, builder, getSqlType(), obj);
		return sqlList;
	}

	protected void addSelectFromTable(final TableRelation obj, final ColumnsHolder columnsHolder, String alias,
			final S builder) {
		builder.select();
		addSelectAllColumns(obj, columnsHolder, alias, builder);
		builder.lineBreak();
		builder.from();
		builder.nameAs(obj.getTable(), alias);
	}

	protected void addSelectAllColumns(final TableRelation obj, final ColumnsHolder columnsHolder, String alias,
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

	protected void addSelectOrderby(final TableRelation obj, final ColumnsHolder columnsHolder, String alias,
			S builder) {
		builder.lineBreak();
		builder.orderBy();
		columnsHolder.forEachKeyColumn((i, column) -> {
			builder.comma(i > 0).name(alias + ".", column);
		});
	}

	protected void addSelectConditionColumns(TableRelation obj, final ColumnsHolder columnsHolder, final String alias,
			S builder) {
		RowComparisonOperator rowComparisonOperator = this.getTableOptions().getSelectByRowComparisonOperatorStrategy()
				.apply(obj.getTable());
		String columnsText = columnsHolder.getKeyColumnsText();
		builder.lineBreak();
		builder.where().true_();
		builder.lineBreak();
		builder._add("/*ROW" + rowComparisonOperator.getSymbol() + "(");
		if (CommonUtils.isEmpty(columnsText)) {
			builder._add("prefix=" + alias + ".");
		} else {
			builder._add("columns=(" + columnsText + ")");
			builder._add(";prefix=" + alias + ".");
		}
		builder._add(")*/");
	}
}
