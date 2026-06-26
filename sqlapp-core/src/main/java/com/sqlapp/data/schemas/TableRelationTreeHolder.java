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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SeparatedStringBuilder;
import com.sqlapp.util.ToStringBuilder;

/**
 * 指定されたテーブル間のリレーションをTreeとして保持します
 */
public class TableRelationTreeHolder {
	private final Map<String, TableRelation> tableMap = CommonUtils.linkedMap();
	private final List<TableRelation> rootTableList = CommonUtils.list();

	public Map<String, TableRelation> getRelationTree() {
		return tableMap;
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
		for (Map.Entry<String, TableRelation> entry : tableMap.entrySet()) {
			final TableRelation tableRelation = entry.getValue();
			if (tableRelation.getParent() == null) {
				continue;
			}
			final TableRelation parentTableRelation = tableMap
					.get(tableRelation.getForeignKeyConstraint().getRelatedTableName());
			parentTableRelation.addChild(tableRelation.getTable());
		}
		for (Map.Entry<String, TableRelation> entry : tableMap.entrySet()) {
			if (entry.getValue().getChildren().isEmpty()) {
				rootTableList.add(entry.getValue());
			}
		}
	}

	public static class TableRelation {
		private ForeignKeyConstraint foreignKeyConstraint;
		private final Table table;
		private Column[] columns;
		private Column[] relatedColumns;
		private final List<Table> children = CommonUtils.list();

		public TableRelation(final Table table) {
			this.table = table;
		}

		public Table getTable() {
			return table;
		}

		public void setForeignKeyConstraint(ForeignKeyConstraint foreignKeyConstraint) {
			this.foreignKeyConstraint = foreignKeyConstraint;
			columns = foreignKeyConstraint.getColumns();
			relatedColumns = getRelatedColumns(foreignKeyConstraint);
		}

		private Column[] getRelatedColumns(ForeignKeyConstraint foreignKeyConstraint) {
			final Table table = foreignKeyConstraint.getRelatedTable();
			final Column[] columns = foreignKeyConstraint.getRelatedColumns().stream()
					.map(rc -> table.getColumns().get(rc.getName())).toArray(i -> new Column[i]);
			return columns;
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

		public void addChild(Table child) {
			this.children.add(child);
		}

		public List<Table> getChildren() {
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
			for (Table table : children) {
				sep.add(table.getName());
			}
			builder.add("children", sep.toString());
			return builder.toString();
		}
	}

	public static class TableRelationTreeRowsSet {
		private TableRelation tableRelation;
		private List<Row> rows = CommonUtils.list();

		public TableRelationTreeRowsSet(final TableRelation tableRelation) {
			this.tableRelation = tableRelation;
		}
	}

	public static class TableRelationTreeRows {
		private TableRelation tableRelation;
		private List<Row> rows = CommonUtils.list();

		public TableRelationTreeRows(final TableRelation tableRelation) {
			this.tableRelation = tableRelation;
		}
	}
}
