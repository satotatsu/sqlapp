/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.cockroach.util.CockroachPlacement;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.util.JsonUtils;

/**
 * Configures placement on an existing target database before creating its
 * contents.
 */
public class CockroachCreateCatalogFactory extends AbstractCreateCatalogFactory<PostgresSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Catalog catalog) {
		var result = new ArrayList<SqlOperation>();
		var specifics = catalog.getSpecifics();
		var keys = new HashSet<>(specifics.keySet());
		keys.removeIf(key -> !key.startsWith("COCKROACH_"));
		keys.removeAll(Set.of("COCKROACH_PRIMARY_REGION", "COCKROACH_SECONDARY_REGION", "COCKROACH_REGIONS",
				"COCKROACH_SURVIVAL_GOAL"));
		if (!keys.isEmpty())
			throw new IllegalArgumentException("Unsupported CockroachDB database placement specifics: " + keys);
		String primary = specifics.get("COCKROACH_PRIMARY_REGION"),
				secondary = specifics.get("COCKROACH_SECONDARY_REGION"),
				goal = specifics.get("COCKROACH_SURVIVAL_GOAL");
		if (primary == null) {
			if (secondary != null || goal != null || specifics.containsKey("COCKROACH_REGIONS"))
				throw new IllegalArgumentException("COCKROACH_PRIMARY_REGION is required for database placement");
		} else {
			CockroachPlacement.quote(primary);
			var regions = new LinkedHashSet<String>();
			regions.add(primary);
			if (specifics.containsKey("COCKROACH_REGIONS")) {
				Object parsed = JsonUtils.fromJsonString(specifics.get("COCKROACH_REGIONS"), Object.class);
				if (!(parsed instanceof List<?> list) || list.isEmpty())
					throw new IllegalArgumentException(
							"COCKROACH_REGIONS must be a nonempty JSON array of region names");
				regions.clear();
				for (Object value : list) {
					if (!(value instanceof String region))
						throw new IllegalArgumentException("COCKROACH_REGIONS entries must be strings");
					CockroachPlacement.quote(region);
					if (!regions.add(region))
						throw new IllegalArgumentException("Duplicate CockroachDB region: " + region);
				}
				if (!regions.contains(primary))
					throw new IllegalArgumentException("COCKROACH_REGIONS must include the primary region");
			}
			if (secondary != null && (secondary.equals(primary) || !regions.contains(secondary)))
				throw new IllegalArgumentException("Secondary region must be a different configured region");
			if (goal != null && !Set.of("ZONE", "REGION").contains(goal))
				throw new IllegalArgumentException("COCKROACH_SURVIVAL_GOAL must be ZONE or REGION");
			add(result, catalog, "PRIMARY REGION " + CockroachPlacement.quote(primary));
			for (String region : regions)
				if (!region.equals(primary))
					add(result, catalog, "ADD REGION IF NOT EXISTS " + CockroachPlacement.quote(region));
			if (secondary != null)
				add(result, catalog, "SECONDARY REGION " + CockroachPlacement.quote(secondary));
			if (goal != null)
				add(result, catalog, "SURVIVE " + goal + " FAILURE");
		}
		result.addAll(super.createSql(catalog));
		return result;
	}

	private void add(List<SqlOperation> result, Catalog catalog, String clause) {
		addSql(result, createSqlBuilder()._add("ALTER DATABASE ")._add(CockroachPlacement.quote(catalog.getName()))
				.space()._add(clause), SqlType.ALTER, catalog);
	}
}
