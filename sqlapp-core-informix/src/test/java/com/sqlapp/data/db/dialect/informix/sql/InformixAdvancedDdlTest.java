/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.informix.metadata.InformixTableReader.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.dialect.informix.metadata.InformixIndexReader;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class InformixAdvancedDdlTest {
	@Test void createsBackingIndexBeforeItsUniqueConstraintThroughXml() throws Exception {
		final var table = new Table("items");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		final var index = new Index("items_ix").setUnique(true).setTableSpaceName("rootdbs");
		index.getColumns().add(table.getColumns().get(0));
		table.getIndexes().add(index);
		final var unique = new UniqueConstraint("items_uk", false);
		unique.getColumns().add(table.getColumns().get(0));
		table.getConstraints().add(unique);
		unique.setIndexName(index.getName());
		unique.getSpecifics().put(INFORMIX_BACKING_INDEX_REQUIRED, "true");
		final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		final var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE);
		assertEquals(3, operations.size());
		assertFalse(operations.get(0).getSqlText().contains("UNIQUE"));
		assertTrue(operations.get(1).getSqlText().contains("CREATE UNIQUE INDEX"));
		assertTrue(operations.get(2).getSqlText().contains("ALTER TABLE"));
		assertTrue(operations.get(2).getSqlText().contains("ADD CONSTRAINT UNIQUE"), operations.get(2).getSqlText());
		assertTrue(operations.get(2).getSqlText().contains("items_uk"));
	}
	@Test void createsTypedTableWithoutFlatteningItsInheritedColumns() throws Exception {
		final var parent = new Table("parent").setSchemaName("owner");
		final var child = new Table("child").setSchemaName("owner");
		child.getSpecifics().put(INFORMIX_ROW_TYPE, "child_t");
		child.getSpecifics().put(INFORMIX_ROW_TYPE_OWNER, "owner");
		child.getColumns().add("inherited_id", c -> c.setDataType(DataType.INT));
		child.getInherits().add(parent);
		final Table restored = SchemaUtils.readXml(new StringReader(child.asXml()));
		final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("OF TYPE \"owner\".\"child_t\" UNDER \"owner\".\"parent\""), sql);
		assertFalse(sql.contains("inherited_id"));
		restored.getSpecifics().remove(INFORMIX_ROW_TYPE);
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE));
	}
	@Test void keepsSameNamedChildConstraintsWithDifferentOwners() {
		final var parent = new Table("parent").setSchemaName("parent_owner");
		parent.getConstraints().addCheckConstraint("shared","id>0");
		final var child = new Table("child").setSchemaName("child_owner");
		child.getSpecifics().put(INFORMIX_ROW_TYPE,"child_t"); child.getSpecifics().put(INFORMIX_ROW_TYPE_OWNER,"child_owner");
		child.getInherits().add(parent);
		final var own = child.getConstraints().addCheckConstraint("shared","extra>0");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var operations = registry.createSql(child,SqlType.CREATE);
		assertEquals(2,operations.size()); assertTrue(operations.get(1).getSqlText().contains("extra>0"));
		own.getSpecifics().put(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER,"parent_owner");
		assertEquals(1,registry.createSql(child,SqlType.CREATE).size());
	}
	@Test void requiresAnExplicitStorageMappingWhenVirtualDataLocationIsUnknown() {
		final var table = new Table("items");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getIndexes().add("ix", table.getColumns().get(0));
		final var index = table.getIndexes().get(0);
		index.getSpecifics().put(InformixIndexReader.INFORMIX_INDEX_STORAGE_OVERRIDE_REQUIRED, "true");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.CREATE));
		index.setTableSpaceName("actual_sbspace");
		assertTrue(registry.createSql(index, SqlType.CREATE).get(0).getSqlText().endsWith("IN \"actual_sbspace\""));
	}
	@Test void rendersNativeComplexColumnDeclarationsThroughXmlAndRejectsInvalidFlags() throws Exception {
		final var table = new Table("items");
		table.getColumns().add("values", c -> {
			c.setDataType(DataType.ARRAY).setDataTypeName("SET(ROW(\"a\" INTEGER) NOT NULL)");
			c.getSpecifics().put(com.sqlapp.data.db.dialect.informix.metadata.InformixColumnReader.INFORMIX_NATIVE_TYPE,"SET(ROW(\"a\" INTEGER) NOT NULL)");
		});
		final Table xml = SchemaUtils.readXml(new StringReader(table.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final String sql = registry.createSql(xml,SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("SET(ROW(\"a\" INTEGER) NOT NULL)"),sql);
		xml.getColumns().get(0).getSpecifics().put(com.sqlapp.data.db.dialect.informix.metadata.InformixColumnReader.INFORMIX_NATIVE_TYPE,"false");
		assertThrows(IllegalArgumentException.class,() -> registry.createSql(xml,SqlType.CREATE));
	}
	@Test void rejectsStaleNativeDeclarationsAfterChangingTheColumnType() {
		final var table = new Table("items");
		table.getColumns().add("value",c -> {
			c.setDataType(DataType.ARRAY).setDataTypeName("SET(INTEGER NOT NULL)");
			c.getSpecifics().put(com.sqlapp.data.db.dialect.informix.metadata.InformixColumnReader.INFORMIX_NATIVE_TYPE,"SET(INTEGER NOT NULL)");
		});
		final var column = table.getColumns().get(0);
		column.setDataType(DataType.INT).setDataTypeName("INTEGER");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var failure = assertThrows(IllegalArgumentException.class,() -> registry.createSql(table,SqlType.CREATE));
		assertTrue(failure.getMessage().contains("update INFORMIX_NATIVE_TYPE"));
		column.getSpecifics().remove(com.sqlapp.data.db.dialect.informix.metadata.InformixColumnReader.INFORMIX_NATIVE_TYPE);
		assertFalse(registry.createSql(table,SqlType.CREATE).get(0).getSqlText().contains("SET("));
	}
}
