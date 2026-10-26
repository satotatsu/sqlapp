/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class Db2NativeAlterTest {
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private List<String> alter(AbstractNamedObject before, AbstractNamedObject after) {
		SqlFactory<AbstractNamedObject> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry()
				.getSqlFactory(after, SqlType.ALTER);
		return factory.createDiffSql(before.diff(after)).stream().map(SqlOperation::getSqlText).toList();
	}

	private Sequence sequence() {
		return new Sequence("S\"Q").setSchemaName("A.Q").setDataType(DataType.BIGINT).setStartValue(BigInteger.ONE)
				.setIncrementBy(BigInteger.ONE).setMinValue(BigInteger.ONE).setMaxValue(BigInteger.valueOf(1000))
				.setCacheSize(20).setCache(true).setCycle(false).setOrder(false);
	}

	@Test
	void sequenceSettingsChangeWithoutCreateDropOrRestartThroughSchemaDiff() {
		Sequence before = sequence(), after = before.clone().setIncrementBy(BigInteger.TWO)
				.setMinValue((BigInteger) null).setMaxValue((BigInteger) null).setCycle(true).setCache(false)
				.setOrder(true).setRemarks("new's comment");
		var expected = List.of(
				"ALTER SEQUENCE \"A.Q\".\"S\"\"Q\" INCREMENT BY 2 NO MINVALUE NO MAXVALUE CYCLE NO CACHE ORDER",
				"COMMENT ON SEQUENCE \"A.Q\".\"S\"\"Q\" IS 'new''s comment'");
		assertEquals(expected, alter(before, after));
		Schema oldSchema = new Schema("A.Q"), newSchema = new Schema("A.Q");
		oldSchema.getSequences().add(before);
		newSchema.getSequences().add(after);
		assertEquals(expected, alter(oldSchema, newSchema));
		assertEquals(List.of(), alter(after, after.clone()));
	}

	@Test
	void sequenceSupportsDescendingExplicitBoundsCacheAndOrderChanges() {
		Sequence before = sequence().setCycle(true).setOrder(true),
				after = before.clone().setCycle(false).setOrder(false).setIncrementBy(BigInteger.valueOf(-3))
						.setMinValue(BigInteger.valueOf(-100)).setMaxValue(BigInteger.valueOf(999)).setCacheSize(40);
		assertEquals(List.of(
				"ALTER SEQUENCE \"A.Q\".\"S\"\"Q\" INCREMENT BY -3 MINVALUE -100 MAXVALUE 999 NO CYCLE CACHE 40 NO ORDER"),
				alter(before, after));
	}

	@Test
	void sequenceCacheAllocationDriftIsNotAConfigurationChange() {
		Sequence before = sequence();
		before.getSpecifics().put("NEXTCACHEFIRSTVALUE", "101");
		Sequence after = before.clone();
		after.getSpecifics().put("NEXTCACHEFIRSTVALUE", "201");
		assertEquals(List.of(), alter(before, after));
		after.setIncrementBy(BigInteger.TWO);
		assertEquals(List.of("ALTER SEQUENCE \"A.Q\".\"S\"\"Q\" INCREMENT BY 2"), alter(before, after));
		after.getSpecifics().put("UNKNOWN_SETTING", "value");
		assertThrows(IllegalArgumentException.class, () -> alter(before, after));
	}

	@Test
	void sequenceRestartTypeRuntimeStateAndInvalidConfigurationRequireExplicitPlan() {
		Sequence before = sequence();
		for (Sequence after : List.of(before.clone().setStartValue(BigInteger.TEN),
				before.clone().setDataType(DataType.INT), before.clone().setLastValue(BigInteger.TEN),
				before.clone().setIncrementBy(BigInteger.ZERO), before.clone().setCacheSize(1),
				before.clone().setIncrementBy((BigInteger) null), before.clone().setScale(1)))
			assertThrows(IllegalArgumentException.class, () -> alter(before, after));
		SqlFactory<Sequence> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(before,
				SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(before));
		before.setDefinition("CREATE SEQUENCE \"A.Q\".\"S\"\"Q\" AS BIGINT");
		assertThrows(IllegalArgumentException.class,
				() -> alter(before, before.clone().setIncrementBy(BigInteger.TWO)));
	}

	@Test
	void viewTriggerAndAliasCommentsChangeAndClearWithoutObjectReplacement() {
		List<AbstractNamedObject<?>> objects = List.<AbstractNamedObject<?>>of(new View("V\"Q").setSchemaName("APP"),
				new Trigger("TR\"Q").setSchemaName("APP"), new Synonym("A\"Q").setSchemaName("SYSPUBLIC"));
		var designators = List.of("TABLE \"APP\".\"V\"\"Q\"", "TRIGGER \"APP\".\"TR\"\"Q\"",
				"PUBLIC ALIAS \"SYSPUBLIC\".\"A\"\"Q\" FOR TABLE");
		for (int i = 0; i < objects.size(); i++) {
			var before = objects.get(i);
			var after = before.clone().setRemarks("new's comment");
			assertEquals(List.of("COMMENT ON " + designators.get(i) + " IS 'new''s comment'"), alter(before, after));
			assertEquals(List.of("COMMENT ON " + designators.get(i) + " IS NULL"), alter(after, before));
			assertEquals(List.of(), alter(before, before.clone()));
		}
	}

	private Table table() {
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add("ID", column -> column.setDataType(DataType.INT));
		Index index = new Index("I\"Q");
		index.getColumns().add("ID");
		index.getSpecifics().putAll(Map.of("INDEX_SCHEMA", "IX.Q", "INDEXTYPE", "REG ", "COMPRESSION", "N"));
		table.getIndexes().add(index);
		return table;
	}

	@Test
	void indexCompressionAndCommentUseNativeAlterThroughTableDiff() {
		Table before = table(), after = before.clone();
		Index index = after.getIndexes().getFirst();
		index.setCompression(true).setRemarks("changed");
		index.getSpecifics().put("COMPRESSION", "Y");
		var expected = List.of("ALTER INDEX \"IX.Q\".\"I\"\"Q\" COMPRESS YES",
				"COMMENT ON INDEX \"IX.Q\".\"I\"\"Q\" IS 'changed'");
		assertEquals(expected, alter(before.getIndexes().getFirst(), index));
		assertEquals(expected, alter(before, after));
		assertEquals(
				List.of("ALTER INDEX \"IX.Q\".\"I\"\"Q\" COMPRESS NO", "COMMENT ON INDEX \"IX.Q\".\"I\"\"Q\" IS NULL"),
				alter(index, before.getIndexes().getFirst()));
	}

	@Test
	void specializedTemporaryAndStructuralIndexChangesDoNotMasqueradeAsNativeAlter() {
		Table before = table(), after = before.clone();
		after.getIndexes().getFirst().setCompression(true);
		after.getIndexes().getFirst().getSpecifics().put("INDEXTYPE", "XVIP");
		Table specialized = after;
		assertThrows(IllegalArgumentException.class,
				() -> alter(before.getIndexes().getFirst(), specialized.getIndexes().getFirst()));
		after = before.clone();
		after.setTableType(Table.TableType.Temporary);
		after.getIndexes().getFirst().setCompression(true);
		Table temporary = after;
		assertThrows(IllegalArgumentException.class,
				() -> alter(before.getIndexes().getFirst(), temporary.getIndexes().getFirst()));
		Index changed = before.getIndexes().getFirst().clone();
		changed.getColumns().getFirst().setOrder(Order.Desc);
		assertThrows(IllegalArgumentException.class, () -> alter(before.getIndexes().getFirst(), changed));
	}

	@Test
	void commentChangesPropagateThroughSchemaAndCatalogAfterXml() throws Exception {
		Schema before = new Schema("APP");
		before.getViews().add(new View("V"));
		before.getTriggers().add(new Trigger("TR"));
		before.getSynonyms().add(new Synonym("A"));
		before.getSequences().add(new Sequence("S"));
		Schema after = SchemaUtils.readXml(new java.io.StringReader(before.asXml()));
		after.getViews().getFirst().setRemarks("view");
		after.getTriggers().getFirst().setRemarks("trigger");
		after.getSynonyms().getFirst().setRemarks("alias");
		after.getSequences().getFirst().setRemarks("sequence");
		var sql = alter(before, after);
		assertEquals(4, sql.size(), sql.toString());
		assertTrue(sql.stream().allMatch(s -> s.startsWith("COMMENT ON ")), sql.toString());
		Catalog original = new Catalog(), target = new Catalog();
		original.getSchemas().add(before);
		target.getSchemas().add(after);
		SqlFactory<Catalog> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(target,
				SqlType.ALTER);
		assertEquals(new HashSet<>(sql), new HashSet<>(
				factory.createDiffSql(original.diff(target)).stream().map(SqlOperation::getSqlText).toList()));
	}

	@Test
	void alterFactoriesDispatchAddedAndDeletedObjectsAndRejectTargetOnlyCalls() {
		List<AbstractNamedObject<?>> objects = List.<AbstractNamedObject<?>>of(
				new View("V").setSchemaName("APP").setDefinition("CREATE VIEW APP.V AS SELECT 1 FROM SYSIBM.SYSDUMMY1"),
				new Trigger("TR").setSchemaName("APP")
						.setDefinition("CREATE TRIGGER APP.TR AFTER INSERT ON APP.T FOR EACH ROW VALUES 1"),
				new Synonym("A").setSchemaName("APP").setObjectName("T").setObjectSchemaName("APP"), sequence(),
				new Index("I").setSchemaName("APP").setDefinition("CREATE INDEX APP.I ON APP.T (ID)"));
		for (var object : objects) {
			SqlFactory<Object> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(object,
					SqlType.ALTER);
			assertThrows(IllegalArgumentException.class, () -> factory.createSql(object));
			var added = factory.createDiffSql(new DbObjectDifference(null, object, new EqualsHandler()));
			assertTrue(added.getFirst().getSqlText().startsWith("CREATE"), object.toString());
			var deleted = factory.createDiffSql(new DbObjectDifference(object, null, new EqualsHandler()));
			assertTrue(deleted.getFirst().getSqlText().startsWith("DROP"), object.toString());
		}
	}
}
