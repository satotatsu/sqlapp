package com.sqlapp.elk;

import org.eclipse.elk.graph.ElkNode;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.elk.schemas.ColumnBuilder;
import com.sqlapp.elk.schemas.ForeignKeyBuilder;
import com.sqlapp.elk.schemas.TableNode;

public enum SVGDrawMode {
	NORMAL() {
	},
	SIMPLE() {
		@Override
		public TableSvgCreator createTableSvgCreator() {
			TableSvgCreator obj = super.createTableSvgCreator();
			obj.setPadding(20);
			return obj;
		}

		@Override
		public TableNode createTableNode(Table table, ElkNode node) {
			final TableNode tableNode = super.createTableNode(table, node);
			tableNode.setColumnbuilder(ColumnBuilder.createSimple());
			tableNode.setForeignKeyBuilder(ForeignKeyBuilder.createSimple());
			tableNode.setMinNameWidth(10.0);
			return tableNode;
		}
	};

	public TableNode createTableNode(Table table, ElkNode node) {
		final TableNode tableNode = new TableNode(table, node);
		return tableNode;
	}

	public TableSvgCreator createTableSvgCreator() {
		return new TableSvgCreator(this);
	}
}
