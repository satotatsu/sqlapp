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
import java.util.Map;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * Oracleのalter tableコマンド
 * 
 * @author tatsuo satoh
 * 
 */
public class OracleCreateTableFactory extends AbstractCreateTableFactory<OracleSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(final Table table) {
		String definition = com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition.get(table);
		if (definition == null)
			return super.createSql(table);
		List<SqlOperation> result = new java.util.ArrayList<>();
		OracleSqlBuilder builder = createSqlBuilder();
		builder._add(definition);
		addSql(result, builder, SqlType.CREATE, table);
		addIndexDefinitions(table, result);
		// CREATE TABLE DDL already contains modeled annotations and constraints.
		addComments(table, result);
		return result;
	}

	@Override
	protected void addCreateObject(final Table table, OracleSqlBuilder builder) {
		builder.create();
		if (table.getTableType() == Table.TableType.Temporary)
			builder.space()._add("GLOBAL TEMPORARY");
		builder.table().name(table, getOptions().isDecorateSchemaName());
	}

	@Override
	protected void addOption(final Table table, OracleSqlBuilder builder) {
		addTableOptions(table, builder);
		if (table.getLobTableSpaceName() != null) {
			if (table.getTableType() == Table.TableType.Temporary)
				throw new IllegalArgumentException(
						"Temporary table cannot specify a permanent LOB tablespace: " + table.getName());
			var lobs = table.getColumns().stream()
					.filter(column -> column.getDataType() == com.sqlapp.data.db.datatype.DataType.BLOB
							|| column.getDataType() == com.sqlapp.data.db.datatype.DataType.CLOB
							|| column.getDataType() == com.sqlapp.data.db.datatype.DataType.NCLOB)
					.toList();
			if (lobs.isEmpty())
				throw new IllegalArgumentException(
						"LOB tablespace requires a BLOB/CLOB/NCLOB column: " + table.getName());
			builder.lineBreak()._add("LOB (");
			for (int i = 0; i < lobs.size(); i++)
				builder.comma(i > 0).name(lobs.get(i));
			builder._add(") STORE AS (TABLESPACE ").name(table.getLobTableSpaceName())._add(")");
		}
		OracleIotOptions.addOverflow(table, builder);
	}

	private void addTableOptions(final Table table, OracleSqlBuilder builder) {
		OracleIotOptions.validate(table);
		String organization = table.getSpecifics().get("oracle.table.organization");
		if (organization != null) {
			if (!"INDEX".equals(organization))
				throw new IllegalArgumentException("Unsupported Oracle table organization: " + table.getName());
			if (table.getSpecifics().containsKey("oracle.table.iotComplex") || table.getPartitioning() != null
					|| table.isCompression() || table.getTableType() == Table.TableType.Temporary)
				throw new IllegalArgumentException(
						"IOT overflow/mapping, partitioning, compression and temporary variants require additional structural support: "
								+ table.getName());
			if (table.getConstraints().getPrimaryKeyConstraint() == null)
				throw new IllegalArgumentException("Index-organized table requires a primary key: " + table.getName());
			var primary = table.getConstraints().getPrimaryKeyConstraint();
			if (!primary.isEnable() || primary.getDeferrability() != null
					&& primary.getDeferrability() != com.sqlapp.data.schemas.Deferrability.NotDeferrable)
				throw new IllegalArgumentException(
						"IOT requires an enabled, nondeferrable primary key: " + table.getName());
			builder.lineBreak()._add("ORGANIZATION INDEX");
			OracleIotOptions.add(table, builder);
		}
		String duration = table.getSpecifics().get("oracle.table.duration");
		if (table.getTableType() == Table.TableType.Temporary) {
			if (duration != null && !"SYS$SESSION".equals(duration) && !"SYS$TRANSACTION".equals(duration))
				throw new IllegalArgumentException("Invalid temporary table duration: " + table.getName());
			if (table.getTableSpaceName() != null || table.isCompression())
				throw new IllegalArgumentException(
						"Temporary table cannot specify permanent tablespace or compression: " + table.getName());
			builder.lineBreak()._add("ON COMMIT ")
					._add("SYS$SESSION".equals(duration) ? "PRESERVE ROWS" : "DELETE ROWS");
		} else {
			if (duration != null)
				throw new IllegalArgumentException("Duration requires a temporary table: " + table.getName());
			if (table.getTableSpaceName() != null)
				builder.lineBreak()._add("TABLESPACE ").name(table.getTableSpaceName());
		}
		String compressFor = table.getSpecifics().get("COMPRESS_FOR");
		if (table.isCompression()) {
			if (compressFor == null || "BASIC".equals(compressFor))
				builder.lineBreak()._add("COMPRESS");
			else if (java.util.List.of("OLTP", "QUERY LOW", "QUERY HIGH", "ARCHIVE LOW", "ARCHIVE HIGH")
					.contains(compressFor))
				builder.lineBreak()._add("COMPRESS FOR ")._add(compressFor);
			else if ("ADVANCED".equals(compressFor))
				builder.lineBreak()._add("ROW STORE COMPRESS ADVANCED");
			else
				throw new IllegalArgumentException("Unsupported table compression mode: " + table.getName());
		} else if (compressFor != null) {
			throw new IllegalArgumentException("COMPRESS_FOR requires compression: " + table.getName());
		}
		Map<String, String> map = table.getSpecifics();
		if (CommonUtils.isEmpty(map)) {
			return;
		}
		boolean hasProperty = false;
		for (Map.Entry<String, String> entry : map.entrySet()) {
			if (isModeledProperty(entry.getKey())) {
				continue;
			}
			if (entry.getValue() != null) {
				hasProperty = true;
			}
		}
		if (!hasProperty) {
			return;
		}
		boolean hasStorage = false;
		for (Map.Entry<String, String> entry : map.entrySet()) {
			if (isModeledProperty(entry.getKey())) {
				continue;
			}
			if (entry.getValue() == null) {
				continue;
			}
			if (builder.isStoragePropertyName(entry.getKey())) {
				hasStorage = true;
				continue;
			}
			builder.lineBreak().oracleProperty(entry.getKey(), entry.getValue());
		}
		if (hasStorage) {
			builder.lineBreak().storage();
			builder.lineBreak()._add("(");
			builder.appendIndent(1);
			for (Map.Entry<String, String> entry : map.entrySet()) {
				if (isModeledProperty(entry.getKey())) {
					continue;
				}
				if (entry.getValue() == null) {
					continue;
				}
				if (!builder.isStoragePropertyName(entry.getKey())) {
					continue;
				}
				builder.lineBreak().oracleProperty(entry.getKey(), entry.getValue());
			}
			builder.appendIndent(-1);
			builder.lineBreak()._add(")");
		}
	}

	private boolean isModeledProperty(String key) {
		return OracleAnnotationUtils.isAnnotationKey(key) || "oracle.table.duration".equals(key)
				|| "COMPRESS_FOR".equals(key) || OracleIotOptions.modeled(key)
				|| "oracle.table.organization".equals(key) || "oracle.table.iotComplex".equals(key);
	}

	@Override
	protected void addOtherDefinitions(Table table, List<SqlOperation> result) {
		addComments(table, result);
	}

	private void addComments(Table table, List<SqlOperation> result) {
		if (table.getRemarks() != null) {
			OracleSqlBuilder builder = this.createSqlBuilder();
			builder.comment().on().table().space().name(table, this.getOptions().isDecorateSchemaName()).is()
					.sqlChar(table.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, table);
		}
		table.getColumns().stream().filter(c -> c.getRemarks() != null).forEach(c -> {
			OracleSqlBuilder builder = this.createSqlBuilder();
			builder.comment().on().column().space().columnName(c, true, this.getOptions().isDecorateSchemaName()).is()
					.sqlChar(c.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, c);
		});
		table.getIndexes().stream().filter(c -> c.getRemarks() != null).forEach(c -> {
			OracleSqlBuilder builder = this.createSqlBuilder();
			builder.comment().on().index().space().name(c, this.getOptions().isDecorateSchemaName()).is()
					.sqlChar(c.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, c);
		});
		table.getConstraints().stream().filter(c -> c.getRemarks() != null).forEach(c -> {
			OracleSqlBuilder builder = this.createSqlBuilder();
			builder.comment().on().constraint().space().name(c, this.getOptions().isDecorateSchemaName()).on()
					.name(table, this.getOptions().isDecorateSchemaName()).is().sqlChar(c.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, c);
		});
	}
}
