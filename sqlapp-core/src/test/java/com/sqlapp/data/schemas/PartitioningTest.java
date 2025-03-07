/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.schemas;

public class PartitioningTest extends AbstractDbObjectTest<Partitioning> {

	private Partitioning getPartitionInfo(PartitioningType partitioningType,
			PartitioningType subPartitioningType, String... columnNames) {
		Partitioning partitionInfo = new Partitioning(partitioningType);
		partitionInfo.setSubPartitioningType(subPartitioningType);
		for (int i = 0; i < 3; i++) {
			partitionInfo.getPartitions().add(
					getPartitionWithSub("patition" + i, i % 2 == 0, "highValue"
							+ i));
		}
		partitionInfo.getPartitioningColumns().add(columnNames);
		for (int i = 0; i < 5; i++) {
			partitionInfo.getSubPartitioningColumns().add("subCol" + i);
		}
		return partitionInfo;
	}

	private Partition getPartitionWithSub(String name, boolean compression,
			String highValue) {
		Partition partition = new Partition();
		partition.setCompression(compression);
		partition.setHighValue(highValue);
		// partition.setCreated(newTimestamp());
		partition.setName(name);
		partition.setTableSpaceName("tableSpace1");
		for (int i = 0; i < 3; i++) {
			SubPartition subPartition = getSubPartition("sub" + name + i,
					compression, highValue + i);
			partition.getSubPartitions().add(subPartition);
		}
		return partition;
	}

	private SubPartition getSubPartition(String name, boolean compression,
			String highValue) {
		SubPartition partition = new SubPartition();
		partition.setCompression(compression);
		partition.setHighValue(highValue);
		// partition.setCreated(newTimestamp());
		partition.setName(name);
		return partition;
	}

	@Override
	protected Partitioning getObject() {
		return getPartitionInfo(PartitioningType.Range, PartitioningType.List,
				"colA", "colB");
	}

	@Override
	protected PartitioningHandler getHandler() {
		return new PartitioningHandler();
	}

	@Override
	protected void testDiffString(Partitioning obj1, Partitioning obj2) {
		obj2.setSubPartitioningType(PartitioningType.Range);
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
