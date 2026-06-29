/**
 * Copyright (C) 2007-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-graphviz.
 *
 * sqlapp-graphviz is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-graphviz is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-graphviz.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.elk.schemas;

import java.util.List;
import java.util.function.Predicate;

import org.eclipse.elk.graph.ElkNode;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.elk.NameMode;
import com.sqlapp.elk.TableSvgCreator;
import com.sqlapp.elk.util.MaxLengthCalculator;
import com.sqlapp.elk.util.TextWidthUtils;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;

@Getter
public class TableNode {
	public void setForeignKeyBuilder(ForeignKeyConstraintBuilder foreignKeyBuilder) {
		this.foreignKeyBuilder = foreignKeyBuilder;
	}

	public void setColumnbuilder(ColumnBuilder columnbuilder) {
		this.columnbuilder = columnbuilder;
	}

	private final Table table;
	private final ElkNode rootNode;
	private final ElkNode node;
	private List<ForeignKeyConstraintNode> foreignKeyConstraintNodes = CommonUtils.list();

	private double totalWidth;
	private double nameWidth;
	private double typeWidth;
	private double minNameWidth = 24.0;

	private boolean columnFilterEnabled = true;

	private Predicate<Column> columnFilter = c -> true;

	private ForeignKeyConstraintBuilder foreignKeyBuilder = ForeignKeyConstraintBuilder.create();

	private ColumnBuilder columnbuilder = ColumnBuilder.create();

	private NameMode nameMode = NameMode.NORMAL;

	public TableNode(Table table, ElkNode rootNode, ElkNode node) {
		this.table = table;
		this.rootNode = rootNode;
		this.node = node;
	}

	// 【追加】テーブル内の文字数から最適な幅を算出するメソッド
	public void calculateTableLayoutInfo() {
		// double maxNameWidth = 120.0; // カラム名列の最小幅
		// double maxTypeWidth = 100.0; // 型名列の最小幅
		MaxLengthCalculator nameCalc = new MaxLengthCalculator(0, TableSvgCreator.FONT_SIZE);
		MaxLengthCalculator typeCalc = new MaxLengthCalculator(0, TableSvgCreator.FONT_SIZE);
		for (Column col : table.getColumns()) {
			if (!test(col)) {
				continue;
			}
			String text = nameMode.getName(col);
			nameCalc.add(text);
			// 型名 + (NN) の文字数から幅を概算 (1文字あたり約6.0px)
			text = columnbuilder.build(col);
			typeCalc.add(text);
		}
		double maxNameWidth = nameCalc.calc() + TableSvgCreator.COLUMN_NAME_PADDING; // カラム名列の最小幅
		double maxTypeWidth = typeCalc.calc() + TableSvgCreator.COLUMN_TYPE_PADDING; // 型名列の最小幅
		// PK列(30px) + カラム名列 + 型名列
		// double totalColumnsWidth = 30.0 + maxNameWidth + maxTypeWidth;
		double totalColumnsWidth = maxNameWidth + maxTypeWidth;

		// テーブル自体の名前が長すぎる場合の考慮 (ヘッダー幅の概算)
		double tableNameWidth = TextWidthUtils.estimateTextWidth(table.getName(), TableSvgCreator.FONT_SIZE)
				+ TableSvgCreator.TABLE_NAME_PADDING;
		if (totalColumnsWidth < tableNameWidth) {
			// テーブル名の方が長い場合、差分をカラム名列に上乗せして調整
			maxNameWidth += (tableNameWidth - totalColumnsWidth);
			totalColumnsWidth = tableNameWidth;
		}
		this.nameWidth = maxNameWidth;
		this.typeWidth = maxTypeWidth;
		this.totalWidth = totalColumnsWidth;
	}

	public boolean test(Column column) {
		if (columnFilterEnabled) {
			return columnFilter.test(column);
		} else {
			return true;
		}
	}

	public String getName(Column column) {
		return nameMode.getName(column);
	}

	public String getName() {
		return nameMode.getName(table);
	}

	public String build(Column column) {
		return columnbuilder.build(column);
	}

	public String build(ForeignKeyConstraint obj) {
		return foreignKeyBuilder.build(obj);
	}

	public ForeignKeyConstraintBuilder getForeignKeyBuilder() {
		return this.foreignKeyBuilder;
	}

	public void setMinNameWidth(double minNameWidth) {
		this.minNameWidth = minNameWidth;
	}

	@SuppressWarnings("unused")
	private Predicate<Column> getColumnFilter() {
		return this.columnFilter;
	}

	public void setColumnFilter(Predicate<Column> columnFilter) {
		this.columnFilter = columnFilter;
	}

	public void setColumnFilterEnabled(boolean columnFilterEnabled) {
		this.columnFilterEnabled = columnFilterEnabled;
	}

	public int getColumnSize() {
		int i = 0;
		for (Column col : table.getColumns()) {
			if (!columnFilter.test(col)) {
				continue;
			}
			i++;
		}
		return i;
	}

	public List<Column> getColumns() {
		List<Column> columns = CommonUtils.list();
		for (Column col : table.getColumns()) {
			if (!columnFilter.test(col)) {
				continue;
			}
			columns.add(col);
		}
		return columns;
	}

	public void setNameMode(NameMode nameMode) {
		this.nameMode = nameMode;
	}
}
