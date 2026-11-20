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

package com.sqlapp.data.db.dialect.jdbc.metadata;

import static com.sqlapp.util.CommonUtils.emptyToNull;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;
import static com.sqlapp.util.DbUtils.close;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnPair;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FlexList;
import com.sqlapp.util.TripleKeyMap;

/**
 * JDBC外部キー制約読み込み作成クラス
 * 
 * @author satoh
 * 
 */
public class JdbcForeignKeyConstraintReader extends ForeignKeyConstraintReader {

	public JdbcForeignKeyConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ForeignKeyConstraint> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		return JdbcMetadataUtils.readTables(connection, context,
				CommonUtils.coalesce(getCatalogName(context), getCatalogName()),
				CommonUtils.coalesce(getSchemaName(context), getSchemaName()), getTableName(context),
				tableContext -> readTable(connection, tableContext, productVersionInfo));
	}

	private List<ForeignKeyConstraint> readTable(Connection connection, ParametersContext context,
			ProductVersionInfo productVersionInfo) {
		ResultSet rs = null;
		List<ForeignKeyConstraint> list = list();
		try {
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			rs = databaseMetaData.getImportedKeys(
					CommonUtils.coalesce(this.getCatalogName(context), this.getCatalogName()),
					CommonUtils.coalesce(this.getSchemaName(context), this.getSchemaName()),
					emptyToNull(this.getTableName(context)));
			Map<Key, List<KeyColumn>> groups = new LinkedHashMap<>();
			while (rs.next()) {
				Key key = new Key(getString(rs, "FKTABLE_CAT"), getString(rs, "FKTABLE_SCHEM"),
						getString(rs, "FKTABLE_NAME"), getString(rs, "FK_NAME"), getString(rs, "PKTABLE_CAT"),
						getString(rs, "PKTABLE_SCHEM"), getString(rs, "PKTABLE_NAME"), getString(rs, "PK_NAME"),
						rs.getInt("UPDATE_RULE"), rs.getInt("DELETE_RULE"), rs.getInt("DEFERRABILITY"));
				ColumnPair pair = new ColumnPair();
				pair.refCatalogName = key.parentCatalog();
				pair.refSchemaName = key.parentSchema();
				pair.refTableName = key.parentTable();
				pair.refColumnName = getString(rs, "PKCOLUMN_NAME");
				pair.columnName = getString(rs, "FKCOLUMN_NAME");
				groups.computeIfAbsent(key, ignored -> list()).add(new KeyColumn(rs.getInt("KEY_SEQ"), pair));
			}
			for (var entry : groups.entrySet()) {
				Key key = entry.getKey();
				List<KeyColumn> columns = entry.getValue();
				if (key.name() == null && columns.stream().allMatch(column -> column.sequence() == 1)) {
					for (KeyColumn column : columns) {
						list.add(createConstraint(key, List.of(column)));
					}
				} else {
					columns.sort(Comparator.comparingInt(KeyColumn::sequence));
					for (int i = 0; i < columns.size(); i++) {
						if (columns.get(i).sequence() != i + 1) {
							throw new SQLException("Ambiguous or invalid JDBC foreign key metadata for " + key
									+ "; name the constraints or use a vendor metadata reader");
						}
					}
					list.add(createConstraint(key, columns));
				}
			}
			return list;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(rs);
		}
	}

	private record Key(String catalog, String schema, String table, String name, String parentCatalog,
			String parentSchema, String parentTable, String parentKey, int updateRule, int deleteRule,
			int deferrability) {
	}

	private record KeyColumn(int sequence, ColumnPair pair) {
	}

	private ForeignKeyConstraint createConstraint(Key key, List<KeyColumn> columns) {
		ForeignKeyConstraint constraint = new ForeignKeyConstraint(key.name());
		constraint.setCatalogName(key.catalog());
		constraint.setSchemaName(key.schema());
		constraint.setTableName(key.table());
		constraint.setUpdateRule(CascadeRule.parse(key.updateRule()));
		constraint.setDeleteRule(CascadeRule.parse(key.deleteRule()));
		constraint.setDeferrability(Deferrability.parse(key.deferrability()));
		FlexList<ColumnPair> pairs = new FlexList<>();
		for (KeyColumn column : columns) {
			pairs.add(column.pair());
		}
		TripleKeyMap<String, String, String, FlexList<ColumnPair>> map = tripleKeyMap();
		map.put(key.catalog(), key.schema(), key.name(), pairs);
		setForeignKeyConstraintColumns(map, List.of(constraint));
		if (key.name() == null) {
			// Keep anonymous constraints distinct in Schema collections and XML without
			// inventing SQL names.
			StringBuilder identity = new StringBuilder();
			for (String value : new String[] { key.catalog(), key.schema(), key.table(), key.parentCatalog(),
					key.parentSchema(), key.parentTable(), key.parentKey(), Integer.toString(key.updateRule()),
					Integer.toString(key.deleteRule()), Integer.toString(key.deferrability()) }) {
				appendIdentity(identity, value);
			}
			for (KeyColumn column : columns) {
				appendIdentity(identity, column.pair().columnName);
				appendIdentity(identity, column.pair().refColumnName);
			}
			constraint.setId("jdbc-fk-" + UUID.nameUUIDFromBytes(identity.toString().getBytes(StandardCharsets.UTF_8)));
		}
		return constraint;
	}

	private void appendIdentity(StringBuilder identity, String value) {
		identity.append(value == null ? -1 : value.length()).append(':');
		if (value != null) {
			identity.append(value);
		}
	}

}
