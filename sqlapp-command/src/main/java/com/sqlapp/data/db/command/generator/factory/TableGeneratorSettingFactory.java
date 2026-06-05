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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.commons.io.FileUtils;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Workbook;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.generator.GeneratorSettingFileType;
import com.sqlapp.data.db.command.generator.GeneratorSettingWorkbook;
import com.sqlapp.data.db.command.generator.setting.ColumnGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.QueryGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.TableGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.strategy.ValueSelectStrategy;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.function.ColumnFunction;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.JsonConverter;

import lombok.Getter;
import lombok.Setter;

/**
 * TableDataGeneratorSetting Factory
 */
@Getter
@Setter
public class TableGeneratorSettingFactory {

	private ColumnFunction<String> columnMinValue = new ColumnMinValue();

	private ColumnFunction<String> columnNextValue = new ColumnNextValue();

	private ColumnFunction<String> columnCurrentValue = new ColumnCurrentValue();

	private ColumnFunction<String> columnMaxValue = new ColumnMaxValue();

	private BiFunction<Column, Dialect, String> columnStartValue = new ColumnStartValue();

	private boolean withSchemaName = false;

	/**
	 * テーブルの値からTableDataGeneratorSettingを作成します
	 * 
	 * @param table        テーブル
	 * @param dialect      DB Dialect
	 * @param tableOptions TableOptions
	 * @param sqlType      sqlType
	 * @return TableDataGeneratorSetting
	 */
	public TableGeneratorSetting createDefault(final Table table, final Dialect dialect, TableOptions tableOptions,
			SqlType sqlType) {
		TableGeneratorSetting setting = new TableGeneratorSetting();
		setQueryDefaultValue(table, dialect, setting);
		setQueryRelations(table, dialect, setting);
		setTableDefaultValues(table, dialect, setting);
		String sql = createInsertSql(table, dialect, tableOptions, sqlType);
		setting.setInsertSql(sql);
		setColumnDefaultValues(table, dialect, setting);
		setColumnRelationGroup(table, dialect, setting);
		return setting;
	}

	/**
	 * テーブルの値からTableDataGeneratorSettingを作成します
	 * 
	 * @param table   テーブル
	 * @param dialect DB Dialect
	 * @return TableDataGeneratorSetting
	 */
	public TableGeneratorSetting createDefault(final Table table, final Dialect dialect) {
		return createDefault(table, dialect, null, SqlType.INSERT);
	}

