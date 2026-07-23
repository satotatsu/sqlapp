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
import java.util.Set;

import com.sqlapp.data.db.sql.ColumnSelectionStrategy;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.db.sql.SqlSignature.ColumnsHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.exceptions.MissingColumnException;
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

	private String target;

	private String prefix;

	private Set<String> columns;

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	private ColumnSelectionStrategy keyType;

	public ColumnSelectionStrategy getKeyType() {
		return keyType;
	}

	public void setKeyType(ColumnSelectionStrategy keyType) {
		this.keyType = keyType;
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public Set<String> getColumns() {
		return columns;
	}

	public void setColumns(Set<String> columns) {
		this.columns = columns;
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
	 * @param context
	 */
	private void addValues(final SqlParameterCollection sqlParameters, final Object context) {
		final List<Row> rows = getRowList(context);
		if (CommonUtils.isEmpty(rows)) {
			return;
		}
		TableRelation tableRelation = sqlParameters.getTableRelation();
		final TableRelation parentTableRelation;
		if ("ROOT".equalsIgnoreCase(getTarget())) {
			parentTableRelation = tableRelation.getRootTableRelation();
		} else if ("PARENT".equalsIgnoreCase(getTarget())) {
			parentTableRelation = tableRelation.getParentTableRelation();
		} else {
			parentTableRelation = tableRelation;
		}
		final SqlSignature sqlSignature = parentTableRelation.getOrCreateSqlSignature(rows);
		final ColumnsHolder columnsHolder;
		if (!CommonUtils.isEmpty(getColumns())) {
			final Set<Column> columns = CommonUtils.linkedSet();
			getColumns().forEach(c -> {
				Column column = parentTableRelation.getTable().getColumns().get(c);
				if (column == null) {
					throw new MissingColumnException(parentTableRelation.getTable(), c, getMatchText());
				}
				columns.add(column);
			});
			columnsHolder = new ColumnsHolder(columns, rows);
		} else {
			if (getKeyType() != null) {
				columnsHolder = getKeyType().get(sqlSignature);
			} else {
				columnsHolder = ColumnSelectionStrategy.PRIMARY_KEY_OR_UNIQUE_KEY_OR_NOT_NULL_UNIQUE_INDEX
						.get(sqlSignature);
			}
		}
		final SqlBuilder builder = new SqlBuilder(this.getDialect());
		builder.indent(1, () -> {
			builder.lineBreak();
			builder.space().and().space();
			final BindParameterHolder holder = columnsHolder.addInParameters(getDialect(), rows, prefix, builder);
			sqlParameters.add(holder);
		});
		sqlParameters.addSql(builder.toString());
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