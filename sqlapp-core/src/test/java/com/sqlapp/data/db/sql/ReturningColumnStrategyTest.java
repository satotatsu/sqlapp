package com.sqlapp.data.db.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SqlBuilder;

class ReturningColumnStrategyTest {

	private static final Dialect dialect = DialectResolver.getInstance().getDefaultDialect();

	@Test
	void testPkTableAddOn() {
		Table table = getPkTable();
		SqlBuilder builder = new SqlBuilder(dialect);
		Set<Column> columns = CommonUtils.flatSet(ColumnSelectionStrategy.PRIMARY_KEY.addOn(table, "s", "t", builder));
		String exptected = """
				ON (
					s."colP1" = t."colP1"
					AND s."colP2" = t."colP2"
				)
				""";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(2, columns.size());
		int i = 1;
		for (Column column : columns) {
			assertEquals("colP" + i, column.getName());
			i++;
		}
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(
				ColumnSelectionStrategy.PRIMARY_KEY_AND_ALL_UNIQUE_KEYS_AND_ALL_NOT_NULL_UNIQUE_INDEXES.addOn(table, "s", "t", builder));
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(2, columns.size());
		i = 1;
		for (Column column : columns) {
			assertEquals("colP" + i, column.getName());
			i++;
		}
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(ColumnSelectionStrategy.FULL.addOn(table, "s", "t", builder));
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(2, columns.size());
		i = 1;
		for (Column column : columns) {
			assertEquals("colP" + i, column.getName());
			i++;
		}
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(ColumnSelectionStrategy.ALL_UNIQUE_KEYS.addOn(table, "s", "t", builder));
		exptected = "";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(0, columns.size());
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(ColumnSelectionStrategy.UNIQUE_KEY.addOn(table, "s", "t", builder));
		exptected = "";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(0, columns.size());
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(ColumnSelectionStrategy.NOT_NULL_UNIQUE_INDEX.addOn(table, "s", "t", builder));
		exptected = "";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(0, columns.size());
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils
				.flatSet(ColumnSelectionStrategy.ALL_NOT_NULL_UNIQUE_INDEXES.addOn(table, "s", "t", builder));
		exptected = "";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(0, columns.size());
		//
	}

	@Test
	void testUkTableAddOn() {
		Table table = getUkTable();
		SqlBuilder builder = new SqlBuilder(dialect);
		Set<Column> columns = CommonUtils.flatSet(ColumnSelectionStrategy.UNIQUE_KEY.addOn(table, "s", "t", builder));
		String exptected = """
				ON (
					s."colU1" = t."colU1"
				)
				""";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(1, columns.size());
		assertEquals("colU1", CommonUtils.first(columns).getName());
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(
				ColumnSelectionStrategy.PRIMARY_KEY_AND_ALL_UNIQUE_KEYS_AND_ALL_NOT_NULL_UNIQUE_INDEXES.addOn(table, "s", "t", builder));
		exptected = """
				ON (
					(
						s."colU1" = t."colU1"
					)
					OR
					(
						s."colU2" = t."colU2"
					)
				)
				""";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(2, columns.size());
		int i = 1;
		for (Column column : columns) {
			assertEquals("colU" + i, column.getName());
			i++;
		}
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(ColumnSelectionStrategy.ALL_UNIQUE_KEYS.addOn(table, "s", "t", builder));
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(2, columns.size());
		i = 1;
		for (Column column : columns) {
			assertEquals("colU" + i, column.getName());
			i++;
		}
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(ColumnSelectionStrategy.FULL.addOn(table, "s", "t", builder));
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(2, columns.size());
		i = 1;
		for (Column column : columns) {
			assertEquals("colU" + i, column.getName());
			i++;
		}
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(ColumnSelectionStrategy.ALL_UNIQUE_KEYS.addOn(table, "s", "t", builder));
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(2, columns.size());
		i = 1;
		for (Column column : columns) {
			assertEquals("colU" + i, column.getName());
			i++;
		}
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(ColumnSelectionStrategy.PRIMARY_KEY.addOn(table, "s", "t", builder));
		exptected = "";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(0, columns.size());
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils
				.flatSet(ColumnSelectionStrategy.ALL_NOT_NULL_UNIQUE_INDEXES.addOn(table, "s", "t", builder));
		exptected = "";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(0, columns.size());
		//
	}

