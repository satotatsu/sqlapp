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

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.sql.SqlSignature.ColumnsHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.exceptions.MissingWhereClauseKeyException;

public enum ColumnSelectionStrategy {
	FULL {
		@Override
		public ColumnsHolder get(SqlSignature sqlSignature) {
			if (hasKeyFullValues(sqlSignature.getPrimaryKey())) {
				return sqlSignature.getPrimaryKey();
			}
			if (hasKeyFullValues(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			if (hasKeyFullValues(sqlSignature.getNotNullUniqueIndex())) {
				return sqlSignature.getNotNullUniqueIndex();
			}
			if (hasKeyFullValues(sqlSignature.getFull())) {
				return sqlSignature.getFull();
			}
			return ColumnsHolder.EMPTY_COLUMN_HOLDER;
		}

		@Override
		public ColumnsHolder getWithoutCheck(SqlSignature sqlSignature) {
			if (isNotEmptyKey(sqlSignature.getPrimaryKey())) {
				return sqlSignature.getPrimaryKey();
			}
			if (isNotEmptyKey(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			if (isNotEmptyKey(sqlSignature.getNotNullUniqueIndex())) {
				return sqlSignature.getNotNullUniqueIndex();
			}
			if (isNotEmptyKey(sqlSignature.getFull())) {
				return sqlSignature.getFull();
			}
			return checkKeyValue(sqlSignature);
		}
	},
	PRIMARY_KEY {
		@Override
		public ColumnsHolder get(SqlSignature sqlSignature) {
			if (hasKeyFullValues(sqlSignature.getPrimaryKey())) {
				return sqlSignature.getPrimaryKey();
			}
			if (hasKeyFullValues(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			if (hasKeyFullValues(sqlSignature.getNotNullUniqueIndex())) {
				return sqlSignature.getNotNullUniqueIndex();
			}
			return ColumnsHolder.EMPTY_COLUMN_HOLDER;
		}

		@Override
		public ColumnsHolder getWithoutCheck(SqlSignature sqlSignature) {
			if (isNotEmptyKey(sqlSignature.getPrimaryKey())) {
				return sqlSignature.getPrimaryKey();
			}
			if (isNotEmptyKey(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			if (isNotEmptyKey(sqlSignature.getNotNullUniqueIndex())) {
				return sqlSignature.getNotNullUniqueIndex();
			}
			return checkKeyValue(sqlSignature);
		}
	},
	UNIQUE_KEY {
		@Override
		public ColumnsHolder get(SqlSignature sqlSignature) {
			if (hasKeyFullValues(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			return ColumnsHolder.EMPTY_COLUMN_HOLDER;
		}

		@Override
		public ColumnsHolder getWithoutCheck(SqlSignature sqlSignature) {
			if (isNotEmptyKey(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			return checkKeyValue(sqlSignature);
		}
	},
	NOT_NULL_UNIQUE_INDEX {
		@Override
		public ColumnsHolder get(SqlSignature sqlSignature) {
			if (hasKeyFullValues(sqlSignature.getNotNullUniqueIndex())) {
				return sqlSignature.getNotNullUniqueIndex();
			}
			return ColumnsHolder.EMPTY_COLUMN_HOLDER;
		}

		@Override
		public ColumnsHolder getWithoutCheck(SqlSignature sqlSignature) {
			if (isNotEmptyKey(sqlSignature.getNotNullUniqueIndex())) {
				return sqlSignature.getNotNullUniqueIndex();
			}
			return ColumnsHolder.EMPTY_COLUMN_HOLDER;
		}
	},
	UNIQUE_KEY_OR_PRIMARY_KEY {
		@Override
		public ColumnsHolder get(SqlSignature sqlSignature) {
			if (hasKeyFullValues(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			if (hasKeyFullValues(sqlSignature.getPrimaryKey())) {
				return sqlSignature.getPrimaryKey();
			}
			return ColumnsHolder.EMPTY_COLUMN_HOLDER;
		}

		@Override
		public ColumnsHolder getWithoutCheck(SqlSignature sqlSignature) {
			if (isNotEmptyKey(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			if (isNotEmptyKey(sqlSignature.getPrimaryKey())) {
				return sqlSignature.getPrimaryKey();
			}
			return checkKeyValue(sqlSignature);
		}
	},
	UNIQUE_KEY_OR_NOT_NULL_UNIQUE_INDEX {
		@Override
		public ColumnsHolder get(SqlSignature sqlSignature) {
			if (hasKeyFullValues(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			if (hasKeyFullValues(sqlSignature.getNotNullUniqueIndex())) {
				return sqlSignature.getNotNullUniqueIndex();
			}
			return ColumnsHolder.EMPTY_COLUMN_HOLDER;
		}

		@Override
		public ColumnsHolder getWithoutCheck(SqlSignature sqlSignature) {
			if (isNotEmptyKey(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			if (isNotEmptyKey(sqlSignature.getNotNullUniqueIndex())) {
				return sqlSignature.getNotNullUniqueIndex();
			}
			return checkKeyValue(sqlSignature);
		}
	},
	UNIQUE_KEY_OR_NOT_NULL_UNIQUE_INDEX_OR_PRIMARY_KEY {
		@Override
		public ColumnsHolder get(SqlSignature sqlSignature) {
			if (hasKeyFullValues(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			if (hasKeyFullValues(sqlSignature.getNotNullUniqueIndex())) {
				return sqlSignature.getNotNullUniqueIndex();
			}
			if (hasKeyFullValues(sqlSignature.getPrimaryKey())) {
				return sqlSignature.getPrimaryKey();
			}
			return ColumnsHolder.EMPTY_COLUMN_HOLDER;
		}

		@Override
		public ColumnsHolder getWithoutCheck(SqlSignature sqlSignature) {
			if (isNotEmptyKey(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			if (isNotEmptyKey(sqlSignature.getNotNullUniqueIndex())) {
				return sqlSignature.getNotNullUniqueIndex();
			}
			if (isNotEmptyKey(sqlSignature.getPrimaryKey())) {
				return sqlSignature.getPrimaryKey();
			}
			return checkKeyValue(sqlSignature);
		}
	},
	UNIQUE_KEY_OR_PRIMARY_KEY_OR_NOT_NULL_UNIQUE_INDEX {
		@Override
		public ColumnsHolder get(SqlSignature sqlSignature) {
			if (hasKeyFullValues(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			if (hasKeyFullValues(sqlSignature.getPrimaryKey())) {
				return sqlSignature.getPrimaryKey();
			}
			if (hasKeyFullValues(sqlSignature.getNotNullUniqueIndex())) {
				return sqlSignature.getNotNullUniqueIndex();
			}
			return ColumnsHolder.EMPTY_COLUMN_HOLDER;
		}

		@Override
		public ColumnsHolder getWithoutCheck(SqlSignature sqlSignature) {
			if (isNotEmptyKey(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			if (isNotEmptyKey(sqlSignature.getPrimaryKey())) {
				return sqlSignature.getPrimaryKey();
			}
			if (isNotEmptyKey(sqlSignature.getNotNullUniqueIndex())) {
				return sqlSignature.getNotNullUniqueIndex();
			}
			return checkKeyValue(sqlSignature);
		}
	},
	PRIMARY_KEY_OR_UNIQUE_KEY_OR_NOT_NULL_UNIQUE_INDEX {
		@Override
		public ColumnsHolder get(SqlSignature sqlSignature) {
			if (hasKeyFullValues(sqlSignature.getPrimaryKey())) {
				return sqlSignature.getPrimaryKey();
			}
			if (hasKeyFullValues(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			if (hasKeyFullValues(sqlSignature.getNotNullUniqueIndex())) {
				return sqlSignature.getNotNullUniqueIndex();
			}
			return ColumnsHolder.EMPTY_COLUMN_HOLDER;
		}

		@Override
		public ColumnsHolder getWithoutCheck(SqlSignature sqlSignature) {
			if (isNotEmptyKey(sqlSignature.getPrimaryKey())) {
				return sqlSignature.getPrimaryKey();
			}
			if (isNotEmptyKey(sqlSignature.getUniqueKey())) {
				return sqlSignature.getUniqueKey();
			}
			if (isNotEmptyKey(sqlSignature.getNotNullUniqueIndex())) {
				return sqlSignature.getNotNullUniqueIndex();
			}
			return checkKeyValue(sqlSignature);
		}
	},;

	protected SqlSignature.ColumnsHolder checkKeyValue(SqlSignature sqlSignature) {
		throw new MissingWhereClauseKeyException(sqlSignature.getTable(), this);
	}

	public SqlSignature.ColumnsHolder get(SqlSignature sqlSignature) {
		return null;
	}

	public SqlSignature.ColumnsHolder getWithoutCheck(SqlSignature sqlSignature) {
		return null;
	}

	protected boolean hasKeyFullValues(ColumnsHolder columnsHolder) {
		if (columnsHolder.isEmptyKey()) {
			return false;
		}
		return columnsHolder.hasKeyFullValues();
	}

	protected boolean isNotEmptyKey(ColumnsHolder columnsHolder) {
		return !columnsHolder.isEmptyKey();
	}

	public Row find(SqlSignature sqlSignature, Row compareRow, List<Row> rows, Set<Integer> rowNums) {
		Row row = null;
		int index = -1;
		boolean find = false;
		SqlSignature.ColumnsHolder columnsHolder = get(sqlSignature);
		for (int rowIndex : rowNums) {
			index = rowIndex;
			row = rows.get(rowIndex);
			find = true;
			for (Column column : columnsHolder.getKeyColumns()) {
				if (!Objects.equals(row.get(column), compareRow.get(column))) {
					find = false;
					break;
				}
			}
			if (find) {
				rowNums.remove(index);
				return row;
			}
		}
		return null;
	}
}
