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

package com.sqlapp.jdbc.sql.node;

import com.sqlapp.data.db.sql.ColumnSelectionStrategy;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.BindParameterHolder;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SqlBuilder;

/**
 * ROW_EQUALS(PRIMARY_KEY) 句で利用する複数のバインド変数のノード
 * 
 * @author satoh
 *
 */
public class RowsEqualsBindVariableNode extends CommentNode {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 8430153028619529776L;

	private ColumnSelectionStrategy columnSelectionStrategy;

	public ColumnSelectionStrategy getColumnSelectionStrategy() {
		return columnSelectionStrategy;
	}

	public void setColumnSelectionStrategy(ColumnSelectionStrategy columnSelectionStrategy) {
		this.columnSelectionStrategy = columnSelectionStrategy;
	}

	@Override
	public void setExpression(final String expression) {
		this.expression = expression;
	}

	@Override
	public boolean eval(final Object context, final SqlParameterCollection sqlParameters) {
		addValues(sqlParameters, context);
		return true;
	}

	/**
	 * SqlParameterCollectionに値を追加する
	 * 
	 * @param sqlParameters
	 * @param val
	 */
	private void addValues(final SqlParameterCollection sqlParameters, final Object context) {
		final RowCollection rows = getRowCollection(context);
		SqlSignature sqlSignature = sqlParameters.getSqlSignature();
		final SqlSignature.ColumnsHolder columnHolder = columnSelectionStrategy.get(sqlSignature);
		final int size = rows.size();
		final BindParameterHolder holder = new BindParameterHolder();
		SqlBuilder builder = new SqlBuilder(this.getDialect());
		if (columnHolder.getKeyColumns().size() == 1) {
			Column column = CommonUtils.first(columnHolder.getKeyColumns());
			builder.space().or().name(column);
			builder.in().space().brackets(() -> {
				for (int i = 0; i < size; i++) {
					Row row = rows.get(i);
					builder.space(i == 0).comma(i > 0)._add("?");
					BindParameter dbParameter = new BindParameter();
					dbParameter.setColumn(column);
					dbParameter.setValue(row.get(column));
					holder.getBindParameters().add(dbParameter);
				}
			});
		} else {
			addRowValueComparisonAllPattern(rows, columnHolder, holder, builder);
		}
		sqlParameters.addSql(builder.toString());
		sqlParameters.add(holder);
	}

	private void addRowValueComparisonAllPattern(final RowCollection rows,
			final SqlSignature.ColumnsHolder columnHolder, final BindParameterHolder holder, final SqlBuilder builder) {
		boolean supportsRowValueComparisonIn = this.getDialect().supportsRowValueComparisonIn();
		if (supportsRowValueComparisonIn) {
			addRowValueComparisonIn(rows, columnHolder, holder, builder);
			return;
		}
		boolean supportsRowValueComparison = this.getDialect().supportsRowValueComparison();
		if (supportsRowValueComparison) {
			addRowValueComparison(rows, columnHolder, holder, builder);
			return;
		}
		addRowValueOrComparison(rows, columnHolder, holder, builder);
	}

	private void addRowValueOrComparison(final RowCollection rows, final SqlSignature.ColumnsHolder columnHolder,
			final BindParameterHolder holder, final SqlBuilder builder) {
		final int size = rows.size();
		builder.lineBreak();
		builder.or().space().brackets(true, () -> {
			for (int i = 0; i < size; i++) {
				Row row = rows.get(i);
				builder.lineBreak(i > 0);
				builder.or(i > 0).space().brackets(() -> {
					columnHolder.forEachKeyColumn((j, column) -> {
						builder.and(j > 0);
						builder.name(column).eq().space()._add("?");
						BindParameter dbParameter = new BindParameter();
						dbParameter.setColumn(column);
						dbParameter.setValue(row.get(column));
						holder.getBindParameters().add(dbParameter);
					});
				});
			}
		});
	}

	private void addRowValueComparison(final RowCollection rows, final SqlSignature.ColumnsHolder columnHolder,
			final BindParameterHolder holder, final SqlBuilder builder) {
		final int size = rows.size();
		builder.lineBreak();
		builder.or().space().brackets(true, () -> {
			for (int i = 0; i < size; i++) {
				Row row = rows.get(i);
				builder.lineBreak(i > 0).or(i > 0).space();
				builder.brackets(() -> {
					columnHolder.forEachKeyColumn((j, column) -> {
						builder.comma(j > 0);
						builder.name(column);
					});
				});
				builder.space().eq().space().brackets(() -> {
					columnHolder.forEachKeyColumn((j, column) -> {
						builder.space(j == 0).comma(j > 0);
						builder._add("?");
						BindParameter dbParameter = new BindParameter();
						dbParameter.setColumn(column);
						dbParameter.setValue(row.get(column));
						holder.getBindParameters().add(dbParameter);
					});
				});
			}
		});
	}

	private void addRowValueComparisonIn(final RowCollection rows, final SqlSignature.ColumnsHolder columnHolder,
			final BindParameterHolder holder, final SqlBuilder builder) {
		final int size = rows.size();
		builder.indent(() -> {
			builder.lineBreak();
			builder.or().space().brackets(() -> {
				columnHolder.forEachKeyColumn((i, column) -> {
					builder.comma(i > 0);
					builder.name(column);
				});
			});
			builder.in().space().brackets(() -> {
				for (int i = 0; i < size; i++) {
					Row row = rows.get(i);
					builder.space(i == 0).comma(i > 0).brackets(() -> {
						columnHolder.forEachKeyColumn((j, column) -> {
							builder.space(j == 0).comma(j > 0);
							builder._add("?");
							BindParameter dbParameter = new BindParameter();
							dbParameter.setColumn(column);
							dbParameter.setValue(row.get(column));
							holder.getBindParameters().add(dbParameter);
						});
					});
				}
			});
		});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public RowsEqualsBindVariableNode clone() {
		return (RowsEqualsBindVariableNode) super.clone();
	}
}