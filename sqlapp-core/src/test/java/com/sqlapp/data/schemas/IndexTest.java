/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.schemas;

import static com.sqlapp.util.CommonUtils.newTimestamp;

public class IndexTest extends AbstractDbObjectTest<Index>{

	@Override
	protected Index getObject() {
		final Index index=new Index("indexA");
		index.getColumns().add("colA", Order.Asc);
		index.getColumns().add("colB", Order.Desc);
		index.getIncludes().add("colC", Order.Desc);
		index.setRemarks("インデックスコメント");
		index.setUnique(true);
		index.setCompression(true);
		index.setTableSpaceName("tableSpaceA");
		index.setCreatedAt(newTimestamp());
		index.setWhere("colA!=1");
		// パーティショニング
		final Partitioning partitioning = new Partitioning();
		index.setPartitioning(partitioning);
		partitioning.getPartitioningColumns().add("colB");
		partitioning.getSubPartitioningColumns().add("colC");
		return index;
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<Index> getHandler() {
		return getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(final Index obj1, final Index obj2) {
		obj2.setName("b");
		obj2.getColumns().get(0).setOrder(Order.Desc);
		obj2.setRemarks("インデックスコメントb");
		final DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}

}
