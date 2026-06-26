/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.schemas;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.exceptions.InvalidTextException;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

import lombok.Data;

public class VirtualForeignKeyLoader {

	private final String encoding = "utf8";

	public VirtualForeignKeyLoader() {
	}

	public void load(final Catalog catalog, final File file) {
		if (file == null || !file.exists()) {
			return;
		}
		final File[] listFiles = file.listFiles();
		if (listFiles == null) {
			return;
		}
		for (final File child : listFiles) {
			final List<String> texts = FileUtils.readTextList(child, encoding);
			loadInternal(catalog, texts);
		}
	}

	public void load(final Schema schema, final File file) {
		Catalog catalog = new Catalog();
		catalog.getSchemas().add(schema);
		load(catalog, file);
	}

	public void loadSchemas(final List<Schema> schemas, final File file) {
		Catalog catalog = new Catalog();
		for (Schema schema : schemas) {
			catalog.getSchemas().add(schema);
		}
		load(catalog, file);
	}

	public void loadTables(final List<Table> tables, final File file) {
		Catalog catalog = new Catalog();
		for (Table table : tables) {
			Schema schema = table.getSchema();
			if (schema != null) {
				if (catalog.getSchemas().contains(schema)) {
					continue;
				}
			} else {
				String schemaName = table.getSchemaName();
				if (table.getSchemaName() == null) {
					schemaName = "_DUMMY_SCHEMA";
				}
				if (!catalog.getSchemas().contains(schemaName)) {
					schema = new Schema(schemaName);
					schema.getTables().add(table);
				}
				if (catalog.getSchemas().contains(schemaName)) {
					continue;
				}
			}
			catalog.getSchemas().add(schema);
		}
		load(catalog, file);
	}

	protected void loadInternal(final Catalog catalog, final String text) {
		List<String> texts = CommonUtils.list(text.split("\n"));
		loadInternal(catalog, texts);
	}

	protected void loadInternal(final Catalog catalog, final List<String> texts) {
		loadInternal(texts, (pair) -> {
			return getTable(pair, pair.getFrom(), catalog);
		}, (pair) -> {
			return getTable(pair, pair.getTo(), catalog);
		}, (pair, table) -> {
			return getColumns(pair, pair.getFrom(), table);
		}, (pair, table) -> {
			return getColumns(pair, pair.getFrom(), table);
		});
	}

	protected void loadInternal(final List<String> texts, GetTable getFromTable, GetTable getToTable,
			GetColumns getColumns, GetColumns getPkColumns) {
		for (int i = 0; i < texts.size(); i++) {
			String text = texts.get(i);
			if (isComment(text)) {
				continue;
			}
			text = text.trim();
			if (CommonUtils.isEmpty(text)) {
				continue;
			}
			final TablePair pair = parse(text, i + 1);
			final Table from = getFromTable.get(pair);
			final Table pkTable = getToTable.get(pair);
			if (pair.getFrom().getColumns().isEmpty()) {
				if (pair.getTo().getColumns().isEmpty()) {
					addAutoForeingKey(text, i, pair, from, pkTable);
				} else {
					throw new InvalidTextException(text, i + 1, "To Columns undefined.");
				}
			} else {
				final List<Column> columns = getColumns.get(pair, from);
				if (pair.getTo().getColumns().isEmpty()) {
					addAutoForeingKeyFrom(text, i, pair, from, pkTable, columns);
				} else {
					final List<Column> pkColumns = getPkColumns.get(pair, pkTable);
					addForeingKey(text, i, from, pkTable, columns, pkColumns);
				}
			}
		}
	}

	private void addForeingKey(String text, int i, final Table from, final Table pkTable, final List<Column> columns,
			final List<Column> pkColumns) {
		if (CommonUtils.size(columns) != CommonUtils.size(pkColumns)) {
			throw new InvalidTextException(text, i + 1,
					"Column size unmatch. " + from.getName() + ".column.size()=" + CommonUtils.size(columns) + ","
							+ pkTable.getName() + ".column.size()=" + CommonUtils.size(pkColumns));
		}
		addForeignKey(text, i, from, columns, pkColumns);
	}