	@Test
	void testIndexTableAddOn() {
		Table table = getIndexTable();
		SqlBuilder builder = new SqlBuilder(dialect);
		Set<Column> columns = CommonUtils
				.flatSet(ColumnSelectionStrategy.NOT_NULL_UNIQUE_INDEX.addOn(table, "s", "t", builder));
		String exptected = """
				ON (
					s."colI1" = t."colI1"
				)
				""";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(1, columns.size());
		assertEquals("colI1", CommonUtils.first(columns).getName());
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(
				ColumnSelectionStrategy.PRIMARY_KEY_AND_ALL_UNIQUE_KEYS_AND_ALL_NOT_NULL_UNIQUE_INDEXES.addOn(table, "s", "t", builder));
		exptected = """
				ON (
					(
						s."colI1" = t."colI1"
					)
					OR
					(
						s."colI2" = t."colI2"
					)
				)
				""";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(2, columns.size());
		int i = 1;
		for (Column column : columns) {
			assertEquals("colI" + i, column.getName());
			i++;
		}
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils
				.flatSet(ColumnSelectionStrategy.ALL_NOT_NULL_UNIQUE_INDEXES.addOn(table, "s", "t", builder));
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(2, columns.size());
		i = 1;
		for (Column column : columns) {
			assertEquals("colI" + i, column.getName());
			i++;
		}
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(ColumnSelectionStrategy.FULL.addOn(table, "s", "t", builder));
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(2, columns.size());
		i = 1;
		for (Column column : columns) {
			assertEquals("colI" + i, column.getName());
			i++;
		}
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(ColumnSelectionStrategy.ALL_UNIQUE_KEYS.addOn(table, "s", "t", builder));
		exptected = "";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(0, columns.size());
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(ColumnSelectionStrategy.PRIMARY_KEY.addOn(table, "s", "t", builder));
		exptected = "";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(0, columns.size());
		//
	}