	public TableGeneratorSetting fromFile(File file) {
		if (!file.exists() || file.isDirectory()) {
			return null;
		}
		final GeneratorSettingFileType enm = GeneratorSettingFileType.parse(file);
		if (enm == null) {
			return null;
		}
		final WorkbookFileType workbookFileType = enm.getWorkbookFileType();
		if (workbookFileType.isWorkbook()) {
			try (Workbook wb = workbookFileType.createWorkBook(file, true)) {
				final TableGeneratorSetting setting = GeneratorSettingWorkbook.readWorkbook(wb);
				setting.setParentDirectory(file.getParentFile());
				return setting;
			} catch (EncryptedDocumentException | InvalidFormatException | IOException e) {
				throw new RuntimeException(e);
			}
		} else if (workbookFileType.isJson() || workbookFileType.isYaml() || workbookFileType.isToml()) {
			try {
				final String text = FileUtils.readFileToString(file, Charset.forName("UTF8"));
				final JsonConverter jsonConverter = workbookFileType.createJsonConverter();
				TableGeneratorSetting setting = jsonConverter.fromJsonString(text, TableGeneratorSetting.class);
				setting.setParentDirectory(file.getParentFile());
				return setting;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	protected void setTableDefaultValues(Table table, Dialect dialect, TableGeneratorSetting setting) {
		setting.setName(table.getName());
		ForeignKeyConstraint fk = getPKFK(table);
		if (fk != null) {
			setting.setNumberOfRows(1);
		} else {
			setting.setNumberOfRows(100);
		}
		setting.setStartValueSql(getStartValueQuerySql(table, dialect));
		final AbstractSqlBuilder<?> sqlBuilder = createSqlBuilder(dialect);
		String selectCountSql = sqlBuilder.select().count()._add("(*)").from().name(table).toString();
		boolean hasIdentity = table.getColumns().stream().filter(c -> c.isIdentity()).findAny().isPresent();
		if (hasIdentity) {
			List<SqlOperation> ops = dialect.createSqlFactoryRegistry().createSql(table, SqlType.IDENTITY_ON);
			if (ops.isEmpty()) {
				setting.setSetupSql(selectCountSql);
				setting.setFinalizeSql(selectCountSql);
			} else {
				StringBuilder builder = new StringBuilder();
				builder.append(selectCountSql);
				builder.append(";\n");
				builder.append("--");
				builder.append(ops.get(0).toString());
				builder.append(";");
				setting.setSetupSql(builder.toString());
				ops = dialect.createSqlFactoryRegistry().createSql(table, SqlType.IDENTITY_OFF);
				builder = new StringBuilder();
				builder.append(selectCountSql);
				builder.append(";\n");
				builder.append("--");
				builder.append(ops.get(0).toString());
				builder.append(";");
				setting.setFinalizeSql(builder.toString());
			}
		} else {
			setting.setSetupSql(selectCountSql);
			setting.setFinalizeSql(selectCountSql);
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
			sqlFactoryRegistry.getOption().setTableOptions(tableOptions.clone());
		}
		sqlFactoryRegistry.getOption().setDecorateSchemaName(false);
		boolean hasMulti = hasMultiForeignKeyInPrimaryKeyColumn(table);
		final SqlFactory<Table> factory;
		if (pkfk == null && hasMulti && sqlType == SqlType.INSERT) {
			factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.INSERT_SELECT_BY_PK);
		} else {
			factory = sqlFactoryRegistry.getSqlFactory(table, sqlType);
		}
		final List<SqlOperation> operations = factory.createSql(table);
		String sql = dialect.toTextFromSqlOperation(operations);
		return sql;
	}

	private boolean hasMultiForeignKeyInPrimaryKeyColumn(final Table table) {
		List<Column> columns = CommonUtils.list();
		for (ReferenceColumn ref : table.getPrimaryKeyConstraint().getColumns()) {
			final Column column = table.getColumns().get(ref.getName());
			columns.add(column);
		}
		List<ForeignKeyConstraint> fks = CommonUtils.list();
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

	protected void setColumnDefaultValues(Table table, Dialect dialect, TableGeneratorSetting setting) {
		ForeignKeyConstraint pkfk = getPKFK(table);
		for (final Column column : table.getColumns()) {
			ColumnGeneratorSetting colSetting = new ColumnGeneratorSetting();
			colSetting.setName(column.getName());
			colSetting.setDataType(column.getDataType());
			colSetting.setPrimaryKeyAndForeignKeyColumn(isPKFKColumn(table, pkfk, column));
			if (pkfk != null && isPKFKColumn(table, pkfk, column)) {
				setColumnDefaultForForeignKey(table, dialect, column, setting, colSetting);
			} else {
				setColumnDefaultForNormal(table, dialect, column, setting, colSetting);
			}
			setting.addColumn(colSetting);
		}
	}

	protected void setColumnDefaultForForeignKey(Table table, Dialect dialect, final Column column,
			TableGeneratorSetting setting, ColumnGeneratorSetting colSetting) {
		colSetting.setMinValue(null);
		colSetting.setMaxValue(null);
		colSetting.setNextValue(this.getColumnCurrentValue().apply(column));
		colSetting.setValues(null);
	}

	protected void setColumnDefaultForNormal(Table table, Dialect dialect, final Column column,
			TableGeneratorSetting setting, ColumnGeneratorSetting colSetting) {
		Object val = this.getColumnMinValue().apply(column);
		colSetting.setMinValue(val != null ? "" + val : null);
		val = this.getColumnMaxValue().apply(column);
		colSetting.setMaxValue(val != null ? "" + val : null);
		colSetting.setNextValue(this.getColumnNextValue().apply(column));
		if (!CommonUtils.isEmpty(column.getValues())) {
			List<Object> vals = CommonUtils.list();
			vals.addAll(column.getValues());
			colSetting.setValues(vals);
		}
	}

	protected void setColumnRelationGroup(Table table, Dialect dialect, TableGeneratorSetting setting) {
		for (final Map.Entry<String, QueryGeneratorSetting> entry : setting.getQuerys().entrySet()) {
			final QueryGeneratorSetting queryGeneratorSetting = entry.getValue();
			if (CommonUtils.isEmpty(queryGeneratorSetting.getRelationColumns())) {
				continue;
			}
			for (Column column : queryGeneratorSetting.getRelationColumns()) {
				final ColumnGeneratorSetting columnGeneratorSetting = setting.getColumns().get(column.getName());
				setColumnDefaultForForeignKey(table, dialect, column, setting, columnGeneratorSetting);
				columnGeneratorSetting.setGenerationGroup(entry.getKey());
			}
		}
	}

	protected void setQueryDefaultValue(Table table, Dialect dialect, TableGeneratorSetting setting) {
		QueryGeneratorSetting query = new QueryGeneratorSetting();
		query.setGenerationGroup("Group1");
		query.setSelectSql(getSampleQuerySql(table, dialect));
		setting.addQueryDefinition(query);
	}

	protected void setQueryRelations(Table table, Dialect dialect, TableGeneratorSetting setting) {
		List<ForeignKeyConstraint> fks = table.getConstraints().getForeignKeyConstraints();
		if (fks.isEmpty()) {
			return;
		}
		for (ForeignKeyConstraint fk : fks) {
			if (matchPK(table, fk)) {
				continue;
			}
			final QueryGeneratorSetting query = new QueryGeneratorSetting();
			query.setGenerationGroup(fk.getName());
			query.setSelectSql(getRelationQuerySql(table, fk, dialect));
			query.setRelationColumns(fk.getColumns());
			query.setSelectionStrategy(ValueSelectStrategy.RANDOM);
			setting.addQueryDefinition(query);
		}
	}

	public static ForeignKeyConstraint getPKFK(Table table) {
		List<ForeignKeyConstraint> fks = table.getConstraints().getForeignKeyConstraints();
		for (ForeignKeyConstraint fk : fks) {
			if (matchPK(table, fk)) {
				return fk;
			}
		}
		return null;
	}

	private static boolean matchPK(Table table, ForeignKeyConstraint fk) {
		int i = 0;
		for (Column column : fk.getColumns()) {
			if (table.isPimaryKeyColumn(column)) {
				i++;
			}
		}
		return table.getPrimaryKeyConstraint().getColumns().size() == i;
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
			Column column = fk.getColumns()[i];
			sqlBuilder.lineBreak();
			sqlBuilder.comma(i > 0);
			sqlBuilder.name(refCol.getName());
			if (!CommonUtils.eq(refCol.getName(), column.getName())) {
				sqlBuilder.as().space();
				sqlBuilder.name(column.getName());
			}
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
			sqlBuilder._fromSysDummy();
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
				return null;
			}
			sqlBuilder.appendIndent(-1);
			sqlBuilder.lineBreak();
			sqlBuilder.from();
			sqlBuilder.name(table);
		} else {
			List<String> colList = CommonUtils.list();
			for (Column column : table.getColumns()) {
				if (table.getPrimaryKeyConstraint().getColumns().contains(column.getName())) {
					continue;
				}
				String exp = columnStartValue.apply(column, dialect);
				if (exp == null) {
					continue;
				}
				colList.add(exp);
				sqlBuilder.lineBreak();
				sqlBuilder.comma();
				sqlBuilder._add(exp);
			}
			for (int j = 0; j < fk.getColumns().length; j++) {
				Column column = fk.getColumns()[j];
				ReferenceColumn refColumn = fk.getRelatedColumns().get(j);
				sqlBuilder.lineBreak();
				sqlBuilder.comma(j > 0);
				sqlBuilder.appendQuoteName("a.", refColumn.getName());
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
		}
		return sqlBuilder.toString();
	}

}
