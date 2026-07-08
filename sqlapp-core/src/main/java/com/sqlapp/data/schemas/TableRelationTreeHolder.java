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

package com.sqlapp.data.schemas;

import java.io.Closeable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.data.schemas.function.ForeignKeyColumnForEach;
import com.sqlapp.jdbc.sql.StatementHolder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SeparatedStringBuilder;
import com.sqlapp.util.ToStringBuilder;

import lombok.Getter;

/**
 * 指定されたテーブル間のリレーションをTreeとして保持します
 */
@Getter
public class TableRelationTreeHolder implements Iterable<TableRelation> {
	private final Map<String, TableRelation> tableMap = CommonUtils.linkedMap();
	private final List<TableRelation> rootTableList = CommonUtils.list();

	public Map<String, TableRelation> getRelationTree() {
		return tableMap;
	}

	public TableRelation findByName(String name) {
		return tableMap.get(name);
	}

	public TableRelationTreeHolder(List<Table> tables) {
		for (Table table : tables) {
			final TableRelation tableRelation = new TableRelation(table);
			tableMap.put(table.getName(), tableRelation);
		}
		// 対象テーブル内にある親を設定
		for (Table table : tables) {
			final TableRelation tableRelation = tableMap.get(table.getName());
			List<ForeignKeyConstraint> fks = table.getConstraints()
					.getForeignKeyConstraints(fk -> fk.getRelatedTable() != table);
			for (ForeignKeyConstraint fk : fks) {
				if (tableMap.containsKey(fk.getRelatedTable().getName())) {
					tableRelation.setForeignKeyConstraint(fk);
					break;
				}
			}
		}
		// 対象テーブル内にある子を設定
		for (TableRelation tableRelation : this) {
			if (tableRelation.getParent() == null) {
				continue;
			}
			final TableRelation parentTableRelation = tableMap
					.get(tableRelation.getForeignKeyConstraint().getRelatedTableName());
			parentTableRelation.addChild(tableRelation);
		}
		for (TableRelation tableRelation : this) {
			if (tableRelation.isRoot()) {
				rootTableList.add(tableRelation);
			}
		}
	}

	public TableRelation getTableRelation(Table table) {
		return tableMap.get(table.getName());
	}

	public static class TableRelation implements Closeable {
		private TableRelation parentTableRelation;
		private ForeignKeyConstraint foreignKeyConstraint;
		private final Table table;
		private final boolean identity;
		private Column[] columns;
		private Column[] relatedColumns;
		private final List<TableRelation> children = CommonUtils.list();
		private long batchCount = 0;
		private final Map<SqlType, StatementHolder> statementHolders = CommonUtils.linkedMap();

		public TableRelation(final Table table) {
			this.table = table;
			final Optional<Column> op = table.getColumns().stream().filter(c -> c.isIdentity()).findFirst();
			this.identity = op.isPresent();
		}

		public Table getTable() {
			return table;
		}

		public void resetBatchCount() {
			batchCount = 0;
		}

		public void setForeignKeyConstraint(ForeignKeyConstraint foreignKeyConstraint) {
			this.foreignKeyConstraint = foreignKeyConstraint;
			columns = foreignKeyConstraint.getColumns();
			relatedColumns = getRelatedColumns(foreignKeyConstraint);
		}

		private void setParentTableRelation(TableRelation parentTableRelation) {
			this.parentTableRelation = parentTableRelation;
		}

		public void addStatementHolder(StatementHolder statementHolder) {
			statementHolders.put(statementHolder.getSqlNode().getSqlType(), statementHolder);
		}

		public StatementHolder getStatementHolder(SqlType sqlType) {
			return this.statementHolders.get(sqlType);
		}

		public TableRelation getParentTableRelation() {
			return this.parentTableRelation;
		}

		private Column[] getRelatedColumns(ForeignKeyConstraint foreignKeyConstraint) {
			final Table table = foreignKeyConstraint.getRelatedTable();
			final Column[] columns = foreignKeyConstraint.getRelatedColumns().stream()
					.map(rc -> table.getColumns().get(rc.getName())).toArray(i -> new Column[i]);
			return columns;
		}

		public void forEach(ForeignKeyColumnForEach cons) {
			if (this.getColumns() == null) {
				return;
			}
			for (int i = 0; i < this.columns.length; i++) {
				cons.consume(i, this.getColumns()[i], this.getRelatedColumns()[i]);
			}
		}

		public boolean isIdentity() {
			return this.identity;
		}

		public boolean isRoot() {
			return parentTableRelation == null;
		}

		public Column[] getColumns() {
			return columns;
		}

		public Column[] getRelatedColumns() {
			return relatedColumns;
		}

		public ForeignKeyConstraint getForeignKeyConstraint() {
			return foreignKeyConstraint;
		}

		public Table getParent() {
			if (foreignKeyConstraint != null) {
				return foreignKeyConstraint.getRelatedTable();
			}
			return null;
		}

		public void addChild(TableRelation child) {
			child.setParentTableRelation(this);
			this.children.add(child);
		}

		public List<TableRelation> getChildren() {
			return children;
		}

		@Override
		public int hashCode() {
			return Objects.hash(table.getSchemaName(), table.getName());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof TableRelation)) {
				return false;
			}
			TableRelation cst = (TableRelation) obj;
			if (!Objects.equals(this.table, cst.table)) {
				return false;
			}
			return true;
		}

		public Row newRow() {
			final Row row = table.newRow();
			table.getRows().add(row);
			if (parentTableRelation != null) {
				row.setParentRow(parentTableRelation.getRow());
			}
			if (table.getDialect().getCorrelationStrategy().isReturnSourceRowid()) {
				// for SQL Server
				SchemaUtils.setInternalRowId(row, (int) batchCount);
			}
			batchCount++;
			this.row = row;
			return row;
		}

		public Row getRow() {
			return row;
		}

		private Row row;

		@Override
		public String toString() {
			ToStringBuilder builder = new ToStringBuilder();
			builder.add("table", table.getName());
			builder.addColumnNames("columns", getColumns());
			if (getParent() != null) {
				builder.add("parent", getParent().getName());
				builder.addColumnNames("relatedColumns", getRelatedColumns());
			}
			SeparatedStringBuilder sep = new SeparatedStringBuilder(",");
			sep.setStart("(").setEnd(")");
			for (TableRelation tableRelation : children) {
				sep.add(tableRelation.getTable().getName());
			}
			builder.add("children", sep.toString());
			return builder.toString();
		}

		@Override
		public void close() {
			for (StatementHolder holder : this.statementHolders.values()) {
				holder.close();
			}
		}
	}

	@Override
	public Iterator<TableRelation> iterator() {
		return tableMap.values().iterator();
	}
}