	@Test
	void testPkUkIndexTableAddOn() {
		Table table = getPkUkIndexTable();
		SqlBuilder builder = new SqlBuilder(dialect);
		Set<Column> columns = CommonUtils.flatSet(ColumnSelectionStrategy.PRIMARY_KEY.addOn(table, "s", "t", builder));
		String exptected = """
				ON (
					s."colP1" = t."colP1"
					AND s."colP2" = t."colP2"
				)
				""";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(2, columns.size());
		int i = 1;
		for (Column column : columns) {
			assertEquals("colP" + i, column.getName());
			i++;
		}
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(
				ColumnSelectionStrategy.PRIMARY_KEY_AND_ALL_UNIQUE_KEYS_AND_ALL_NOT_NULL_UNIQUE_INDEXES.addOn(table, "s", "t", builder));
		exptected = """
				ON (
					(
						s."colU1" = t."colU1"
					)
					OR
					(
						s."colU2" = t."colU2"
					)
					OR
					(
						s."colI1" = t."colI1"
					)
					OR
					(
						s."colI2" = t."colI2"
					)
					OR
					(
						s."colP1" = t."colP1"
						AND s."colP2" = t."colP2"
					)
				)
				""";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(6, columns.size());
		i = 1;
		int ukCopunt = 1;
		int pkCopunt = 1;
		int indexCopunt = 1;
		int elseCopunt = 0;
		for (Column column : columns) {
			if (column.getName().startsWith("colU")) {
				assertEquals("colU" + (ukCopunt), column.getName());
				ukCopunt++;
			} else if (column.getName().startsWith("colI")) {
				assertEquals("colI" + (indexCopunt), column.getName());
				indexCopunt++;
			} else if (column.isPrimaryKey()) {
				assertEquals("colP" + (pkCopunt), column.getName());
				pkCopunt++;
			} else {
				elseCopunt++;
			}
			i++;
		}
		assertEquals(0, elseCopunt);
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(ColumnSelectionStrategy.FULL.addOn(table, "s", "t", builder));
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(6, columns.size());
		i = 1;
		ukCopunt = 1;
		pkCopunt = 1;
		indexCopunt = 1;
		elseCopunt = 0;
		for (Column column : columns) {
			if (column.getName().startsWith("colU")) {
				assertEquals("colU" + (ukCopunt), column.getName());
				ukCopunt++;
			} else if (column.getName().startsWith("colI")) {
				assertEquals("colI" + (indexCopunt), column.getName());
				indexCopunt++;
			} else if (column.isPrimaryKey()) {
				assertEquals("colP" + (pkCopunt), column.getName());
				pkCopunt++;
			} else {
				elseCopunt++;
			}
			i++;
		}
		assertEquals(0, elseCopunt);
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(ColumnSelectionStrategy.ALL_UNIQUE_KEYS.addOn(table, "s", "t", builder));
		exptected = """
				ON (
					(
						s."colU1" = t."colU1"
					)
					OR
					(
						s."colU2" = t."colU2"
					)
				)
					""";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(2, columns.size());
		i = 1;
		ukCopunt = 1;
		pkCopunt = 1;
		indexCopunt = 1;
		elseCopunt = 0;
		for (Column column : columns) {
			if (column.getName().startsWith("colU")) {
				assertEquals("colU" + (ukCopunt), column.getName());
				ukCopunt++;
			} else if (column.getName().startsWith("colI")) {
				assertEquals("colI" + (indexCopunt), column.getName());
				indexCopunt++;
			} else if (column.isPrimaryKey()) {
				assertEquals("colP" + (pkCopunt), column.getName());
				pkCopunt++;
			} else {
				elseCopunt++;
			}
			i++;
		}
		assertEquals(0, elseCopunt);
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(ColumnSelectionStrategy.UNIQUE_KEY.addOn(table, "s", "t", builder));
		exptected = """
				ON (
					s."colU1" = t."colU1"
				)
					""";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(1, columns.size());
		i = 1;
		ukCopunt = 1;
		pkCopunt = 1;
		indexCopunt = 1;
		elseCopunt = 0;
		for (Column column : columns) {
			if (column.getName().startsWith("colU")) {
				assertEquals("colU" + (ukCopunt), column.getName());
				ukCopunt++;
			} else if (column.getName().startsWith("colI")) {
				assertEquals("colI" + (indexCopunt), column.getName());
				indexCopunt++;
			} else if (column.isPrimaryKey()) {
				assertEquals("colP" + (pkCopunt), column.getName());
				pkCopunt++;
			} else {
				elseCopunt++;
			}
			i++;
		}
		assertEquals(0, elseCopunt);
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils.flatSet(ColumnSelectionStrategy.NOT_NULL_UNIQUE_INDEX.addOn(table, "s", "t", builder));
		exptected = """
				ON (
					s."colI1" = t."colI1"
				)
					""";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(1, columns.size());
		i = 1;
		ukCopunt = 1;
		pkCopunt = 1;
		indexCopunt = 1;
		elseCopunt = 0;
		for (Column column : columns) {
			if (column.getName().startsWith("colU")) {
				assertEquals("colU" + (ukCopunt), column.getName());
				ukCopunt++;
			} else if (column.getName().startsWith("colI")) {
				assertEquals("colI" + (indexCopunt), column.getName());
				indexCopunt++;
			} else if (column.isPrimaryKey()) {
				assertEquals("colP" + (pkCopunt), column.getName());
				pkCopunt++;
			} else {
				elseCopunt++;
			}
			i++;
		}
		assertEquals(0, elseCopunt);
		//
		builder = new SqlBuilder(dialect);
		columns = CommonUtils
				.flatSet(ColumnSelectionStrategy.ALL_NOT_NULL_UNIQUE_INDEXES.addOn(table, "s", "t", builder));
		exptected = """
				ON (
					(
						s."colI1" = t."colI1"
					)
					OR
					(
						s."colI2" = t."colI2"
					)
				)
				""";
		assertEquals(exptected.trim(), builder.toString().trim());
		assertEquals(2, columns.size());
		i = 1;
		ukCopunt = 1;
		pkCopunt = 1;
		indexCopunt = 1;
		elseCopunt = 0;
		for (Column column : columns) {
			if (column.getName().startsWith("colU")) {
				assertEquals("colU" + (ukCopunt), column.getName());
				ukCopunt++;
			} else if (column.getName().startsWith("colI")) {
				assertEquals("colI" + (indexCopunt), column.getName());
				indexCopunt++;
			} else if (column.isPrimaryKey()) {
				assertEquals("colP" + (pkCopunt), column.getName());
				pkCopunt++;
			} else {
				elseCopunt++;
			}
			i++;
		}
		assertEquals(0, elseCopunt);
		//
	}

