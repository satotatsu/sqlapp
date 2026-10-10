/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.postgres.sql.PostgresDropTableFactory;
import com.sqlapp.data.schemas.*;

/** Follow actual dependency order rather than the input collection order. */
public class CockroachDropTableFactory extends PostgresDropTableFactory {
	@Override
	protected List<DbObjectDifference> sortDbObjectDifference(List<DbObjectDifference> differences) {
		var tables = differences.stream().map(diff -> diff.getOriginal(Table.class)).toList();
		var result = new ArrayList<DbObjectDifference>();
		for (var table : SchemaUtils.getNewSortedTableList(tables, Table.TableOrder.DROP))
			for (var diff : differences)
				if (diff.getOriginal() == table) {
					result.add(diff);
					break;
				}
		return result;
	}
}
