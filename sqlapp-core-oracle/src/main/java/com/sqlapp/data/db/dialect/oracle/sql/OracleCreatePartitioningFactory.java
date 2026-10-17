/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreatePartitioningFactory;
import com.sqlapp.data.schemas.AbstractPartition;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.SubPartition;
import com.sqlapp.util.CommonUtils;

public class OracleCreatePartitioningFactory extends AbstractCreatePartitioningFactory<OracleSqlBuilder> {

	@Override
	public void addObjectDetail(Partitioning obj, OracleSqlBuilder builder) {
		if (obj == null) {
			return;
		}
		OraclePartitionVersions.validate(obj.getPartitioningType(), obj.getSubPartitioningType(), getDialect());
		if (obj.getPartitioningType() != PartitioningType.Range && obj.getPartitioningType() != PartitioningType.List && obj.getPartitioningType() != PartitioningType.Hash)
			throw new IllegalArgumentException("Oracle partition CREATE supports RANGE, LIST and HASH");
		if (obj.getPartitioningColumns().isEmpty() || obj.getSubPartitioningType() != null && obj.getSubPartitioningColumns().isEmpty())
			throw new IllegalArgumentException("Partition and subpartition keys must be specified");
		if (obj.getPartitioningType() != PartitioningType.Hash && obj.getPartitions().isEmpty())
			throw new IllegalArgumentException("RANGE/LIST partition CREATE requires explicit partitions");
		if (obj.getSubPartitionSize() != null && (obj.getSubPartitionSize() <= 0 || obj.getSubPartitioningType() != PartitioningType.Hash))
			throw new IllegalArgumentException("Subpartition count requires HASH subpartitioning and a positive value");
		builder.lineBreak().partitionBy().space()._add(obj.getPartitioningType());
		builder._add("(");
		builder.names(obj.getPartitioningColumns());
		builder._add(" )");
		if (obj.getPartitioningType().isSizePartitioning() && obj.getPartitions().isEmpty()) {
			if (obj.getPartitionSize() <= 0)
				throw new IllegalArgumentException(
						"Hash partitioning requires a positive partition count or explicit partitions");
			builder.space().partitions().space()._add(obj.getPartitionSize());
		}
		if (obj.getSubPartitioningType() != null) {
			builder.lineBreak().subpartitionBy().space()._add(obj.getSubPartitioningType());
			builder._add("(");
			builder.names(obj.getSubPartitioningColumns());
			builder._add(" )");
			if (obj.getSubPartitionSize() != null) builder._add(" SUBPARTITIONS ")._add(obj.getSubPartitionSize());
		}
		appendPartitionDefinition(false, obj, obj.getPartitions(), builder);
	}

	protected void appendPartitionDefinition(boolean subpartition, Partitioning obj,
			List<? extends AbstractPartition<?>> partitionCollection, OracleSqlBuilder builder) {
		if (partitionCollection.size() > 0) {
			builder.lineBreak()._add("(");
			builder.appendIndent(1);
			for (int i = 0; i < partitionCollection.size(); i++) {
				AbstractPartition<?> partition = partitionCollection.get(i);
				builder.lineBreak().comma(i > 0);
				appendPartitionDefinition(subpartition, obj, partition, builder);
			}
			builder.appendIndent(-1);
			builder.lineBreak()._add(")");
		}
	}

	protected void appendPartitionDefinition(boolean subpartition, Partitioning partitionInfo,
			AbstractPartition<?> partition, OracleSqlBuilder builder) {
		builder.subpartition(subpartition);
		builder.partition(!subpartition);
		builder.space().name(partition.getName()).space();
		if (subpartition) {
			appendPartitionDefinition(partitionInfo.getSubPartitioningType(), partition, builder);
		} else {
			appendPartitionDefinition(partitionInfo.getPartitioningType(), partition, builder);
		}
		if (partitionInfo.getIndex() != null) {
			if (subpartition)
				throw new IllegalArgumentException("Global composite index partitioning is not supported");
			OracleIndexPartitionCompression.add(partitionInfo.getIndex(), partition, getDialect(), builder);
			OracleIndexPartitionLifecycle.addCreateState(partition, false, builder);
		}
		if (partitionInfo.getSubPartitioningType() == null || subpartition) {
			return;
		}
		if (partition.toPartition().getSubPartitions().isEmpty()) return;
		builder.lineBreak()._add("(");
		builder.appendIndent(1);
		if (partition instanceof Partition) {
			for (int j = 0; j < partition.toPartition().getSubPartitions().size(); j++) {
				SubPartition subPartition = partition.toPartition().getSubPartitions().get(j);
				builder.lineBreak().comma(j > 0);
				appendPartitionDefinition(true, partitionInfo, subPartition, builder);
			}
		}
		builder.appendIndent(-1);
		builder.lineBreak()._add(")");
	}

