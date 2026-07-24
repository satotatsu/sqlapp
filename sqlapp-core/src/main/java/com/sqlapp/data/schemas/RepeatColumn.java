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

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.util.ToStringBuilder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RepeatColumn {

	private final String baseName;

	private final Map<Integer, Column> columns = new TreeMap<>();

	public void add(int index, Column column) {
		if (!columns.isEmpty()) {
			Column first = firstColumn();
			if (!Objects.equals(getTypeKey(first), getTypeKey(column))) {
				throw new IllegalArgumentException("Column type mismatch: " + column.getName());
			}
		}
		columns.put(index, column);
	}

	public Set<Integer> indexes() {
		return columns.keySet();
	}

	public Column firstColumn() {
		if (columns.isEmpty()) {
			throw new IllegalStateException("columns is empty.");
		}
		return columns.values().iterator().next();
	}

	private RepeatColumnKeyForType getTypeKey(Column column) {
		return new RepeatColumnKeyForType(column.getDataType(), column.getLength(), column.getScale(),
				column.isNotNull(), column.getDefaultValue());
	}

	public RepeatColumnKey getKey() {
		return new RepeatColumnKey(Set.copyOf(columns.keySet()));
	}

	static record RepeatColumnKeyForType(DataType dataType, Long length, Integer scale, boolean isNotNull,
			String defaultValue) {
	}

	static record RepeatColumnKey(Set<Integer> indexes) {
	}

	@Override
	public String toString() {
		ToStringBuilder builder = new ToStringBuilder();
		builder.add("baseName", baseName);
		builder.add("size", columns.size());
		Column column = firstColumn();
		builder.add("dataType", column.getDataType());
		builder.add("length", column.getLength());
		builder.add("scale", column.getScale());
		builder.add("notNull", column.isNotNull());
		builder.add("defaultValue", column.getDefaultValue());
		return builder.toString();
	}

}