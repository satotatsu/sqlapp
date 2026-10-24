/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreatePartitioningFactory;
import com.sqlapp.data.schemas.AbstractPartition;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.PartitionCollection;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.util.CommonUtils;

public class Db2CreatePartitioningFactory extends AbstractCreatePartitioningFactory<Db2SqlBuilder> {

	@Override
	public void addObjectDetail(Partitioning obj, Db2SqlBuilder builder) {
		if (obj == null) {
			return;
		}
		if (!(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_910))
			throw new IllegalArgumentException("Db2 range partitioning requires LUW 9.1 or later");
		if (obj.getPartitioningType() != PartitioningType.Range || obj.getSubPartitioningType() != null
				|| !obj.getSubPartitioningColumns().isEmpty())
			throw new IllegalArgumentException(
					"Db2 native range generation requires RANGE without subpartitioning; use a complete table definition for other organizations");
		if (obj.getPartitioningColumns().isEmpty() || obj.getPartitions().isEmpty())
			throw new IllegalArgumentException("Db2 range partitioning requires key columns and explicit partitions");
		builder.lineBreak().partitionBy().space()._add(PartitioningType.Range);
		builder._add("(");
		int ordinal = 0;
		for (var column : obj.getPartitioningColumns()) {
			if (column.isIncludedColumn() || column.getOrder() == com.sqlapp.data.schemas.Order.Desc)
				throw new IllegalArgumentException("Db2 range partition key cannot use INCLUDE or DESC");
			builder.comma(ordinal++ > 0);
			if (column.getStatement().isEmpty())
				builder.name(column);
			else {
				if (column.getStatement().size() != 1 || column.getStatement().getFirst() == null
						|| column.getStatement().getFirst().isBlank())
					throw new IllegalArgumentException("Db2 partition key requires one SQL expression");
				builder._add(column.getStatement().getFirst());
			}
			if (column.getNullsOrder() != null)
				builder.space()._add(column.getNullsOrder().getSqlValue());
		}
		builder._add(" )");
		appendPartitionDefinition(obj, obj.getPartitions(), builder);
	}

	protected void appendPartitionDefinition(Partitioning obj, PartitionCollection partitionCollection,
			Db2SqlBuilder builder) {
		if (partitionCollection.size() > 0) {
			builder.lineBreak()._add("(");
			builder.appendIndent(1);
			for (int i = 0; i < partitionCollection.size(); i++) {
				Partition partition = partitionCollection.get(i);
				builder.lineBreak().comma(i > 0);
				appendPartitionDefinition(obj, partition, builder);
			}
			builder.appendIndent(-1);
			builder.lineBreak()._add(")");
		}
	}

	protected void appendPartitionDefinition(Partitioning partitionInfo, AbstractPartition<?> partition,
			Db2SqlBuilder builder) {
		builder.partition();
		if (partition instanceof Partition p && !p.getSubPartitions().isEmpty())
			throw new IllegalArgumentException("Db2 range partitions do not support modeled subpartitions");
		if (CommonUtils.isEmpty(partition.getLowValue()) || CommonUtils.isEmpty(partition.getHighValue()))
			throw new IllegalArgumentException(
					"Db2 range partition recreation requires both explicit bounds; use MINVALUE/MAXVALUE for unbounded ranges");
		builder.space().name(partition.getName()).space();
		appendPartitionDefinition(partitionInfo.getPartitioningType(), partition, builder);
	}

	protected void appendPartitionDefinition(PartitioningType partitioningType, AbstractPartition<?> partition,
			Db2SqlBuilder builder) {
		if (!CommonUtils.isEmpty(partition.getLowValue())) {
			builder.starting().from().space()._add("(")._add(partition.getLowValue())._add(")");
			if (!partition.isLowValueInclusive()) {
				if (!hasInfiniteBound(partition.getLowValue()))
					builder.space()._add("EXCLUSIVE");
			}
		}
		if (!CommonUtils.isEmpty(partition.getHighValue())) {
			builder.ending().at().space()._add("(")._add(partition.getHighValue())._add(")");
			if (!partition.isHighValueInclusive()) {
				if (!hasInfiniteBound(partition.getHighValue()))
					builder.space()._add("EXCLUSIVE");
			}
		}
		if (!CommonUtils.isEmpty(partition.getTableSpaceName())) {
			builder.in().name(partition.getTableSpaceName());
		}
		if (!CommonUtils.isEmpty(partition.getIndexTableSpaceName())) {
			if (!(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_970))
				throw new IllegalArgumentException("Db2 per-partition INDEX IN requires LUW 9.7 or later");
			builder.index().in().name(partition.getIndexTableSpaceName());
		}
		if (!CommonUtils.isEmpty(partition.getLobTableSpaceName())) {
			builder._long().in().name(partition.getLobTableSpaceName());
		}
	}

	private boolean hasInfiniteBound(String sql) {
		boolean sentinel = java.util.regex.Pattern.compile("(?i)(?:^|,)\\s*(?:MINVALUE|MAXVALUE)\\s*(?:,|$)")
				.matcher(sql).find();
		if (sentinel && !sql.matches("(?i)\\s*(?:MINVALUE|MAXVALUE)(?:\\s*,\\s*(?:MINVALUE|MAXVALUE))*\\s*"))
			throw new IllegalArgumentException(
					"Db2 mixed finite/infinite exclusive bounds require a complete table definition");
		return sentinel;
	}

}
