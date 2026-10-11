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

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.sql.SqlType;

/**
 * INDEX生成クラス
 * 
 * @author satoh
 * 
 */
public class OracleCreateIndexFactory extends AbstractCreateIndexFactory<OracleSqlBuilder> {

	@Override
	public void addObjectDetail(Index index, Table table, OracleSqlBuilder builder) {
		builder.unique(index.isUnique());
		if ((index.getIndexType() != null && index.getIndexType().isBitMap())
				|| "true".equalsIgnoreCase(index.getSpecifics().get("oracle.index.bitmap"))) builder.space()._add("BITMAP");
		builder.index().ifNotExists(table != null && this.getOptions().isCreateIfNotExists()).space().name(index, false).on();
		builder.name(table, this.getOptions().isDecorateSchemaName()
				|| (index.getSchemaName() != null && !index.getSchemaName().equals(table.getSchemaName())));
		builder.space()._add("(");
		boolean firstColumn = true;
		for (var column : index.getColumns()) {
			builder.comma(!firstColumn);
			addColumn(column, builder);
			firstColumn = false;
		}
		builder.space()._add(")");
		if ("LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality"))) {
			builder.space()._add("LOCAL");
			if (index.getPartitioning() != null && !index.getPartitioning().getPartitions().isEmpty()) {
				builder.space()._add("(");
				boolean first = true;
				for (var partition : index.getPartitioning().getPartitions()) {
					builder.comma(!first)._add("PARTITION").space().name(partition.getName());
					first = false;
					addStorage(partition, builder);
					if (!partition.getSubPartitions().isEmpty()) {
						builder.space()._add("(");
						boolean firstSub = true;
						for (var sub : partition.getSubPartitions()) {
							builder.comma(!firstSub)._add("SUBPARTITION").space().name(sub.getName());
							firstSub = false;
							addStorage(sub, builder);
						}
						builder._add(")");
					}
				}
				builder._add(")");
			}
		} else if (index.getPartitioning() != null) {
			builder.space()._add("GLOBAL");
			var detail = this.getAddObjectDetail(index.getPartitioning(), SqlType.CREATE);
			if (detail != null) detail.addObjectDetail(index.getPartitioning(), builder);
		}
		if (index.getTableSpaceName() != null) builder.space()._add("TABLESPACE").space().name(index.getTableSpaceName());
		if (index.isCompression()) builder.space()._add("COMPRESS");
	}
	private void addStorage(com.sqlapp.data.schemas.AbstractPartition<?> partition, OracleSqlBuilder builder) {
		if (partition.getTableSpaceName() != null) builder.space()._add("TABLESPACE").space().name(partition.getTableSpaceName());
		if (partition.isCompression()) builder.space()._add("COMPRESS");
	}
}
