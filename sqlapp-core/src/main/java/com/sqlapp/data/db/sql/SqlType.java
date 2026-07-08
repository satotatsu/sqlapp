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

import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table.TableOrder;
import com.sqlapp.util.EnumUtils;

/**
 * SQL TYPE
 * 
 * @author tatsuo satoh
 * 
 */
public enum SqlType {
	// DCL
	/**
	 * IDENTITY ON
	 */
	IDENTITY_ON(SqlMetaType.DCL) {
		@Override
		public SqlType reverse() {
			return IDENTITY_OFF;
		}
	},
	/**
	 * IDENTITY OFF
	 */
	IDENTITY_OFF(SqlMetaType.DCL) {
		@Override
		public SqlType reverse() {
			return IDENTITY_ON;
		}
	},
	/**
	 * DDL AUTOCOMMIT ON
	 */
	DDL_AUTOCOMMIT_ON(SqlMetaType.DCL) {
		@Override
		public SqlType reverse() {
			return DDL_AUTOCOMMIT_OFF;
		}
	},
	/**
	 * DDL AUTOCOMMIT OFF
	 */
	DDL_AUTOCOMMIT_OFF(SqlMetaType.DCL) {
		@Override
		public SqlType reverse() {
			return DDL_AUTOCOMMIT_ON;
		}
	},
	/**
	 * SET_SEARCH_PATH_TO_SCHEMA
	 */
	SET_SEARCH_PATH_TO_SCHEMA(SqlMetaType.DCL),
	/**
	 * GRANT
	 */
	GRANT(SqlMetaType.DCL) {
		@Override
		public SqlType reverse() {
			return REVOKE;
		}
	},
	/**
	 * REVOKE
	 */
	REVOKE(SqlMetaType.DCL) {
		@Override
		public SqlType reverse() {
			return GRANT;
		}
	},
	/**
	 * ANALYZE
	 */
	ANALYZE(SqlMetaType.DCL),
	/**
	 * DEFRAG
	 */
	DEFRAG(SqlMetaType.DCL),
	/**
	 * DEFRAG FULL
	 */
	DEFRAG_FULL(SqlMetaType.DCL),
	/**
	 * OPTIMIZE
	 */
	OPTIMIZE(SqlMetaType.DCL),
	/**
	 * REPAIR
	 */
	REPAIR(SqlMetaType.DCL),
	/**
	 * REBUILD
	 */
	REBUILD(SqlMetaType.DCL)
	// ==============================DML=====================================
	,
	/**
	 * SELECT
	 */
	SELECT(SqlMetaType.DML),
	/**
	 * SELECT
	 */
	SELECT_ROWS(SqlMetaType.DML) {
		@Override
		public SqlExecuteType getSqlExecuteType() {
			return SqlExecuteType.ROWS;
		}
	},
	/**
	 * SELECT
	 */
	SELECT_TABLE(SqlMetaType.DML) {

	},
	/**
	 * SELECT_FOR_APP
	 */
	SELECT_FOR_APP(SqlMetaType.DML) {

	},
	/**
	 * SEQUENCE_NEXT_VALUES
	 */
	SEQUENCE_NEXT_VALUES(SqlMetaType.DML),
	/**
	 * INSERT
	 */
	INSERT(SqlMetaType.DML, State.Added) {
		@Override
		public TableOrder getTableOrder() {
			return TableOrder.CREATE;
		}

		@Override
		public SqlType reverse() {
			return DELETE;
		}
	},
	/**
	 * MERGE ROWS
	 */
	INSERT_ROWS(SqlMetaType.DML, State.Modified) {
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[] { INSERT };
		}

		@Override
		public boolean supportRows() {
			return true;
		}

		@Override
		public SqlExecuteType getSqlExecuteType() {
			return SqlExecuteType.ROWS;
		}

