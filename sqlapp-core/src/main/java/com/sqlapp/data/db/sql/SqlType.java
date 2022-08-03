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

package com.sqlapp.data.db.sql;

import java.util.Comparator;

import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.EnumUtils;

/**
 * SQL TYPE
 * 
 * @author tatsuo satoh
 * 
 */
public enum SqlType{
	//DCL
	/**
	 * IDENTITY ON
	 */
	IDENTITY_ON(SqlMetaType.DCL){
		@Override
		public SqlType reverse(){
			return IDENTITY_OFF;
		}
	}
	,/**
	 * IDENTITY OFF
	 */
	IDENTITY_OFF(SqlMetaType.DCL){
		@Override
		public SqlType reverse(){
			return IDENTITY_ON;
		}
	}
	,/**
	 * DDL AUTOCOMMIT ON
	 */
	DDL_AUTOCOMMIT_ON(SqlMetaType.DCL){
		@Override
		public SqlType reverse(){
			return DDL_AUTOCOMMIT_OFF;
		}
	}
	,/**
	 * DDL AUTOCOMMIT OFF
	 */
	DDL_AUTOCOMMIT_OFF(SqlMetaType.DCL){
		@Override
		public SqlType reverse(){
			return DDL_AUTOCOMMIT_ON;
		}
	}
	,
	/**
	 * SET_SEARCH_PATH_TO_SCHEMA
	 */
	SET_SEARCH_PATH_TO_SCHEMA(SqlMetaType.DCL)
	,
	/**
	 * GRANT
	 */
	GRANT(SqlMetaType.DCL){
		@Override
		public SqlType reverse(){
			return REVOKE;
		}
	}
	,
	/**
	 * REVOKE
	 */
	REVOKE(SqlMetaType.DCL){
		@Override
		public SqlType reverse(){
			return GRANT;
		}
	}
	,
	/**
	 * ANALYZE
	 */
	ANALYZE(SqlMetaType.DCL)
	,
	/**
	 * DEFRAG
	 */
	DEFRAG(SqlMetaType.DCL)
	,
	/**
	 * DEFRAG FULL
	 */
	DEFRAG_FULL(SqlMetaType.DCL)
	,
	/**
	 * OPTIMIZE
	 */
	OPTIMIZE(SqlMetaType.DCL)
	,
	/**
	 * REPAIR
	 */
	REPAIR(SqlMetaType.DCL)
	,
	/**
	 * REBUILD
	 */
	REBUILD(SqlMetaType.DCL)
	//==============================DML=====================================
	,
	/**
	 * SELECT
	 */
	SELECT(SqlMetaType.DML)
	,
	/**
	 * SELECT ALL
	 */
	SELECT_ALL(SqlMetaType.DML)
	,
	/**
	 * SELECT BY PK
	 */
	SELECT_BY_PK(SqlMetaType.DML)
	,
	/**
	 * INSERT
	 */
	INSERT(SqlMetaType.DML, State.Added){
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.CREATE.getComparator();
		}
		@Override
		public SqlType reverse(){
			return DELETE;
		}
	}
	,
	/**
	 * INSERT ROW
	 */
	INSERT_ROW(SqlMetaType.DML, State.Added){
		@Override
		public boolean supportRows(){
			return true;
		}
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.CREATE.getComparator();
		}
		@Override
		public SqlType reverse(){
			return DELETE_ROW;
		}
	}
	,
	/**
	 * INSERT AS SELECT FROM WHERE NOT EXISTS PK
	 */
	INSERT_SELECT_ROW(SqlMetaType.DML, State.Added){
		@Override
		public boolean supportRows(){
			return true;
		}
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.CREATE.getComparator();
		}
	}
	,
	/**
	 * INSERT AS SELECT FROM WHERE NOT EXISTS PK
	 */
	INSERT_SELECT_BY_PK(SqlMetaType.DML, State.Added){
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.CREATE.getComparator();
		}
	}
	,
	/**
	 * INSERT AS SELECT
	 */
	INSERT_SELECT_ALL(SqlMetaType.DML, State.Added){
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.CREATE.getComparator();
		}
	}
	,
	/**
	 * UPDATE
	 */
	UPDATE(SqlMetaType.DML, State.Modified){
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.CREATE.getComparator();
		}
	}
	,
	/**
	 * UPDATE_BY_PK
	 */
	UPDATE_BY_PK(SqlMetaType.DML, State.Modified){
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.CREATE.getComparator();
		}
		@Override
		public final boolean isOptimisticLockable(){
			return true;
		}
	}
	,/**
	 * UPDATE ROW
	 */
	UPDATE_ROW(SqlMetaType.DML, State.Modified){
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.CREATE.getComparator();
		}

		@Override
		public boolean supportRows(){
			return true;
		}
	}
	,
	/**
	 * UPDATE ALL
	 */
	UPDATE_ALL(SqlMetaType.DML, State.Modified){
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.CREATE.getComparator();
		}
	}
	,
	/**
	 * DELETE_BY_PK
	 */
	DELETE_BY_PK(SqlMetaType.DML, State.Deleted){
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.DROP.getComparator();
		}
		@Override
		public final boolean isOptimisticLockable(){
			return true;
		}
	}
	,
	/**
	 * DELETE
	 */
	DELETE(SqlMetaType.DML, State.Deleted){
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.DROP.getComparator();
		}
		@Override
		public SqlType reverse(){
			return INSERT;
		}
	}
	,
	/**
	 * DELETE ROW
	 */
	DELETE_ROW(SqlMetaType.DML, State.Deleted){
		@Override
		public boolean supportRows(){
			return true;
		}
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.DROP.getComparator();
		}
		@Override
		public SqlType reverse(){
			return INSERT_ROW;
		}
	}
	,
	/**
	 * DELETE ALL
	 */
	DELETE_ALL(SqlMetaType.DML, State.Deleted){
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.DROP.getComparator();
		}
	}
	,/**
	 * MERGE(UPSERT)
	 */
	MERGE_BY_PK(SqlMetaType.DML, State.Modified){
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[]{INSERT_SELECT_BY_PK, UPDATE_BY_PK};
		}
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.CREATE.getComparator();
		}
	}
	,
	/**
	 * MERGE ROW
	 */
	MERGE_ROW(SqlMetaType.DML, State.Modified){
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[]{INSERT_SELECT_ROW, UPDATE};
		}
		@Override
		public boolean supportRows(){
			return true;
		}
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.CREATE.getComparator();
		}
	}
	,/**
	 * MERGE(UPSERT)
	 */
	MERGE_ALL(SqlMetaType.DML, State.Modified){
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[]{INSERT_SELECT_ALL, UPDATE_ALL};
		}
		@Override
		public Comparator<Table> getTableComparator(){
			return Table.TableOrder.CREATE.getComparator();
		}
	}
	,/**
	 * LOCK
	 */
	LOCK(SqlMetaType.DML)
	//==============================DDL=====================================
	,/**
	 * CREATE
	 */
	CREATE(SqlMetaType.DDL, State.Added){
		@Override
		public SqlType reverse(){
			return DROP;
		}
	}
	,/**
	 * DROP
	 */
	DROP(SqlMetaType.DDL, State.Deleted){
		@Override
		public SqlType reverse(){
			return CREATE;
		}
	}
	,/**
	 * ALTER
	 */
	ALTER(SqlMetaType.DDL, State.Modified){
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[]{DROP, CREATE};
		}
	}
	,/**
	 * ADD PARTITION
	 */
	ADD_PARTITION(SqlMetaType.DDL, State.Added){
		@Override
		public SqlType reverse(){
			return DROP_PARTITION;
		}
	}
	,/**
	 * DROP PARTITION
	 */
	DROP_PARTITION(SqlMetaType.DDL, State.Deleted){
		@Override
		public SqlType reverse(){
			return ADD_PARTITION;
		}
	}
	,/**
	 * MODIFY PARTITION
	 */
	MODIFY_PARTITION(SqlMetaType.DDL, State.Deleted){
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[]{DROP_PARTITION, ADD_PARTITION};
		}
	}
	,
	/**
	 * MERGE PARTITION
	 */
	MERGE_PARTITION(SqlMetaType.DDL, State.Modified){
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[]{DROP_PARTITION, ADD_PARTITION};
		}
		@Override
		public SqlType reverse(){
			return SPLIT_PARTITION;
		}
	}
	,
	/**
	 * SPLIT PARTITION
	 */
	SPLIT_PARTITION(SqlMetaType.DDL, State.Modified){
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[]{ADD_PARTITION};
		}
		@Override
		public SqlType reverse(){
			return MERGE_PARTITION;
		}
	}
	,
	/**
	 * TRUNCATE
	 */
	TRUNCATE(SqlMetaType.DDL, State.Deleted){
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[]{DELETE_ALL, COMMIT};
		}
	}
	,
	/**
	 * REFRESH(FOR Materialized View)
	 */
	REFRESH(SqlMetaType.DDL, null)
	,
	/**
	 * REFRESH FAST(FOR Materialized View)
	 */
	REFRESH_FAST(SqlMetaType.DDL, null)
	,/**
	 * REFRESH COMPLETE(FOR Materialized View)
	 */
	REFRESH_COMPLETE(SqlMetaType.DDL, null)
	, 
	/**
	 * SET_COMMENT
	 */
	SET_COMMENT(SqlMetaType.COMMENT, null)
	//==============================TCL=====================================
	,
	/**
	 * COMMIT
	 */
	COMMIT(SqlMetaType.TCL)
	,
	/**
	 * ROLLBACK
	 */
	ROLLBACK(SqlMetaType.TCL)
	//==============================OTHER=====================================
	,
	/**
	 * COMMENT
	 */
	COMMENT(SqlMetaType.COMMENT, null){
		@Override
		public boolean isComment(){
			return true;
		}
	}
	,
	/**
	 * EMPTY_LINE
	 */
	EMPTY_LINE(SqlMetaType.EMPTY_LINE, null){
		@Override
		public boolean isEmptyLine(){
			return true;
		}
	}
	,
	/**
	 * OTHER
	 */
	OTHER(SqlMetaType.OTHER, null)
	;
	/**
	 * 対応するステート
	 */
	private final State state;

	private final SqlMetaType sqlMetaType;

	private SqlType(final SqlMetaType sqlMetaType, final State state) {
		this.sqlMetaType = sqlMetaType;
		this.state = state;
	}

	private SqlType(final SqlMetaType sqlMetaType) {
		this(sqlMetaType, null);
	}

	/**
	 * @return the sqlMetaType
	 */
	public SqlMetaType getSqlMetaType() {
		return sqlMetaType;
	}

	public Comparator<Table> getTableComparator(){
		return null;
	}
	
	/**
	 * DMLかどうか
	 * 
	 */
	public boolean isDml() {
		return this.sqlMetaType == SqlMetaType.DML;
	}

	/**
	 * DCLかどうか
	 * 
	 */
	public boolean isDcl() {
		return this.sqlMetaType == SqlMetaType.DCL;
	}

	public boolean isComment(){
		return false;
	}

	public boolean isEmptyLine(){
		return false;
	}

	public SqlType reverse(){
		return this;
	}
	
	/**
	 * DDLかどうか
	 * 
	 */
	public boolean isDdl() {
		return this.sqlMetaType == SqlMetaType.DDL;
	}

	private static final SqlType[] EMPTY=new SqlType[0];
	
	/**
	 * @return the surrogates
	 */
	public SqlType[] getSurrogates() {
		return EMPTY;
	}

	/**
	 * @return the state
	 */
	public State getState() {
		return state;
	}

	public boolean supportRows(){
		return false;
	}
	
	/**
	 * 楽観的ロック可能か?
	 */
	public boolean isOptimisticLockable(){
		return false;
	}

	/**
	 * 文字列から値を取得します。
	 * 
	 * @param type
	 */
	public static SqlType parse(final String type) {
		return EnumUtils.parse(SqlType.class, type);
	}
	
}
