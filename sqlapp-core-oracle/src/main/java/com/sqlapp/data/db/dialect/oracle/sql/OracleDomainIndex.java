/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;

/** Catalog-backed, nonpartitioned domain indexes. */
final class OracleDomainIndex {
	private OracleDomainIndex() { }
	static boolean isDomain(Index index) {
		return index.getIndexType() == IndexType.Domain || index.getIndexType() == IndexType.FunctionDomain;
	}
	static void validate(Index index) {
		if (index.getName() == null || index.getName().isBlank()) throw new IllegalArgumentException("Domain index name is required");
		if (index.getPartitioning() != null || index.getSpecifics().containsKey("oracle.index.locality"))
			throw new IllegalArgumentException("Partitioned domain indexes require dedicated recreation support: " + index.getName());
		if (index.isUnique() || index.isCompression() || index.getCompressionType() != null || index.getTableSpaceName() != null
				|| index.getSpecifics().containsKey("oracle.index.prefixLength") || index.getSpecifics().containsKey("oracle.index.reverse")
				|| "true".equalsIgnoreCase(index.getSpecifics().get("oracle.index.bitmap"))
				|| "true".equalsIgnoreCase(index.getSpecifics().get("oracle.index.join")))
			throw new IllegalArgumentException("Domain index attributes must be expressed through its indextype parameters: " + index.getName());
		if (index.getTable() != null && index.getTable().getTableType() == Table.TableType.Temporary)
			throw new IllegalArgumentException("Domain indexes cannot be created on temporary tables");
		for (String key : List.of("ITYP_OWNER", "ITYP_NAME")) {
			String value = index.getSpecifics().get(key);
			if (value == null || value.isBlank()) throw new IllegalArgumentException("Domain index requires " + key + ": " + index.getName());
		}
		for (String key : List.of("DOMIDX_STATUS", "DOMIDX_OPSTATUS")) {
			String status = index.getSpecifics().get(key);
			if (status != null && !"VALID".equals(status)) throw new IllegalArgumentException("Domain index is not ready for generation: " + key + "=" + status);
		}
		String status = index.getSpecifics().get("oracle.index.status");
		if (status != null && !Set.of("VALID", "N/A").contains(status)) throw new IllegalArgumentException("Unsupported domain index status: " + status);
		String visibility = index.getSpecifics().get("oracle.index.visibility");
		if (visibility != null && !"VISIBLE".equals(visibility)) throw new IllegalArgumentException("Domain index visibility changes require dedicated support");
	}
	static void create(Index index, Table table, boolean decorate, OracleSqlBuilder builder) {
		validate(index);
		if (index.getColumns().size() != 1 || index.getColumns().get(0).getOrder() == Order.Desc)
			throw new IllegalArgumentException("Domain index requires exactly one unordered column or expression");
		builder.index().space().name(index, decorate).on().name(table, decorate
				|| index.getSchemaName() != null && !index.getSchemaName().equals(table.getSchemaName()));
		builder.space()._add("(").name(index.getColumns().get(0))._add(") INDEXTYPE IS ")
				.name(index.getSpecifics().get("ITYP_OWNER"))._add(".").name(index.getSpecifics().get("ITYP_NAME"));
		parameters(parameterValue(index), builder);
	}
	static String parameterValue(Index index) {
		String plain = index.getSpecifics().get("PARAMETERS");
		if (plain != null) return plain;
		String encoded = index.getSpecifics().get("oracle.index.domain.parametersBase64");
		if (encoded == null) return null;
		try {
			byte[] bytes = java.util.Base64.getDecoder().decode(encoded);
			return java.nio.charset.StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
					.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
		} catch (IllegalArgumentException | java.nio.charset.CharacterCodingException error) {
			throw new IllegalArgumentException("Invalid oracle.index.domain.parametersBase64: " + index.getName(), error);
		}
	}
	static void parameters(String value, OracleSqlBuilder builder) {
		if (value == null) return;
		if (value.length() > 1000) throw new IllegalArgumentException("Domain index parameter string exceeds 1000 characters");
		builder.space()._add("PARAMETERS ('")._add(value.replace("'", "''"))._add("')");
	}
}
