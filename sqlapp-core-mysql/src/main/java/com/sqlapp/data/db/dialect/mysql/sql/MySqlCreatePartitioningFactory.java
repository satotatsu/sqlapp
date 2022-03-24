/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.mysql.sql;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreatePartitioningFactory;
import com.sqlapp.data.schemas.AbstractPartition;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.PartitionCollection;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.SubPartition;
import com.sqlapp.util.CommonUtils;

public class MySqlCreatePartitioningFactory extends
		AbstractCreatePartitioningFactory<MySqlSqlBuilder> {

	@Override
	public void addObjectDetail(Partitioning obj, MySqlSqlBuilder builder) {
		if (obj == null) {
			return;
		}
		builder.lineBreak().partitionBy().space()._add(obj.getPartitioningType());
		builder._add("(");
		builder.names(obj.getPartitioningColumns());
		builder._add(" )");
		if (obj.getPartitioningType().isSizePartitioning()) {
			builder.space().partitions().space()
					._add(obj.getPartitionSize());
		}
		if (obj.getSubPartitioningType() != null) {
			builder.lineBreak().subpartitionBy().space()
					._add(obj.getSubPartitioningType());
			builder._add("(");
			builder.names(obj.getSubPartitioningColumns());
			builder._add(" )");
		}
		appendPartitionDefinition(false, obj,
				obj.getPartitions(), builder);
	}

	protected void appendPartitionDefinition(boolean subpartition,
			Partitioning obj, PartitionCollection partitionCollection, MySqlSqlBuilder builder) {
		if (partitionCollection.size() > 0) {
			builder.lineBreak()._add("(");
			builder.appendIndent(1);
			for (int i = 0; i < partitionCollection.size(); i++) {
				Partition partition = partitionCollection.get(i);
				builder.lineBreak().comma(i>0);
				appendPartitionDefinition(subpartition, obj, partition, builder);
			}
			builder.appendIndent(-1);
			builder.lineBreak()._add(")");
		}
	}

	protected void appendPartitionDefinition(boolean subpartition,
			Partitioning partitionInfo, AbstractPartition<?> partition, MySqlSqlBuilder builder) {
		builder.subpartition(subpartition);
		builder.partition(!subpartition);
		builder.space().name(partition.getName()).space();
		if (subpartition) {
			appendPartitionDefinition(partitionInfo.getSubPartitioningType(), partition, builder);
		} else {
			appendPartitionDefinition(partitionInfo.getPartitioningType(), partition, builder);
		}
		if (partitionInfo.getSubPartitioningType() == null || subpartition) {
			return;
		}
		builder.lineBreak()._add("(");
		builder.appendIndent(1);
		if (partition instanceof Partition){
			for (int j = 0; j < ((Partition)partition).getSubPartitions().size(); j++) {
				SubPartition subPartition = ((Partition)partition).getSubPartitions().get(j);
				builder.lineBreak().comma(j>0);
				appendPartitionDefinition(true, partitionInfo, subPartition, builder);
			}
		}
		builder.appendIndent(-1);
		builder.lineBreak()._add(")");
	}

	protected void appendPartitionDefinition(PartitioningType partitioningType,
			AbstractPartition<?> partition, MySqlSqlBuilder builder) {
		if (partitioningType == PartitioningType.Range||partitioningType == PartitioningType.RangeColumns) {
			if ("MAXVALUE".equalsIgnoreCase(partition.getHighValue())) {
				builder.values().lessThan().space()._add(partition.getHighValue());
			} else {
				builder.values().lessThan().space()._add("(")
						._add(partition.getHighValue())._add(")");
			}
		} else if (partitioningType == PartitioningType.List) {
			builder.values().in()._add("(")._add(partition.getHighValue())
					._add(")");
		}
		if (!CommonUtils.isEmpty(partition.getTableSpaceName())) {
			builder.tablespace().eq().name(partition.getTableSpaceName());
		}
		if (!CommonUtils.isEmpty(partition.getRemarks())) {
			builder.comment().eq().sqlChar(partition.getRemarks());
		}
	}

}
