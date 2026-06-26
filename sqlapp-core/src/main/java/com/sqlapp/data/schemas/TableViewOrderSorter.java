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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.sqlapp.util.CommonUtils;

public final class TableViewOrderSorter {

	private TableViewOrderSorter() {
	}

	public static <T> List<T> sort(Collection<T> tables, Function<T, Table> func) {
		List<T> sortTargets = CommonUtils.list();
		List<T> excludes = CommonUtils.list();
		for (T t : tables) {
			Table table = func.apply(t);
			if (hasRelation(table)) {
				sortTargets.add(t);
			} else {
				excludes.add(t);
			}
		}
		List<T> sorted = TableInsertOrderSorter.sort(sortTargets, func);
		sorted.addAll(excludes);
		return sorted;
	}

	private static boolean hasRelation(Table table) {
		if (table.getConstraints().getForeignKeyConstraints().size() > 0) {
			return true;
		}
		Schema schema = table.getSchema();
		if (schema == null) {
			return false;
		}
		for (Table tab : schema.getTables()) {
			if (tab == table) {
				continue;
			}
			for (ForeignKeyConstraint fk : tab.getConstraints().getForeignKeyConstraints()) {
				if (fk.getRelatedTable() == table) {
					return true;
				}
			}
		}
		return false;
	}

	public static <T> List<T> reverse(Collection<T> tables, Function<T, Table> func) {
		List<T> sorted = sort(tables, func);
		Collections.reverse(sorted);
		return sorted;
	}

}