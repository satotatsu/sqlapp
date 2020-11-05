/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.jdbc.sql.node;

import com.sqlapp.jdbc.sql.ResultSetConcurrency;
import com.sqlapp.jdbc.sql.ResultSetHoldability;
import com.sqlapp.jdbc.sql.ResultSetType;
import com.sqlapp.jdbc.sql.SqlParameterCollection;

/**
 * JDBCのクエリのノード
 * 
 * @author tatsuo satoh
 *
 */
public class QueryNode extends CommentNode {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * JDBCのフェッチサイズ
	 */
	private Integer fetchSize = 100;
	/**
	 * 結果セットの型。TYPE_FORWARD_ONLY、TYPE_SCROLL_INSENSITIVE、または
	 * TYPE_SCROLL_SENSITIVE のうちの 1 つ
	 */
	private ResultSetType resultSetType = ResultSetType.getDefault();
	/**
	 * 並行処理の種類。CONCUR_READ_ONLY または CONCUR_UPDATABLE
	 */
	private ResultSetConcurrency resultSetConcurrency = ResultSetConcurrency
			.getDefault();
	/**
	 * resultSetの保持期間。 HOLD_CURSORS_OVER_COMMIT または CLOSE_CURSORS_AT_COMMIT
	 */
	private ResultSetHoldability resultSetHoldability = ResultSetHoldability
			.getDefault();

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.sql.node.Node#eval(com.sqlapp.data.sql.node.Node,
	 * java.lang.Object, com.sqlapp.data.sql.SqlParameterCollection)
	 */
	@Override
	public boolean eval(Object context, SqlParameterCollection sqlParameters) {
		sqlParameters.setResultSetConcurrency(this.getResultSetConcurrency());
		sqlParameters.setResultSetHoldability(this.getResultSetHoldability());
		sqlParameters.setResultSetType(this.getResultSetType());
		sqlParameters.setFetchSize(this.getFetchSize());
		return true;
	}

	/**
	 * @return the fetchSize
	 */
	public Integer getFetchSize() {
		return fetchSize;
	}

	/**
	 * @param fetchSize
	 *            the fetchSize to set
	 */
	public void setFetchSize(Integer fetchSize) {
		this.fetchSize = fetchSize;
	}

	/**
	 * @return the resultSetType
	 */
	public ResultSetType getResultSetType() {
		return resultSetType;
	}

	/**
	 * @param resultSetType
	 *            the resultSetType to set
	 */
	public void setResultSetType(ResultSetType resultSetType) {
		this.resultSetType = resultSetType;
	}

	/**
	 * @return the resultSetConcurrency
	 */
	public ResultSetConcurrency getResultSetConcurrency() {
		return resultSetConcurrency;
	}

	/**
	 * @param resultSetConcurrency
	 *            the resultSetConcurrency to set
	 */
	public void setResultSetConcurrency(
			ResultSetConcurrency resultSetConcurrency) {
		this.resultSetConcurrency = resultSetConcurrency;
	}

	/**
	 * @return the resultSetHoldability
	 */
	public ResultSetHoldability getResultSetHoldability() {
		return resultSetHoldability;
	}

	/**
	 * @param resultSetHoldability
	 *            the resultSetHoldability to set
	 */
	public void setResultSetHoldability(
			ResultSetHoldability resultSetHoldability) {
		this.resultSetHoldability = resultSetHoldability;
	}

}
