package com.sqlapp.elk;

import org.eclipse.elk.graph.ElkNode;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.elk.schemas.ColumnBuilder;
import com.sqlapp.elk.schemas.ForeignKeyBuilder;
import com.sqlapp.elk.schemas.SchemaNode;
import com.sqlapp.elk.schemas.TableNode;

public enum SVGDrawMode {
	NORMAL() {
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
			ret.setColumnbuilder(ColumnBuilder.createSimple());
			ret.setForeignKeyBuilder(ForeignKeyBuilder.createSimple());
			ret.setMinNameWidth(10.0);
			return ret;
		}
	};

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
