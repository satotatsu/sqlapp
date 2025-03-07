/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.graphviz.schemas;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.graphviz.labeltable.Align;
import com.sqlapp.graphviz.labeltable.TdElement;
import com.sqlapp.graphviz.labeltable.TrElement;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.function.BiFunction;
import java.util.function.Function;

@Accessors(fluent = true, chain = true)
@Getter
@Setter
public class TableColumnCellBuilder extends AbstractSchemaGraphBuilder {

	private String defaultColor = null;

	private String defaultBgcolor = "#A9D0F5";

	private String defaultPkBgcolor = "#81BEF7";

	private Function<Column, String> name = (c) -> c.getName();

	private Function<Column, String> color = (c) -> this.defaultColor();

	private Function<Column, String> bgcolor = (c) -> this.defaultBgcolor();

	private Function<Column, String> pkBgcolor = (c) -> this.defaultPkBgcolor();

	private boolean createEmptyCell = true;

	private TableColumnCellBuilder() {
	}

	public static TableColumnCellBuilder create() {
		TableColumnCellBuilder builder = new TableColumnCellBuilder();
		return builder;
	}

	public static TableColumnCellBuilder createSimple() {
		TableColumnCellBuilder builder = new TableColumnCellBuilder();
		builder.createEmptyCell(false);
		builder.dataType((c, type) -> null);
		builder.notNull(c -> null);
		builder.identity(c -> null);
		builder.defaultValue(c -> null);
		builder.check(c -> null);
		return builder;
	}

	private Function<Column, String> notNull = (c) -> {
		return "(NN)";
	};

	private Function<Column, String> identity = (c) -> {
		return "IDENTITY";
	};

	private Function<Column, String> defaultValue = (c) -> {
		return c.getDefaultValue();
	};

	private Function<Column, String> check = (c) -> {
		return c.getCheck();
	};

	private BiFunction<Column, String, String> dataType = (c, type) -> {
		return type;
	};

	private int colspan = 1;

	public void build(Column column, TrElement tr) {
		createHead(column, tr);
		createName(column, tr);
		createDataType(column, tr);
		createNotNull(column, tr);
		createIdentity(column, tr);
		createDefaultValue(column, tr);
		createCheck(column, tr);
		createTail(column, tr);
	}

	private TableColumnCellBuilder createHead(Column column, TrElement tr) {
		tr.addCell(cell -> {
			setCommonAttribute(column, cell);
			cell.setPort("head_" + SchemaGraphUtils.getName(column));
			cell.setValue("");
		});
		return instance();
	}

	private TableColumnCellBuilder createTail(Column column, TrElement tr) {
		tr.addCell(cell -> {
			setCommonAttribute(column, cell);
			cell.setPort("tail_" + SchemaGraphUtils.getName(column));
			cell.setValue("");
		});
		return instance();
	}

	private TableColumnCellBuilder createName(Column column, TrElement tr) {
		String value = name.apply(column);
		if (value != null) {
			tr.addCell(cell -> {
				setCommonAttribute(column, cell);
				cell.setPort(getPortName(column));
				if (column.isForeignKey()) {
					cell.addFont(font -> {
						font.setColor("blue");
						font.setValue(value);
					});
				} else {
					cell.setValue(value);
				}
				if (CommonUtils.isEmpty(column.getDisplayRemarks())) {
					cell.setTooltip(column.getRemarks());
				} else {
					cell.setTooltip(column.getDisplayRemarks());
				}
			});
		} else {
			addEmptyCell(column, tr);
		}
		return instance();
	}

	private void addEmptyCell(Column column, TrElement tr) {
		if (this.createEmptyCell) {
			tr.addCell(cell -> {
				setCommonAttribute(column, cell);
				cell.setValue("");
			});
		}
	}

	private TableColumnCellBuilder createNotNull(Column column, TrElement tr) {
		String value = notNull.apply(column);
		if (value != null) {
			tr.addCell(cell -> {
				setCommonAttribute(column, cell);
				if (column.isNotNull()) {
					cell.setValue(value);
				} else {
					cell.setValue("");
				}
			});
		} else {
			addEmptyCell(column, tr);
		}
		return instance();
	}

	private TableColumnCellBuilder createDataType(final Column column, TrElement tr) {
		Dialect dialect = column.getDialect();
		if (dialect == null) {
			dialect = DialectResolver.getInstance().getDefaultDialect();
		}
		AbstractSqlBuilder<?> sqlBuilder = dialect.createSqlBuilder();
		sqlBuilder.addTypeDefinition(column);
		String type = sqlBuilder.toString();
		String value = dataType.apply(column, type);
		if (value != null) {
			tr.addCell(cell -> {
				setCommonAttribute(column, cell);
				cell.setValue(value);
			});
		} else {
			addEmptyCell(column, tr);
		}
		return instance();
	}

	private TableColumnCellBuilder createIdentity(Column column, TrElement tr) {
		String value = identity.apply(column);
		if (value != null) {
			tr.addCell(cell -> {
				setCommonAttribute(column, cell);
				if (column.isIdentity()) {
					cell.setValue(value);
				} else {
					cell.setValue("");
				}
			});
		} else {
			addEmptyCell(column, tr);
		}
		return instance();
	}

	private TableColumnCellBuilder createDefaultValue(Column column, TrElement tr) {
		String value = defaultValue.apply(column);
		if (value != null) {
			tr.addCell(cell -> {
				setCommonAttribute(column, cell);
				cell.setValue(value);
			});
		} else {
			addEmptyCell(column, tr);
		}
		return instance();
	}

	private TableColumnCellBuilder createCheck(Column column, TrElement tr) {
		String value = check.apply(column);
		if (value != null) {
			tr.addCell(cell -> {
				setCommonAttribute(column, cell);
				cell.setValue(value);
			});
		} else {
			addEmptyCell(column, tr);
		}
		return instance();
	}

	private String getPortName(Column column) {
		return SchemaGraphUtils.getName(column);
	}

	private TableColumnCellBuilder setCommonAttribute(Column column, TdElement cell) {
		cell.setAlign(Align.LEFT);
		cell.setColor(getColumnColor(column));
		if (column.isPrimaryKey()) {
			cell.setBgcolor(getColumnPkBgcolor(column));
		} else {
			cell.setBgcolor(getColumnBgcolor(column));
		}
		return instance();
	}

	private TableColumnCellBuilder instance() {
		return this;
	}

	private String getColumnColor(Column column) {
		String value = color.apply(column);
		if (value == null) {
			return this.defaultColor();
		}
		return value;
	}

	private String getColumnBgcolor(Column column) {
		String value = bgcolor.apply(column);
		if (value == null) {
			return this.defaultBgcolor();
		}
		return value;
	}

	private String getColumnPkBgcolor(Column column) {
		String value = pkBgcolor.apply(column);
		if (value == null) {
			return this.defaultBgcolor();
		}
		return value;
	}

}