	protected void appendPartitionDefinition(PartitioningType partitioningType, AbstractPartition<?> partition,
			OracleSqlBuilder builder) {
		if (partitioningType != PartitioningType.Range && partitioningType != PartitioningType.List && partitioningType != PartitioningType.Hash)
			throw new IllegalArgumentException("Unsupported Oracle partition type: " + partitioningType);
		if (partitioningType != PartitioningType.Hash && (partition.getHighValue() == null || partition.getHighValue().isBlank()))
			throw new IllegalArgumentException("Partition boundary is required: " + partition.getName());
		if (partitioningType == PartitioningType.Hash && partition.getHighValue() != null)
			throw new IllegalArgumentException("HASH partition cannot specify a boundary: " + partition.getName());
		if ((partition.isCompression() || partition.getCompressionType() != null) && (partition.getPartitioning() == null || partition.getPartitioning().getIndex() == null))
			throw new IllegalArgumentException("Partition compression requires a dedicated CREATE representation: " + partition.getName());
		if (partitioningType == PartitioningType.Range || partitioningType == PartitioningType.RangeColumns) {
			builder.values().lessThan().space()._add("(")._add(partition.getHighValue())._add(")");
		} else if (partitioningType == PartitioningType.List) {
			builder.values().space()._add("(")._add(partition.getHighValue())._add(")");
		}
		if (!CommonUtils.isEmpty(partition.getTableSpaceName())) {
			builder.tablespace().space().name(partition.getTableSpaceName());
		}
		if (partition.getPartitioning() == null || partition.getPartitioning().getIndex() == null)
			addTablePartitionAttributes(partitioningType, partition, builder);
	}
	private void addTablePartitionAttributes(PartitioningType type, AbstractPartition<?> partition, OracleSqlBuilder builder) {
		if (!CommonUtils.isEmpty(partition.getRemarks())) throw new IllegalArgumentException("Oracle does not support inline partition comments: " + partition.getName());
		java.util.Set<String> physical = java.util.Set.of("PCT_FREE", "PCT_USED", "INI_TRANS", "MAX_TRANS", "LOGGING");
		java.util.Set<String> storage = java.util.Set.of("INITIAL_EXTENT", "NEXT_EXTENT", "MIN_EXTENTS", "MAX_EXTENTS", "PCT_INCREASE", "FREELISTS", "FREELIST_GROUPS", "BUFFER_POOL");
		for (var entry : partition.getSpecifics().entrySet()) {
			if (entry.getValue() == null) continue;
			String key = entry.getKey(), value = entry.getValue();
			if (!physical.contains(key) && !storage.contains(key)) throw new IllegalArgumentException("Unsupported Oracle partition attribute: " + key);
			if (type == PartitioningType.Hash || partition instanceof SubPartition) throw new IllegalArgumentException("HASH/subpartition physical attributes must be inherited: " + key);
			if ("LOGGING".equals(key)) {
				if (!java.util.Set.of("YES", "NO", "TRUE", "FALSE").contains(value.toUpperCase(java.util.Locale.ROOT))) throw new IllegalArgumentException("Invalid partition LOGGING: " + value);
			} else if ("BUFFER_POOL".equals(key)) {
				if (!java.util.Set.of("DEFAULT", "KEEP", "RECYCLE").contains(value)) throw new IllegalArgumentException("Invalid partition BUFFER_POOL: " + value);
			} else if (!("MAX_EXTENTS".equals(key) && "UNLIMITED".equals(value)) && !value.matches("[0-9]+")) throw new IllegalArgumentException("Invalid numeric partition attribute: " + key);
		}
		for (var entry : partition.getSpecifics().entrySet()) if (entry.getValue() != null && physical.contains(entry.getKey())) builder.space().oracleProperty(entry.getKey(), entry.getValue());
		boolean first = true;
		for (var entry : partition.getSpecifics().entrySet()) if (entry.getValue() != null && storage.contains(entry.getKey())) {
			if (first) builder._add(" STORAGE ("); else builder.space();
			first = false; builder.oracleProperty(entry.getKey(), entry.getValue());
		}
		if (!first) builder._add(")");
	}

}
