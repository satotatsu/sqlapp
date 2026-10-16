/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.DomainColumn;

/** Oracle-specific roles of components in flexible domain associations. */
public final class OracleDomainAssociationUtils {
	public static final String KIND = "oracle.domain.kind";
	public static final String DISCRIMINANT = "oracle.domain.discriminant";

	private OracleDomainAssociationUtils() {
	}

	public static boolean isFlexible(Domain domain) {
		return "FLEXIBLE".equals(domain.getSpecifics().get(KIND));
	}

	public static boolean isDiscriminant(DomainColumn column) {
		String value = column.getSpecifics().get(DISCRIMINANT);
		if (value == null || "false".equalsIgnoreCase(value))
			return false;
		if ("true".equalsIgnoreCase(value))
			return true;
		throw new IllegalArgumentException("Invalid domain discriminant flag: " + column.getName());
	}

	static Map<List<String>, Map<String, Column>> groups(Table table) {
		Map<List<String>, Map<String, Column>> groups = new LinkedHashMap<>();
		for (Column column : table.getColumns()) {
			if (column.getDomain() != null && isFlexible(column.getDomain())
					&& column.getDomain().getColumns().isEmpty()) {
				throw new IllegalArgumentException(
						"Resolve flexible domain components before altering associations: " + column.getDomainName());
			}
			if (column.getDomain() == null || column.getDomain().getColumns().isEmpty())
				continue;
			String group = column.getSpecifics().get(Oracle23aiCreateTableFactory.DOMAIN_ASSOCIATION_ID);
			var key = java.util.Arrays.asList(column.getDomainSchemaName(), column.getDomainName(), group);
			var parts = groups.computeIfAbsent(key, k -> new LinkedHashMap<>());
			String part = column.getDomainColumnName() == null ? column.getName() : column.getDomainColumnName();
			if (parts.putIfAbsent(part, column) != null) {
				throw new IllegalArgumentException(
						"Duplicate domain component; set a distinct associationId for repeated associations: " + part);
			}
		}
		for (var parts : groups.values())
			orderedColumns(parts);
		return groups;
	}

	static List<Column> orderedColumns(Map<String, Column> parts) {
		Domain domain = parts.values().iterator().next().getDomain();
		if (parts.size() != domain.getColumns().size())
			throw new IllegalArgumentException("Incomplete domain association: " + domain.getName());
		if (isFlexible(domain)) {
			long discriminants = domain.getColumns().stream().filter(OracleDomainAssociationUtils::isDiscriminant)
					.count();
			if (discriminants == 0 || discriminants == domain.getColumns().size()) {
				throw new IllegalArgumentException(
						"Flexible domain requires value and USING components: " + domain.getName());
			}
		}
		List<Column> result = new ArrayList<>();
		for (boolean discriminant : new boolean[] { false, true }) {
			for (DomainColumn component : domain.getColumns()) {
				if ((isFlexible(domain) && isDiscriminant(component)) != discriminant)
					continue;
				Column column = parts.get(component.getName());
				if (column == null)
					throw new IllegalArgumentException("Missing domain component: " + component.getName());
				result.add(column);
			}
		}
		return result;
	}

}
