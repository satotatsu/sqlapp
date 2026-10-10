/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Changes configuration without implicitly restarting an existing sequence. */
public class CockroachAlterSequenceFactory extends SimpleSqlFactory<Sequence,PostgresSqlBuilder> {
	@Override public List<SqlOperation> createSql(Sequence sequence) {throw new UnsupportedOperationException("Sequence ALTER requires original.diff(target)");}
	@Override public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var target=difference.getTarget(Sequence.class);
		if(target.isCycle()) throw new UnsupportedOperationException("CockroachDB CYCLE is not implemented");var changed=new HashSet<>(difference.getChangedProperties().keySet());
		var builder=createSqlBuilder().alter().sequence().space().name(difference.getOriginal(Sequence.class),getOptions().isDecorateSchemaName());
		boolean options=false;
		for(var property:List.of(SchemaProperties.INCREMENT_BY,SchemaProperties.MIN_VALUE,SchemaProperties.MAX_VALUE,SchemaProperties.START_VALUE,SchemaProperties.CYCLE)) {
			if(!changed.remove(property.getLabel())) continue;
			options=true;
			switch(property) {
			case INCREMENT_BY -> builder._add(" INCREMENT BY ")._add(target.getIncrementBy());
			case MIN_VALUE -> builder._add(" MINVALUE ")._add(target.getMinValue());
			case MAX_VALUE -> builder._add(" MAXVALUE ")._add(target.getMaxValue());
			case START_VALUE -> builder._add(" START WITH ")._add(target.getStartValue());
			case CYCLE -> builder._add(target.isCycle()?" CYCLE":" NO CYCLE");
			default -> throw new IllegalStateException();
			}
		}
		if(options) changed.remove(SchemaProperties.DEFINITION.getLabel());
		if(!changed.isEmpty()) throw new UnsupportedOperationException("Unsupported CockroachDB sequence changes: "+changed+"; sequence restart requires an explicit migration");
		var result=new ArrayList<SqlOperation>();if(options) addSql(result,builder,SqlType.ALTER,target);return result;
	}
}
