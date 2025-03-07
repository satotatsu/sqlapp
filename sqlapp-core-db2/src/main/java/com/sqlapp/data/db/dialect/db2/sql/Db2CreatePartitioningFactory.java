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

public class Db2CreatePartitioningFactory extends
		AbstractCreatePartitioningFactory<Db2SqlBuilder> {

	@Override
	public void addObjectDetail(Partitioning obj, Db2SqlBuilder builder) {
		if (obj == null) {
			return;
		}
		builder.lineBreak().partitionBy().space()._add(PartitioningType.Range);
		builder._add("(");
		builder.names(obj.getPartitioningColumns());
		builder._add(" )");
		appendPartitionDefinition(obj,
				obj.getPartitions(), builder);
	}

	protected void appendPartitionDefinition(Partitioning obj, PartitionCollection partitionCollection, Db2SqlBuilder builder) {
		if (partitionCollection.size() > 0) {
			builder.lineBreak()._add("(");
			builder.appendIndent(1);
			for (int i = 0; i < partitionCollection.size(); i++) {
				Partition partition = partitionCollection.get(i);
				builder.lineBreak().comma(i>0);
				appendPartitionDefinition(obj, partition, builder);
			}
			builder.appendIndent(-1);
			builder.lineBreak()._add(")");
		}
	}

	protected void appendPartitionDefinition(Partitioning partitionInfo, AbstractPartition<?> partition, Db2SqlBuilder builder) {
		builder.partition();
		builder.space().name(partition.getName()).space();
		appendPartitionDefinition(partitionInfo.getPartitioningType(), partition, builder);
	}

	protected void appendPartitionDefinition(PartitioningType partitioningType,
			AbstractPartition<?> partition, Db2SqlBuilder builder) {
		if (!CommonUtils.isEmpty(partition.getLowValue())) {
			builder.starting().from().space()._add("(")._add(partition.getLowValue())
					._add(")");
			if (!partition.isLowValueInclusive()){
				builder.inclusive();
			}
		}
		if (!CommonUtils.isEmpty(partition.getHighValue())) {
			builder.ending().at().space()._add("(")._add(partition.getHighValue())
					._add(")");
			if (partition.isHighValueInclusive()){
				builder.inclusive();
			}
		}
		if (!CommonUtils.isEmpty(partition.getTableSpaceName())) {
			builder.in().name(partition.getTableSpaceName());
		}
		if (!CommonUtils.isEmpty(partition.getIndexTableSpaceName())) {
			builder.index().in().name(partition.getIndexTableSpaceName());
		}
		if (!CommonUtils.isEmpty(partition.getLobTableSpaceName())) {
			builder._long().in().name(partition.getLobTableSpaceName());
		}
	}

}