	private Table getTable() {
		Table table = new Table("tabA");
		table.getColumns().add(c -> {
			c.setName("colA");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("colB");
			c.setDataType(DataType.VARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colC");
			c.setDataType(DataType.BIGINT);
		});
		table.getColumns().add(c -> {
			c.setName("colD");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colI1");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colI2");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colI3");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colI4");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colP1");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colP2");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colU1");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colU2");
			c.setDataType(DataType.NVARCHAR);
		});
		return table;
	}

	private Table getPkUkIndexTable() {
		Table table = getTable();
		addPK(table);
		addUks(table);
		addIndexes(table);
		return table;
	}

	private Table getPkTable() {
		Table table = getTable();
		addPK(table);
		return table;
	}

	private void addPK(Table table) {
		table.setPrimaryKey(table.getColumns().get("colP1"), table.getColumns().get("colP2"));
	}

	private Table getUkTable() {
		Table table = getTable();
		addUks(table);
		return table;
	}

	private void addUks(Table table) {
		table.getConstraints().addUniqueConstraint(uc -> {
			uc.setName("UK_" + table.getName() + "1");
			uc.getColumns().add(table.getColumns().get("colU1"));
		});
		table.getConstraints().addUniqueConstraint(uc -> {
			uc.setName("UK_" + table.getName() + "2");
			uc.getColumns().add(table.getColumns().get("colU2"));
		});
	}

	private Table getIndexTable() {
		Table table = getTable();
		addIndexes(table);
		return table;
	}

	private void addIndexes(Table table) {
		table.getIndexes().add(idx -> {
			idx.setName("IDX_" + table.getName() + "1");
			idx.setUnique(true);
			Column column = table.getColumns().get("colI1");
			column.setNotNull(true);
			idx.getColumns().add(column);
		});
		table.getIndexes().add(idx -> {
			idx.setName("IDX_" + table.getName() + "2");
			idx.setUnique(true);
			Column column = table.getColumns().get("colI2");
			column.setNotNull(true);
			idx.getColumns().add(column);
		});
		table.getIndexes().add(idx -> {
			idx.setName("IDX_" + table.getName() + "3");
			idx.setUnique(true);
			Column column = table.getColumns().get("colI3");
			idx.getColumns().add(column);
		});
		table.getIndexes().add(idx -> {
			idx.setName("IDX_" + table.getName() + "4");
			idx.setUnique(true);
			Column column = table.getColumns().get("colI4");
			column.setNotNull(false);
			idx.getColumns().add(column);
		});
	}

}
