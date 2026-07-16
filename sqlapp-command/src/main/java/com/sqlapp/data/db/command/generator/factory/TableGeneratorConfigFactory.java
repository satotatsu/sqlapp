/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.generator.factory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.commons.io.FileUtils;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Workbook;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.dataconfig.ConfigFileType;
import com.sqlapp.data.db.command.generator.GeneratorConfigWorkbook;
import com.sqlapp.data.db.command.generator.config.ColumnGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.FileGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.QueryGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.strategy.ValueSelectStrategy;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.function.ColumnFunction;
import com.sqlapp.data.schemas.rowiterator.DataFormat;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.JsonConverter;

import lombok.Getter;
import lombok.Setter;

/**
 * TableDataGeneratorConfig Factory
 */
@Getter
@Setter
public class TableGeneratorConfigFactory {

	private ColumnFunction<String> columnMinValue = new ColumnMinValue();

	private ColumnFunction<String> columnNextValue = new ColumnNextValue();

	private ColumnFunction<String> columnCurrentValue = new ColumnCurrentValue();

	private ColumnFunction<String> columnMaxValue = new ColumnMaxValue();

	private ColumnFunction<String> columnFormat = new ColumnFormat();

	private Function<Table, Integer> rowAmplificationFactor = t -> 100;

	private BiFunction<Column, Dialect, String> columnStartValue = new ColumnStartValue();

	private boolean withSchemaName = false;

	/**
	 * テーブルの値からTableDataGeneratorConfigを作成します
	 * 
	 * @param table        テーブル
	 * @param dialect      DB Dialect
	 * @param tableOptions TableOptions
	 * @param sqlType      sqlType
	 * @return TableDataGeneratorConfig
	 */
	public TableGeneratorConfig createDefault(final Table table, final Dialect dialect, TableOptions tableOptions,
			SqlType sqlType) {
		TableGeneratorConfig config = new TableGeneratorConfig();
		setQueryDefaultValue(table, dialect, config);
		setFileDefaultValue(table, dialect, config);
		setQueryRelations(table, dialect, config);
		setTableDefaultValues(table, dialect, config);
		String sql = createInsertSql(table, dialect, tableOptions, sqlType);
		config.setInsertSql(sql);
		setColumnDefaultValues(table, dialect, config);
		setColumnRelationGroup(table, dialect, config);
		return config;
	}

	/**
	 * テーブルの値からTableDataGeneratorConfigを作成します
	 * 
	 * @param table   テーブル
	 * @param dialect DB Dialect
	 * @return TableDataGeneratorConfig
	 */
	public TableGeneratorConfig createDefault(final Table table, final Dialect dialect) {
		return createDefault(table, dialect, null, SqlType.INSERT);
	}

