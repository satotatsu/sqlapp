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

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnCollection;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.BindParameterHolder;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SeparatedStringBuilder;

/**
 * VALUES 句で利用する複数のバインド変数のノード
 * 
 * @author satoh
 *
 */
public class ValuesBindVariableNode extends NeedsEndNode {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 8430153028619529776L;

	@Override
	public void setExpression(final String expression) {
		this.expression = expression;
	}

	@Override
	public boolean eval(final Object context, final SqlParameterCollection sqlParameters) {
		addValues(sqlParameters, context);
		return true;
	}

	private static final String UNION_ALL = "UNION ALL";

	/**
	 * SqlParameterCollectionに値を追加する
	 * 
	 * @param sqlParameters
	 * @param val
	 */
	private void addValues(final SqlParameterCollection sqlParameters, final Object context) {
		final boolean supportsValues = supportsValues(sqlParameters.getDialect());
		final RowCollection rows = getRowCollection(context);
		final ColumnCollection columns = getColumns(rows);
		final int size = rows.size();
		boolean hasRowNo = hasRowNo(rows);
		final String questionText;
		if (hasRowNo) {
			questionText = getQuestionText(size + 1);
		} else {
			questionText = getQuestionText(size);
		}
		final BindParameterHolder holder = new BindParameterHolder();
		if (supportsValues) {
			SeparatedStringBuilder builder = new SeparatedStringBuilder("\n, ");
			builder.setStart(this.getExpression() + "\n  ");
			builder.setOpenQuate("(").setCloseQuate(")");
			for (int i = 0; i < size; i++) {
				Row row = rows.get(i);
				if (hasRowNo) {
					BindParameter rowNoParameter = createRowNo(row);
					holder.getBindParameters().add(rowNoParameter);
				}
				for (Column column : columns) {
					BindParameter dbParameter = new BindParameter();
					dbParameter.setName("row(" + column.getName() + ")");
					dbParameter.setValue(row.get(column));
					dbParameter.setType(column.getDataType());
					holder.getBindParameters().add(dbParameter);
				}
				builder.add(questionText);
			}
			sqlParameters.addSql(builder.toString());
		} else {
			String dummyTable = getDummyTableNamme(sqlParameters.getDialect());
			SeparatedStringBuilder builder = new SeparatedStringBuilder("\n" + UNION_ALL + "\n");
			for (int i = 0; i < size; i++) {
				Row row = rows.get(i);
				if (hasRowNo) {
					BindParameter rowNoParameter = createRowNo(row);
					holder.getBindParameters().add(rowNoParameter);
				}
				for (Column column : columns) {
					BindParameter dbParameter = new BindParameter();
					dbParameter.setName("row(" + column.getName() + ")");
					dbParameter.setValue(row.get(column));
					dbParameter.setType(column.getDataType());
					holder.getBindParameters().add(dbParameter);
				}
				builder.add(createSelectText(questionText, dummyTable));
			}
			sqlParameters.addSql(builder.toString());
		}
		sqlParameters.add(holder);
	}

	private String createSelectText(String questionText, String dummyTable) {
		if (CommonUtils.isEmpty(dummyTable)) {
			return "SELECT " + questionText;
		}
		return "SELECT " + questionText + " FROM " + dummyTable;
	}

	private String getDummyTableNamme(Dialect dialect) {
		if (dialect == null) {
			return "";
		}
		return dialect.getSelectDummyTableName() != null ? dialect.getSelectDummyTableName() : "";
	}

	private boolean supportsValues(Dialect dialect) {
		if (dialect == null) {
			return false;
		}
		return dialect.supportsValues();
	}

	/**
	 * 子供の全要素のevalを実施する
	 * 
	 * @param context
	 * @param sqlParameters
	 * @return true
	 */
	@Override
	protected boolean evalChilds(Object context, SqlParameterCollection sqlParameters) {
		// VALUES (,),(,)/*END*/までは評価しても意味がないので無視
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public ValuesBindVariableNode clone() {
		return (ValuesBindVariableNode) super.clone();
	}
}