		@Override
		public TableOrder getTableOrder() {
			return TableOrder.CREATE;
		}
	},
	/**
	 * INSERT AS SELECT FROM WHERE NOT EXISTS PK
	 */
	INSERT_SELECT_NOT_EXISTS(SqlMetaType.DML, State.Added) {
		@Override
		public TableOrder getTableOrder() {
			return TableOrder.CREATE;
		}
	},
	/**
	 * INSERT AS SELECT
	 */
	INSERT_SELECT_TABLE(SqlMetaType.DML, State.Added) {
		@Override
		public TableOrder getTableOrder() {
			return TableOrder.CREATE;
		}
	},
	/**
	 * UPDATE
	 */
	UPDATE(SqlMetaType.DML, State.Modified) {
		@Override
		public TableOrder getTableOrder() {
			return TableOrder.CREATE;
		}

		@Override
		public final boolean isOptimisticLockable() {
			return true;
		}
	},
	/**
	 * UPDATE
	 */
	UPDATE_TABLE(SqlMetaType.DML, State.Modified) {
		@Override
		public TableOrder getTableOrder() {
			return TableOrder.CREATE;
		}
	},
	/**
	 * UPDATE
	 */
	UPDATE_FOR_APP(SqlMetaType.DML, State.Modified) {
		@Override
		public TableOrder getTableOrder() {
			return TableOrder.CREATE;
		}
	},
	/**
	 * DELETE_BY_PK
	 */
	DELETE(SqlMetaType.DML, State.Deleted) {
		@Override
		public TableOrder getTableOrder() {
			return TableOrder.DROP;
		}

		@Override
		public final boolean isOptimisticLockable() {
			return true;
		}
	},
	/**
	 * DELETE ALL
	 */
	DELETE_ALL(SqlMetaType.DML, State.Deleted) {
		@Override
		public TableOrder getTableOrder() {
			return TableOrder.DROP;
		}
	},
	/**
	 * DELETE
	 */
	DELETE_FOR_APP(SqlMetaType.DML, State.Deleted) {
		@Override
		public TableOrder getTableOrder() {
			return TableOrder.DROP;
		}

		@Override
		public SqlType reverse() {
			return INSERT;
		}
	},
	/**
	 * MERGE(UPSERT)
	 */
	MERGE(SqlMetaType.DML, State.Modified) {
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[] { UPDATE, INSERT };
		}

		@Override
		public TableOrder getTableOrder() {
			return TableOrder.CREATE;
		}
	},
	/**
	 * MERGE ROWS
	 */
	MERGE_ROWS(SqlMetaType.DML, State.Modified) {
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[] { MERGE };
		}

		@Override
		public boolean supportRows() {
			return true;
		}

		@Override
		public SqlExecuteType getSqlExecuteType() {
			return SqlExecuteType.ROWS;
		}

		@Override
		public TableOrder getTableOrder() {
			return TableOrder.CREATE;
		}
	},
	/**
	 * MERGE(UPSERT)
	 */
	MERGE_TABLE(SqlMetaType.DML, State.Modified) {
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[] { INSERT_SELECT_TABLE, UPDATE_TABLE };
		}

		@Override
		public TableOrder getTableOrder() {
			return TableOrder.CREATE;
		}
	},
	/**
	 * LOCK
	 */
	LOCK(SqlMetaType.DML)
	// ==============================DDL=====================================
	,
	/**
	 * CREATE
	 */
	CREATE(SqlMetaType.DDL, State.Added) {
		@Override
		public SqlType reverse() {
			return DROP;
		}
	},
	/**
	 * CREATE TEMPORARY
	 */
	CREATE_TEMPORARY(SqlMetaType.DDL, State.Added) {
		@Override
		public SqlType reverse() {
			return DROP;
		}
	},
	/**
	 * DROP
	 */
	DROP(SqlMetaType.DDL, State.Deleted) {
		@Override
		public SqlType reverse() {
			return CREATE;
		}
	},
	/**
	 * ALTER
	 */
	ALTER(SqlMetaType.DDL, State.Modified) {
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[] { DROP, CREATE };
		}
	},
	/**
	 * ADD PARTITION
	 */
	ADD_PARTITION(SqlMetaType.DDL, State.Added) {
		@Override
		public SqlType reverse() {
			return DROP_PARTITION;
		}
	},
	/**
	 * DROP PARTITION
	 */
	DROP_PARTITION(SqlMetaType.DDL, State.Deleted) {
		@Override
		public SqlType reverse() {
			return ADD_PARTITION;
		}
	},
	/**
	 * MODIFY PARTITION
	 */
	MODIFY_PARTITION(SqlMetaType.DDL, State.Deleted) {
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[] { DROP_PARTITION, ADD_PARTITION };
		}
	},
	/**
	 * MERGE PARTITION
	 */
	MERGE_PARTITION(SqlMetaType.DDL, State.Modified) {
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[] { DROP_PARTITION, ADD_PARTITION };
		}

		@Override
		public SqlType reverse() {
			return SPLIT_PARTITION;
		}
	},
	/**
	 * SPLIT PARTITION
	 */
	SPLIT_PARTITION(SqlMetaType.DDL, State.Modified) {
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[] { ADD_PARTITION };
		}

		@Override
		public SqlType reverse() {
			return MERGE_PARTITION;
		}
	},
	/**
	 * TRUNCATE
	 */
	TRUNCATE(SqlMetaType.DDL, State.Deleted) {
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[] { DELETE_ALL };
		}
	},
	/**
	 * TRUNCATE TEMPORARY
	 */
	TRUNCATE_TEMPORARY(SqlMetaType.DDL, State.Deleted) {
		@Override
		public SqlType[] getSurrogates() {
			return new SqlType[] { DELETE_ALL };
		}
	},
	/**
	 * REFRESH(FOR Materialized View)
	 */
	REFRESH(SqlMetaType.DDL, null),
	/**
	 * REFRESH FAST(FOR Materialized View)
	 */
	REFRESH_FAST(SqlMetaType.DDL, null),
	/**
	 * REFRESH COMPLETE(FOR Materialized View)
	 */
	REFRESH_COMPLETE(SqlMetaType.DDL, null),
	/**
	 * SET_COMMENT
	 */
	SET_COMMENT(SqlMetaType.COMMENT, null)
	// ==============================TCL=====================================
	,
	/**
	 * COMMIT
	 */
	COMMIT(SqlMetaType.TCL),
	/**
	 * ROLLBACK
	 */
	ROLLBACK(SqlMetaType.TCL)
	// ==============================OTHER=====================================
	,
	/**
	 * COMMENT
	 */
	COMMENT(SqlMetaType.COMMENT, null) {
		@Override
		public boolean isComment() {
			return true;
		}
	},
	/**
	 * EMPTY_LINE
	 */
	EMPTY_LINE(SqlMetaType.EMPTY_LINE, null) {
		@Override
		public boolean isEmptyLine() {
			return true;
		}
	},
	/**
	 * OTHER
	 */
	OTHER(SqlMetaType.OTHER, null);

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

	public TableOrder getTableOrder() {
		return null;
	}

	public boolean isDeprecated() {
		return false;
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

	public boolean isComment() {
		return false;
	}

	public boolean isEmptyLine() {
		return false;
	}

	public boolean isSql() {
		return !this.isComment() && !this.isEmptyLine();
	}

	public SqlType reverse() {
		return this;
	}

	public SqlExecuteType getSqlExecuteType() {
		return null;
	}

	/**
	 * DDLかどうか
	 * 
	 */
	public boolean isDdl() {
		return this.sqlMetaType == SqlMetaType.DDL;
	}

	private static final SqlType[] EMPTY = new SqlType[0];

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

	public boolean supportRows() {
		return false;
	}

	/**
	 * 楽観的ロック可能か?
	 */
	public boolean isOptimisticLockable() {
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
