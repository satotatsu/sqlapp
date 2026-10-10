/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;
import java.util.*;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;
/** Delete dependents first, then create/alter prerequisites before their dependents. */
public class CockroachAlterSchemaFactory extends AbstractAlterSchemaFactory<PostgresSqlBuilder> {
	private static final List<String> ORDER=List.of("sequences","types","tables","functions","views");
	@Override public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var changes=difference.toDifference().getChangedProperties(getDialect());
		var unsupported=new HashSet<>(changes.keySet());unsupported.removeAll(ORDER);
		if(!unsupported.isEmpty()) throw new UnsupportedOperationException("Unsupported CockroachDB schema changes: "+unsupported);
		if(changes.get("tables") instanceof DbObjectDifferenceCollection tables) {
			for(var removed:tables.getList(State.Deleted)) for(var added:tables.getList(State.Added)) {
				var original=(Table)removed.getOriginal();var target=(Table)added.getTarget();
				if(original.getId()!=null && original.getId().equals(target.getId()))
					throw new UnsupportedOperationException("Possible CockroachDB table rename: generate originalTable.diff(targetTable) explicitly to preserve data");
			}
		}
		var result=new ArrayList<SqlOperation>();var reverse=new ArrayList<>(ORDER);Collections.reverse(reverse);
		for(String key:reverse) if(changes.get(key) instanceof DbObjectDifferenceCollection collection) result.addAll(createDiffSql(collection.getList(State.Deleted)));
		for(String key:ORDER) if(changes.get(key) instanceof DbObjectDifferenceCollection collection) result.addAll(createDiffSql(collection.getList(State.Added,State.Modified)));
		return result;
	}
}
