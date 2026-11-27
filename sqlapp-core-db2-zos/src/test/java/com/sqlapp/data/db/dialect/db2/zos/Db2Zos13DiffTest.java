/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.zos;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/** Host-correct ALTER and DROP plans; unsupported changes fail explicitly. */
class Db2Zos13DiffTest {
	private final Db2Zos13 dialect = new Db2Zos13();

	@Test
	void columnAdditionRemovalWideningAndDefaults() {
		var before = table();
		var after = table();
		after.getColumns().get("B").setLength(40).setDefaultValue("'X'");
		after.getColumns().remove("C");
		after.getColumns().add(new Column("D").setDataType(DataType.INT).setRemarks("new"));
		String sql = alter(before, after);
		assertTrue(sql.contains("ALTER TABLE \"APP\".\"T\" ALTER COLUMN \"B\" SET DATA TYPE VARCHAR(40)"), sql);
		assertTrue(sql.contains("ALTER TABLE \"APP\".\"T\" ALTER COLUMN \"B\" SET DEFAULT 'X'"), sql);
		assertTrue(sql.contains("ALTER TABLE \"APP\".\"T\" DROP COLUMN \"C\" RESTRICT"), sql);
		assertTrue(sql.contains("ALTER TABLE \"APP\".\"T\" ADD COLUMN \"D\" INTEGER"), sql);
		assertTrue(sql.contains("COMMENT ON COLUMN \"APP\".\"T\".\"D\" IS 'new'"), sql);
		assertTrue(sql.contains("REORG is required"), sql);
	}

	@Test
	void unsupportedInPlaceChangesFail() {
		var before = table();
		var nullability = table();
		nullability.getColumns().get("B").setNotNull(true);
		assertThrows(IllegalArgumentException.class, () -> alter(before, nullability),
				"z/OS cannot change nullability in place");
		var narrowing = table();
		narrowing.getColumns().get("B").setLength(5);
		assertThrows(IllegalArgumentException.class, () -> alter(before, narrowing));
		var physical = table();
		physical.getSpecifics().put("DB2_HOST_APPEND", "Y");
		assertThrows(IllegalArgumentException.class, () -> alter(before, physical));
	}

	@Test
	void indexesAndKeysUseHostFactories() {
		var before = table();
		var oldIndex = new Index("OLD_IX");
		oldIndex.getColumns().add("A");
		before.getIndexes().add(oldIndex);
		var after = table();
		var newIndex = new Index("NEW_IX");
		newIndex.getColumns().add("B");
		after.getIndexes().add(newIndex);
		after.getConstraints().addUniqueConstraint("UK", after.getColumns().get("A"));
		before.getConstraints().addUniqueConstraint("OLD_UK", before.getColumns().get("B"));
		String sql = alter(before, after);
		assertTrue(sql.contains("DROP INDEX \"APP\".\"OLD_IX\""), sql);
		assertTrue(sql.contains("CREATE INDEX \"APP\".\"NEW_IX\" ON \"APP\".\"T\""), sql);
		assertTrue(sql.contains("ALTER TABLE \"APP\".\"T\" DROP CONSTRAINT \"OLD_UK\""), sql);
		assertTrue(sql.contains("ADD CONSTRAINT"), sql);
	}

	@Test
	void tableAdditionAndRemovalUseCreateAndDrop() {
		var factory = dialect.createSqlFactoryRegistry().getSqlFactory(table(), SqlType.ALTER);
		assertTrue(join(factory.createDiffSql(table().diff(null))).startsWith("DROP TABLE"));
		assertTrue(join(factory.createDiffSql(new DbObjectDifference(null, table(), new DefaultSchemaEqualsHandler())))
				.contains("CREATE TABLE"));
	}

	@Test
	void sequenceAlterNeverRestarts() {
		var before = sequence();
		var after = sequence().setIncrementBy(5).setCacheSize(0).setCache(false);
		String sql = join(diff(before, after));
		assertTrue(sql.startsWith("ALTER SEQUENCE \"APP\".\"S\" INCREMENT BY 5"), sql);
		assertTrue(sql.contains("NO CACHE"), sql);
		var restart = sequence().setStartValue(100);
		assertThrows(IllegalArgumentException.class, () -> diff(before, restart));
		assertTrue(join(dialect.createSqlFactoryRegistry().createSql(before, SqlType.DROP)).contains("DROP SEQUENCE"));
	}

	@Test
	void viewsMasksAndTriggersAlterOnlyWhatIsSafe() {
		var view = new View("V").setSchemaName("APP");
		view.setStatement("SELECT 1 FROM SYSIBM.SYSDUMMY1");
		var commented = view.clone();
		commented.setRemarks("note");
		assertEquals("COMMENT ON TABLE \"APP\".\"V\" IS 'note'", join(diff(view, commented)));
		var changed = view.clone();
		changed.setStatement("SELECT 2 FROM SYSIBM.SYSDUMMY1");
		assertThrows(IllegalArgumentException.class, () -> diff(view, changed));
		var mask = new Mask("M").setSchemaName("APP").setTableName("T").setColumnName("B").setEnable(true);
		mask.setStatement("CASE WHEN 1=1 THEN B END");
		var disabled = mask.clone().setEnable(false);
		assertEquals("ALTER MASK \"APP\".\"M\" DISABLE", join(diff(mask, disabled)));
		assertEquals("DROP MASK \"APP\".\"M\"", join(dialect.createSqlFactoryRegistry().createSql(mask, SqlType.DROP)));
	}

	private static Table table() {
		var table = new Table("T").setSchemaName("APP");
		table.getColumns().add(new Column("A").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("B").setDataType(DataType.VARCHAR).setLength(10));
		table.getColumns().add(new Column("C").setDataType(DataType.INT));
		return table;
	}

	private static Sequence sequence() {
		var sequence = new Sequence("S").setSchemaName("APP");
		sequence.setDataType(DataType.INT).setStartValue(1).setIncrementBy(1).setMinValue(1).setMaxValue(1000)
				.setCacheSize(20).setCache(true);
		return sequence;
	}

	private String alter(Table before, Table after) {
		return join(dialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER)
				.createDiffSql(before.diff(after)));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private List<com.sqlapp.data.db.sql.SqlOperation> diff(AbstractSchemaObject before, AbstractSchemaObject after) {
		return dialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
	}

	private static String join(List<com.sqlapp.data.db.sql.SqlOperation> operations) {
		return String.join("\n", operations.stream().map(op -> op.getSqlText()).toList());
	}
}
