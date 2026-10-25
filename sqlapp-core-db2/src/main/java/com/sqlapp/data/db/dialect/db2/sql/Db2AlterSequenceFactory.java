/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.State;

public class Db2AlterSequenceFactory extends Db2_1110CreateSequenceFactory {
	@Override
	public List<SqlOperation> createSql(com.sqlapp.data.schemas.Sequence target) {
		throw new IllegalArgumentException("Db2 ALTER requires original and target models; use createDiffSql");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Sequence target = difference.getTarget(Sequence.class);
		if (target != null && target.getScale() != null && target.getScale() != 0)
			throw new IllegalArgumentException("Db2 sequence scale must be zero");
		if (difference.getState() == State.Unchanged)
			return List.of();
		if (difference.getState() == State.Added)
			return super.createSql(difference.getTarget(com.sqlapp.data.schemas.Sequence.class));
		if (difference.getState() == State.Deleted) {
			com.sqlapp.data.schemas.Sequence before = difference.getOriginal(com.sqlapp.data.schemas.Sequence.class);
			com.sqlapp.data.db.sql.SqlFactory<com.sqlapp.data.schemas.Sequence> drop = getSqlFactoryRegistry()
					.getSqlFactory(before, com.sqlapp.data.db.sql.SqlType.DROP);
			return drop.createSql(before);
		}
		if (difference.getState() == State.Modified) {
			Sequence originalModel = difference.getOriginal(Sequence.class).clone();
			Sequence targetModel = difference.getTarget(Sequence.class).clone();
			// Cache allocation boundaries drift during normal use and are not DDL settings.
			originalModel.getSpecifics().remove("NEXTCACHEFIRSTVALUE");
			targetModel.getSpecifics().remove("NEXTCACHEFIRSTVALUE");
			DbObjectDifference settings = originalModel.diff(targetModel);
			Set<String> changed = settings.getChangedProperties().keySet();
			Set<String> supported = new HashSet<>();
			for (SchemaProperties property : List.of(SchemaProperties.INCREMENT_BY, SchemaProperties.MIN_VALUE,
					SchemaProperties.MAX_VALUE, SchemaProperties.CYCLE, SchemaProperties.CACHE,
					SchemaProperties.CACHE_SIZE, SchemaProperties.SEQUENCE_ORDER, SchemaProperties.REMARKS))
				supported.add(property.getLabel());
			if (!supported.containsAll(changed))
				throw new IllegalArgumentException(
						"Db2 sequence type, identity, START or allocation-state changes require an explicit plan; automatic ALTER never restarts a sequence");
			Sequence after = difference.getTarget(Sequence.class);
			if (!Db2AlterCommentSql.onlyRemarks(settings)) {
				if (!after.getDefinition().isEmpty())
					throw new IllegalArgumentException(
							"Db2 full-definition sequence changes require an explicit plan keeping native DDL consistent");
				// Reuse CREATE's vendor type/range validation without returning its SQL.
				super.createSql(after);
			}
			StringBuilder clauses = new StringBuilder();
			if (changed.contains(SchemaProperties.INCREMENT_BY.getLabel())) {
				if (after.getIncrementBy() == null)
					throw new IllegalArgumentException("Db2 ALTER SEQUENCE requires an explicit increment");
				clauses.append(" INCREMENT BY ").append(after.getIncrementBy());
			}
			if (changed.contains(SchemaProperties.MIN_VALUE.getLabel()))
				clauses.append(after.getMinValue() == null ? " NO MINVALUE" : " MINVALUE " + after.getMinValue());
			if (changed.contains(SchemaProperties.MAX_VALUE.getLabel()))
				clauses.append(after.getMaxValue() == null ? " NO MAXVALUE" : " MAXVALUE " + after.getMaxValue());
			if (changed.contains(SchemaProperties.CYCLE.getLabel()))
				clauses.append(after.isCycle() ? " CYCLE" : " NO CYCLE");
			if (changed.contains(SchemaProperties.CACHE.getLabel())
					|| changed.contains(SchemaProperties.CACHE_SIZE.getLabel())) {
				if (!after.isCache())
					clauses.append(" NO CACHE");
				else {
					if (after.getCacheSize() == null || after.getCacheSize() < 2)
						throw new IllegalArgumentException(
								"Db2 ALTER SEQUENCE CACHE requires an explicit size of at least 2");
					clauses.append(" CACHE ").append(after.getCacheSize());
				}
			}
			if (changed.contains(SchemaProperties.SEQUENCE_ORDER.getLabel()))
				clauses.append(after.isOrder() ? " ORDER" : " NO ORDER");
			List<SqlOperation> result = new ArrayList<>();
			String name = name(after, getOptions().isDecorateSchemaName());
			if (!clauses.isEmpty())
				result.add(createOperation("ALTER SEQUENCE " + name + clauses, SqlType.ALTER, after));
			if (changed.contains(SchemaProperties.REMARKS.getLabel()))
				result.add(createOperation(
						Db2AlterCommentSql.comment(getDialect(), "SEQUENCE " + name, after.getRemarks()),
						SqlType.SET_COMMENT, after));
			return result;
		}
		return super.createDiffSql(difference);
	}
}
