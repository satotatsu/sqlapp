/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.util.JsonUtils;

/**
 * Ordering of domain definitions using catalog dependencies retained through
 * XML.
 */
public final class OracleDomainDependencies {

	public static final String DEPENDENCIES = "oracle.domain.dependencies";

	private OracleDomainDependencies() {
	}

	static void validateRemovals(List<Schema> targetSchemas, List<SqlOperation> operations) {
		java.util.Set<List<String>> removed = new java.util.HashSet<>();
		for (SqlOperation operation : operations) {
			if (operation.getSqlType() == SqlType.DROP && operation.getOriginal() instanceof Domain domain) {
				removed.add(Arrays.asList(domain.getSchemaName(), domain.getName()));
			}
		}
		if (removed.isEmpty())
			return;
		for (Schema schema : targetSchemas) {
			for (Domain domain : schema.getDomains()) {
				for (var dependency : dependencies(domain)) {
					if (removed.contains(dependency))
						throw new IllegalArgumentException("Target domain still references a removed domain: "
								+ schema.getName() + "." + domain.getName() + " -> " + dependency);
				}
			}
			for (var table : schema.getTables()) {
				for (var column : table.getColumns()) {
					if (column.getDomainName() == null)
						continue;
					String owner = column.getDomainSchemaName() == null ? schema.getName()
							: column.getDomainSchemaName();
					if (removed.contains(Arrays.asList(owner, column.getDomainName()))) {
						throw new IllegalArgumentException("Target column still references a removed domain: "
								+ schema.getName() + "." + table.getName() + "." + column.getName() + " -> " + owner
								+ "." + column.getDomainName());
					}
				}
			}
		}
	}

	static List<SqlOperation> postponeDrops(List<SqlOperation> operations) {
		List<SqlOperation> result = new ArrayList<>();
		Map<Domain, SqlOperation> drops = new java.util.IdentityHashMap<>();
		List<Domain> domains = new ArrayList<>();
		for (SqlOperation operation : operations) {
			if (operation.getSqlType() == SqlType.DROP && operation.getOriginal() instanceof Domain domain) {
				domains.add(domain);
				drops.put(domain, operation);
			} else
				result.add(operation);
		}
		List<Domain> sorted = sort(domains);
		java.util.Collections.reverse(sorted);
		for (Domain domain : sorted)
			result.add(drops.get(domain));
		return result;
	}

	static List<DbObjectDifference> sortDifferences(List<DbObjectDifference> differences, boolean deleting) {
		Map<Domain, DbObjectDifference> byDomain = new java.util.IdentityHashMap<>();
		List<Domain> domains = new ArrayList<>();
		for (var difference : differences) {
			Domain domain = deleting ? difference.getOriginal(Domain.class) : difference.getTarget(Domain.class);
			if (domain == null)
				throw new IllegalArgumentException("Domain difference lacks the required original/target");
			domains.add(domain);
			byDomain.put(domain, difference);
		}
		List<Domain> sorted = sort(domains);
		if (deleting)
			java.util.Collections.reverse(sorted);
		return sorted.stream().map(byDomain::get).toList();
	}

	public static List<Domain> sort(List<Domain> domains) {
		Map<List<String>, Domain> remaining = new LinkedHashMap<>();
		for (Domain domain : domains) {
			var key = Arrays.asList(domain.getSchemaName(), domain.getName());
			if (remaining.putIfAbsent(key, domain) != null) {
				throw new IllegalArgumentException("Duplicate Oracle domain: " + key);
			}
		}
		List<Domain> result = new ArrayList<>();
		while (!remaining.isEmpty()) {
			boolean progress = false;
			var iterator = remaining.entrySet().iterator();
			while (iterator.hasNext()) {
				Domain domain = iterator.next().getValue();
				if (dependencies(domain).stream().anyMatch(remaining::containsKey))
					continue;
				result.add(domain);
				iterator.remove();
				progress = true;
			}
			if (!progress)
				throw new IllegalArgumentException("Cyclic Oracle domain dependencies: " + remaining.keySet());
		}
		return result;
	}

	static List<List<String>> dependencies(Domain domain) {
		String json = domain.getSpecifics().get(DEPENDENCIES);
		if (json == null)
			return List.of();
		List<?> rows = JsonUtils.fromJsonString(json, List.class);
		if (rows == null)
			throw new IllegalArgumentException("Invalid Oracle domain dependencies: " + domain.getName());
		List<List<String>> result = new ArrayList<>();
		for (Object row : rows) {
			if (!(row instanceof List<?> key) || key.size() != 2 || !(key.get(0) instanceof String owner)
					|| !(key.get(1) instanceof String name)) {
				throw new IllegalArgumentException("Invalid Oracle domain dependencies: " + domain.getName());
			}
			result.add(List.of(owner, name));
		}
		return result;
	}
}
