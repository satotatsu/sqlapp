/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-elk-svg.
 *
 * sqlapp-elk-svg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-elk-svg is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-elk-svg.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.elk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/** Writes Mermaid ER source from the shared Schema model, without running a layout engine. */
public class TableMermaidCreator {

	private final NameMode nameMode;

	public TableMermaidCreator() {
		this(NameMode.NORMAL);
	}

	public TableMermaidCreator(NameMode nameMode) {
		this.nameMode = Objects.requireNonNull(nameMode, "nameMode");
	}

	/** Generates columns, foreign keys and structural relationships within the selection. */
	public String generate(Collection<Table> tables) {
		return generate(tables, table -> column -> true);
	}

	String generate(Collection<Table> tables, Function<Table, Predicate<Column>> columnFilter) {
		Objects.requireNonNull(tables, "tables");
		Map<Table, String> ids = new IdentityHashMap<>();
		List<Table> selected = new ArrayList<>();
		for (Table table : tables) {
			Objects.requireNonNull(table, "tables must not contain null");
			if (!ids.containsKey(table)) {
				ids.put(table, "t" + ids.size());
				selected.add(table);
			}
		}
		StringBuilder result = new StringBuilder("erDiagram\n");
		for (Table table : selected) {
			String label = nameMode.getName(table);
			if (table.getSchemaName() != null) {
				label = table.getSchemaName() + "." + label;
			}
			if (table.getCatalogName() != null) {
				label = table.getCatalogName() + "." + label;
			}
			result.append("    ").append(ids.get(table)).append("[\"").append(escape(label)).append("\"]\n");
			Predicate<Column> filter = columnFilter.apply(table);
			List<Column> columns = table.getColumns().stream().filter(filter).toList();
			if (columns.isEmpty()) {
				continue;
			}
			result.append("    ").append(ids.get(table)).append(" {\n");
			Set<String> attributeNames = new HashSet<>();
			for (Column column : columns) {
				String type = column.getDataTypeName();
				if (type == null && column.getDataType() != null) {
					type = column.getDataType().toString();
				}
				String name = nameMode.getName(column);
				// Attribute tokens have stricter grammar than quoted entity aliases.
				String typeToken = token(type, "unknown");
				String nameToken = uniqueToken(token(name, "column"), attributeNames);
				result.append("        ").append(typeToken).append(' ').append(nameToken);
				List<String> keys = new ArrayList<>();
				if (column.isPrimaryKey()) {
					keys.add("PK");
				}
				if (column.isForeignKey()) {
					keys.add("FK");
				}
				if (table.getConstraints().getUniqueConstraints().stream().anyMatch(key -> !key.isPrimaryKey()
						&& key.getColumns().stream().anyMatch(ref -> Objects.equals(ref.getName(), column.getName())))) {
					keys.add("UK");
				}
				if (!keys.isEmpty()) {
					result.append(' ').append(String.join(", ", keys));
				}
				List<String> details = new ArrayList<>();
				if (!Objects.equals(nameToken, name)) {
					details.add("name: " + name);
				}
				if (type != null && !Objects.equals(typeToken, type)) {
					details.add("type: " + type);
				}
				if (column.isNotNull()) {
					details.add("NOT NULL");
				}
				if (!details.isEmpty()) {
					result.append(" \"").append(escape(String.join("; ", details))).append('"');
				}
				result.append('\n');
			}
			result.append("    }\n");
		}
		for (Table table : selected) {
			for (var fk : table.getConstraints().getForeignKeyConstraints()) {
				String parent = ids.get(fk.getRelatedTable());
				if (parent == null) {
					continue;
				}
				boolean required = !fk.getColumns().isEmpty() && fk.getColumns().stream().allMatch(Column::isNotNull);
				boolean unique = !fk.getColumns().isEmpty() && !table.getConstraints()
						.getUniqueConstraints(fk.getColumns().toArray(Column[]::new)).isEmpty();
				boolean identifying = !fk.getColumns().isEmpty() && fk.getColumns().stream().allMatch(Column::isPrimaryKey);
				result.append("    ").append(parent).append(required ? " ||" : " |o")
						.append(identifying ? "--" : "..").append(unique ? "o| " : "o{ ")
						.append(ids.get(table)).append(" : \"").append(escape(fk.getName())).append("\"\n");
			}
		}
		boolean structuralLegend = false;
		for (Table table : selected) {
			String partitionParent = table.getPartitionParent() == null ? null
					: ids.get(table.getPartitionParent().getTable());
			for (Table parent : table.getInherits()) {
				String parentId = ids.get(parent);
				// Some readers also represent a partition parent as an inheritance parent.
				if (parentId != null && !parentId.equals(partitionParent)) {
					structuralLegend = appendStructuralLegend(result, structuralLegend);
					result.append("    ").append(ids.get(table)).append(" }o..o{ ").append(parentId)
							.append(" : \"inherits\"\n");
				}
			}
			if (partitionParent != null) {
				structuralLegend = appendStructuralLegend(result, structuralLegend);
				result.append("    ").append(ids.get(table)).append(" }o..|| ").append(partitionParent)
						.append(" : \"partition of\"\n");
			}
		}
		return result.toString();
	}

	private static boolean appendStructuralLegend(StringBuilder result, boolean written) {
		if (!written) {
			result.append("    %% inherits / partition of describe table structure, not foreign keys.\n")
					.append("    %% Their endpoint markers describe table-level multiplicities, not row cardinalities.\n");
		}
		return true;
	}

	private static String token(String value, String fallback) {
		if (value == null || value.isEmpty()) {
			return fallback;
		}
		String token = value.replaceAll("[^\\p{L}\\p{N}_\\-\\[\\]\\(\\)\\.,]", "_");
		boolean reserved = token.equalsIgnoreCase("PK") || token.equalsIgnoreCase("FK") || token.equalsIgnoreCase("UK");
		return Character.isLetter(token.codePointAt(0)) && !reserved ? token : "value_" + token;
	}

	private static String uniqueToken(String candidate, Set<String> used) {
		String value = candidate;
		int suffix = 2;
		while (!used.add(value.toLowerCase(Locale.ROOT))) {
			value = candidate + "_" + suffix++;
		}
		return value;
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder result = new StringBuilder();
		value.codePoints().forEach(c -> {
			if (Character.isISOControl(c) || c == 0x2028 || c == 0x2029) {
				result.append(' ');
			} else if (c == '"' || c == '#' || c == '&' || c == '<' || c == '>' || c == '\\' || c == '`' || c == '%') {
				result.append('#').append(c).append(';');
			} else {
				result.appendCodePoint(c);
			}
		});
		// Mermaid's direction lexer also matches inside quoted labels.
		return result.toString().replaceAll("(?i)direction(?=\\s+(?:TB|BT|RL|LR))", "#100;irection");
	}
}
