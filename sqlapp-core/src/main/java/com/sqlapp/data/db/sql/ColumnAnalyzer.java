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

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.CommonUtils;

public enum ColumnAnalyzer {
	FULL {
		@Override
		public Set<Column> getKeyColumns(Table table, List<Row> rows) {
			Set<Column> result = CommonUtils.linkedSet();
			result.addAll(table.getColumns());
			return result;
		}

		@Override
		public List<Set<Column>> getKeyColumnsList(Table table, List<Row> rows) {
			List<Set<Column>> result = CommonUtils.list();
			Set<Column> columns = PRIMARY_KEY.getKeyColumns(table, rows);
			if (!columns.isEmpty()) {
				result.add(columns);
			}
			List<Set<Column>> columnsSet = UNIQUE_KEYS.getKeyColumnsList(table, rows);
			result.addAll(columnsSet);
			columnsSet = NOT_NULL_UNIQUE_INDEXES.getKeyColumnsList(table, rows);
			result.addAll(columnsSet);
			return result;
		}
	},
	PRIMARY_KEY {
		@Override
		public Set<Column> getKeyColumns(Table table, List<Row> rows) {
			UniqueConstraint pk = table.getPrimaryKeyConstraint();
			return getColumnsByUk(pk);
		}

		@Override
		public List<Set<Column>> getKeyColumnsList(Table table, List<Row> rows) {
			final List<Set<Column>> columnsSet = CommonUtils.list();
			final Set<Column> columns = getKeyColumns(table, rows);
			if (!columns.isEmpty()) {
				columnsSet.add(columns);
			}
			return columnsSet;
		}
	},
	UNIQUE_KEY {
		@Override
		public Set<Column> getKeyColumns(Table table, List<Row> rows) {
			List<UniqueConstraint> uks = getUniqueConstrainsts(table);
			if (table.getRows().size() > 0) {
				Row row = rows.get(0);
				boolean notNull = true;
				for (UniqueConstraint uk : uks) {
					Set<Column> columns = getColumnsByUk(uk);
					for (Column column : columns) {
						if (row.get(column) == null) {
							notNull = false;
							break;
						}
					}
					if (notNull) {
						return columns;
					}
				}
			}
			for (UniqueConstraint uk : uks) {
				Set<Column> columns = getColumnsByUk(uk);
				if (!columns.isEmpty()) {
					return columns;
				}
			}
			return CommonUtils.linkedSet();
		}

		@Override
		public List<Set<Column>> getKeyColumnsList(Table table, List<Row> rows) {
			final List<Set<Column>> columnsSet = CommonUtils.list();
			final Set<Column> columns = getKeyColumns(table, rows);
			if (!columns.isEmpty()) {
				columnsSet.add(columns);
			}
			return columnsSet;
		}
	},
	FOREIGN_KEY {
		@Override
		public Set<Column> getKeyColumns(Table table, List<Row> rows) {
			Set<Column> result = CommonUtils.linkedSet();
			List<ForeignKeyConstraint> uks = getForeignKeyConstrainsts(table);
			for (ForeignKeyConstraint uk : uks) {
				Set<Column> columns = getColumnsByFk(uk);
				if (!columns.isEmpty()) {
					return columns;
				}
			}
			return result;
		}

		@Override
		public List<Set<Column>> getKeyColumnsList(Table table, List<Row> rows) {
			final List<Set<Column>> columnsSet = CommonUtils.list();
			List<ForeignKeyConstraint> uks = getForeignKeyConstrainsts(table);
			for (ForeignKeyConstraint uk : uks) {
				Set<Column> columns = getColumnsByFk(uk);
				if (!columns.isEmpty()) {
					columnsSet.add(columns);
					return columnsSet;
				}
			}
			return columnsSet;
		}
	},
	FOREIGN_KEYS {
		@Override
		public Set<Column> getKeyColumns(Table table, List<Row> rows) {
			Set<Column> result = CommonUtils.linkedSet();
			List<ForeignKeyConstraint> uks = getForeignKeyConstrainsts(table);
			for (ForeignKeyConstraint uk : uks) {
				Set<Column> columns = getColumnsByFk(uk);
				if (!columns.isEmpty()) {
					result.addAll(columns);
				}
			}
			return result;
		}

		@Override
		public List<Set<Column>> getKeyColumnsList(Table table, List<Row> rows) {
			final List<Set<Column>> columnsSet = CommonUtils.list();
			List<ForeignKeyConstraint> uks = getForeignKeyConstrainsts(table);
			for (ForeignKeyConstraint uk : uks) {
				Set<Column> columns = getColumnsByFk(uk);
				if (!columns.isEmpty()) {
					columnsSet.add(columns);
				}
			}
			return columnsSet;
		}
	},
	UNIQUE_KEYS {
		@Override
		public Set<Column> getKeyColumns(Table table, List<Row> rows) {
			Set<Column> result = CommonUtils.linkedSet();
			List<UniqueConstraint> uks = getUniqueConstrainsts(table);
			for (UniqueConstraint uk : uks) {
				result.addAll(getColumnsByUk(uk));
			}
			return result;
		}

		@Override
		public List<Set<Column>> getKeyColumnsList(Table table, List<Row> rows) {
			final List<Set<Column>> columnsSet = CommonUtils.list();
			final List<UniqueConstraint> uks = getUniqueConstrainsts(table);
			for (UniqueConstraint uk : uks) {
				Set<Column> columns = getColumnsByUk(uk);
				if (!columns.isEmpty()) {
					columnsSet.add(columns);
				}
			}
			return columnsSet;
		}
	},
	NOT_NULL_UNIQUE_INDEX {
		@Override
		public Set<Column> getKeyColumns(Table table, List<Row> rows) {
			final List<Index> indexes = getUniqueIndexex(table);
			if (table.getRows().size() > 0) {
				Row row = rows.get(0);
				boolean notNull = true;
				for (Index index : indexes) {
					Set<Column> columns = getColumnsByIndex(index);
					for (Column column : columns) {
						if (row.get(column) == null) {
							notNull = false;
							break;
						}
					}
					if (notNull) {
						return columns;
					}
				}
			}
			for (Index index : indexes) {
				Set<Column> columns = getColumnsByIndex(index);
				if (!columns.isEmpty()) {
					return columns;
				}
			}
			return CommonUtils.linkedSet();
		}

		@Override
		public List<Set<Column>> getKeyColumnsList(Table table, List<Row> rows) {
			final List<Set<Column>> columnsSet = CommonUtils.list();
			final List<Index> idexes = getUniqueIndexex(table);
			for (Index index : idexes) {
				final Set<Column> columns = getNotNullColumnsByIndex(index);
				if (!columns.isEmpty()) {
					columnsSet.add(columns);
					return columnsSet;
				}
			}
			return columnsSet;
		}
	},
	NOT_NULL_UNIQUE_INDEXES {
		@Override
		public Set<Column> getKeyColumns(Table table, List<Row> rows) {
			Set<Column> result = CommonUtils.linkedSet();
			final List<Index> indexes = getUniqueIndexex(table);
			for (Index index : indexes) {
				final Set<Column> columns = getNotNullColumnsByIndex(index);
				if (columns.isEmpty()) {
					result.addAll(columns);
				}
			}
			return result;
		}

		@Override
		public List<Set<Column>> getKeyColumnsList(Table table, List<Row> rows) {
			final List<Set<Column>> columnsSet = CommonUtils.list();
			final List<Index> idexes = getUniqueIndexex(table);
			for (Index index : idexes) {
				final Set<Column> columns = getNotNullColumnsByIndex(index);
				if (!columns.isEmpty()) {
					columnsSet.add(columns);
				}
			}
			return columnsSet;
		}
	},
	INDEXES {
		@Override
		public Set<Column> getKeyColumns(Table table, List<Row> rows) {
			Set<Column> result = CommonUtils.linkedSet();
			final List<Index> idexes = table.getIndexes();
			for (Index index : idexes) {
				Set<Column> cols = getColumnsByIndex(index);
				if (!cols.isEmpty()) {
					result.addAll(cols);
				}
			}
			return result;
		}

		@Override
		public List<Set<Column>> getKeyColumnsList(Table table, List<Row> rows) {
			final List<Set<Column>> columnsSet = CommonUtils.list();
			final List<Index> idexes = table.getIndexes();
			for (Index index : idexes) {
				final Set<Column> columns = getColumnsByIndex(index);
				if (!columns.isEmpty()) {
					columnsSet.add(columns);
				}
			}
			return columnsSet;
		}
	},;

