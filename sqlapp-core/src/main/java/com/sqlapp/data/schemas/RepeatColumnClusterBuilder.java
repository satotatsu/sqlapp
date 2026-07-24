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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.schemas.RepeatColumn.RepeatColumnKey;

import lombok.Getter;

public class RepeatColumnClusterBuilder {
	private static final Pattern PATTERN = Pattern.compile("^(.*?)(?:_|-)?(\\d+)$");

	private final Table table;

	private RepeatColumnClusterBuilder(Table table) {
		this.table = table;
	}

	public static RepeatColumnClusterBuilder of(Table table) {
		return new RepeatColumnClusterBuilder(table);
	}

	public List<RepeatColumnCluster> build() {
		List<RepeatColumn> list = buildRepeatColumn();
		return clustering(list);
	}

	private List<RepeatColumn> buildRepeatColumn() {
		Map<String, RepeatColumn> map = new LinkedHashMap<>();
		for (Column column : table.getColumns()) {
			Matcher matcher = PATTERN.matcher(column.getName());
			if (!matcher.matches()) {
				continue;
			}
			String baseName = matcher.group(1);
			int index = Integer.parseInt(matcher.group(2));
			map.computeIfAbsent(baseName, RepeatColumn::new).add(index, column);
		}
		return new ArrayList<>(map.values());
	}

	private List<RepeatColumnCluster> clustering(List<RepeatColumn> columns) {
		Map<RepeatColumnKey, RepeatColumnCluster> cluster = new LinkedHashMap<>();
		columns.forEach(c -> {
			cluster.computeIfAbsent(c.getKey(), RepeatColumnCluster::new).getColumns().add(c);
		});
		// return
		return cluster.values().stream().filter(RepeatColumnCluster::isNormalizable).toList();
	}

	@Getter
	public static class RepeatColumnCluster implements Iterable<RepeatColumn> {
		RepeatColumnCluster(RepeatColumnKey key) {
			this.key = key;
		}

		private final RepeatColumnKey key;
		private final List<RepeatColumn> columns = new ArrayList<>();

		public int size() {
			return columns.size();
		}

		public int getRepeatCount() {
			return key.indexes().size();
		}

		public Set<Integer> getIndexes() {
			return key.indexes();
		}

		public boolean isNormalizable() {
			return columns.size() >= 2 && key.indexes().size() >= 2;
		}

		@Override
		public Iterator<RepeatColumn> iterator() {
			return columns.iterator();
		}

		public List<Column> getRepresentativeColumns() {
			return columns.stream().map(RepeatColumn::firstColumn).toList();
		}
	}
}
