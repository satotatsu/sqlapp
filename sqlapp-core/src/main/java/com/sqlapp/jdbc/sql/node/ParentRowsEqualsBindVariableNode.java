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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.ForeignKeyNotFoundException;
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
public class ParentRowsEqualsBindVariableNode extends CommentNode {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 8430153028619529776L;

	private String foreignKeyName;

	public String getForeignKeyName() {
		return foreignKeyName;
	}

	public void setForeignKeyName(String foreignKeyName) {
		this.foreignKeyName = foreignKeyName;
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
		final List<Row> rows = getRowList(context);
		final Table parentTable = sqlParameters.getTable();
		Optional<ForeignKeyConstraint> fkOps = parentTable.getChildRelations().stream()
				.filter(fk -> Objects.equals(foreignKeyName, fk.getName())).findFirst();
		if (!fkOps.isPresent()) {
			throw new ForeignKeyNotFoundException(foreignKeyName, null, parentTable);
		}
		ForeignKeyConstraint fk = fkOps.get();
		final int size = rows.size();
		final BindParameterHolder holder = new BindParameterHolder();
		SqlBuilder builder = new SqlBuilder(this.getDialect());
		if (fk.getColumns().size() == 1) {
			Column column = CommonUtils.first(fk.getColumns());
			Column parentColumn = CommonUtils.first(fk.getRelatedColumns()).getColumn();
			builder.space().or().name(column);
			builder.in().space().brackets(() -> {
				for (int i = 0; i < size; i++) {
					Row row = rows.get(i);
					builder.space(i == 0).comma(i > 0)._add("?");
					BindParameter dbParameter = new BindParameter();
					dbParameter.setColumn(column);
					dbParameter.setValue(row.get(parentColumn));
					holder.getBindParameters().add(dbParameter);
				}
			});
		} else {
			addRowValueComparisonAllPattern(rows, fk, holder, builder);
		}
		sqlParameters.addSql(builder.toString());
		sqlParameters.add(holder);
	}

	private void addRowValueComparisonAllPattern(final List<Row> rows, ForeignKeyConstraint fk,
			final BindParameterHolder holder, final SqlBuilder builder) {
		boolean supportsRowValueComparisonIn = this.getDialect().supportsRowValueComparisonIn();
		if (supportsRowValueComparisonIn) {
			addRowValueComparisonIn(rows, fk, holder, builder);
			return;
		}
		boolean supportsRowValueComparison = this.getDialect().supportsRowValueComparison();
		if (supportsRowValueComparison) {
			addRowValueComparison(rows, fk, holder, builder);
			return;
		}
		addRowValueOrComparison(rows, fk, holder, builder);
	}

	private void addRowValueOrComparison(final List<Row> rows, final ForeignKeyConstraint fk,
			final BindParameterHolder holder, final SqlBuilder builder) {
		final int size = rows.size();
		builder.lineBreak();
		builder.or().space().brackets(true, () -> {
			for (int i = 0; i < size; i++) {
				Row row = rows.get(i);
				builder.lineBreak(i > 0);
				builder.or(i > 0).space().brackets(() -> {
					fk.forEach((j, c, rc) -> {
						builder.and(j > 0);
						builder.name(rc).eq().space()._add("?");
						BindParameter dbParameter = new BindParameter();
						dbParameter.setColumn(rc);
						dbParameter.setValue(row.get(rc));
						holder.getBindParameters().add(dbParameter);
					});
				});
			}
		});
	}

	private void addRowValueComparison(final List<Row> rows, final ForeignKeyConstraint fk,
			final BindParameterHolder holder, final SqlBuilder builder) {
		final int size = rows.size();
		builder.lineBreak();
		builder.or().space().brackets(true, () -> {
			for (int i = 0; i < size; i++) {
				Row row = rows.get(i);
				builder.lineBreak(i > 0).or(i > 0).space();
				builder.brackets(() -> {
					fk.forEach((j, c, rc) -> {
						builder.comma(j > 0);
						builder.name(rc);
					});
				});
				builder.space().eq().space().brackets(() -> {
					fk.forEach((j, c, rc) -> {
						builder.space(j == 0).comma(j > 0);
						builder._add("?");
						BindParameter dbParameter = new BindParameter();
						dbParameter.setColumn(rc);
						dbParameter.setValue(row.get(rc));
						holder.getBindParameters().add(dbParameter);
					});
				});
			}
		});
	}

	private void addRowValueComparisonIn(final List<Row> rows, final ForeignKeyConstraint fk,
			final BindParameterHolder holder, final SqlBuilder builder) {
		final int size = rows.size();
		builder.indent(() -> {
			builder.lineBreak();
			builder.or().space().brackets(() -> {
				fk.forEach((i, c, rc) -> {
					builder.comma(i > 0);
					builder.name(rc);
				});
			});
			builder.in().space().brackets(() -> {
				for (int i = 0; i < size; i++) {
					Row row = rows.get(i);
					builder.space(i == 0).comma(i > 0).brackets(() -> {
						fk.forEach((j, c, rc) -> {
							builder.space(j == 0).comma(j > 0);
							builder._add("?");
							BindParameter dbParameter = new BindParameter();
							dbParameter.setColumn(rc);
							dbParameter.setValue(row.get(rc));
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
	public ParentRowsEqualsBindVariableNode clone() {
		return (ParentRowsEqualsBindVariableNode) super.clone();
	}
}