	public Set<Column> getKeyColumns(Table table, List<Row> rows) {
		return Collections.emptySet();
	}

	public Set<Column> getKeyColumns(Table table) {
		return getKeyColumns(table, Collections.emptyList());
	}

	public List<Set<Column>> getKeyColumnsList(Table table, List<Row> rows) {
		return Collections.emptyList();
	}

	public List<Set<Column>> getKeyColumnsList(Table table) {
		return getKeyColumnsList(table, Collections.emptyList());
	}

	public Set<Column> getNullColumns(Table table, List<Row> rows) {
		Set<Column> columns = getKeyColumns(table, rows);
		if (rows.isEmpty()) {
			return Collections.emptySet();
		}
		Set<Column> result = CommonUtils.linkedSet();
		Row row = rows.get(0);
		for (Column column : columns) {
			if (row.get(column) == null) {
				result.add(column);
			}
		}
		return getNullColumns(table, columns);
	}

	public static Set<Column> getNotNullColumns(Table table, Set<Column> columns) {
		if (table.getRows().isEmpty()) {
			return columns;
		}
		Set<Column> result = CommonUtils.linkedSet();
		Row row = table.getRows().get(0);
		for (Column column : columns) {
			if (row.get(column) != null) {
				result.add(column);
			}
		}
		return result;
	}

