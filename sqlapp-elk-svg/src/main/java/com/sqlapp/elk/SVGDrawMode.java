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
				for (int i = 0; i < fk.getColumns().length; i++) {
					columns.add(fk.getColumns()[i]);
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
