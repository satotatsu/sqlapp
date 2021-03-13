/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.metadata.SequenceReader;
import com.sqlapp.data.db.metadata.SynonymReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.parameter.ParametersContextBuilder;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.SqlComparisonOperator;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;

public abstract class AbstractSchemaDataSourceCommand extends AbstractDataSourceCommand {


	private SqlFactoryRegistry sqlFactoryRegistry;

	/**
	 * @return the sqlFactoryRegistry
	 */
	public SqlFactoryRegistry getSqlFactoryRegistry(final Dialect dialect) {
		if (sqlFactoryRegistry != null) {
			return sqlFactoryRegistry;
		}
		if (dialect != null) {
			this.sqlFactoryRegistry = dialect.createSqlFactoryRegistry();
		}
		return sqlFactoryRegistry;
	}

	/**
	 * @param sqlFactoryRegistry
	 *            the sqlFactoryRegistry to set
	 */
	public void setSqlFactoryRegistry(final SqlFactoryRegistry sqlFactoryRegistry) {
		this.sqlFactoryRegistry = sqlFactoryRegistry;
	}

	protected Map<String, Schema> getSchemas(final Connection connection, final Dialect dialect, final SchemaReader schemaReader, final Predicate<Schema> schemaNameFilter){
		final Catalog catalog=new Catalog();
		final Map<String, Schema> schemaMap=CommonUtils.linkedMap();
		final List<Schema> schemas=schemaReader.getAll(connection);
		final Map<String, Schema> allSchemaMap=CommonUtils.linkedMap();
		final Set<String> catalogNames=CommonUtils.treeSet();
		final Set<String> schemaNames=CommonUtils.treeSet();
		if (!schemas.isEmpty()){
			final Schema schema=schemas.get(0);
			copyDBInfo(schema, catalog);
		}
		schemas.forEach(s->{
			catalog.getSchemas().add(s);
			allSchemaMap.put(s.getName(), s);
			if (schemaNameFilter.test(s)){
				schemaMap.put(s.getName(), s);
				if (s.getCatalogName()!=null){
					catalogNames.add(s.getCatalogName());
				}
				if (s.getName()!=null){
					schemaNames.add(s.getName());
				}
			}
		});
		final DoubleKeyMap<String,String,Table> tableMap=CommonUtils.doubleKeyMap();
		final ParametersContext context=ParametersContextBuilder.create()
				.catalogName(SqlComparisonOperator.IN, catalogNames)
				.schemaName(SqlComparisonOperator.IN, schemaNames)
				.build();
		final TableReader tableReader=schemaReader.getTableReader();
		final List<Table> tables=tableReader.getAllFull(connection, context);
		tables.forEach(s->{
			final Schema schema=allSchemaMap.get(s.getSchemaName());
			if (schema!=null){
				schema.getTables().add(s);
			}
		});
		final SequenceReader sequenceReader=schemaReader.getSequenceReader();
		if (sequenceReader!=null){
			final List<Sequence> sequences=sequenceReader.getAllFull(connection, context);
			sequences.forEach(s->{
				final Schema schema=allSchemaMap.get(s.getSchemaName());
				if (schema!=null){
					schema.getSequences().add(s);
				}
			});
		}
		final SynonymReader synonymReader=schemaReader.getSynonymReader();
		if (synonymReader!=null){
			synonymReader.setCatalogName(null);
			synonymReader.setSchemaName(null);
			final List<Synonym> synonyms=synonymReader.getAllFull(connection);
			if (!synonyms.isEmpty()){
				final DoubleKeyMap<String,String,Synonym> rootSynonymMap=CommonUtils.doubleKeyMap();
				synonyms.forEach(s->{
					final Schema schema=allSchemaMap.get(s.getSchemaName());
					if (schema!=null){
						schema.getSynonyms().add(s);
					}
				});
				synonyms.forEach(s->{
					final Synonym root=s.rootSynonym();
					rootSynonymMap.put(root.getSchemaName(), root.getName(), root);
				});
				readSynonymTables(connection, tableReader, allSchemaMap, rootSynonymMap, tableMap);
			}
		}
		return schemaMap;
	}

	
	private void copyDBInfo(final Schema schema, final Catalog catalog){
		catalog.setProductName(schema.getProductName());
		catalog.setProductMajorVersion(schema.getProductMajorVersion());
		catalog.setProductMinorVersion(schema.getProductMinorVersion());
		catalog.setProductRevision(schema.getProductRevision());
		catalog.setDialect(schema.getDialect());
	}
	
	private void readSynonymTables(final Connection connection, final TableReader tableReader, final Map<String, Schema> allSchemaMap, final DoubleKeyMap<String,String,Synonym> rootSynonymMap, final DoubleKeyMap<String,String,Table> tableMap){
		final Set<String> schemaNames=CommonUtils.treeSet();
		final Set<String> tableNames=CommonUtils.treeSet();
		rootSynonymMap.toList().forEach(s->{
			if (!tableMap.containsKey(s.getObjectSchemaName(), s.getObjectName())){
				if (s.getObjectSchemaName()!=null){
					schemaNames.add(s.getObjectSchemaName());
				}
				if (s.getObjectName()!=null){
					tableNames.add(s.getObjectName());
				}
			}
		});
		if (CommonUtils.isEmpty(schemaNames)){
			return;
		}
		
		final ParametersContext context=ParametersContextBuilder.create()
			.schemaName(SqlComparisonOperator.IN, schemaNames)
			.tableName(SqlComparisonOperator.IN, tableNames).build();
		final List<Table> tables=tableReader.getAllFull(connection, context);
		tables.forEach(t->{
			final Schema schema=allSchemaMap.get(t.getSchemaName());
			if (!schema.getTables().contains(t.getName())){
				schema.getTables().add(t);
			}
		});
	}
}