	private void addAutoForeingKeyFrom(String text, int i, TablePair pair, final Table from, final Table pkTable,
			final List<Column> columns) {
		UniqueConstraint pk = pkTable.getPrimaryKeyConstraint();
		if (columns.size() == 1 && columns.size() == pk.getColumns().size()) {
			Column pkColumn = pk.getColumns().get(0).getColumn();
			Column column = columns.get(0);
			if (!CommonUtils.eq(column.getDataType(), pkColumn.getDataType())) {
				throw new InvalidTextException(text, i + 1, "Column DataType unmatch. from.dataType="
						+ column.getDataType() + ", to.dataType=" + pkColumn.getDataType());
			}
			addForeignKey(text, i, from, column, pkColumn);
			return;
		}
		if (pk != null && pk.getColumns().size() == columns.size()) {
			List<Column> pkColumns = CommonUtils.list();
			for (Column column : columns) {
				if (!pk.getColumns().contains(column.getName())) {
					throw new InvalidTextException(text, i + 1, "To Column not found.");
				}
				Column pkColumn = pk.getColumns().get(column.getName()).getColumn();
				if (!CommonUtils.eq(column.getDataType(), pkColumn.getDataType())) {
					throw new InvalidTextException(text, i + 1, "Column DataType unmatch. from.dataType="
							+ column.getDataType() + ", to.dataType=" + pkColumn.getDataType());
				}
				pkColumns.add(pkColumn);
			}
			addForeignKey(text, i, from, columns, pkColumns);
		}
	}

	private void addAutoForeingKey(String text, int i, TablePair pair, final Table from, final Table pkTable) {
		UniqueConstraint pk = pkTable.getPrimaryKeyConstraint();
		if (pk != null && pk.getColumns().size() == 1) {
			Column pkColumn = pk.getColumns().get(0).getColumn();
			String name = pkColumn.getName();
			// ID->IDもしくはPARENT_ID->IDパターン
			if ("ID".equalsIgnoreCase(name)) {
				for (Column column : from.getColumns()) {
					if (!"PARENT_ID".equalsIgnoreCase(column.getName())
							&& !"PARENTID".equalsIgnoreCase(column.getName())) {
						continue;
					}
					if (!CommonUtils.eq(column.getDataType(), pkColumn.getDataType())) {
						continue;
					}
					addForeignKey(text, i, from, column, pkColumn);
					return;
				}
				for (Column column : from.getColumns()) {
					if (!name.equalsIgnoreCase(column.getName())) {
						continue;
					}
					if (!CommonUtils.eq(column.getDataType(), pkColumn.getDataType())) {
						continue;
					}
					addForeignKey(text, i, from, column, pkColumn);
					return;
				}
			}
		} else {
			// PK同士の名前と部分キーが一致
			UniqueConstraint fromPk = from.getPrimaryKeyConstraint();
			if (addForeingKeyByUniqueKey(text, i, from, pk, fromPk)) {
				return;
			}
			for (UniqueConstraint uc : pkTable.getConstraints().getUniqueConstraints(c -> !c.isPrimaryKey())) {
				if (addForeingKeyByUniqueKey(text, i, from, uc, fromPk)) {
					return;
				}
			}
		}
	}

	private boolean addForeingKeyByUniqueKey(String text, int i, final Table from, UniqueConstraint pk,
			UniqueConstraint fromPk) {
		// PK同士の名前と部分キーが一致
		boolean match = true;
		List<Column> columns = CommonUtils.list();
		List<Column> pkColumns = CommonUtils.list();
		if (fromPk.getColumns().size() < pk.getColumns().size()) {
			return false;
		}
		for (ReferenceColumn rc : pk.getColumns()) {
			Column pkColumn = rc.getColumn();
			String name = pkColumn.getName();
			if (!fromPk.getColumns().contains(name)) {
				match = false;
				break;
			}
			Column column = fromPk.getColumns().get(name).getColumn();
			if (!CommonUtils.eq(column.getDataType(), pkColumn.getDataType())) {
				match = false;
				continue;
			}
			columns.add(column);
			pkColumns.add(pkColumn);
		}
		if (match) {
			addForeignKey(text, i, from, columns, pkColumns);
			return true;
		}
		return false;
	}

	private void addForeignKey(String text, int i, Table from, final Column column, final Column pkColumn) {
		addForeignKey(text, i, from, new Column[] { column }, new Column[] { pkColumn });
	}

	private void addForeignKey(String text, int i, Table from, final List<Column> columns,
			final List<Column> pkColumns) {
		addForeignKey(text, i, from, columns.toArray(new Column[0]), pkColumns.toArray(new Column[0]));
	}

