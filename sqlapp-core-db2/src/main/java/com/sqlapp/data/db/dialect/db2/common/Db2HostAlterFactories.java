/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Mask;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.State;

/**
 * Host ALTER/DROP for schema objects. Additions and removals use the registered
 * CREATE and DROP factories; in-place changes are limited to what the host
 * products can alter, and anything else fails with an explicit-plan error.
 */
public final class Db2HostAlterFactories {
	private static final String REMARKS = SchemaProperties.REMARKS.getLabel();

	private Db2HostAlterFactories() {
	}

	/** Shared Added/Deleted/remarks handling. */
	abstract static class Base<T extends AbstractSchemaObject<?>> extends SimpleSqlFactory<T, Db2SqlBuilder> {
		@Override
		public List<SqlOperation> createSql(T target) {
			throw new IllegalArgumentException("Db2 host ALTER requires original and target models; use createDiffSql");
		}

		@SuppressWarnings("unchecked")
		@Override
		public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
			if (difference.getState() == State.Unchanged)
				return List.of();
			if (difference.getState() == State.Added || difference.getState() == State.Deleted) {
				T value = (T) (difference.getState() == State.Added ? difference.getTarget()
						: difference.getOriginal());
				SqlFactory<T> factory = getSqlFactoryRegistry().getSqlFactory(value,
						difference.getState() == State.Added ? SqlType.CREATE : SqlType.DROP);
				return factory.createSql(value);
			}
			T before = (T) difference.getOriginal(), after = (T) difference.getTarget();
			return modified(before, after, difference.getChangedProperties().keySet());
		}

		abstract List<SqlOperation> modified(T before, T after, Set<String> changed);

		SqlOperation comment(String target, String remarks, com.sqlapp.data.schemas.DbCommonObject<?> object) {
			var builder = createSqlBuilder();
			builder._add("COMMENT ON " + target + " IS ").sqlChar(remarks == null ? "" : remarks);
			return createOperation(builder.toString(), SqlType.SET_COMMENT, object);
		}

		static IllegalArgumentException plan(Object object, Set<String> changed) {
			return new IllegalArgumentException("Db2 host changes to " + changed
					+ " require an explicit replacement plan for dependent objects and privileges: " + object);
		}

