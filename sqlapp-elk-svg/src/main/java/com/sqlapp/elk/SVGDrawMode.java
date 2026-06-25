/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.elk;

import java.util.List;
import java.util.Set;

import org.eclipse.elk.graph.ElkNode;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.elk.schemas.ColumnBuilder;
import com.sqlapp.elk.schemas.ForeignKeyBuilder;
import com.sqlapp.elk.schemas.SchemaNode;
import com.sqlapp.elk.schemas.TableNode;
import com.sqlapp.util.CommonUtils;

public enum SVGDrawMode {
	NORMAL() {
		@Override
		public void reset(TableNode tableNode) {
			tableNode.setColumnbuilder(ColumnBuilder.create());
			tableNode.setForeignKeyBuilder(ForeignKeyBuilder.create());
			tableNode.setColumnFilterEnabled(false);
		}
	},
	SIMPLE() {
		@Override
		public TableSvgCreator createTableSvgCreator() {
			TableSvgCreator ret = super.createTableSvgCreator();
			ret.setPadding(20);
			return ret;
		}

		@Override
		public TableNode createTableNode(Table table, ElkNode rootNode, ElkNode node) {
			final TableNode ret = super.createTableNode(table, rootNode, node);
			List<ForeignKeyConstraint> fks = table.getConstraints().getForeignKeyConstraints();
			final Set<Column> columns = CommonUtils.set();
			for (ForeignKeyConstraint fk : fks) {
				for (int i = 0; i < fk.getColumns().size(); i++) {
					columns.add(fk.getColumns().get(i));
				}
			}
			ret.setColumnbuilder(ColumnBuilder.createSimple());
			ret.setForeignKeyBuilder(ForeignKeyBuilder.createSimple());
			ret.setMinNameWidth(10.0);
			ret.setColumnFilter(column -> {
				if (column.isPrimaryKey()) {
					return true;
				}
				if (column.isForeignKey()) {
					return true;
				}
				if (columns.contains(column)) {
					return true;
				}
				if (column.getOrdinal() < 5) {
					return true;
				}
				return false;
			});
			return ret;
		}
	};

	public void reset(TableNode tableNode) {

	}

	public TableNode createTableNode(Table obj, ElkNode rootNode, ElkNode node) {
		final TableNode ret = new TableNode(obj, rootNode, node);
		return ret;
	}

	public SchemaNode createSchemaNode(Schema obj, ElkNode rootNode, ElkNode node) {
		final SchemaNode ret = new SchemaNode(obj, rootNode, node);
		return ret;
	}

	public TableSvgCreator createTableSvgCreator() {
		return new TableSvgCreator(this);
	}
}
