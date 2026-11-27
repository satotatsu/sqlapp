/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.*;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class FirebirdSchemaForeignKeyOrderTest {
	private Schema circular() {
		var schema = new Schema();
		for (String name : List.of("left table", "right table")) {
			var table = new Table(name);
			table.getColumns().add(new Column("id").setDataType(DataType.INT));
			table.getColumns().add(new Column("parent").setDataType(DataType.INT));
			var primary = new UniqueConstraint(name + " pk", true);
			primary.getColumns().add(table.getColumns().get("id"));
			table.getConstraints().add(primary);
			schema.getTables().add(table);
		}
		var left = schema.getTables().get(0);
		var right = schema.getTables().get(1);
		left.getConstraints().add(
				new ForeignKeyConstraint("left fk", left.getColumns().get("parent"), right.getColumns().get("id")));
		right.getConstraints().add(
				new ForeignKeyConstraint("right fk", right.getColumns().get("parent"), left.getColumns().get("id")));
		FirebirdSchemaDependencies.set(schema, List.of(
				new Dependency(new ObjectId(Kind.TABLE, left.getName()), new ObjectId(Kind.TABLE, right.getName())),
				new Dependency(new ObjectId(Kind.TABLE, right.getName()), new ObjectId(Kind.TABLE, left.getName()))));
		return schema;
	}

	@Test
	void circularKeysFollowBothTablesAndSurviveXmlWithoutChangingTheSource() throws Exception {
		var source = circular();
		String xml = source.asXml();
		var restored = SchemaUtils.readXml(new StringReader(xml));
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50)) {
			var plan = dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE);
			assertEquals(4, plan.size());
			assertTrue(plan.get(0).getSqlText().startsWith("CREATE TABLE"));
			assertTrue(plan.get(1).getSqlText().startsWith("CREATE TABLE"));
			assertFalse(plan.get(0).getSqlText().contains("FOREIGN KEY"));
			assertFalse(plan.get(1).getSqlText().contains("FOREIGN KEY"));
			assertTrue(plan.get(2).getSqlText().contains("ALTER TABLE \"left table\" ADD CONSTRAINT \"left fk\""));
			assertTrue(plan.get(3).getSqlText().contains("ALTER TABLE \"right table\" ADD CONSTRAINT \"right fk\""));
		}
		assertEquals(xml, source.asXml());
		assertEquals(xml, restored.asXml());
	}

	@Test
	void schemaAlterAddsKeysLastAndDropsKeysBeforeTables() throws Exception {
		var source = circular();
		String xml = source.asXml();
		var empty = new Schema();
		FirebirdSchemaDependencies.set(empty, List.of());
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50)) {
			var factory = dialect.createSqlFactoryRegistry().getSqlFactory(source, SqlType.ALTER);
			var add = factory.createDiffSql(empty.diff(source));
			assertEquals(4, add.size());
			assertTrue(add.get(0).getSqlText().startsWith("CREATE TABLE"));
			assertTrue(add.get(1).getSqlText().startsWith("CREATE TABLE"));
			assertTrue(add.get(2).getSqlText().contains("FOREIGN KEY"));
			assertTrue(add.get(3).getSqlText().contains("FOREIGN KEY"));
			var drop = factory.createDiffSql(source.diff(empty));
			assertEquals(4, drop.size());
			assertTrue(drop.get(0).getSqlText().contains("DROP CONSTRAINT \"left fk\""));
			assertTrue(drop.get(1).getSqlText().contains("DROP CONSTRAINT \"right fk\""));
			assertTrue(drop.get(2).getSqlText().startsWith("DROP TABLE"));
			assertTrue(drop.get(3).getSqlText().startsWith("DROP TABLE"));
		}
		assertEquals(xml, source.asXml());
	}

	@Test
	void addedTableCannotRetainAReferenceToARemovedProvider() {
		var before = circular();
		before.getTables().get(0).getConstraints().remove("left fk");
		before.getTables().remove("right table");
		var after = circular();
		after.getTables().get(0).getConstraints().remove("left fk");
		after.getTables().remove("left table");
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		assertThrows(UnsupportedOperationException.class,
				() -> registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(before.diff(after)));
	}

	@Test
	void constraintIndexesAreNotCreatedTwiceAndTheirCommentsFollowTheKey() {
		var schema = circular();
		var table = schema.getTables().get(0);
		var key = table.getConstraints().getForeignKeyConstraints().get(0);
		key.getSpecifics().put("firebird.indexName", "fk index");
		key.getSpecifics().put("firebird.indexDescending", "true");
		var index = new Index("fk index").setRemarks("index's comment");
		index.getColumns().add(table.getColumns().get("parent"));
		table.getIndexes().add(index);
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		var create = registry.createSql(schema, SqlType.CREATE).stream().map(operation -> operation.getSqlText())
				.toList();
		assertEquals(5, create.size());
		assertTrue(create.get(2).contains("USING DESCENDING INDEX \"fk index\""), create.toString());
		assertEquals("COMMENT ON INDEX \"fk index\" IS 'index''s comment'", create.get(3));
		assertFalse(create.stream().anyMatch(sql -> sql.startsWith("CREATE ASCENDING INDEX")));
		var drop = registry.createSql(key, SqlType.DROP);
		assertEquals("ALTER TABLE \"left table\" DROP CONSTRAINT \"left fk\"", drop.get(0).getSqlText());
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(new ForeignKeyConstraint("unowned"), SqlType.DROP));
	}

	@Test
	void addedTableAndExistingTableCanReferenceEachOther() {
		var after = circular();
		var before = after.clone();
		before.getTables().get(0).getConstraints().remove("left fk");
		before.getTables().remove("right table");
		FirebirdSchemaDependencies.set(before, List.of());
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		var add = registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(before.diff(after));
		assertEquals(3, add.size());
		assertTrue(add.get(0).getSqlText().startsWith("CREATE TABLE \"right table\""));
		assertTrue(add.get(1).getSqlText().contains("ADD CONSTRAINT \"left fk\""));
		assertTrue(add.get(2).getSqlText().contains("ADD CONSTRAINT \"right fk\""));
		var drop = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(after.diff(before));
		assertEquals(3, drop.size());
		assertTrue(drop.get(0).getSqlText().contains("DROP CONSTRAINT \"right fk\""));
		assertTrue(drop.get(1).getSqlText().contains("DROP CONSTRAINT \"left fk\""));
		assertTrue(drop.get(2).getSqlText().startsWith("DROP TABLE \"right table\""));
	}

	@Test
	void everyReferentialActionUsesFirebirdSyntaxAcrossCreateEntryPoints() {
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50)) {
			var registry = dialect.createSqlFactoryRegistry();
			for (var action : CascadeRule.values()) {
				var schema = circular();
				var table = schema.getTables().get(0);
				var key = table.getConstraints().getForeignKeyConstraints().get(0);
				key.setDeleteRule(action).setUpdateRule(action);
				String expected = switch (action) {
				case None, Restrict -> "NO ACTION";
				case Cascade -> "CASCADE";
				case SetNull -> "SET NULL";
				case SetDefault -> "SET DEFAULT";
				};
				for (Object object : List.<Object>of(key, table, schema)) {
					var sql = registry.createSql(object, SqlType.CREATE).stream()
							.map(operation -> operation.getSqlText()).toList();
					assertTrue(sql.stream().anyMatch(
							text -> text.contains("ON DELETE " + expected) && text.contains("ON UPDATE " + expected)),
							sql.toString());
					assertFalse(sql.stream().anyMatch(text -> text.contains(" RESTRICT") || text.contains(" NONE")),
							sql.toString());
				}
				assertEquals(action, key.getDeleteRule());
			}
		}
	}

	@Test
	void catalogConstraintIndexCommentsHaveAnExplicitStableTargetName() {
		var schema = circular();
		var table = schema.getTables().get(0);
		var key = table.getConstraints().getForeignKeyConstraints().get(0);
		key.getSpecifics().put("firebird.indexComment", "catalog comment");
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(schema, SqlType.CREATE));
		key.getSpecifics().put("firebird.indexName", "explicit index");
		for (Object object : List.<Object>of(key, table, schema)) {
			var plan = registry.createSql(object, SqlType.CREATE);
			assertEquals(1, plan.stream().filter(operation -> operation.getSqlText()
					.equals("COMMENT ON INDEX \"explicit index\" IS 'catalog comment'")).count());
		}
		for (var item : schema.getTables())
			item.setTableType(Table.TableType.Temporary);
		assertTrue(registry.createSql(table, SqlType.CREATE_TEMPORARY).stream().anyMatch(operation -> operation
				.getSqlText().equals("COMMENT ON INDEX \"explicit index\" IS 'catalog comment'")));
	}

	@Test
	void indexCommentChangesDoNotRebuildForeignKeys() {
		var source = circular();
		source.setDialect(DialectHolder.defaultDialect25);
		var table = source.getTables().get(0);
		var key = table.getConstraints().getForeignKeyConstraints().get(0);
		key.getSpecifics().put("firebird.catalog.indexName", "RDB$FOREIGN_TEST");
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		var target = table.clone();
		target.getConstraints().getForeignKeyConstraints().get(0).getSpecifics().put("firebird.indexComment",
				"new comment");
		var add = registry.createSql(table.diff(target), SqlType.ALTER);
		assertEquals(1, add.size());
		assertEquals("COMMENT ON INDEX RDB$FOREIGN_TEST IS 'new comment'", add.get(0).getSqlText());
		var remove = registry.createSql(target.diff(table), SqlType.ALTER);
		assertEquals(1, remove.size());
		assertEquals("COMMENT ON INDEX RDB$FOREIGN_TEST IS NULL", remove.get(0).getSqlText());
		var changed = target.clone();
		var changedKey = changed.getConstraints().getForeignKeyConstraints().get(0);
		changedKey.getSpecifics().put("firebird.indexName", "stable index");
		changedKey.setDeleteRule(CascadeRule.Cascade);
		var rebuild = registry.createSql(target.diff(changed), SqlType.ALTER);
		assertTrue(rebuild.stream().anyMatch(
				operation -> operation.getSqlText().equals("COMMENT ON INDEX \"stable index\" IS 'new comment'")));
	}

	@Test
	void onlyForeignKeyEdgesAreDeferredAndInvalidTableOptionsStillFail() {
		var schema = circular();
		schema.getViews().add(new View("V1").setStatement("SELECT * FROM V2"));
		schema.getViews().add(new View("V2").setStatement("SELECT * FROM V1"));
		var edges = new java.util.ArrayList<>(FirebirdSchemaDependencies.get(schema));
		edges.add(new Dependency(new ObjectId(Kind.VIEW, "V1"), new ObjectId(Kind.VIEW, "V2")));
		edges.add(new Dependency(new ObjectId(Kind.VIEW, "V2"), new ObjectId(Kind.VIEW, "V1")));
		FirebirdSchemaDependencies.set(schema, edges);
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(schema, SqlType.CREATE));
		var external = circular();
		external.getTables().get(0).getSpecifics().put("firebird.external.file", "/tmp/never-opened");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(external, SqlType.CREATE));
		var disabled = circular();
		disabled.getTables().get(0).getConstraints().getForeignKeyConstraints().get(0).setEnable(false);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(disabled, SqlType.CREATE));
	}
}