	public TableGeneratorConfig fromFile(File file) {
		if (!file.exists() || file.isDirectory()) {
			return null;
		}
		final ConfigFileType enm = ConfigFileType.parse(file);
		if (enm == null) {
			return null;
		}
		final DataFormat workbookFileType = enm.getWorkbookFileType();
		if (workbookFileType.isWorkbook()) {
			try (Workbook wb = workbookFileType.createWorkBook(file, true)) {
				final TableGeneratorConfig config = GeneratorConfigWorkbook.readWorkbook(wb);
				config.setFile(file);
				config.setFileType(enm);
				return config;
			} catch (EncryptedDocumentException | InvalidFormatException | IOException e) {
				throw new RuntimeException(e);
			}
		} else if (workbookFileType.isJson() || workbookFileType.isYaml() || workbookFileType.isToml()) {
			try {
				final String text = FileUtils.readFileToString(file, Charset.forName("UTF8"));
				final JsonConverter jsonConverter = workbookFileType.createJsonConverter();
				TableGeneratorConfig config = jsonConverter.fromJsonString(text, TableGeneratorConfig.class);
				config.check();
				config.setFile(file);
				config.setFileType(enm);
				return config;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	protected void setTableDefaultValues(Table table, Dialect dialect, TableGeneratorConfig config) {
		config.setSchemaName(table.getSchemaName());
		config.setName(table.getName());
		ForeignKeyConstraint fk = getPKFK(table);
		if (fk != null) {
			config.setDataSourceExpression("iterator(1)");
		} else {
			Integer val = rowAmplificationFactor.apply(table);
			if (val != null) {
				config.setDataSourceExpression("iterator(" + val + ")");
			} else {
				config.setDataSourceExpression("iterator(100)");
			}
		}
		config.setColumnMappingExpression("[\"_index\":value]");
		;
		config.setStartValueSql(getStartValueQuerySql(table, dialect));
		final AbstractSqlBuilder<?> sqlBuilder = createSqlBuilder(dialect);
		sqlBuilder.select();
		sqlBuilder.lineBreak().count()._add("(*)");
		sqlBuilder.lineBreak().from().name(table);
		String selectCountSql = sqlBuilder.toString();
		config.setStartCountSql(selectCountSql);
		config.setFinishCountSql(selectCountSql);
		boolean hasIdentity = table.getColumns().stream().filter(c -> c.isIdentity()).findAny().isPresent();
		if (hasIdentity) {
			List<SqlOperation> ops = dialect.createSqlFactoryRegistry().createSql(table, SqlType.IDENTITY_ON);
			if (!ops.isEmpty()) {
				config.setInitializeSql("--" + ops.get(0).toString());
				ops = dialect.createSqlFactoryRegistry().createSql(table, SqlType.IDENTITY_OFF);
				config.setFinalizeSql("--" + ops.get(0).toString());
			}
		}
	}

	/**
	 * テーブルに対応した指定したSqlTypeのSQLを生成します。
	 * 
	 * @param table        Table
	 * @param dialect      Dialect
	 * @param tableOptions TableOptions
	 * @param sqlType      SqlType
	 * @return テーブルに対応した指定したSqlTypeのSQL
	 */
	public String createInsertSql(final Table table, final Dialect dialect, TableOptions tableOptions,
			SqlType sqlType) {
		ForeignKeyConstraint pkfk = getPKFK(table);
		SqlFactoryRegistry sqlFactoryRegistry = dialect.createSqlFactoryRegistry();
		if (tableOptions != null) {
			sqlFactoryRegistry.setTableOptions(tableOptions.clone());
		}
		sqlFactoryRegistry.getOptions().setDecorateSchemaName(false);
		boolean hasMulti = hasMultiForeignKeyInPrimaryKeyColumn(table);
		SqlFactory<Table> factory;
		if (pkfk == null && hasMulti && sqlType == SqlType.INSERT) {
			factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.INSERT_SELECT_NOT_EXISTS);
		} else {
			if (sqlType == SqlType.INSERT && hasUniqueKey(table)) {
				factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.INSERT_SELECT_NOT_EXISTS);
			} else {
				factory = sqlFactoryRegistry.getSqlFactory(table, sqlType);
			}
		}
		final List<SqlOperation> operations = factory.createSql(table);
		String sql = dialect.toTextFromSqlOperation(operations);
		return sql;
	}

	private boolean hasUniqueKey(final Table table) {
		if (table.getConstraints().getUniqueConstraints().size() > 1) {
			return true;
		}
		for (Index index : table.getIndexes()) {
			if (table.getConstraints().contains(index.getName())) {
				continue;
			}
			if (index.isUnique()) {
				return true;
			}
		}
		return false;
	}

	private boolean hasMultiForeignKeyInPrimaryKeyColumn(final Table table) {
		final List<ForeignKeyConstraint> fks = CommonUtils.list();
		for (ForeignKeyConstraint fk : table.getConstraints().getForeignKeyConstraints()) {
			if (fk.getRelatedTable() == table) {
				continue;
			}
			Column[] cols = fk.getColumns();
			if (table.isAllPimaryKeyColumn(cols)) {
				fks.add(fk);
			}
		}
		return fks.size() > 1;
	}

	protected void setColumnDefaultValues(Table table, Dialect dialect, TableGeneratorConfig config) {
		ForeignKeyConstraint pkfk = getPKFK(table);
		for (final Column column : table.getColumns()) {
			ColumnGeneratorConfig colConfig = new ColumnGeneratorConfig();
			colConfig.setName(column.getName());
			colConfig.setDataType(column.getDataType());
			colConfig.setPrimaryKeyAndForeignKeyColumn(isPKFKColumn(table, pkfk, column));
			if (pkfk != null && isPKFKColumn(table, pkfk, column)) {
				setColumnDefaultForForeignKey(table, dialect, column, config, colConfig);
			} else {
				setColumnDefaultForNormal(table, dialect, column, config, colConfig);
			}
			config.addColumn(colConfig);
		}
	}

	protected void setColumnDefaultForForeignKey(Table table, Dialect dialect, final Column column,
			TableGeneratorConfig config, ColumnGeneratorConfig colConfig) {
		colConfig.setMinValue(null);
		colConfig.setMaxValue(null);
		colConfig.setNextValue(this.getColumnCurrentValue().apply(column));
		colConfig.setValues(null);
	}

	protected void setColumnDefaultForNormal(Table table, Dialect dialect, final Column column,
			TableGeneratorConfig config, ColumnGeneratorConfig colConfig) {
		Object val = this.getColumnMinValue().apply(column);
		colConfig.setMinValue(val != null ? "" + val : null);
		val = this.getColumnMaxValue().apply(column);
		colConfig.setMaxValue(val != null ? "" + val : null);
		colConfig.setNextValue(this.getColumnNextValue().apply(column));
		colConfig.setFormatExpression(this.getColumnFormat().apply(column));
		if (!CommonUtils.isEmpty(column.getValues())) {
			List<Object> vals = CommonUtils.list();
			vals.addAll(column.getValues());
			colConfig.setValues(vals);
		}
	}

	protected void setColumnRelationGroup(Table table, Dialect dialect, TableGeneratorConfig config) {
		for (final Map.Entry<String, QueryGeneratorConfig> entry : config.getQueries().entrySet()) {
			final QueryGeneratorConfig queryGeneratorConfig = entry.getValue();
			if (CommonUtils.isEmpty(queryGeneratorConfig.getRelationColumns())) {
				continue;
			}
			for (Column column : queryGeneratorConfig.getRelationColumns()) {
				final ColumnGeneratorConfig columnGeneratorConfig = config.getColumns().get(column.getName());
				setColumnDefaultForForeignKey(table, dialect, column, config, columnGeneratorConfig);
				columnGeneratorConfig.setLookupGroup(entry.getKey());
			}
		}
	}

	protected void setQueryDefaultValue(Table table, Dialect dialect, TableGeneratorConfig config) {
		QueryGeneratorConfig query = new QueryGeneratorConfig();
		query.setGenerationGroup("Group1");
		query.setSelectSql(getSampleQuerySql(table, dialect));
		config.addQueryDefinition(query);
	}

	protected void setQueryRelations(Table table, Dialect dialect, TableGeneratorConfig config) {
		final List<ForeignKeyConstraint> fks = table.getConstraints().getForeignKeyConstraints();
		if (fks.isEmpty()) {
			return;
		}
		for (ForeignKeyConstraint fk : fks) {
			if (matchPK(table, fk)) {
				continue;
			}
			final QueryGeneratorConfig query = new QueryGeneratorConfig();
			query.setGenerationGroup(fk.getName());
			query.setSelectSql(getRelationQuerySql(table, fk, dialect));
			query.setRelationColumns(fk.getColumns());
			query.setColumnMappingExpression(createColumnMappingExpression(fk));
			query.setSelectionStrategy(ValueSelectStrategy.RANDOM);
			config.addQueryDefinition(query);
		}
	}

	private String createColumnMappingExpression(ForeignKeyConstraint fk) {
		final StringBuilder builder = new StringBuilder();
		builder.append("[");
		int columnCount = 0;
		int nameMatchCount = 0;
		for (int i = 0; i < fk.getColumns().length; i++) {
			Column column = fk.getColumns()[i];
			ReferenceColumn rCol = fk.getRelatedColumns().get(i);
			if (CommonUtils.eqIgnoreCase(column.getName(), rCol.getName())) {
				nameMatchCount++;
			} else {
				if (columnCount == 0) {
					builder.append("\n\t");
				} else {
					builder.append("\n\t, ");
				}
				builder.append("[").append("\"").append(column.getName()).append("\":");
				builder.append(rCol.getName()).append("]");
				columnCount++;
			}
		}
		builder.append("\n]");
		if (nameMatchCount == fk.getColumns().length) {
			return null;
		}
		return builder.toString();
	}

	protected void setFileDefaultValue(Table table, Dialect dialect, TableGeneratorConfig config) {
		final FileGeneratorConfig obj = new FileGeneratorConfig();
		obj.setLookupGroup("FileGroup1");
		String expression = """
				[
					["code":"GBR", "name":"United Kingdom"]
					, ["code":"FRA", "name":"France"]
					, ["code":"ESP", "name":"Spain"]
					, ["code":"DEU", "name":"Germany"]
					, ["code":"USA", "name":"United States of America"]
					, ["code":"JPN", "name":"JAPAN"]
				]""";
		obj.setDataSourceExpression(expression);
		String mapping = """
				[
					"col_a":code
					, "col_b":name
					, "col_c":code + "_" + name
				]""";
		obj.setColumnMappingExpression(mapping);
		obj.setSelectionStrategy(ValueSelectStrategy.RANDOM);
		String weight = """
				code==\"USA\"?100:1""";
		obj.setSelectionStrategyWeightExpression(weight);
		config.addFileDefinition(obj);
	}

	public static ForeignKeyConstraint getPKFK(Table table) {
		final List<ForeignKeyConstraint> fks = table.getConstraints().getForeignKeyConstraints();
		for (ForeignKeyConstraint fk : fks) {
			if (matchPK(table, fk)) {
				return fk;
			}
		}
		return null;
	}

	public static ForeignKeyConstraint getUKFK(Table table) {
		final List<ForeignKeyConstraint> fks = table.getConstraints().getForeignKeyConstraints();
		for (ForeignKeyConstraint fk : fks) {
			final List<UniqueConstraint> uks = table.getConstraints().getUniqueConstraints(uk -> !uk.isPrimaryKey());
			for (UniqueConstraint uk : uks) {
				if (matchUK(table, fk, uk)) {
					return fk;
				}
			}
		}
		return null;
	}

	private static boolean matchPK(Table table, ForeignKeyConstraint fk) {
		return matchUK(table, fk, table.getPrimaryKeyConstraint());
	}

	private static boolean matchUK(Table table, ForeignKeyConstraint fk, UniqueConstraint uk) {
		int i = 0;
		for (Column column : fk.getColumns()) {
			if (uk.getColumns().contains(column.getName())) {
				i++;
			}
		}
		return uk.getColumns().size() == i;
	}

	public static boolean isPKFKColumn(Table table, ForeignKeyConstraint fk, Column column) {
		if (fk == null) {
			return false;
		}
		boolean match = false;
		for (Column col : fk.getColumns()) {
			if (CommonUtils.eq(col.getName(), column.getName())) {
				match = true;
				break;
			}
		}
		if (!match) {
			return false;
		}
		return table.isPimaryKeyColumn(column);
	}

	protected String getRelationQuerySql(Table table, ForeignKeyConstraint fk, Dialect dialect) {
		final AbstractSqlBuilder<?> sqlBuilder = dialect.createSqlBuilder();
		sqlBuilder.select();
		sqlBuilder.appendIndent(+1);
		Table relatedTable = fk.getRelatedTable();
		for (int i = 0; i < fk.getRelatedColumns().size(); i++) {
			ReferenceColumn refCol = fk.getRelatedColumns().get(i);
//			Column column = fk.getColumns()[i];
			sqlBuilder.lineBreak();
			sqlBuilder.comma(i > 0);
			sqlBuilder.name(refCol.getName());
//			if (!CommonUtils.eq(refCol.getName(), column.getName())) {
//				sqlBuilder.as().space();
//				sqlBuilder.name(column.getName());
//			}
			i++;
		}
		sqlBuilder.appendIndent(-1);
		sqlBuilder.lineBreak();
		sqlBuilder.from();
		sqlBuilder.name(relatedTable);
		return sqlBuilder.toString();

	}

	protected String getSampleQuerySql(Table table, Dialect dialect) {
		int i = 0;
		final AbstractSqlBuilder<?> sqlBuilder = dialect.createSqlBuilder();
		sqlBuilder.select();
		sqlBuilder.appendIndent(+1);
		for (Column column : table.getColumns()) {
			Object val = column.getDataType().getDefaultValue();
			if (val == null) {
				continue;
			}
			sqlBuilder.lineBreak();
			sqlBuilder.comma(i > 0);
			if ("".equals(val)) {
				sqlBuilder._add("''");
			} else if (column.getDataType().isNumeric()) {
				sqlBuilder._add(Converters.getDefault().convertString(val));
			} else {
				sqlBuilder._add("'" + Converters.getDefault().convertString(val) + "'");
			}
			sqlBuilder.as().space();
			sqlBuilder.name(column.getName());
			i++;
		}
		sqlBuilder.appendIndent(-1);
		if (dialect.getSelectDummyTableName() != null) {
			sqlBuilder.lineBreak();
			sqlBuilder.fromSysDummy();
		}
		return sqlBuilder.toString();
	}

	protected AbstractSqlBuilder<?> createSqlBuilder(Dialect dialect) {
		final AbstractSqlBuilder<?> sqlBuilder = dialect.createSqlBuilder();
		sqlBuilder.setWithSchemaName(withSchemaName);
		return sqlBuilder;
	}

	protected String getStartValueQuerySql(Table table, Dialect dialect) {
		int i = 0;
		ForeignKeyConstraint fk = getPKFK(table);
		final AbstractSqlBuilder<?> sqlBuilder = createSqlBuilder(dialect);
		sqlBuilder.select();
		sqlBuilder.appendIndent(+1);
		if (fk == null) {
			for (Column column : table.getColumns()) {
				String exp = columnStartValue.apply(column, dialect);
				if (exp == null) {
					continue;
				}
				sqlBuilder.lineBreak();
				sqlBuilder.comma(i > 0);
				sqlBuilder._add(exp);
				i++;
			}
			if (i == 0) {
				final AbstractSqlBuilder<?> tmpBuilder = createSqlBuilder(dialect);
				tmpBuilder.select()._add(" 1");
				tmpBuilder.lineBreak();
				tmpBuilder.fromSysDummy();
				return tmpBuilder.toString();
			}
			sqlBuilder.appendIndent(-1);
			sqlBuilder.lineBreak();
			sqlBuilder.from();
			sqlBuilder.name(table);
		} else {
			List<String> colList = CommonUtils.list();
			Set<String> colNames = CommonUtils.set();
			for (int j = 0; j < fk.getColumns().length; j++) {
				Column column = fk.getColumns()[j];
				ReferenceColumn refColumn = fk.getRelatedColumns().get(j);
				sqlBuilder.lineBreak();
				sqlBuilder.comma(j > 0);
				sqlBuilder.name("a.", refColumn);
				colNames.add(column.getName());
				if (!CommonUtils.eq(refColumn.getName(), column.getName())) {
					sqlBuilder.as();
					sqlBuilder.name(column);
				}
			}
			for (String exp : colList) {
				sqlBuilder.lineBreak();
				sqlBuilder.comma();
				sqlBuilder._add(exp);
			}
			sqlBuilder.appendIndent(-1);
			sqlBuilder.lineBreak();
			sqlBuilder.from();
			sqlBuilder.name(fk.getRelatedTable());
			sqlBuilder._add(" a");
			sqlBuilder.lineBreak();
			sqlBuilder.where().not().exists();
			sqlBuilder.lineBreak();
			sqlBuilder.brackets(true, () -> {
				sqlBuilder.select()._add(" 1");
				sqlBuilder.lineBreak();
				sqlBuilder.from();
				sqlBuilder.name(table);
				sqlBuilder._add(" b");
				sqlBuilder.lineBreak();
				sqlBuilder.where();
				for (int j = 0; j < fk.getColumns().length; j++) {
					sqlBuilder.lineBreak();
					sqlBuilder.and(j > 0);
					Column pkCol = fk.getColumns()[j];
					sqlBuilder.name("b.", pkCol);
					sqlBuilder.eq();
					ReferenceColumn refColumn = fk.getRelatedColumns().get(j);
					sqlBuilder.name("a.", refColumn);
				}
			});
		}
		return sqlBuilder.toString();
	}

	public File writeFile(File dir, Locale locale, TableGeneratorConfig config)
			throws FileNotFoundException, IOException {
		switch (config.getFileType()) {
		case JSON:
		case TOML:
		case YAML:
			return writeTextFile(config, dir);
		default:
			return writeFileWorkbook(config, locale, dir);
		}
	}

	private File writeTextFile(TableGeneratorConfig config, File dir) throws IOException {
		JsonConverter jsonConverter = config.getFileType().getWorkbookFileType().createJsonConverter();
		jsonConverter.setIndentOutput(true);
		String text = jsonConverter.toJsonString(config);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		File file = new File(dir,
				config.getName() + "." + config.getFileType().getWorkbookFileType().getFileExtension());
		FileUtils.write(file, text, Charset.forName("UTF-8"));
		return file;
	}

	private File writeFileWorkbook(TableGeneratorConfig config, Locale locale, File dir) throws IOException {
		try (Workbook wb = config.getFileType().getWorkbookFileType().createWorkbook()) {
			GeneratorConfigWorkbook.Table.writeSheet(config, locale, wb);
			GeneratorConfigWorkbook.Column.writeSheet(config, locale, wb);
			GeneratorConfigWorkbook.Query.writeSheet(config, locale, wb);
			GeneratorConfigWorkbook.File.writeSheet(config, locale, wb);
			if (!dir.exists()) {
				dir.mkdirs();
			}
			File file = new File(dir, config.getName() + ".xlsx");
			try (FileOutputStream os = new FileOutputStream(file);
					BufferedOutputStream bs = new BufferedOutputStream(os)) {
				wb.write(bs);
				bs.flush();
			}
			return file;
		}
	}
}
