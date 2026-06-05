/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;

import com.sqlapp.util.CommonUtils;

public final class TableInsertOrderSorter {

	private TableInsertOrderSorter() {
	}

	private static class TableComparator<T> implements Comparator<T> {
		private final Function<T, Table> func;

		TableComparator(Function<T, Table> func) {
			this.func = func;
		}

		@Override
		public int compare(T o1, T o2) {
			Table table1 = func.apply(o1);
			Table table2 = func.apply(o2);
			if (table1.getConstraints().getForeignKeyConstraints().isEmpty()) {
				if (table2.getConstraints().getForeignKeyConstraints().isEmpty()) {
					return CommonUtils.compare(table1.getName().toUpperCase(), table2.getName().toUpperCase());
				}
			}
			return 0;
		}
	}

	public static <T> List<T> sort(Collection<T> tables, Function<T, Table> func) {
		List<T> sorted = sortInternal(tables, func);
		Collections.sort(sorted, new TableComparator<T>(func));
		return sorted;
	}

	public static <T> List<T> reverse(Collection<T> tables, Function<T, Table> func) {
		List<T> sorted = sort(tables, func);
		Collections.reverse(sorted);
		return sorted;
	}

	private static <T> List<T> sortInternal(Collection<T> tables, Function<T, Table> func) {
		Map<T, Integer> indegree = new HashMap<>();
		Map<T, Set<T>> graph = new HashMap<>();
		for (T table : tables) {
			indegree.put(table, 0);
			graph.put(table, new LinkedHashSet<>());
		}
		//
		// FK
		// Parent -> Child
		//
		for (T child : tables) {
			Table table = func.apply(child);
			List<ForeignKeyConstraint> fks = table.getConstraints()
					.getForeinKeyConstraints(fk -> !fk.getRelatedTable().equals(table));
			for (ForeignKeyConstraint fk : fks) {
				Table parent = fk.getRelatedTable();
				if (parent == null) {
					continue;
				}
				if (!indegree.containsKey(parent)) {
					continue;
				}
				if (graph.get(parent).add(child)) {
					indegree.put(child, indegree.get(child) + 1);
				}
			}
		}
		Queue<T> queue = new ArrayDeque<>();
		for (Map.Entry<T, Integer> e : indegree.entrySet()) {
			if (e.getValue() == 0) {
				queue.add(e.getKey());
			}
		}
		List<T> result = new ArrayList<>(tables.size());
		while (!queue.isEmpty()) {
			T parent = queue.remove();
			result.add(parent);
			for (T child : graph.get(parent)) {
				int count = indegree.get(child) - 1;
				indegree.put(child, count);
				if (count == 0) {
					queue.add(child);
				}
			}
		}
		//
		// cycle tables
		//
		if (result.size() != tables.size()) {
			for (T table : tables) {
				if (!result.contains(table)) {
					result.add(table);
				}
			}
		}
		return result;
	}

}