/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class InformixAlterCatalogFactoryTest {
	private Catalog catalog() {
		var catalog = new Catalog("db").setProductName("Informix").setProductMajorVersion(14).setProductMinorVersion(10)
				.setDialect(DialectHolder.defaultDialect);
		catalog.getSchemas().add(new Schema("parent_owner"));
		catalog.getSchemas().add(new Schema("child_owner"));
		var parent = new Table("parent");
		catalog.getSchemas().get("parent_owner").getTables().add(parent);
		parent.getColumns().add("id", c -> c.setDataType(DataType.INT));
		parent.getConstraints().addPrimaryKeyConstraint("pk_parent", "id");
		parent.getConstraints().addCheckConstraint("ck_parent", "id > 0");
		var child = new Table("child");
		catalog.getSchemas().get("child_owner").getTables().add(child);
		child.getColumns().add("parent_id", c -> c.setDataType(DataType.INT));
		child.getConstraints().addForeignKeyConstraint("fk_parent", f -> f
				.setColumns(child.getColumns().get("parent_id")).setRelatedColumns(parent.getColumns().get("id")));
		return catalog;
	}

	private Table parent(Catalog c) {
		return c.getSchemas().get("parent_owner").getTables().get("parent");
	}

	private Table child(Catalog c) {
		return c.getSchemas().get("child_owner").getTables().get("child");
	}

	private List<String> sql(Catalog before, Catalog after) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(before.diff(after), SqlType.ALTER)
				.stream().map(o -> o.getSqlText().replaceAll("\\s+", " ").strip()).toList();
	}

	@Test
	void rebuildsIncomingForeignKeysAfterKeysAndChecksAcrossOwners() throws Exception {
		var before = catalog();
		var after = before.clone();
		parent(after).getColumns().get("id").setDefaultValue("1");
		String originalXml = before.asXml(), targetXml = after.asXml();
		var operations = sql(before, after);
		assertEquals(originalXml, before.asXml());
		assertEquals(targetXml, after.asXml());
		assertEquals(7, operations.size(), operations.toString());
		assertTrue(operations.get(0).contains("DROP CONSTRAINT \"child_owner\".\"fk_parent\""), operations.toString());
		int modify = index(operations, "MODIFY");
		assertEquals(3, modify, operations.toString());
		assertTrue(operations.get(6).contains("FOREIGN KEY"), operations.toString());
		assertTrue(operations.get(6).contains("REFERENCES \"parent_owner\".\"parent\""), operations.toString());
		assertNull(parent(before).getColumns().get("id").getDefaultValue());
		assertEquals(2, parent(after).getConstraints().size());
		assertEquals(1, child(after).getConstraints().size());
	}

	private int index(List<String> sql, String part) {
		for (int i = 0; i < sql.size(); i++)
			if (sql.get(i).contains(part))
				return i;
		return -1;
	}

	@Test
	void addsRemovesAndRenamesNamedNotNullUsingOneColumnModify() {
		var before = catalog();
		var after = before.clone();
		var table = child(after);
		var column = table.getColumns().get("parent_id").setNotNull(true);
		table.getConstraints().add(new NotNullConstraint("nn_parent").setColumn(column));
		var add = sql(before, after);
		assertEquals(1, add.stream().filter(s -> s.contains("MODIFY")).count());
		assertTrue(add.stream().anyMatch(s -> s.contains("NOT NULL CONSTRAINT \"nn_parent\"")), add.toString());
		assertTrue(add.getLast().contains("ADD CONSTRAINT FOREIGN KEY"));
		var remove = sql(after, before);
		assertEquals(1, remove.stream().filter(s -> s.contains("MODIFY") && !s.contains("NOT NULL")).count());
		var renamed = after.clone();
		child(renamed).getConstraints().get("nn_parent").setName("nn_new");
		var rename = sql(after, renamed);
		assertEquals(1, rename.stream().filter(s -> s.contains("MODIFY")).count());
		assertTrue(rename.stream().anyMatch(s -> s.contains("NOT NULL CONSTRAINT \"nn_new\"")), rename.toString());
		var filtering = after.clone();
		child(filtering).getConstraints().get("nn_parent").getSpecifics()
				.put(com.sqlapp.data.db.dialect.informix.util.InformixConstraintModes.MODE, "FILTERING WITHOUT ERROR");
		var modeChange = sql(after, filtering);
		assertEquals(1, modeChange.stream().filter(s -> s.contains("MODIFY")).count());
		assertTrue(
				modeChange.stream()
						.anyMatch(s -> s.contains("NOT NULL CONSTRAINT \"nn_parent\" FILTERING WITHOUT ERROR")),
				modeChange.toString());
	}

	@Test
	void replacesReferencedPrimaryKeyWithUniqueKeyBeforeRestoringForeignKey() {
		var before = catalog();
		var after = before.clone();
		var table = parent(after);
		table.getConstraints().remove("pk_parent");
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
		table.getConstraints().addUniqueConstraint("uk_parent", table.getColumns().get("id"));
		var operations = sql(before, after);
		assertTrue(operations.getFirst().contains("DROP CONSTRAINT \"child_owner\".\"fk_parent\""));
		int unique = index(operations, "ADD CONSTRAINT UNIQUE");
		assertTrue(unique > index(operations, "DROP CONSTRAINT \"parent_owner\".\"pk_parent\""));
		assertTrue(unique < index(operations, "ADD CONSTRAINT FOREIGN KEY"));
		assertTrue(sql(after, before).stream().anyMatch(s -> s.contains("ADD CONSTRAINT PRIMARY KEY")));
	}

	@Test
	void changesOnlyTheForeignKeyMode() {
		var before = catalog();
		var after = before.clone();
		var foreign = child(after).getConstraints().get("fk_parent");
		foreign.getSpecifics().put(com.sqlapp.data.db.dialect.informix.util.InformixConstraintModes.MODE,
				"FILTERING WITHOUT ERROR");
		var operations = sql(before, after);
		assertEquals(2, operations.size());
		assertTrue(operations.getLast().endsWith("FILTERING WITHOUT ERROR"), operations.toString());
	}

	@Test
	void addsDropsAndChangesOnlyTheRequestedForeignKey() {
		var before = catalog();
		var removed = before.clone();
		child(removed).getConstraints().remove("fk_parent");
		var drop = sql(before, removed);
		assertEquals(1, drop.size());
		assertTrue(drop.getFirst().contains("DROP CONSTRAINT \"child_owner\".\"fk_parent\""));
		var add = sql(removed, before);
		assertEquals(1, add.size());
		assertTrue(add.getFirst().contains("ADD CONSTRAINT FOREIGN KEY"));
		var changed = before.clone();
		var fk = (ForeignKeyConstraint) child(changed).getConstraints().get("fk_parent");
		fk.setDeleteRule(CascadeRule.Cascade);
		var replace = sql(before, changed);
		assertEquals(2, replace.size());
		assertEquals(drop.getFirst(), replace.getFirst());
		assertTrue(replace.getLast().contains("ON DELETE CASCADE"));
		fk.setUpdateRule(CascadeRule.Cascade);
		assertThrows(UnsupportedOperationException.class, () -> sql(before, changed));
	}

	@Test
	void retargetsAForeignKeyAcrossOwnersAndRejectsANonKeyTarget() {
		var before = catalog();
		var alternate = new Table("alternate");
		before.getSchemas().get("child_owner").getTables().add(alternate);
		alternate.getColumns().add("code", c -> c.setDataType(DataType.INT));
		alternate.getColumns().add("unkeyed", c -> c.setDataType(DataType.INT));
		alternate.getConstraints().addUniqueConstraint("uk_code", alternate.getColumns().get("code"));
		var after = before.clone();
		var target = after.getSchemas().get("child_owner").getTables().get("alternate");
		var foreign = (ForeignKeyConstraint) child(after).getConstraints().get("fk_parent");
		foreign.setRelatedColumns(target.getColumns().get("code"));
		var operations = sql(before, after);
		assertEquals(2, operations.size(), operations.toString());
		assertTrue(operations.getFirst().contains("DROP CONSTRAINT"));
		assertTrue(operations.getLast().contains("REFERENCES \"child_owner\".\"alternate\""));
		assertTrue(operations.getLast().contains("\"code\""));
		assertTrue(sql(after, before).getLast().contains("REFERENCES \"parent_owner\".\"parent\""));
		foreign.setRelatedColumns(target.getColumns().get("unkeyed"));
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
	}

	@Test
	void replacesTheCheckExpressionAfterColumnModification() {
		var before = catalog();
		var after = before.clone();
		parent(after).getColumns().get("id").setDefaultValue("2");
		((CheckConstraint) parent(after).getConstraints().get("ck_parent")).setExpression("id > 1");
		var operations = sql(before, after);
		int drop = index(operations, "DROP CONSTRAINT \"parent_owner\".\"ck_parent\"");
		int modify = index(operations, "MODIFY");
		int create = index(operations, "ADD CONSTRAINT CHECK (id > 1)");
		assertTrue(drop >= 0 && drop < modify && modify < create, operations.toString());
		assertTrue(sql(after, before).stream().anyMatch(s -> s.contains("ADD CONSTRAINT CHECK (id > 0)")));
		parent(after).getConstraints().get("ck_parent").setEnable(false);
		assertTrue(sql(before, after).stream().anyMatch(s -> s.contains("CHECK (id > 1)") && s.endsWith("DISABLED")));
	}

	@Test
	void changesCheckModeWithoutChangingTheExpression() {
		var before = catalog();
		var after = before.clone();
		var check = parent(after).getConstraints().get("ck_parent");
		parent(before).getConstraints().addCheckConstraint("ck_unchanged", "id < 100");
		parent(after).getConstraints().addCheckConstraint("ck_unchanged", "id < 100");
		var mode = com.sqlapp.data.db.dialect.informix.util.InformixConstraintModes.MODE;
		check.getSpecifics().put(mode, "FILTERING WITH ERROR");
		var operations = sql(before, after);
		assertEquals(2, operations.size(), operations.toString());
		assertTrue(operations.get(0).contains("DROP CONSTRAINT \"parent_owner\".\"ck_parent\""));
		assertTrue(operations.get(1).contains("CHECK (id > 0)") && operations.get(1).endsWith("FILTERING WITH ERROR"));
		assertTrue(sql(after, before).stream().anyMatch(s -> s.contains("CHECK (id > 0)") && !s.contains("FILTERING")));
		check.setEnable(false);
		assertThrows(IllegalArgumentException.class, () -> sql(before, after));
	}

	@Test
	void addsACheckAfterItsColumnAndDropsItBeforeRemovingTheColumn() {
		var before = catalog();
		var after = before.clone();
		var table = child(after);
		table.getColumns().add("payload", c -> c.setDataType(DataType.INT));
		table.getConstraints().addCheckConstraint("ck_payload", "payload > 0");
		var forward = sql(before, after);
		int addColumn = index(forward, "ADD ( \"payload\"");
		assertTrue(addColumn >= 0 && addColumn < index(forward, "ADD CONSTRAINT CHECK (payload > 0)"),
				forward.toString());
		var reverse = sql(after, before);
		int dropCheck = index(reverse, "DROP CONSTRAINT \"child_owner\".\"ck_payload\"");
		assertTrue(dropCheck >= 0 && dropCheck < index(reverse, "DROP ( \"payload\")"), reverse.toString());
		assertTrue(reverse.stream().noneMatch(s -> s.contains("ADD CONSTRAINT CHECK (payload > 0)")));
	}

	@Test
	void renamesReferencedColumnAndRecreatesTheUpdatedKeyCheckAndForeignKey() {
		var before = catalog();
		parent(before).getColumns().get("id").setId("parent-id");
		var after = before.clone();
		var table = parent(after);
		var renamed = table.getColumns().get("id").setName("new_id");
		table.getConstraints().remove("pk_parent");
		table.getConstraints().addPrimaryKeyConstraint("pk_parent", "new_id");
		((ForeignKeyConstraint) child(after).getConstraints().get("fk_parent")).setRelatedColumns(renamed);
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
		((CheckConstraint) table.getConstraints().get("ck_parent")).setExpression("new_id > 0");
		var operations = sql(before, after);
		int dropForeign = index(operations, "DROP CONSTRAINT \"child_owner\".\"fk_parent\"");
		int rename = index(operations, "RENAME COLUMN");
		int key = index(operations, "ADD CONSTRAINT PRIMARY KEY");
		int check = index(operations, "ADD CONSTRAINT CHECK (new_id > 0)");
		assertTrue(dropForeign >= 0 && dropForeign < rename && rename < key && rename < check, operations.toString());
		assertTrue(operations.get(key).contains("\"new_id\""));
		assertTrue(operations.getLast().contains("FOREIGN KEY") && operations.getLast().contains("\"new_id\""));
		assertTrue(sql(after, before).stream().anyMatch(s -> s.contains("RENAME COLUMN") && s.endsWith("TO \"id\"")));
	}

	@Test
	void removesAColumnAfterDroppingItsCheckAndRestoresTheReplacementExpression() {
		var before = catalog();
		var table = child(before);
		table.getColumns().add("payload", c -> c.setDataType(DataType.INT));
		table.getConstraints().addCheckConstraint("ck_child", "payload > 0 AND parent_id > 0");
		var after = before.clone();
		child(after).getColumns().remove("payload");
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
		((CheckConstraint) child(after).getConstraints().get("ck_child")).setExpression("parent_id > 0");
		var operations = sql(before, after);
		int drop = index(operations, "DROP CONSTRAINT \"child_owner\".\"ck_child\"");
		int column = index(operations, "DROP ( \"payload\")");
		int check = index(operations, "ADD CONSTRAINT CHECK (parent_id > 0)");
		assertTrue(drop >= 0 && drop < column && column < check, operations.toString());
	}

	@Test
	void renamesAnIndependentColumnWithStableIdentityAndModifiesItsNewName() {
		var before = catalog();
		standalone(before, child(before), "payload_idx");
		child(before).getIndexes().clear();
		child(before).getColumns().get("payload").setId("payload-id");
		var after = before.clone();
		child(after).getColumns().get("payload").setName("renamed").setDataType(DataType.BIGINT);
		var operations = sql(before, after);
		int rename = index(operations, "RENAME COLUMN \"child_owner\".\"child\".\"payload\" TO \"renamed\"");
		assertTrue(rename >= 0 && rename < index(operations, "MODIFY ( \"renamed\" INT8"), operations.toString());
		assertTrue(operations.stream().noneMatch(s -> s.contains("DROP (") || s.contains("ADD (")));
		assertTrue(
				sql(after, before).stream().anyMatch(s -> s.contains("RENAME COLUMN") && s.endsWith("TO \"payload\"")));
		child(after).getColumns().get("renamed").setId("other-id");
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
	}

	@Test
	void dropsAnIndependentIndexBeforeRemovingItsColumnAndRestoresTheForeignKey() {
		var before = catalog();
		standalone(before, child(before), "payload_index");
		var after = before.clone();
		child(after).getIndexes().remove("payload_index");
		child(after).getColumns().remove("payload");
		var operations = sql(before, after);
		int dropIndex = index(operations, "DROP INDEX");
		int dropColumn = index(operations, "DROP ( \"payload\")");
		assertTrue(dropIndex >= 0 && dropIndex < dropColumn, operations.toString());
		assertTrue(dropColumn < index(operations, "ADD CONSTRAINT FOREIGN KEY"));
		assertTrue(sql(after, before).stream().anyMatch(s -> s.contains("ADD ( \"payload\"")));
	}

	@Test
	void rejectsRemovingAColumnStillUsedByAnIndexOrForeignKey() {
		var before = catalog();
		standalone(before, child(before), "payload_index");
		var after = before.clone();
		child(after).getColumns().remove("payload");
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
		var foreignKeyChange = before.clone();
		child(foreignKeyChange).getColumns().remove("parent_id");
		assertThrows(UnsupportedOperationException.class, () -> sql(before, foreignKeyChange));
	}

	@Test
	void appendsColumnsBeforeCreatingTheirIndexAndRestoringConstraints() {
		var before = catalog();
		var after = before.clone();
		var table = parent(after);
		table.getColumns().get("id").setDefaultValue("2");
		table.getColumns().add("payload", c -> c.setDataType(DataType.INT));
		table.getColumns().add("suffix", c -> c.setDataType(DataType.INT));
		var idx = new Index("payload_idx");
		idx.getColumns().add(table.getColumns().get("payload"));
		table.getIndexes().add(idx);
		var operations = sql(before, after);
		int first = index(operations, "ADD ( \"payload\"");
		int second = index(operations, "ADD ( \"suffix\"");
		int createIndex = index(operations, "CREATE INDEX");
		assertTrue(first >= 0 && first < second && second < createIndex, operations.toString());
		assertTrue(index(operations, "MODIFY") >= 0);
		assertTrue(createIndex < index(operations, "ADD CONSTRAINT PRIMARY KEY"));
		assertTrue(operations.getLast().contains("ADD CONSTRAINT FOREIGN KEY"));
		assertThrows(UnsupportedOperationException.class, () -> sql(after, before));
	}

	@Test
	void insertsMultipleColumnsBeforeTheSameExistingColumnInTargetOrder() {
		var before = catalog();
		var after = before.clone();
		var columns = parent(after).getColumns();
		columns.add(0, new Column("first_insert").setDataType(DataType.INT));
		columns.add(1, new Column("second_insert").setDataType(DataType.INT));
		var operations = sql(before, after);
		int first = index(operations, "ADD ( \"first_insert\" INTEGER BEFORE \"id\")");
		int second = index(operations, "ADD ( \"second_insert\" INTEGER BEFORE \"id\")");
		assertTrue(first >= 0 && first < second, operations.toString());
		assertTrue(second < index(operations, "ADD CONSTRAINT PRIMARY KEY"));
		assertTrue(operations.stream().noneMatch(s -> s.contains("MODIFY") || s.contains("RENAME COLUMN")));
	}

	@Test
	void modifiesBothSidesBeforeRestoringTheForeignKeyAndSupportsReverse() {
		var before = catalog();
		var after = before.clone();
		parent(after).getColumns().get("id").setDataType(DataType.BIGINT);
		child(after).getColumns().get("parent_id").setDataType(DataType.BIGINT);
		var operations = sql(before, after);
		assertEquals(2, operations.stream().filter(s -> s.contains("MODIFY")).count());
		assertTrue(operations.get(operations.size() - 1).contains("FOREIGN KEY"));
		assertEquals(2, sql(after, before).stream().filter(s -> s.contains("MODIFY")).count());
		assertTrue(sql(before, before.clone()).isEmpty());
	}

	@Test
	void keepsStandaloneTableSafetyAndRejectsUnsupportedMixedChanges() {
		var before = catalog();
		var after = before.clone();
		parent(after).getColumns().get("id").setDefaultValue("2");
		assertThrows(UnsupportedOperationException.class, () -> DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(parent(before).diff(parent(after)), SqlType.ALTER));
		parent(after).getColumns().get("id").setName("renamed");
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
	}

	@Test
	void preservesStandaloneIndexesAndRejectsUnresolvedReferencesBeforeReturningSql() throws Exception {
		var before = catalog();
		var standalone = new Index("idx").setTableSpaceName("standalone_space");
		standalone.getColumns().add(parent(before).getColumns().get("id"), Order.Desc);
		parent(before).getIndexes().add(standalone);
		var after = before.clone();
		parent(after).getColumns().get("id").setDefaultValue("2");
		String originalXml = before.asXml(), targetXml = after.asXml();
		var operations = sql(before, after);
		assertEquals(7, operations.size());
		assertTrue(operations.stream().noneMatch(s -> s.contains("DROP INDEX") || s.contains("CREATE INDEX")));
		assertEquals(originalXml, before.asXml());
		assertEquals(targetXml, after.asXml());
		var missing = catalog();
		missing.getSchemas().remove("parent_owner");
		var changed = missing.clone();
		child(changed).getColumns().get("parent_id").setDefaultValue("2");
		assertThrows(UnsupportedOperationException.class, () -> sql(missing, changed));
	}

	@Test
	void preservesNamedNotNullAndHandlesCompositeSelfReferenceOnce() {
		var before = catalog();
		var table = parent(before);
		child(before).getConstraints().clear();
		table.getColumns().add("second", c -> c.setDataType(DataType.INT));
		table.getConstraints().remove("pk_parent");
		table.getConstraints().addPrimaryKeyConstraint("pk_parent", "id", "second");
		table.getConstraints().add(new NotNullConstraint("nn_id").setColumn(table.getColumns().get("id")));
		table.getConstraints().addForeignKeyConstraint("fk_self",
				f -> f.setColumns(table.getColumns().get("id"), table.getColumns().get("second"))
						.setRelatedColumns(table.getColumns().get("id"), table.getColumns().get("second")));
		var after = before.clone();
		parent(after).getColumns().get("id").setDefaultValue("3");
		var operations = sql(before, after);
		assertEquals(1,
				operations.stream().filter(s -> s.contains("DROP CONSTRAINT") && s.contains("fk_self")).count());
		assertTrue(
				operations.stream().filter(s -> s.contains("MODIFY")).allMatch(s -> s.contains("NOT NULL CONSTRAINT")));
		assertTrue(operations.stream().noneMatch(s -> s.contains("DROP CONSTRAINT") && s.contains("nn_id")));
	}

	@Test
	void rejectsVirtualRelationsAndNamedBackingIndexes() {
		var before = catalog();
		var after = before.clone();
		parent(after).getColumns().get("id").setDefaultValue("2");
		((ForeignKeyConstraint) child(before).getConstraints().get("fk_parent")).setVirtual(true);
		((ForeignKeyConstraint) child(after).getConstraints().get("fk_parent")).setVirtual(true);
		final var virtualBefore = before;
		final var virtualAfter = after;
		assertThrows(UnsupportedOperationException.class, () -> sql(virtualBefore, virtualAfter));
		before = catalog();
		after = before.clone();
		((UniqueConstraint) parent(before).getConstraints().get("pk_parent")).setIndexName("backing");
		((UniqueConstraint) parent(after).getConstraints().get("pk_parent")).setIndexName("backing");
		parent(after).getColumns().get("id").setDefaultValue("2");
		final var indexedBefore = before;
		final var indexedAfter = after;
		assertThrows(UnsupportedOperationException.class, () -> sql(indexedBefore, indexedAfter));
	}

	@Test
	void requiresDifferenceAndRejectsMixedCatalogChanges() {
		var before = catalog();
		var after = before.clone();
		parent(after).getColumns().get("id").setDefaultValue("2");
		after.getUsers().add(new User("unrelated"));
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(before, SqlType.ALTER));
	}

	private void backing(Catalog catalog, String name) {
		var table = parent(catalog);
		var index = new Index(name).setUnique(true).setIndexType(IndexType.BTree).setTableSpaceName("index space");
		index.getSpecifics().put(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER, "index_owner");
		index.getColumns().add(table.getColumns().get("id"));
		table.getIndexes().add(index);
		((UniqueConstraint) table.getConstraints().get("pk_parent")).setIndexName(name);
		table.getConstraints().get("pk_parent").getSpecifics().put(
				com.sqlapp.data.db.dialect.informix.metadata.InformixTableReader.INFORMIX_BACKING_INDEX_REQUIRED,
				"true");
	}

	@Test
	void ensuresNamedBackingIndexAfterModifyBeforeKeysWithoutDroppingIt() throws Exception {
		var before = catalog();
		backing(before, "ux_parent");
		var after = before.clone();
		parent(after).getColumns().get("id").setDefaultValue("2");
		String originalXml = before.asXml(), targetXml = after.asXml();
		var operations = sql(before, after);
		int ensure = index(operations, "CREATE UNIQUE INDEX IF NOT EXISTS");
		assertTrue(ensure > index(operations, "MODIFY"), operations.toString());
		assertTrue(ensure < index(operations, "ADD CONSTRAINT PRIMARY KEY"), operations.toString());
		assertTrue(operations.get(ensure).contains("\"index_owner\".\"ux_parent\""), operations.toString());
		assertTrue(operations.get(ensure).contains("IN \"index space\""), operations.toString());
		assertTrue(operations.stream().noneMatch(s -> s.contains("DROP INDEX")));
		assertEquals(originalXml, before.asXml());
		assertEquals(targetXml, after.asXml());
	}

	@Test
	void ensuresAnIndexEvenWhenItSharesItsConstraintName() {
		var before = catalog();
		backing(before, "pk_parent");
		var after = before.clone();
		parent(after).getColumns().get("id").setDefaultValue("2");
		assertEquals(1,
				sql(before, after).stream().filter(s -> s.contains("CREATE UNIQUE INDEX IF NOT EXISTS")).count());
	}

	@Test
	void gatesConditionalIndexCreationAt1170() {
		for (int[] version : new int[][] { { 11, 70 }, { 12, 10 }, { 14, 10 }, { 15, 0 } }) {
			var before = catalog().setProductMajorVersion(version[0]).setProductMinorVersion(version[1]);
			backing(before, "ux_parent");
			var after = before.clone();
			parent(after).getColumns().get("id").setDefaultValue("2");
			assertTrue(sql(before, after).stream().anyMatch(s -> s.contains("IF NOT EXISTS")));
		}
		var old = catalog().setProductMajorVersion(11).setProductMinorVersion(50);
		backing(old, "ux_parent");
		var changed = old.clone();
		parent(changed).getColumns().get("id").setDefaultValue("2");
		assertThrows(UnsupportedOperationException.class, () -> sql(old, changed));
	}

	@Test
	void rejectsDisabledBackingIndexesBeforeReturningSql() {
		var before = catalog();
		backing(before, "ux_parent");
		parent(before).getIndexes().get("ux_parent").setEnable(false);
		var after = before.clone();
		parent(after).getColumns().get("id").setDefaultValue("2");
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
	}

	@Test
	void rejectsFilteringBackingIndexRatherThanRecreatingItEnabled() {
		var before = catalog();
		backing(before, "ux_parent");
		var after = before.clone();
		parent(after).getIndexes().get("ux_parent").getSpecifics()
				.put(com.sqlapp.data.db.dialect.informix.util.InformixIndexModes.MODE, "FILTERING WITHOUT ERROR");
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
	}

	@Test
	void renamesNamedBackingIndexBeforeRestoringTheOriginalKey() {
		var before = catalog();
		backing(before, "ux_parent");
		var after = before.clone();
		var table = parent(after);
		var key = table.getConstraints().getPrimaryKeyConstraint();
		var renamed = table.getIndexes().get("ux_parent").clone().setName("ux_renamed");
		table.getIndexes().remove("ux_parent");
		table.getIndexes().add(renamed);
		key.setIndex(renamed);
		var operations = sql(before, after);
		int drop = index(operations, "DROP INDEX IF EXISTS \"index_owner\".\"ux_parent\"");
		int create = index(operations, "CREATE UNIQUE INDEX \"index_owner\".\"ux_renamed\"");
		assertTrue(drop > 0 && create > drop && create < index(operations, "ADD CONSTRAINT PRIMARY KEY"),
				operations.toString());
		assertTrue(operations.getLast().contains("ADD CONSTRAINT FOREIGN KEY"));
		assertTrue(sql(after, before).stream()
				.anyMatch(s -> s.contains("CREATE UNIQUE INDEX \"index_owner\".\"ux_parent\"")));
	}

	@Test
	void leavesOrdinaryIndexCreateDefaultsUnchanged() {
		var before = catalog();
		backing(before, "ux_parent");
		var after = before.clone();
		parent(after).getColumns().get("id").setDefaultValue("2");
		sql(before, after);
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var index = parent(before).getIndexes().get("ux_parent");
		assertFalse(registry.createSql(index, SqlType.CREATE).get(0).getSqlText().contains("IF NOT EXISTS"));
		assertEquals(DialectHolder.defaultDialect.createSqlFactoryRegistry().getOptions().isCreateIfNotExists(),
				registry.getOptions().isCreateIfNotExists());
	}

	private void standalone(Catalog catalog, Table table, String name) {
		if (table.getColumns().get("payload") == null)
			table.getColumns().add("payload", c -> c.setDataType(DataType.INT));
		var index = new Index(name).setTableSpaceName("old_space");
		index.getSpecifics().put(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER, "old_owner");
		index.getColumns().add(table.getColumns().get("payload"));
		table.getIndexes().add(index);
	}

	@Test
	void changesIndependentIndexOwnerOrderAndStorageAroundColumnModify() throws Exception {
		var before = catalog();
		standalone(before, parent(before), "idx");
		var after = before.clone();
		parent(after).getColumns().get("id").setDefaultValue("2");
		var changed = parent(after).getIndexes().get("idx");
		changed.setTableSpaceName("new space");
		changed.getColumns().get(0).setOrder(Order.Desc);
		changed.getSpecifics().put(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER, "new_owner");
		String originalXml = before.asXml(), targetXml = after.asXml();
		var operations = sql(before, after);
		int drop = index(operations, "DROP INDEX"), modify = index(operations, "MODIFY"),
				create = index(operations, "CREATE INDEX");
		assertTrue(drop > 0 && drop < modify && modify < create, operations.toString());
		assertTrue(create < index(operations, "ADD CONSTRAINT PRIMARY KEY"), operations.toString());
		assertTrue(operations.get(drop).contains("\"old_owner\".\"idx\""));
		assertTrue(operations.get(create).contains("\"new_owner\".\"idx\""));
		assertTrue(operations.get(create).contains("DESC"));
		assertTrue(operations.get(create).contains("IN \"new space\""));
		assertFalse(operations.get(create).contains("IF NOT EXISTS"));
		assertEquals(originalXml, before.asXml());
		assertEquals(targetXml, after.asXml());
		assertEquals(1, sql(after, before).stream().filter(s -> s.contains("DROP INDEX")).count());
	}

	@Test
	void plansIndexAddsDeletesAndRenamesOnceAcrossTables() {
		var before = catalog();
		standalone(before, parent(before), "retired");
		standalone(before, child(before), "renamed_old");
		var after = before.clone();
		parent(after).getColumns().get("id").setDefaultValue("2");
		parent(after).getIndexes().remove("retired");
		child(after).getIndexes().get("renamed_old").setName("renamed_new");
		standalone(after, parent(after), "added");
		var operations = sql(before, after);
		assertEquals(2, operations.stream().filter(s -> s.contains("DROP INDEX")).count());
		assertEquals(2, operations.stream().filter(s -> s.contains("CREATE INDEX")).count());
		assertTrue(operations.stream().anyMatch(s -> s.contains("CREATE INDEX") && s.contains("renamed_new")));
		assertEquals(1,
				operations.stream().filter(s -> s.contains("DROP CONSTRAINT") && s.contains("fk_parent")).count());
		int modify = index(operations, "MODIFY");
		for (int i = 0; i < operations.size(); i++) {
			if (operations.get(i).contains("DROP INDEX"))
				assertTrue(i < modify);
			if (operations.get(i).contains("CREATE INDEX"))
				assertTrue(i > modify);
		}
	}

	@Test
	void supportsIndexOnlyChangesThroughTheSameDependencyPlanner() {
		var before = catalog();
		standalone(before, parent(before), "idx");
		var after = before.clone();
		parent(after).getIndexes().get("idx").setTableSpaceName("new_space");
		var operations = sql(before, after);
		assertTrue(operations.stream().noneMatch(s -> s.contains("MODIFY")));
		assertEquals(1, operations.stream().filter(s -> s.contains("DROP INDEX")).count());
		assertEquals(1, operations.stream().filter(s -> s.contains("CREATE INDEX")).count());
		assertTrue(sql(before, before.clone()).isEmpty());
	}

	@Test
	void replacesNamedBackingIndexBeforeRestoringItsKey() {
		var before = catalog();
		backing(before, "ux_parent");
		var after = before.clone();
		parent(after).getColumns().get("id").setDefaultValue("2");
		parent(after).getIndexes().get("ux_parent").setTableSpaceName("new_space");
		var operations = sql(before, after);
		int drop = index(operations, "DROP INDEX IF EXISTS");
		int modify = index(operations, "MODIFY");
		int create = index(operations, "CREATE UNIQUE INDEX");
		assertTrue(index(operations, "DROP CONSTRAINT \"parent_owner\".\"pk_parent\"") < drop, operations.toString());
		assertTrue(drop > 0 && drop < modify && modify < create, operations.toString());
		assertTrue(create < index(operations, "ADD CONSTRAINT PRIMARY KEY"), operations.toString());
		assertTrue(operations.get(drop).contains("\"index_owner\".\"ux_parent\""));
		assertTrue(operations.get(create).contains("IN \"new_space\""));
		assertFalse(operations.get(create).contains("IF NOT EXISTS"));
		assertEquals(1, operations.stream().filter(s -> s.contains("CREATE UNIQUE INDEX")).count());
		assertTrue(sql(after, before).stream()
				.anyMatch(s -> s.contains("CREATE UNIQUE INDEX") && s.contains("index space")));
	}

	@Test
	void preservesTargetDisabledAndFilteringModesOnIndependentIndexes() {
		var before = catalog();
		standalone(before, parent(before), "idx");
		var after = before.clone();
		parent(after).getIndexes().get("idx").setEnable(false);
		assertTrue(sql(before, after).stream().anyMatch(s -> s.contains("CREATE INDEX") && s.endsWith("DISABLED")));
		var filtering = before.clone();
		var index = parent(filtering).getIndexes().get("idx");
		index.setUnique(true);
		index.getSpecifics().put(com.sqlapp.data.db.dialect.informix.util.InformixIndexModes.MODE,
				"FILTERING WITH ERROR");
		assertTrue(sql(before, filtering).stream()
				.anyMatch(s -> s.contains("CREATE UNIQUE INDEX") && s.endsWith("FILTERING WITH ERROR")));
	}

	@Test
	void rejectsUnrepresentableIndependentIndexPayloadColumns() {
		var before = catalog();
		standalone(before, parent(before), "idx");
		var after = before.clone();
		parent(after).getIndexes().get("idx").getIncludes().add(parent(after).getColumns().get("id"));
		assertThrows(UnsupportedOperationException.class, () -> sql(before, after));
	}
}
