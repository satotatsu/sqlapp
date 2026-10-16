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
				|| "true".equalsIgnoreCase(index.getSpecifics().get("oracle.index.bitmap")))
			builder.space()._add("BITMAP");
		builder.index().ifNotExists(table != null && this.getOptions().isCreateIfNotExists()).space().name(index, this.getOptions().isDecorateSchemaName())
				.on();
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
					OracleIndexPartitionCompression.add(index, partition, getDialect(), builder);
					OracleIndexPartitionLifecycle.addCreateState(partition,
							index.getPartitioning().getSubPartitioningType() != null || !partition.getSubPartitions().isEmpty(), builder);
					if (!partition.getSubPartitions().isEmpty()) {
						builder.space()._add("(");
						boolean firstSub = true;
						for (var sub : partition.getSubPartitions()) {
							OracleIndexPartitionCompression.validateSubpartition(index, partition, sub);
							builder.comma(!firstSub)._add("SUBPARTITION").space().name(sub.getName());
							firstSub = false;
							addStorage(sub, builder);
							OracleIndexPartitionLifecycle.addCreateState(sub, false, builder);
						}
						builder._add(")");
					}
				}
				builder._add(")");
			}
		} else if (index.getPartitioning() != null) {
			if (index.getPartitioning().getSubPartitioningType() != null)
				throw new IllegalArgumentException("Global composite index partitioning is not supported: " + index.getName());
			builder.space()._add("GLOBAL");
			var detail = this.getAddObjectDetail(index.getPartitioning(), SqlType.CREATE);
			if (detail != null)
				detail.addObjectDetail(index.getPartitioning(), builder);
		}
		if (index.getTableSpaceName() != null)
			builder.space()._add("TABLESPACE").space().name(index.getTableSpaceName());
		if (index.isCompression()) {
			if (index.getIndexType() != null && index.getIndexType().isBitMap() || "true".equalsIgnoreCase(index.getSpecifics().get("oracle.index.bitmap"))) throw new IllegalArgumentException("Bitmap indexes do not support these compression clauses: " + index.getName());
			String mode = index.getCompressionType();
			if (mode == null || "ENABLED".equals(mode)) {
				builder.space()._add("COMPRESS");
				String prefix = index.getSpecifics().get("oracle.index.prefixLength");
				if (prefix != null) {
					int count;
					try { count = Integer.parseInt(prefix); } catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid index compression prefix: " + index.getName(), error); }
					if (count < 1 || count > index.getColumns().size() || index.isUnique() && count == index.getColumns().size()) throw new IllegalArgumentException("Invalid index compression prefix: " + index.getName());
					builder.space()._add(count);
				}
			} else if ("ADVANCED LOW".equals(mode) || "ADVANCED HIGH".equals(mode)) {
				if (!(getDialect() instanceof com.sqlapp.data.db.dialect.oracle.Oracle12c) || "ADVANCED HIGH".equals(mode) && !(getDialect() instanceof com.sqlapp.data.db.dialect.oracle.Oracle18c)) throw new IllegalArgumentException("Advanced index compression is not supported by this dialect version: " + index.getName());
				if (index.getSpecifics().containsKey("oracle.index.prefixLength")) throw new IllegalArgumentException("Advanced compression cannot specify a prefix length: " + index.getName());
				builder.space()._add("COMPRESS ")._add(mode);
			} else throw new IllegalArgumentException("Unsupported index compression type: " + index.getName());
		} else if (index.getCompressionType() != null || index.getSpecifics().containsKey("oracle.index.prefixLength")) {
			throw new IllegalArgumentException("Index compression options require compression=true: " + index.getName());
		}
		String visibility = index.getSpecifics().get("oracle.index.visibility");
		if (visibility != null) {
			if (!(getDialect() instanceof com.sqlapp.data.db.dialect.oracle.Oracle11g)
					&& !(getDialect() instanceof com.sqlapp.data.db.dialect.oracle.Oracle11gR2))
				throw new IllegalArgumentException("Index visibility requires Oracle 11g or later: " + index.getName());
			if (!"VISIBLE".equals(visibility) && !"INVISIBLE".equals(visibility))
				throw new IllegalArgumentException("Invalid index visibility: " + index.getName());
			builder.space()._add(visibility);
		}
		String status = index.getSpecifics().get("oracle.index.status");
		if ("UNUSABLE".equals(status)) {
			if (index.getPartitioning() != null)
				throw new IllegalArgumentException("Partitioned index status requires per-partition state: " + index.getName());
			builder.space()._add("UNUSABLE");
		} else if (status != null && !"VALID".equals(status)
				&& !("N/A".equals(status) && index.getPartitioning() != null)) {
			throw new IllegalArgumentException("Unsupported index status: " + index.getName());
		}
	}

	private void addStorage(com.sqlapp.data.schemas.AbstractPartition<?> partition, OracleSqlBuilder builder) {
		if (partition.getTableSpaceName() != null)
			builder.space()._add("TABLESPACE").space().name(partition.getTableSpaceName());
	}
}
