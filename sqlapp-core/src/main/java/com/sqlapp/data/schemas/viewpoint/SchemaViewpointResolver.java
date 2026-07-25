/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 */
package com.sqlapp.data.schemas.viewpoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Table;

/**
 * Resolves stable viewpoint identifiers to tables in a Catalog.
 */
public class SchemaViewpointResolver {

	public Resolution resolve(Catalog catalog, SchemaViewpoints definitions, String viewpointId) {
		validateHeader(definitions);
		SchemaViewpoint viewpoint = uniqueViewpoint(definitions, viewpointId);
		Set<String> viewpointReferences = viewpoint.getTables() == null
				? new LinkedHashSet<>()
				: new LinkedHashSet<>(viewpoint.getTables());
		if (viewpointReferences.isEmpty()) {
			throw new IllegalArgumentException("Viewpoint contains no tables: " + viewpointId);
		}
		LinkedHashSet<Table> selected = new LinkedHashSet<>();
		selected.addAll(resolveReferences(catalog, viewpointReferences).values());
		return new Resolution(viewpoint, List.copyOf(selected));
	}

	private void validateHeader(SchemaViewpoints definitions) {
		if (definitions == null || !SchemaViewpoints.FORMAT.equals(definitions.getFormat())) {
			throw new IllegalArgumentException("Unsupported schema viewpoints format.");
		}
		if (definitions.getVersion() != SchemaViewpoints.CURRENT_VERSION) {
			throw new IllegalArgumentException("Unsupported schema viewpoints version: "
					+ definitions.getVersion());
		}
	}

	private SchemaViewpoint uniqueViewpoint(SchemaViewpoints definitions, String viewpointId) {
		if (viewpointId == null || viewpointId.isBlank()) {
			throw new IllegalArgumentException("viewpointId is required.");
		}
		SchemaViewpoint found = null;
		Set<String> ids = new LinkedHashSet<>();
		for (SchemaViewpoint viewpoint : definitions.getViewpoints()) {
			if (viewpoint.getId() == null || viewpoint.getId().isBlank()) {
				throw new IllegalArgumentException("Viewpoint id is required.");
			}
			String key = normalize(viewpoint.getId());
			if (!ids.add(key)) {
				throw new IllegalArgumentException("Duplicate viewpoint id: " + viewpoint.getId());
			}
			if (key.equals(normalize(viewpointId))) {
				found = viewpoint;
			}
		}
		if (found == null) {
			throw new IllegalArgumentException("Unknown viewpoint: " + viewpointId);
		}
		return found;
	}

	private java.util.Map<String, Table> resolveReferences(Catalog catalog, Collection<String> references) {
		java.util.Map<String, Table> result = new LinkedHashMap<>();
		for (String reference : references) {
			result.put(reference, resolveReference(catalog, reference));
		}
		return result;
	}

	public Table resolveReference(Catalog catalog, String reference) {
		if (reference == null || reference.isBlank()) {
			throw new IllegalArgumentException("Table reference is required.");
		}
		String[] names = reference.split("\\.", -1);
		if (names.length < 1 || names.length > 3) {
			throw new IllegalArgumentException("Invalid table reference: " + reference);
		}
		List<Table> matches = tables(catalog).stream().filter(table -> matches(table, names)).toList();
		if (matches.isEmpty()) {
			throw new IllegalArgumentException("Unknown table reference: " + reference);
		}
		if (matches.size() > 1) {
			throw new IllegalArgumentException("Ambiguous table reference: " + reference);
		}
		return matches.getFirst();
	}

	private boolean matches(Table table, String[] names) {
		if (!equalsName(table.getName(), names[names.length - 1])) {
			return false;
		}
		if (names.length >= 2 && !equalsName(table.getSchemaName(), names[names.length - 2])) {
			return false;
		}
		return names.length < 3 || equalsName(table.getCatalogName(), names[0]);
	}

	private boolean equalsName(String actual, String expected) {
		return actual != null && actual.equalsIgnoreCase(expected);
	}

	public String qualifiedName(Table table) {
		List<String> names = new ArrayList<>();
		if (table.getCatalogName() != null && !table.getCatalogName().isBlank()) {
			names.add(table.getCatalogName());
		}
		if (table.getSchemaName() != null && !table.getSchemaName().isBlank()) {
			names.add(table.getSchemaName());
		}
		names.add(table.getName());
		return String.join(".", names);
	}

	private List<Table> tables(Catalog catalog) {
		List<Table> result = new ArrayList<>();
		catalog.getSchemas().forEach(schema -> result.addAll(schema.getTables()));
		return result;
	}

	private String normalize(String value) {
		return value.toLowerCase(Locale.ROOT);
	}

	public record Resolution(SchemaViewpoint viewpoint, List<Table> tables) {
	}
}
