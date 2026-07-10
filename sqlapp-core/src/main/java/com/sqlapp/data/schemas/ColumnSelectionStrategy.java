package com.sqlapp.data.schemas;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

public enum ColumnSelectionStrategy {
	FULL {
		@Override
		public Set<Column> getKeyColumns(Table table) {
			Set<Column> result = CommonUtils.linkedSet();
			result.addAll(table.getColumns());
			return result;
		}

		@Override
		public Set<Set<Column>> getKeyColumnsSet(Table table) {
			return PRIMARY_KEY_AND_ALL_UNIQUE_KEYS_AND_ALL_NOT_NULL_UNIQUE_INDEXES.getKeyColumnsSet(table);
		}
	},
	PRIMARY_KEY {
		@Override
		public Set<Column> getKeyColumns(Table table) {
			UniqueConstraint pk = table.getPrimaryKeyConstraint();
			return getColumnsByUk(pk);
		}

		@Override
		public Set<Set<Column>> getKeyColumnsSet(Table table) {
			final Set<Set<Column>> columnsSet = CommonUtils.linkedSet();
			final Set<Column> columns = getKeyColumns(table);
			if (!columns.isEmpty()) {
				columnsSet.add(columns);
			}
			return columnsSet;
		}
	},
	UNIQUE_KEY {
		@Override
		public Set<Column> getKeyColumns(Table table) {
			List<UniqueConstraint> uks = getUniqueConstrainsts(table);
			for (UniqueConstraint uk : uks) {
				Set<Column> columns = getColumnsByUk(uk);
				if (!columns.isEmpty()) {
					return columns;
				}
			}
			return CommonUtils.linkedSet();
		}

		@Override
		public Set<Set<Column>> getKeyColumnsSet(Table table) {
			final Set<Set<Column>> columnsSet = CommonUtils.linkedSet();
			final Set<Column> columns = getKeyColumns(table);
			if (!columns.isEmpty()) {
				columnsSet.add(columns);
			}
			return columnsSet;
		}
	},
	FOREIGN_KEY {
		@Override
		public Set<Column> getKeyColumns(Table table) {
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
		public Set<Set<Column>> getKeyColumnsSet(Table table) {
			final Set<Set<Column>> columnsSet = CommonUtils.linkedSet();
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
	ALL_UNIQUE_KEYS {
		@Override
		public Set<Column> getKeyColumns(Table table) {
			Set<Column> result = CommonUtils.linkedSet();
			List<UniqueConstraint> uks = getUniqueConstrainsts(table);
			for (UniqueConstraint uk : uks) {
				result.addAll(getColumnsByUk(uk));
			}
			return result;
		}

		@Override
		public Set<Set<Column>> getKeyColumnsSet(Table table) {
			final Set<Set<Column>> columnsSet = CommonUtils.linkedSet();
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
		public Set<Column> getKeyColumns(Table table) {
			Set<Column> result = CommonUtils.linkedSet();
			final List<Index> indexes = getUniqueIndexex(table);
			for (Index index : indexes) {
				final Set<Column> columns = getNotNullColumnsByIndex(index);
				if (!columns.isEmpty()) {
					return columns;
				}
			}
			return result;
		}

		@Override
		public Set<Set<Column>> getKeyColumnsSet(Table table) {
			final Set<Set<Column>> columnsSet = CommonUtils.linkedSet();
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
	ALL_INDEXES {
		@Override
		public Set<Column> getKeyColumns(Table table) {
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
		public Set<Set<Column>> getKeyColumnsSet(Table table) {
			final Set<Set<Column>> columnsSet = CommonUtils.linkedSet();
			final List<Index> idexes = table.getIndexes();
			for (Index index : idexes) {
				final Set<Column> columns = getColumnsByIndex(index);
				if (!columns.isEmpty()) {
					columnsSet.add(columns);
				}
			}
			return columnsSet;
		}
	},
	ALL_NOT_NULL_UNIQUE_INDEXES {
		@Override
		public Set<Column> getKeyColumns(Table table) {
			Set<Column> result = CommonUtils.linkedSet();
			final List<Index> idexes = getUniqueIndexex(table);
			for (Index index : idexes) {
				result.addAll(getNotNullColumnsByIndex(index));
			}
			return result;
		}

		@Override
		public Set<Set<Column>> getKeyColumnsSet(Table table) {
			final Set<Set<Column>> columnsSet = CommonUtils.linkedSet();
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
	UNIQUE_KEY_OR_PRIMARY_KEY_OR_NOT_NULL_UNIQUE_INDEX {
		@Override
		public Set<Column> getKeyColumns(Table table) {
			Set<Column> result = UNIQUE_KEY.getKeyColumns(table);
			if (!result.isEmpty()) {
				return result;
			}
			result = PRIMARY_KEY.getKeyColumns(table);
			if (!result.isEmpty()) {
				return result;
			}
			result = NOT_NULL_UNIQUE_INDEX.getKeyColumns(table);
			return result;
		}

		@Override
		public Set<Set<Column>> getKeyColumnsSet(Table table) {
			Set<Set<Column>> result = UNIQUE_KEY.getKeyColumnsSet(table);
			if (!result.isEmpty()) {
				return result;
			}
			result = PRIMARY_KEY.getKeyColumnsSet(table);
			if (!result.isEmpty()) {
				return result;
			}
			result = ALL_NOT_NULL_UNIQUE_INDEXES.getKeyColumnsSet(table);
			return result;
		}
	},
	PRIMARY_KEY_OR_UNIQUE_KEY_OR_NOT_NULL_UNIQUE_INDEX {
		@Override
		public Set<Column> getKeyColumns(Table table) {
			Set<Column> result = PRIMARY_KEY.getKeyColumns(table);
			if (!result.isEmpty()) {
				return result;
			}
			result = UNIQUE_KEY.getKeyColumns(table);
			if (!result.isEmpty()) {
				return result;
			}
			result = NOT_NULL_UNIQUE_INDEX.getKeyColumns(table);
			return result;
		}

		@Override
		public Set<Set<Column>> getKeyColumnsSet(Table table) {
			Set<Set<Column>> result = PRIMARY_KEY.getKeyColumnsSet(table);
			if (!result.isEmpty()) {
				return result;
			}
			result = UNIQUE_KEY.getKeyColumnsSet(table);
			if (!result.isEmpty()) {
				return result;
			}
			result = ALL_NOT_NULL_UNIQUE_INDEXES.getKeyColumnsSet(table);
			return result;
		}
	},
	ALL_UNIQUE_KEYS_AND_PRIMARY_KEY {
		@Override
		public Set<Column> getKeyColumns(Table table) {
			Set<Column> result = ALL_UNIQUE_KEYS.getKeyColumns(table);
			Set<Column> keys = PRIMARY_KEY.getKeyColumns(table);
			result.addAll(keys);
			return result;
		}

		@Override
		public Set<Set<Column>> getKeyColumnsSet(Table table) {
			Set<Set<Column>> columnsSet = CommonUtils.linkedSet();
			Set<Set<Column>> colcSet = ALL_UNIQUE_KEYS.getKeyColumnsSet(table);
			for (Set<Column> columns : colcSet) {
				if (!columns.isEmpty()) {
					columnsSet.add(columns);
				}
			}
			Set<Column> pkColumns = PRIMARY_KEY.getKeyColumns(table);
			if (!pkColumns.isEmpty()) {
				columnsSet.add(pkColumns);
			}
			return columnsSet;
		}
	},
	ALL_UNIQUE_KEYS_AND_PRIMARY_KEY_AND_ALL_NOT_NULL_UNIQUE_INDEXES {
		@Override
		public Set<Column> getKeyColumns(Table table) {
			Set<Column> result = ALL_UNIQUE_KEYS.getKeyColumns(table);
			Set<Column> keys = PRIMARY_KEY.getKeyColumns(table);
			result.addAll(keys);
			keys = ALL_NOT_NULL_UNIQUE_INDEXES.getKeyColumns(table);
			result.addAll(keys);
			return result;
		}

		@Override
		public Set<Set<Column>> getKeyColumnsSet(Table table) {
			Set<Set<Column>> columnsSet = CommonUtils.linkedSet();
			Set<Set<Column>> colcSet = ALL_UNIQUE_KEYS.getKeyColumnsSet(table);
			for (Set<Column> columns : colcSet) {
				if (!columns.isEmpty()) {
					columnsSet.add(columns);
				}
			}
			Set<Column> pkColumns = PRIMARY_KEY.getKeyColumns(table);
			if (!pkColumns.isEmpty()) {
				columnsSet.add(pkColumns);
			}
			colcSet = ALL_NOT_NULL_UNIQUE_INDEXES.getKeyColumnsSet(table);
			for (Set<Column> columns : colcSet) {
				if (!columns.isEmpty()) {
					columnsSet.add(columns);
				}
			}
			return columnsSet;
		}
	},
	PRIMARY_KEY_AND_ALL_UNIQUE_KEYS_AND_ALL_NOT_NULL_UNIQUE_INDEXES {
		@Override
		public Set<Column> getKeyColumns(Table table) {
			Set<Column> result = PRIMARY_KEY.getKeyColumns(table);
			Set<Column> uks = ALL_UNIQUE_KEYS.getKeyColumns(table);
			result.addAll(uks);
			Set<Column> iks = ALL_NOT_NULL_UNIQUE_INDEXES.getKeyColumns(table);
			result.addAll(iks);
			return result;
		}

		@Override
		public Set<Set<Column>> getKeyColumnsSet(Table table) {
			Set<Set<Column>> columnsSet = CommonUtils.linkedSet();
			Set<Column> pkColumns = PRIMARY_KEY.getKeyColumns(table);
			if (!pkColumns.isEmpty()) {
				columnsSet.add(pkColumns);
			}
			Set<Set<Column>> colcSet = ALL_UNIQUE_KEYS.getKeyColumnsSet(table);
			for (Set<Column> columns : colcSet) {
				if (!columns.isEmpty()) {
					columnsSet.add(columns);
				}
			}
			colcSet = ALL_NOT_NULL_UNIQUE_INDEXES.getKeyColumnsSet(table);
			for (Set<Column> columns : colcSet) {
				if (!columns.isEmpty()) {
					columnsSet.add(columns);
				}
			}
			return columnsSet;
		}
	},;

	public Set<Column> getKeyColumns(Table table) {
		return Collections.emptySet();
	}

	public Set<Column> getNullColumns(Table table) {
		Set<Column> columns = getKeyColumns(table);
		if (table.getRows().isEmpty()) {
			return columns;
		}
		Set<Column> result = CommonUtils.linkedSet();
		Row row = table.getRows().get(0);
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
			return columns;
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

	public static Set<Set<Column>> getNullColumnsSet(Table table, Set<Set<Column>> columnsSet) {
		if (table.getRows().isEmpty()) {
			return columnsSet;
		}
		Set<Set<Column>> result = CommonUtils.linkedSet();
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

	public Set<Set<Column>> getKeyColumnsSet(Table table) {
		return Collections.emptySet();
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

	public Set<Set<Column>> addOn(Table table, String targetTableAlias, final String sourceTableAlias,
			AbstractSqlBuilder<?> builder) {
		Set<Set<Column>> columnsSet = getKeyColumnsSet(table);
		if (columnsSet.isEmpty()) {
			return Collections.emptySet();
		}
		if (columnsSet.size() == 1) {
			builder.on().space().brackets(() -> {
				builder.indent(() -> {
					int i = 0;
					for (Set<Column> columns : columnsSet) {
						for (final Column column : columns) {
							builder.lineBreak();
							builder.and(i > 0).name(targetTableAlias + ".", column);
							builder.eq();
							builder.name(sourceTableAlias + ".", column);
							i++;
						}
						break;
					}
				});
				builder.lineBreak();
			});
		} else {
			builder.on().space().brackets(() -> {
				builder.indent(() -> {
					builder.lineBreak();
					int i = 0;
					for (Set<Column> columns : columnsSet) {
						builder.lineBreak(i > 0).or(i > 0).lineBreak(i > 0);
						builder.brackets(true, () -> {
							int j = 0;
							for (final Column column : columns) {
								builder.lineBreak(j > 0).and(j > 0).name(targetTableAlias + ".", column);
								builder.eq();
								builder.name(sourceTableAlias + ".", column);
								j++;
							}
						});
						i++;
					}
				});
				builder.lineBreak();
			});
		}
		return columnsSet;
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

}
