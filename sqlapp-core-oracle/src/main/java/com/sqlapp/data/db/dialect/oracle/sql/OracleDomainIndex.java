/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.db.dialect.Dialect;

/** Catalog-backed domain indexes and simple local partitions. */
final class OracleDomainIndex {
	private OracleDomainIndex() {
	}

	static boolean isDomain(Index index) {
		return index.getIndexType() == IndexType.Domain || index.getIndexType() == IndexType.FunctionDomain;
	}

	static void validate(Index index) {
		if (index.getName() == null || index.getName().isBlank())
			throw new IllegalArgumentException("Domain index name is required");
		if (index.getPartitioning() != null) {
			if (!"LOCAL".equals(index.getSpecifics().get("oracle.index.locality"))
					|| index.getPartitioning().getSubPartitioningType() != null)
				throw new IllegalArgumentException("Domain indexes support only simple LOCAL partitioning");
			for (Partition partition : index.getPartitioning().getPartitions())
				validatePartition(partition);
		} else if (index.getSpecifics().containsKey("oracle.index.locality"))
			throw new IllegalArgumentException("Domain index locality requires a partitioning model");
		if (index.isUnique() || index.isCompression() || index.getCompressionType() != null
				|| index.getTableSpaceName() != null || index.getSpecifics().containsKey("oracle.index.prefixLength")
				|| index.getSpecifics().containsKey("oracle.index.reverse")
				|| "true".equalsIgnoreCase(index.getSpecifics().get("oracle.index.bitmap"))
				|| "true".equalsIgnoreCase(index.getSpecifics().get("oracle.index.join")))
			throw new IllegalArgumentException(
					"Domain index attributes must be expressed through its indextype parameters: " + index.getName());
		if (index.getTable() != null && index.getTable().getTableType() == Table.TableType.Temporary)
			throw new IllegalArgumentException("Domain indexes cannot be created on temporary tables");
		for (String key : List.of("ITYP_OWNER", "ITYP_NAME")) {
			String value = index.getSpecifics().get(key);
			if (value == null || value.isBlank())
				throw new IllegalArgumentException("Domain index requires " + key + ": " + index.getName());
		}
		for (String key : List.of("DOMIDX_STATUS", "DOMIDX_OPSTATUS")) {
			String status = index.getSpecifics().get(key);
			if (status != null && !"VALID".equals(status))
				throw new IllegalArgumentException("Domain index is not ready for generation: " + key + "=" + status);
		}
		String status = index.getSpecifics().get("oracle.index.status");
		if (status != null && !Set.of("VALID", "N/A").contains(status))
			throw new IllegalArgumentException("Unsupported domain index status: " + status);
		String visibility = index.getSpecifics().get("oracle.index.visibility");
		if (visibility != null && !"VISIBLE".equals(visibility))
			throw new IllegalArgumentException("Domain index visibility changes require dedicated support");
	}

	static void create(Index index, Table table, boolean decorate, OracleSqlBuilder builder) {
		validate(index);
		if (index.getColumns().size() != 1 || index.getColumns().get(0).getOrder() == Order.Desc)
			throw new IllegalArgumentException("Domain index requires exactly one unordered column or expression");
		builder.index().space().name(index, decorate).on().name(table,
				decorate || index.getSchemaName() != null && !index.getSchemaName().equals(table.getSchemaName()));
		builder.space()._add("(").name(index.getColumns().get(0))._add(") INDEXTYPE IS ")
				.name(index.getSpecifics().get("ITYP_OWNER"))._add(".").name(index.getSpecifics().get("ITYP_NAME"));
		if (index.getPartitioning() != null) {
			builder.space()._add("LOCAL");
			if (!index.getPartitioning().getPartitions().isEmpty()) {
				builder.space()._add("(");
				boolean first = true;
				for (Partition partition : index.getPartitioning().getPartitions()) {
					String status = partition.getSpecifics().get("oracle.index.status");
					if (status != null && !"USABLE".equals(status))
						throw new IllegalArgumentException(
								"Domain CREATE cannot preserve this partition status: " + status);
					builder.comma(!first)._add("PARTITION ").name(partition.getName());
					first = false;
					parameters(parameterValue(partition), builder);
				}
				builder._add(")");
			}
		}
		parameters(parameterValue(index), builder);
	}

