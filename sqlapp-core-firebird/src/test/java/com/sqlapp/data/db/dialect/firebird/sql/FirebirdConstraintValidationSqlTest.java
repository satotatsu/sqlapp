/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class FirebirdConstraintValidationSqlTest {
	private Table table(String name) {
		Table table = new Table(name);
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		return table;
	}

	@Test
	void checksRejectDisabledDeferredAndEmptyExpressionsAcrossEntryPoints() {
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		for (CheckConstraint check : new CheckConstraint[] { new CheckConstraint("ck", "ID>0").setEnable(false),
				new CheckConstraint("ck", "ID>0").setDeferrability(Deferrability.InitiallyDeferred),
				new CheckConstraint("ck", " ") }) {
			Table table = table("t");
			table.getConstraints().add(check);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(check, SqlType.CREATE));
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE_TEMPORARY));
		}
	}

	@Test
	void inlineChecksAndAlterUseTheSameValidation() {
		Table original = table("t");
		Table target = original.clone();
		target.getColumns().get(0).setCheckConstraint(new CheckConstraint("inline", "ID>0").setEnable(false));
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(target, SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void unsupportedConstraintKindsCannotDisappear() {
		Table table = table("t");
		table.getConstraints().add(new ExcludeConstraint("excluded"));
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
	}

	@Test
	void temporaryCheckAndForeignKeyUseResolvedPhysicalNames() {
		Table parent = table("parent").setTableType(Table.TableType.Temporary);
		Table child = table("child").setTableType(Table.TableType.Temporary);
		child.getConstraints().add(new CheckConstraint("ck", "ID>0"));
		ForeignKeyConstraint foreign = new ForeignKeyConstraint("fk", child.getColumns().get(0),
				parent.getColumns().get(0));
		child.getConstraints().add(foreign);
		SqlFactory<Table> factory = DialectHolder.defaultDialect25.createSqlFactoryRegistry().getSqlFactory(child,
				SqlType.CREATE_TEMPORARY);
		factory.getTableOptions().setTemporaryTableNamePrefix(t -> "tmp_");
		String sql = factory.createSql(child).get(0).getSqlText();
		assertTrue(sql.contains("CHECK"));
		assertTrue(sql.contains("FOREIGN KEY"));
		assertTrue(sql.contains("REFERENCES \"tmp_parent\""), sql);
		assertEquals("parent", foreign.getRelatedTable().getName());
	}

	@Test
	void invalidForeignKeysHaveActionableErrors() {
		Table parent = table("p");
		Table child = table("c");
		ForeignKeyConstraint foreign = new ForeignKeyConstraint("fk", child.getColumns().get(0),
				parent.getColumns().get(0));
		child.getConstraints().add(foreign);
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		foreign.setMatchOption(ForeignKeyConstraint.MatchOption.Full);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(child, SqlType.CREATE));
		foreign.setMatchOption(ForeignKeyConstraint.MatchOption.Simple);
		foreign.getRelatedColumns().clear();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(child, SqlType.CREATE));
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(new CheckConstraint("ck", "1=1"), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(new ForeignKeyConstraint("fk"), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(new UniqueConstraint("pk", true), SqlType.CREATE));
	}
}
