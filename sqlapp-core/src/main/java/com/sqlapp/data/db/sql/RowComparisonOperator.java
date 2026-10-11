/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlSignature.ColumnsHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.BindParameterHolder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SqlBuilder;

public enum RowComparisonOperator {
	EQUAL("=") {
		@Override
		protected void addOperator(final SqlBuilder builder) {
			builder.eq();
		}

		@Override
		protected void addMultiColumnOperatorIn(ColumnsHolder columnsHolder, final Row row, String prefix,
				final BindParameterHolder holder, final SqlBuilder builder) {
			builder.brackets(() -> {
				columnsHolder.forEachKeyColumn((i, column) -> {
					builder.comma(i > 0);
					columnsHolder.addName(column, prefix, builder);
				});
			});
			builder.in();
			builder.space().brackets(() -> {
				columnsHolder.forEachKeyColumn((j, column) -> {
					builder.space(j == 0).comma(j > 0);
					builder._add("?");
					BindParameter dbParameter = new BindParameter();
					dbParameter.setColumn(column);
					dbParameter.setValue(row.get(column));
					holder.getBindParameters().add(dbParameter);
				});
			});
		}

		@Override
		protected void addMultiColumnOperator(ColumnsHolder columnsHolder, final Row row, String prefix,
				final BindParameterHolder holder, final SqlBuilder builder) {
			builder.brackets(true, () -> {
				Column col = CommonUtils.first(columnsHolder.getKeyColumns());
				columnsHolder.addName(col, prefix, builder);
				builder.in();
				builder.space().brackets(() -> {
					columnsHolder.forEachKeyColumn((i, column) -> {
						builder.comma(i > 0);
						if (!CommonUtils.isEmpty(prefix)) {
							builder.name(prefix, column);
						} else {
							builder.name(column, true);
						}
						BindParameter dbParameter = new BindParameter();
						dbParameter.setColumn(column);
						dbParameter.setValue(row.get(column));
						holder.getBindParameters().add(dbParameter);
					});
				});
			});
		}
	},
	GREATER_THAN_OR_EQUAL(">=") {
		@Override
		protected void addOperator(final SqlBuilder builder) {
			builder.gte();
		}
	},
	GREATER_THAN(">") {
		@Override
		protected void addOperator(final SqlBuilder builder) {
			builder.gt();
		}
	},
	LESS_THAN_OR_EQUAL("<=") {
		@Override
		protected void addOperator(final SqlBuilder builder) {
			builder.lte();
		}
	},
	LESS_THAN("<") {
		@Override
		protected void addOperator(final SqlBuilder builder) {
			builder.lt();
		}
	};

	private final String symbol;

	protected void addOperator(final SqlBuilder builder) {

	}

	protected void addOperator(final boolean condition, final SqlBuilder builder) {
		if (condition) {
			addOperator(builder);
		}
	}

	public BindParameterHolder addOperator(final Dialect dialect, ColumnsHolder columnsHolder, final Row row,
			String prefix, final SqlBuilder builder) {
		final BindParameterHolder holder = new BindParameterHolder();
		if (columnsHolder.getKeyColumns().size() == 1) {
			Column column = CommonUtils.first(columnsHolder.getKeyColumns());
			columnsHolder.addName(column, prefix, builder);
			addOperator(builder);
			builder.space()._add("?");
			BindParameter dbParameter = new BindParameter();
			dbParameter.setColumn(column);
			dbParameter.setValue(row.get(column));
			holder.getBindParameters().add(dbParameter);
		} else {
			addMultiColumnOperator(dialect, columnsHolder, row, prefix, holder, builder);
		}
		return holder;
	}

	protected void addMultiColumnOperator(final Dialect dialect, ColumnsHolder columnsHolder, final Row row,
			String prefix, final BindParameterHolder holder, final SqlBuilder builder) {
		boolean supportsRowValueComparisonWithParameters = dialect.supportsRowValueComparisonWithParameters();
		boolean supportsRowValueComparisonIn = dialect.supportsRowValueComparisonIn();
		if (supportsRowValueComparisonIn && supportsRowValueComparisonWithParameters) {
			addMultiColumnOperatorIn(columnsHolder, row, prefix, holder, builder);
			return;
		}
		addMultiColumnOperator(columnsHolder, row, prefix, holder, builder);
	}

	protected void addMultiColumnOperator(ColumnsHolder columnsHolder, final Row row, String prefix,
			final BindParameterHolder holder, final SqlBuilder builder) {
		final int size = columnsHolder.getKeyColumns().size();
		builder.brackets(true, () -> {
			Column col = CommonUtils.first(columnsHolder.getKeyColumns());
			columnsHolder.addName(col, prefix, builder);
			addOperator(builder);
			builder.space()._add("?");
			BindParameter firstParameter = new BindParameter();
			firstParameter.setColumn(col);
			firstParameter.setValue(row.get(col));
			holder.getBindParameters().add(firstParameter);
			for (int i = 1; i < size; i++) {
				int[] cnt = new int[1];
				cnt[0] = i;
				builder.lineBreak();
				builder.or().space().brackets(() -> {
					boolean[] first = new boolean[1];
					first[0] = true;
					columnsHolder.forEachKeyColumn((j, column) -> {
						if (j > cnt[0]) {
							return;
						}
						builder.and(!first[0]);
						columnsHolder.addName(column, prefix, builder);
						addOperator(j >= cnt[0], builder);
						builder.eq(j < cnt[0]).space()._add("?");
						BindParameter dbParameter = new BindParameter();
						dbParameter.setColumn(column);
						dbParameter.setValue(row.get(column));
						holder.getBindParameters().add(dbParameter);
						first[0] = false;
					});
				});
			}
		});
	}

	protected void addMultiColumnOperatorIn(ColumnsHolder columnsHolder, final Row row, String prefix,
			final BindParameterHolder holder, final SqlBuilder builder) {
		builder.brackets(() -> {
			columnsHolder.forEachKeyColumn((i, column) -> {
				builder.comma(i > 0);
				columnsHolder.addName(column, prefix, builder);
			});
		});
		addOperator(builder);
		builder.space().brackets(() -> {
			columnsHolder.forEachKeyColumn((j, column) -> {
				builder.space(j == 0).comma(j > 0);
				builder._add("?");
				BindParameter dbParameter = new BindParameter();
				dbParameter.setColumn(column);
				dbParameter.setValue(row.get(column));
				holder.getBindParameters().add(dbParameter);
			});
		});
	}

	private RowComparisonOperator(String symbol) {
		this.symbol = symbol;
	}

	public String getSymbol() {
		return this.symbol;
	}

	public static RowComparisonOperator parse(String text) {
		for (RowComparisonOperator enm : values()) {
			if (enm.getSymbol().equals(text)) {
				return enm;
			}
		}
		return null;
	}
}
