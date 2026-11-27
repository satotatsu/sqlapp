/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Constant;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Operator;
import com.sqlapp.data.schemas.OperatorClass;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Type;

/**
 * Stable ordering of catalog additions around domain prerequisites and
 * consumers.
 */
final class OracleDomainMigrationOrder {
	private OracleDomainMigrationOrder() {
	}

	static List<SqlOperation> additions(List<SqlOperation> operations) {
		Map<List<String>, Integer> domains = new LinkedHashMap<>();
		Set<Integer> prerequisites = new LinkedHashSet<>();
		Set<Integer> roots = new LinkedHashSet<>();
		for (int i = 0; i < operations.size(); i++) {
			SqlOperation operation = operations.get(i);
			if (operation.getSqlType() != SqlType.CREATE)
				continue;
			DbCommonObject<?> object = operation.getOriginal();
			if (object instanceof Domain domain) {
				var key = Arrays.asList(domain.getSchemaName(), domain.getName());
				if (domains.putIfAbsent(key, i) != null)
					throw new IllegalArgumentException("Duplicate domain creation: " + key);
			} else if (object instanceof Sequence || object instanceof Type || object instanceof Constant
					|| object instanceof Operator || object instanceof OperatorClass
					|| object instanceof com.sqlapp.data.schemas.Package) {
				prerequisites.add(i);
			} else if (object != null && !(object instanceof AbstractSchemaObject<?>) && !(object instanceof Column)
					&& object.getAncestor(Schema.class) == null) {
				prerequisites.add(i);
				roots.add(i);
			}
		}
		if (domains.isEmpty())
			return operations;
		Set<Integer> domainIndices = new LinkedHashSet<>(domains.values());
		List<Set<Integer>> required = new ArrayList<>();
		for (int i = 0; i < operations.size(); i++)
			required.add(new LinkedHashSet<>());
		for (var entry : domains.entrySet()) {
			int index = entry.getValue();
			required.get(index).addAll(prerequisites);
			Domain domain = operations.get(index).getOriginal();
			for (var dependency : OracleDomainDependencies.dependencies(domain)) {
				Integer prerequisite = domains.get(dependency);
				if (prerequisite != null)
					required.get(index).add(prerequisite);
			}
		}
		Map<String, Integer> previousPrerequisite = new HashMap<>();
		Integer previousOrdinary = null;
		for (int i = 0; i < operations.size(); i++) {
			SqlOperation operation = operations.get(i);
			if (prerequisites.contains(i)) {
				if (!roots.contains(i))
					required.get(i).addAll(roots);
				DbCommonObject<?> object = operation.getOriginal();
				String schema = object instanceof AbstractSchemaObject<?> schemaObject ? schemaObject.getSchemaName()
						: null;
				Integer previous = previousPrerequisite.put(schema, i);
				if (previous != null)
					required.get(i).add(previous);
				continue;
			}
			if (domainIndices.contains(i)
					|| operation.getSqlType() == SqlType.DROP && operation.getOriginal() instanceof Domain)
				continue;
			if (previousOrdinary != null)
				required.get(i).add(previousOrdinary);
			previousOrdinary = i;
			DbCommonObject<?>[] objects = operation.getTargets();
			if (objects == null || objects.length == 0)
				objects = operation.getOriginals();
			if (objects == null)
				continue;
			for (DbCommonObject<?> object : objects) {
				if (object == null)
					continue;
				Table table = object instanceof Table t ? t : object.getAncestor(Table.class);
				if (table == null)
					continue;
				for (Column column : table.getColumns()) {
					if (column.getDomainName() == null)
						continue;
					String schema = column.getDomainSchemaName() == null ? table.getSchemaName()
							: column.getDomainSchemaName();
					Integer prerequisite = domains.get(Arrays.asList(schema, column.getDomainName()));
					if (prerequisite != null)
						required.get(i).add(prerequisite);
				}
			}
		}
		List<List<Integer>> consumers = new ArrayList<>();
		int[] indegree = new int[operations.size()];
		for (int i = 0; i < operations.size(); i++)
			consumers.add(new ArrayList<>());
		PriorityQueue<Integer> ready = new PriorityQueue<>();
		for (int i = 0; i < operations.size(); i++) {
			indegree[i] = required.get(i).size();
			for (int prerequisite : required.get(i))
				consumers.get(prerequisite).add(i);
			if (indegree[i] == 0)
				ready.add(i);
		}
		List<SqlOperation> result = new ArrayList<>();
		while (!ready.isEmpty()) {
			int index = ready.remove();
			result.add(operations.get(index));
			for (int consumer : consumers.get(index))
				if (--indegree[consumer] == 0)
					ready.add(consumer);
		}
		if (result.size() != operations.size())
			throw new IllegalArgumentException("Cyclic domain migration dependencies: " + domains.keySet());
		return result;
	}
}