	static void validatePartition(Partition partition) {
		if (partition.getName() == null || partition.getName().isBlank())
			throw new IllegalArgumentException("Domain partition name is required");
		if (!partition.getSubPartitions().isEmpty() || partition.isCompression()
				|| partition.getTableSpaceName() != null
				|| partition.getCompressionType() != null && !"DISABLED".equals(partition.getCompressionType()))
			throw new IllegalArgumentException(
					"Domain partition attributes must be expressed through its parameters: " + partition.getName());
		String operation = partition.getSpecifics().get("DOMIDX_OPSTATUS");
		if (operation != null && !"VALID".equals(operation))
			throw new IllegalArgumentException("Domain partition operation is not valid: " + operation);
		String status = partition.getSpecifics().get("oracle.index.status");
		if (status != null && !Set.of("USABLE", "UNUSABLE").contains(status))
			throw new IllegalArgumentException("Invalid Domain partition status: " + status);
	}

	static String parameterValue(AbstractSchemaObject<?> index) {
		String plain = index.getSpecifics().get("PARAMETERS");
		if (plain != null)
			return plain;
		String encoded = index.getSpecifics().get("oracle.index.domain.parametersBase64");
		if (encoded == null)
			return null;
		try {
			byte[] bytes = java.util.Base64.getDecoder().decode(encoded);
			return java.nio.charset.StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes))
					.toString();
		} catch (IllegalArgumentException | java.nio.charset.CharacterCodingException error) {
			throw new IllegalArgumentException("Invalid oracle.index.domain.parametersBase64: " + index.getName(),
					error);
		}
	}

	record PartitionChange(Partition target, String parameters, boolean parametersChanged, String state) {
	}

	private static Partitioning normalized(Partitioning info) {
		Partitioning copy = info.clone();
		for (Partition partition : copy.getPartitions()) {
			partition.getSpecifics().remove("PARAMETERS");
			partition.getSpecifics().remove("oracle.index.domain.parametersBase64");
			partition.getSpecifics().remove("oracle.index.status");
		}
		return copy;
	}

	static List<PartitionChange> partitionChanges(Index before, Index after, Dialect dialect) {
		Partitioning oldInfo = before.getPartitioning(), newInfo = after.getPartitioning();
		if (oldInfo == null || newInfo == null || !normalized(oldInfo).diff(normalized(newInfo)).toDifference()
				.getChangedProperties(dialect).isEmpty())
			throw new IllegalArgumentException(
					"Domain partition alteration requires unchanged structure and physical attributes");
		List<PartitionChange> result = new java.util.ArrayList<>();
		for (Partition target : newInfo.getPartitions()) {
			Partition original = oldInfo.getPartitions().get(target.getName());
			String parameters = parameterValue(target);
			boolean changed = !java.util.Objects.equals(parameterValue(original), parameters);
			if (changed && parameters == null)
				throw new IllegalArgumentException(
						"Domain partition parameter changes require an explicit target string");
			String oldStatus = original.getSpecifics().get("oracle.index.status"),
					status = target.getSpecifics().get("oracle.index.status");
			String state = null;
			if (!java.util.Objects.equals(oldStatus, status)) {
				if ("UNUSABLE".equals(status) && (oldStatus == null || "USABLE".equals(oldStatus)))
					state = "UNUSABLE";
				else if ("USABLE".equals(status) && "UNUSABLE".equals(oldStatus))
					state = "REBUILD";
				else
					throw new IllegalArgumentException(
							"Unsupported Domain partition status transition: " + oldStatus + " -> " + status);
			}
			if (changed || state != null)
				result.add(new PartitionChange(target, parameters, changed, state));
		}
		return result;
	}

	static void parameters(String value, OracleSqlBuilder builder) {
		if (value == null)
			return;
		if (value.length() > 1000)
			throw new IllegalArgumentException("Domain index parameter string exceeds 1000 characters");
		builder.space()._add("PARAMETERS ('")._add(value.replace("'", "''"))._add("')");
	}
}
