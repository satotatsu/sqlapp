package com.sqlapp.data.db.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.EqualsHandler;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.MissingWhereClauseKeyException;

class SqlGenerationRegressionTest extends AbstractStandardFactoryTest {

	@Test
	void explicitUniqueIndexStrategyRejectsMissingAndPartialKeys() {
		Table table = table("target");
		for (boolean partial : List.of(false, true)) {
			if (partial) {
				table.getIndexes().add("partial", table.getColumns().get("id")).setUnique(true).setWhere("value > 0");
			}
			for (SqlType type : List.of(SqlType.UPDATE, SqlType.DELETE, SqlType.MERGE, SqlType.INSERT_SELECT_NOT_EXISTS)) {
				SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, type);
				factory.getTableOptions().setUpdateKeyColumnsMatchingStrategy(t -> ColumnSelectionStrategy.NOT_NULL_UNIQUE_INDEX);
				factory.getTableOptions().setDeleteKeyColumnsMatchingStrategy(t -> ColumnSelectionStrategy.NOT_NULL_UNIQUE_INDEX);
				factory.getTableOptions().setMergeKeyColumnsMatchingStrategy(t -> ColumnSelectionStrategy.NOT_NULL_UNIQUE_INDEX);
				factory.getTableOptions().setInsertSelectNotExistsKeyColumnsMatchingStrategy(t -> ColumnSelectionStrategy.NOT_NULL_UNIQUE_INDEX);
				assertThrows(MissingWhereClauseKeyException.class, () -> factory.createSql(table), type.toString());
			}
		}
	}

	@Test
	void explicitUniqueIndexStrategyRetainsValidDeleteCondition() {
		Table table = table("target");
		table.getIndexes().add("whole", table.getColumns().get("id")).setUnique(true);
		SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.DELETE);
		factory.getTableOptions().setDeleteKeyColumnsMatchingStrategy(t -> ColumnSelectionStrategy.NOT_NULL_UNIQUE_INDEX);
		assertTrue(factory.createSql(table).get(0).getSqlText().contains("AND \"id\" ="));
	}

	@Test
	void expressionIndexCannotSupplyOnlyItsResolvedColumnsAsAKey() {
		for (boolean expressionFirst : List.of(false, true)) {
			Table table = table("target");
			var index = table.getIndexes().add("expression_unique");
			index.setUnique(true);
			if (expressionFirst) {
				index.getColumns().add(new ReferenceColumn("ABS(value)"));
			}
			index.getColumns().add(table.getColumns().get("id"));
			if (!expressionFirst) {
				index.getColumns().add(new ReferenceColumn("ABS(value)"));
			}
			assertTrue(ColumnAnalyzer.NOT_NULL_UNIQUE_INDEX.getKeyColumns(table).isEmpty());
			assertTrue(ColumnAnalyzer.NOT_NULL_UNIQUE_INDEXES.getKeyColumnsList(table).isEmpty());
			for (SqlType type : List.of(SqlType.UPDATE, SqlType.DELETE, SqlType.MERGE, SqlType.INSERT_SELECT_NOT_EXISTS)) {
				SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, type);
				assertThrows(MissingWhereClauseKeyException.class, () -> factory.createSql(table), type.toString());
			}
			Row row = table.getRows().newElement();
			row.put("id", 1);
			row.put("value", 2);
			assertTrue(ColumnAnalyzer.NOT_NULL_UNIQUE_INDEX.getKeyColumns(table, List.of(row)).isEmpty());
			table.getIndexes().add("whole", table.getColumns().get("value")).setUnique(true);
			assertEquals(Set.of(table.getColumns().get("value")),
					ColumnAnalyzer.NOT_NULL_UNIQUE_INDEX.getKeyColumns(table, List.of(row)));
		}
	}

	@Test
	void collectsAllNotNullUniqueIndexColumnsConsistently() {
		Table table = table("target");
		table.getIndexes().add("first", table.getColumns().get("id")).setUnique(true);
		table.getIndexes().add("second", table.getColumns().get("value")).setUnique(true);
		Set<Column> expected = Set.of(table.getColumns().get("id"), table.getColumns().get("value"));
		assertEquals(expected, ColumnAnalyzer.NOT_NULL_UNIQUE_INDEXES.getKeyColumns(table));
		assertEquals(expected, ColumnAnalyzer.NOT_NULL_UNIQUE_INDEXES.getKeyColumnsList(table).stream()
				.flatMap(Set::stream).collect(java.util.stream.Collectors.toSet()));
		table.getColumns().get("value").setNotNull(false);
		assertEquals(Set.of(table.getColumns().get("id")), ColumnAnalyzer.NOT_NULL_UNIQUE_INDEXES.getKeyColumns(table));
	}

	@Test
	void dropsConstraintsFromRenamedTableWithoutMutatingInput() {
		for (boolean replace : List.of(false, true)) {
			Table original = table("old_table");
			original.setPrimaryKey("old_pk", original.getColumns().get("id"));
			Table target = original.clone();
			target.setName("new_table");
			target.getConstraints().clear();
			if (replace) {
				target.setPrimaryKey("new_pk", target.getColumns().get("id"));
			}
			SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(target, SqlType.ALTER);
			List<SqlOperation> operations = factory.createDiffSql(original.diff(target));
			assertEquals("ALTER TABLE \"old_table\" RENAME TO \"new_table\"", operations.get(0).getSqlText());
			assertEquals("ALTER TABLE \"new_table\" DROP CONSTRAINT \"old_pk\"", operations.get(1).getSqlText());
			if (replace) {
				assertTrue(operations.get(2).getSqlText().startsWith("ALTER TABLE \"new_table\" ADD CONSTRAINT"));
			}
			assertEquals("old_table", original.getName());
			assertEquals("old_table", original.getPrimaryKeyConstraint().getTable().getName());
			assertEquals("new_table", target.getName());
		}
	}

	@Test
	void partialUniqueIndexCannotSupplyAnUnconditionalKey() {
		Table table = table("target");
		table.getIndexes().add("partial", table.getColumns().get("id")).setUnique(true).setWhere("value > 0");
		assertTrue(ColumnAnalyzer.NOT_NULL_UNIQUE_INDEX.getKeyColumns(table).isEmpty());
		assertTrue(ColumnAnalyzer.NOT_NULL_UNIQUE_INDEXES.getKeyColumnsList(table).isEmpty());
		for (SqlType type : List.of(SqlType.UPDATE, SqlType.DELETE, SqlType.MERGE, SqlType.INSERT_SELECT_NOT_EXISTS)) {
			SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, type);
			assertThrows(MissingWhereClauseKeyException.class,
					() -> factory.createSql(table), type.toString());
		}
	}

	@Test
	void skipsPartialIndexAndSelectsAnOrdinaryUniqueIndexWithRowValues() {
		Table table = table("target");
		table.getIndexes().add("partial", table.getColumns().get("id")).setUnique(true).setWhere("value > 0");
		table.getIndexes().add("whole", table.getColumns().get("value")).setUnique(true);
		Row row = table.getRows().newElement();
		row.put("id", 1);
		row.put("value", 2);
		assertEquals(Set.of(table.getColumns().get("value")),
				ColumnAnalyzer.NOT_NULL_UNIQUE_INDEX.getKeyColumns(table, List.of(row)));
	}

	@Test
	void selectsLaterUniqueConstraintWhenFirstContainsNull() {
		Table table = table("target");
		table.getConstraints().addUniqueConstraint("first", table.getColumns().get("id"));
		table.getConstraints().addUniqueConstraint("second", table.getColumns().get("value"));
		Row row = table.getRows().newElement();
		row.put("value", 42);
		SqlSignature signature = new SqlSignature(table, List.of(row));
		signature.setColumnSelectionStrategy(ColumnSelectionStrategy.UNIQUE_KEY);
		assertEquals(Set.of(table.getColumns().get("value")), signature.getSelectedColumnsHolder().getKeyColumns());
	}

	@Test
	void selectsLaterUniqueIndexWhenFirstContainsNull() {
		Table table = table("target");
		table.getIndexes().add("first", table.getColumns().get("id")).setUnique(true);
		table.getIndexes().add("second", table.getColumns().get("value")).setUnique(true);
		Row row = table.getRows().newElement();
		row.put("value", 42);
		assertEquals(Set.of(table.getColumns().get("value")),
				ColumnAnalyzer.NOT_NULL_UNIQUE_INDEX.getKeyColumns(table, List.of(row)));
	}

	@Test
	void createsParentBeforeChildInDifferenceCollections() {
		List<Table> tables = relatedTables();
		DbObjectDifference parent = difference(null, tables.get(0));
		DbObjectDifference child = difference(null, tables.get(1));
		CreateTableFactory factory = new CreateTableFactory();
		assertEquals(List.of(parent, child), factory.sortDbObjectDifference(List.of(child, parent)));
	}

	@Test
	void createsTemporaryParentBeforeChildInDifferenceCollections() {
		List<Table> tables = relatedTables();
		DbObjectDifference parent = difference(null, tables.get(0));
		DbObjectDifference child = difference(null, tables.get(1));
		CreateTemporaryTableFactory factory = new CreateTemporaryTableFactory();
		assertEquals(List.of(parent, child), factory.sortDbObjectDifference(List.of(child, parent)));
	}

	@Test
	void dropsChildBeforeParentInDifferenceCollections() {
		List<Table> tables = relatedTables();
		DbObjectDifference parent = difference(tables.get(0), null);
		DbObjectDifference child = difference(tables.get(1), null);
		DropTableFactory factory = new DropTableFactory();
		assertEquals(List.of(child, parent), factory.sortDbObjectDifference(List.of(parent, child)));
	}

	@Test
	void rejectsUpdatesContainingOnlyKeys() {
		Table table = new Table("key_only");
		table.getColumns().add(new Column("id").setDataType(DataType.INT).setNotNull(true));
		table.setPrimaryKey(table.getColumns().get("id"));
		for (SqlType type : List.of(SqlType.UPDATE, SqlType.UPDATE_FOR_APP, SqlType.UPDATE_TABLE)) {
			SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, type);
			IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
					() -> factory.createSql(table));
			assertTrue(error.getMessage().contains("key_only"));
		}
	}

	@Test
	void rejectsUpdatesWhenAllNonKeyColumnsAreDisabled() {
		Table table = table("disabled");
		table.setPrimaryKey(table.getColumns().get("id"));
		SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.UPDATE);
		factory.getTableOptions().setUpdateableColumn(false);
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(table));
	}

	@Test
	void renamesColumnBeforeAlteringItsDefinition() {
		Table original = table("target");
		Table target = original.clone();
		Column oldColumn = original.getColumns().get("value");
		Column newColumn = target.getColumns().get("value");
		newColumn.setName("renamed").setDataType(DataType.BIGINT);
		AlterTableFactory factory = (AlterTableFactory) sqlFactoryRegistry.getSqlFactory(target, SqlType.ALTER);
		List<SqlOperation> operations = new ArrayList<>();
		factory.addRenameOrAlterColumn(original, target, oldColumn.diff(newColumn), operations);
		assertEquals(List.of("ALTER TABLE \"target\" RENAME COLUMN \"value\" TO \"renamed\"",
				"ALTER TABLE \"target\" ALTER COLUMN \"renamed\" BIGINT NOT NULL"),
				operations.stream().map(SqlOperation::getSqlText).toList());
		assertEquals("value", oldColumn.getName());
		assertEquals("renamed", newColumn.getName());
	}

	private DbObjectDifference difference(Table original, Table target) {
		return new DbObjectDifference(original, target, new EqualsHandler());
	}

	private List<Table> relatedTables() {
		Schema schema = new Schema("test_schema");
		Table parent = table("parent");
		Table child = table("child");
		schema.getTables().add(parent);
		schema.getTables().add(child);
		parent.setPrimaryKey(parent.getColumns().get("id"));
		child.getConstraints().addForeignKeyConstraint("fk_parent", child.getColumns().get("id"),
				parent.getColumns().get("id"));
		return List.of(parent, child);
	}

	private Table table(String name) {
		Table table = new Table(name);
		table.getColumns().add(new Column("id").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("value").setDataType(DataType.INT).setNotNull(true));
		return table;
	}
}
