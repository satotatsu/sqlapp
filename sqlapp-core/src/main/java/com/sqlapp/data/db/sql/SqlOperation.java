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

import java.io.Serializable;
import java.util.Collection;

import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.util.CommonUtils;

public class SqlOperation implements Serializable {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1518852564523842052L;

	public SqlOperation() {
	}

	public static SqlOperation UNDO_OPERATION=new SqlOperation("-- //@UNDO ", SqlType.COMMENT);

	public static SqlOperation EMPTY_LINE_OPERATION=new SqlOperation("", SqlType.EMPTY_LINE);

	public static SqlOperation COMMENT_SEPARATOR_OPERATION=new SqlOperation("-- ###################################################################################################", SqlType.COMMENT);
	
	public SqlOperation(String sqlText) {
		this(sqlText, null);
	}

	public SqlOperation(String sqlText, SqlType sqlType) {
		this(sqlText, sqlType, (DbCommonObject<?>)null, (DbCommonObject<?>)null);
	}

	public SqlOperation(String sqlText, SqlType sqlType, DbCommonObject<?> original) {
		this(sqlText, sqlType, original, null);
	}

	public SqlOperation(String sqlText, SqlType sqlType, DbCommonObject<?> original, DbCommonObject<?> target) {
		this.sqlText = sqlText;
		this.sqlType = sqlType;
		if (original!=null){
			this.originals = new DbCommonObject<?>[]{original};
		} else{
			this.originals = new DbCommonObject<?>[0];
		}
		if (target!=null){
			this.targets = new DbCommonObject<?>[]{target};
		} else{
			this.targets = new DbCommonObject<?>[0];
		}
	}

	public SqlOperation(String sqlText, SqlType sqlType, Collection<? extends DbCommonObject<?>> originals) {
		this(sqlText, sqlType, toArray(originals));
	}

	public SqlOperation(String sqlText, SqlType sqlType, Collection<? extends DbCommonObject<?>> originals, Collection<? extends DbCommonObject<?>> targets) {
		this(sqlText, sqlType, toArray(originals), toArray(targets));
	}

	private static DbCommonObject<?>[] toArray(Collection<? extends DbCommonObject<?>> args){
		if (args==null){
			return null;
		}
		return args.toArray(new DbCommonObject<?>[0]);
	}
	
	public SqlOperation(String sqlText, SqlType sqlType, DbCommonObject<?>[] originals) {
		this(sqlText, sqlType, originals, null);
	}

	public SqlOperation(String sqlText, SqlType sqlType, DbCommonObject<?>[] originals, DbCommonObject<?>[] targets) {
		this.sqlText = sqlText;
		this.sqlType = sqlType;
		this.originals = originals;
		this.targets = targets;
	}

	/**
	 * SQL Type
	 */
	private SqlType sqlType = null;
	/**
	 * SQL text
	 */
	private String sqlText = null;
	/**
	 * SQL text
	 */
	private String startStatementTerminator = null;
	/**
	 * SQL text
	 */
	private String endStatementTerminator = null;
	/**
	 * SQL statement terminator
	 */
	private String terminator = null;

	private transient DbCommonObject<?>[] originals = null;

	private transient DbCommonObject<?>[] targets = null;

	/**
	 * @return the original
	 */
	@SuppressWarnings("unchecked")
	public <T extends DbCommonObject<?>> T getOriginal() {
		return (T)CommonUtils.first(originals);
	}

	/**
	 * @return the original
	 */
	public DbCommonObject<?>[] getOriginals() {
		return originals;
	}

	/**
	 * @return the target
	 */
	@SuppressWarnings("unchecked")
	public <T extends DbCommonObject<?>> T  getTarget() {
		return (T)CommonUtils.first(targets);
	}

	/**
	 * @return the target
	 */
	public DbCommonObject<?>[]  getTargets() {
		return targets;
	}

	/**
	 * @return the sqlType
	 */
	public SqlType getSqlType() {
		return sqlType;
	}

	/**
	 * @return the sqlText
	 */
	public String getSqlText() {
		return sqlText;
	}



	/**
	 * @return the startStatementTerminator
	 */
	public String getStartStatementTerminator() {
		return startStatementTerminator;
	}

	/**
	 * @param startStatementTerminator the startStatementTerminator to set
	 */
	public void setStartStatementTerminator(String startStatementTerminator) {
		this.startStatementTerminator = startStatementTerminator;
	}

	/**
	 * @return the endStatementTerminator
	 */
	public String getEndStatementTerminator() {
		return endStatementTerminator;
	}

	/**
	 * @param endStatementTerminator the endStatementTerminator to set
	 */
	public void setEndStatementTerminator(String endStatementTerminator) {
		this.endStatementTerminator = endStatementTerminator;
	}

	/**
	 * @param sqlText
	 *            the sqlText to set
	 */
	public SqlOperation setSqlText(String sqlText) {
		this.sqlText = sqlText;
		return this;
	}


	/**
	 * @return the terminator
	 */
	public String getTerminator() {
		return terminator;
	}

	/**
	 * @param terminator the terminator to set
	 */
	public void setTerminator(String terminator) {
		this.terminator = terminator;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return this.sqlText;
	}
}