		static String name(AbstractSchemaObject<?> object) {
			return object.getSchemaName() == null ? Db2CreationContext.identifier(object.getName())
					: Db2CreationContext.qualified(object.getSchemaName(), object.getName());
		}
	}

	/**
	 * Objects whose only in-place change is their comment: views and MQTs
	 * ({@code COMMENT ON TABLE}), triggers, distinct/array types and aliases.
	 */
	public static class CommentOnly extends Base<AbstractSchemaObject<?>> {
		@Override
		List<SqlOperation> modified(AbstractSchemaObject<?> before, AbstractSchemaObject<?> after,
				Set<String> changed) {
			if (!Set.of(REMARKS).containsAll(changed))
				throw plan(after.getName(), changed);
			String kind = switch (after.getClass().getSimpleName()) {
			case "View", "Mview" -> "TABLE";
			case "Trigger" -> "TRIGGER";
			case "Domain" -> "TYPE";
			case "Synonym" -> "ALIAS";
			case "Constant" -> "VARIABLE";
			default -> throw plan(after.getName(), changed);
			};
			return List.of(comment(kind + " " + name(after),
					((com.sqlapp.data.schemas.properties.RemarksProperty<?>) after).getRemarks(), after));
		}
	}

	/** Remarks become COMMENT ON INDEX; structural changes drop and recreate. */
	public static class AlterIndex extends Base<Index> {
		@Override
		List<SqlOperation> modified(Index before, Index after, Set<String> changed) {
			if (Set.of(REMARKS).containsAll(changed)) {
				String schema = after.getSpecifics().getOrDefault("INDEX_SCHEMA", after.getSchemaName());
				String target = schema == null ? Db2CreationContext.identifier(after.getName())
						: Db2CreationContext.qualified(schema, after.getName());
				return List.of(comment("INDEX " + target, after.getRemarks(), after));
			}
			List<SqlOperation> result = new ArrayList<>();
			SqlFactory<Index> drop = getSqlFactoryRegistry().getSqlFactory(before, SqlType.DROP);
			result.addAll(drop.createSql(before));
			SqlFactory<Index> create = getSqlFactoryRegistry().getSqlFactory(after, SqlType.CREATE);
			result.addAll(create.createSql(after));
			return result;
		}
	}

	/**
	 * ENABLE/DISABLE and comments in place; a new RETURN expression is replaced.
	 */
	public static class AlterMask extends Base<Mask> {
		@Override
		List<SqlOperation> modified(Mask before, Mask after, Set<String> changed) {
			Set<String> inPlace = Set.of(REMARKS, SchemaProperties.ENABLE.getLabel());
			if (!inPlace.containsAll(changed))
				throw plan(after.getName(), changed);
			List<SqlOperation> result = new ArrayList<>();
			if (changed.contains(SchemaProperties.ENABLE.getLabel()))
				result.add(createOperation("ALTER MASK " + name(after) + (after.isEnable() ? " ENABLE" : " DISABLE"),
						SqlType.ALTER, after));
			if (changed.contains(REMARKS))
				result.add(comment("MASK " + name(after), after.getRemarks(), after));
			return result;
		}
	}

	/** DROP MASK. */
	public static class DropMask extends AbstractDropNamedObjectFactory<Mask, Db2SqlBuilder> {
		@Override
		protected void addDropObject(Mask mask, Db2SqlBuilder builder) {
			builder._add("DROP MASK " + Base.name(mask));
		}
	}

	/**
	 * ALTER SEQUENCE for increment, bounds, cycle, cache and order. Data type,
	 * START and the current position are never changed implicitly.
	 */
	public static class AlterSequence extends Base<Sequence> {
		private static final Set<String> SUPPORTED = Set.of(SchemaProperties.INCREMENT_BY.getLabel(),
				SchemaProperties.MIN_VALUE.getLabel(), SchemaProperties.MAX_VALUE.getLabel(),
				SchemaProperties.CYCLE.getLabel(), SchemaProperties.CACHE.getLabel(),
				SchemaProperties.CACHE_SIZE.getLabel(), SchemaProperties.SEQUENCE_ORDER.getLabel(), REMARKS);

		@Override
		List<SqlOperation> modified(Sequence before, Sequence after, Set<String> changed) {
			if (!SUPPORTED.containsAll(changed))
				throw new IllegalArgumentException(
						"Db2 host sequence type, START or allocation-state changes require an explicit plan; automatic ALTER never restarts a sequence: "
								+ changed);
			var clauses = new ArrayList<String>();
			if (changed.contains(SchemaProperties.INCREMENT_BY.getLabel())) {
				BigDecimal increment = after.getIncrementBy() == null ? null
						: new BigDecimal(after.getIncrementBy().toString());
				if (increment == null || increment.signum() == 0)
					throw new IllegalArgumentException(
							"Sequence increment must be a nonzero value: " + after.getName());
				clauses.add("INCREMENT BY " + increment.toPlainString());
			}
			if (changed.contains(SchemaProperties.MIN_VALUE.getLabel()))
				clauses.add(after.getMinValue() == null ? "NO MINVALUE" : "MINVALUE " + after.getMinValue());
			if (changed.contains(SchemaProperties.MAX_VALUE.getLabel()))
				clauses.add(after.getMaxValue() == null ? "NO MAXVALUE" : "MAXVALUE " + after.getMaxValue());
			if (changed.contains(SchemaProperties.CYCLE.getLabel()))
				clauses.add(after.isCycle() ? "CYCLE" : "NO CYCLE");
			if (changed.contains(SchemaProperties.CACHE.getLabel())
					|| changed.contains(SchemaProperties.CACHE_SIZE.getLabel())) {
				Integer size = after.getCacheSize();
				clauses.add(!after.isCache() || size == null || size == 0 ? "NO CACHE" : "CACHE " + size);
			}
			if (changed.contains(SchemaProperties.SEQUENCE_ORDER.getLabel()))
				clauses.add(after.isOrder() ? "ORDER" : "NO ORDER");
			List<SqlOperation> result = new ArrayList<>();
			if (!clauses.isEmpty())
				result.add(createOperation("ALTER SEQUENCE " + name(after) + " " + String.join(" ", clauses),
						SqlType.ALTER, after));
			if (changed.contains(REMARKS))
				result.add(comment("SEQUENCE " + name(after), after.getRemarks(), after));
			return result;
		}
	}
}
