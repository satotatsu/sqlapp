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

import com.sqlapp.data.db.sql.ColumnSelectionStrategy;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.db.sql.SqlSignature.ColumnsHolder;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.jdbc.sql.BindParameterHolder;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
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
	 * @param context
	 */
	private void addValues(final SqlParameterCollection sqlParameters, final Object context) {
		final List<Row> rows = getRowList(context);
		final SqlSignature sqlSignature = sqlParameters.getSqlSignature();
		final ColumnsHolder columnsHolder = columnSelectionStrategy.get(sqlSignature);
		SqlBuilder builder = new SqlBuilder(this.getDialect());
		builder.space().or().space();
		final BindParameterHolder holder = columnsHolder.addInParameters(getDialect(), rows, null, builder);
		sqlParameters.add(holder);
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