	public static Set<Column> getNullColumns(Table table, Set<Column> columns) {
		if (table.getRows().isEmpty()) {
			return Collections.emptySet();
		}
		Set<Column> result = CommonUtils.linkedSet();
		Row row = table.getRows().get(0);
		for (Column column : columns) {
			if (row.get(column) == null) {
				result.add(column);
			}
		}
		return result;
	}

	public static List<Set<Column>> getNotNullColumnsList(Table table, List<Set<Column>> columnsSet) {
		if (table.getRows().isEmpty()) {
			return columnsSet;
		}
		List<Set<Column>> result = CommonUtils.list();
		Row row = table.getRows().get(0);
		for (Set<Column> columns : columnsSet) {
			Set<Column> cols = CommonUtils.linkedSet();
			for (Column column : columns) {
				if (row.get(column) == null) {
					cols.add(column);
				}
			}
			if (!cols.isEmpty()) {
				result.add(cols);
			}
		}
		return result;
	}

	public static List<Set<Column>> getNullColumnsSet(Table table, List<Set<Column>> columnsSet) {
		if (table.getRows().isEmpty()) {
			return Collections.emptyList();
		}
		List<Set<Column>> result = CommonUtils.list();
		Row row = table.getRows().get(0);
		for (Set<Column> columns : columnsSet) {
			Set<Column> cols = CommonUtils.linkedSet();
			for (Column column : columns) {
				if (row.get(column) == null) {
					cols.add(column);
				}
			}
			if (!cols.isEmpty()) {
				result.add(cols);
			}
		}
		return result;
	}

	protected static Set<Column> getColumnsByUk(UniqueConstraint uk) {
		if (uk == null) {
			return CommonUtils.linkedSet();
		}
		List<Column> list = uk.getColumns().stream().map(c -> c.getColumn()).toList();
		return CommonUtils.linkedSet(list);
	}

	protected static Set<Column> getColumnsByFk(ForeignKeyConstraint uk) {
		if (uk == null) {
			return CommonUtils.linkedSet();
		}
		Set<Column> columns = CommonUtils.linkedSet();
		for (int i = 0; i < uk.getColumns().length; i++) {
			columns.add(uk.getColumns()[i]);
		}
		return columns;
	}

	protected static Set<Column> getColumnsByIndex(Index index) {
		if (index == null || !index.isUnique()) {
			return CommonUtils.linkedSet();
		}
		Set<Column> result = CommonUtils.linkedSet();
		for (ReferenceColumn rCol : index.getColumns()) {
			Column column = rCol.getColumn();
			if (column != null) {
				result.add(column);
			}
		}
		return result;
	}

	protected static Set<Column> getNotNullColumnsByIndex(Index index) {
		if (index == null || !index.isUnique()) {
			return CommonUtils.linkedSet();
		}
		Set<Column> result = CommonUtils.linkedSet();
		for (ReferenceColumn rCol : index.getColumns()) {
			Column column = rCol.getColumn();
			if (column == null || !column.isNotNull()) {
				return CommonUtils.linkedSet();
			}
			result.add(column);
		}
		return result;
	}

	protected static List<UniqueConstraint> getUniqueConstrainsts(Table table) {
		return table.getConstraints().getUniqueConstraints(uk -> !uk.isPrimaryKey());
	}

	protected static List<ForeignKeyConstraint> getForeignKeyConstrainsts(Table table) {
		return table.getConstraints().getForeignKeyConstraints();
	}

	protected static List<Index> getUniqueIndexex(Table table) {
		return table.getIndexes().stream().filter(index -> index.isUnique()).toList();
	}

	public static Set<Column> and(Set<Column> columns1, Set<Column> columns2) {
		final Set<Column> result = CommonUtils.linkedSet();
		for (Column column1 : columns1) {
			if (columns2.contains(column1)) {
				result.add(column1);
			}
		}
		return result;
	}

}
