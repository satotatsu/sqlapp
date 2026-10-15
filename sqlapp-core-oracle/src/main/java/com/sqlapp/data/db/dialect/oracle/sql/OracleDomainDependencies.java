/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.util.JsonUtils;

/** Ordering of domain definitions using catalog dependencies retained through XML. */
public final class OracleDomainDependencies {

	public static final String DEPENDENCIES = "oracle.domain.dependencies";

	private OracleDomainDependencies() { }

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
				if (dependencies(domain).stream().anyMatch(remaining::containsKey)) continue;
				result.add(domain);
				iterator.remove();
				progress = true;
			}
			if (!progress) throw new IllegalArgumentException("Cyclic Oracle domain dependencies: " + remaining.keySet());
		}
		return result;
	}

	private static List<List<String>> dependencies(Domain domain) {
		String json = domain.getSpecifics().get(DEPENDENCIES);
		if (json == null) return List.of();
		List<?> rows = JsonUtils.fromJsonString(json, List.class);
		if (rows == null) throw new IllegalArgumentException("Invalid Oracle domain dependencies: " + domain.getName());
		List<List<String>> result = new ArrayList<>();
		for (Object row : rows) {
			if (!(row instanceof List<?> key) || key.size() != 2
					|| !(key.get(0) instanceof String owner) || !(key.get(1) instanceof String name)) {
				throw new IllegalArgumentException("Invalid Oracle domain dependencies: " + domain.getName());
			}
			result.add(List.of(owner, name));
		}
		return result;
	}
}