	private void addForeignKey(String text, int i, Table from, final Column[] columns, final Column[] pkColumns) {
		final ForeignKeyConstraint fk = new ForeignKeyConstraint(
				"fk_" + from.getName() + "_virtual" + (from.getConstraints().getForeignKeyConstraints().size() + 1),
				columns, pkColumns);
		fk.setVirtual(true);
		// FKの重複チェック
		for (ForeignKeyConstraint fkVal : from.getConstraints().getForeignKeyConstraints()) {
			if (fkVal.getColumns().length != fk.getColumns().length) {
				continue;
			}
			boolean match = true;
			for (int j = 0; j < fk.getColumns().length; j++) {
				if (!CommonUtils.eq(fk.getColumns()[j].getName(), fkVal.getColumns()[j].getName())) {
					match = false;
					break;
				}
				if (!CommonUtils.eq(fk.getRelatedColumns().get(j).getName(),
						fkVal.getRelatedColumns().get(j).getName())) {
					match = false;
					break;
				}
			}
			if (match) {
				throw new InvalidTextException(text, (i + 1), "Duplicate foreign Key.");
			}
		}
		from.getConstraints().add(fk);
	}

	@FunctionalInterface
	interface GetTable {
		Table get(TablePair pair);
	}

	@FunctionalInterface
	interface GetColumns {
		List<Column> get(TablePair pair, Table table);
	}

	private Table getTable(final TablePair pair, final Table table, final Catalog catalog) {
		Table from;
		if (!CommonUtils.isEmpty(table.getSchemaName())) {
			final Schema schema = catalog.getSchemas().get(table.getSchemaName());
			if (schema == null) {
				throw new InvalidTextException(pair.getLine(), pair.getLineNo(), table + "(Schema) does not found.");
			}
			from = schema.getTable(table.getName());
			if (from == null) {
				throw new InvalidTextException(pair.getLine(), pair.getLineNo(), table + " does not found.");
			}
			return from;
		} else {
			for (final Schema schema : catalog.getSchemas()) {
				from = schema.getTable(table.getName());
				if (from != null) {
					return from;
				}
			}
		}
		throw new InvalidTextException(pair.getLine(), pair.getLineNo(), table + " does not found.");
	}

	private List<Column> getColumns(final TablePair pair, final Table ref, final Table table) {
		final List<Column> columns = CommonUtils.list();
		if (!ref.getColumns().isEmpty()) {
			for (final Column col : ref.getColumns()) {
				final Column column = table.getColumns().get(col.getName());
				if (column == null) {
					throw new InvalidTextException(pair.getLine(), pair.getLineNo(), col + " does not found.");
				}
				columns.add(column);
			}
		}
		return columns;
	}

	private static Pattern COMMENT_PATTERN = Pattern.compile("\\s*#.*");

	private boolean isComment(final String text) {
		final Matcher matcher = COMMENT_PATTERN.matcher(text);
		return matcher.matches();
	}

	private TablePair parse(String text, final int lineNo) {
		final String base = text;
		text = text.trim();
		final String[] texts = text.split("\\s*->\\s*");
		if (texts.length == 0) {
			throw new InvalidTextException(base, lineNo, "No relations(->) found.");
		}
		if (texts.length != 2) {
			throw new InvalidTextException(base, lineNo, "Multiple relations(->) found. count=" + texts.length);
		}
		final Table from = parseTable(base, texts[0], lineNo);
		final Table to = parseTable(base, texts[1], lineNo);
		final TablePair pair = new TablePair();
		pair.setFrom(from);
		pair.setTo(to);
		pair.setLine(text);
		pair.setLineNo(lineNo);
		return pair;
	}

	private Table parseTable(final String base, final String tableText, final int lineNo) {
		final int start = tableText.indexOf('(');
		String tablePart = null;
		final Table table = new Table();
		if (start > 0) {
			tablePart = tableText.substring(0, start).trim();
			if (!tableText.endsWith(")")) {
				throw new InvalidTextException(base, lineNo, "aaaaa(id,val)->bbbbb");
			}
			final String[] columns = tableText.substring(start + 1, tableText.length() - 1).split("\\s*,\\s*");
			for (String col : columns) {
				final Column column = table.newColumn();
				col = col.trim();
				if (CommonUtils.isEmpty(col)) {
					throw new InvalidTextException(base, lineNo, "Invalid column definition. value=" + tableText);
				}
				column.setName(col);
				table.getColumns().add(column);
			}
		} else {
			tablePart = tableText;
		}
		final String[] names = tablePart.split("\\.");
		if (names.length == 1) {
			table.setName(names[0]);
		} else if (names.length == 2) {
			table.setSchemaName(names[0]);
			table.setName(names[1]);
		} else if (names.length == 3) {
			table.setCatalogName(names[0]);
			table.setSchemaName(names[1]);
			table.setName(names[2]);
		} else {
			throw new InvalidTextException(base, lineNo, "Invalid tableName. value=" + tablePart);
		}
		return table;
	}

	@Data
	static class TablePair {
		private Table from;
		private Table to;
		private String line;
		private int lineNo;
	}

}
