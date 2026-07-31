package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;

class Postgres180NotEnforcedConstraintTest
		extends AbstractPostgresSqlFactoryTest {
	@Override
	protected int getMajorVersion() {
		return 18;
	}

	@Test
	void testCheckNotEnforced() {
		Table table = table("ORDERS");
		CheckConstraint check = new CheckConstraint("CK_AMOUNT", "AMOUNT >= 0");
		table.getConstraints().add(check);
		check.getSpecifics().put(
				Postgres180CreateCheckConstraintFactory.NOT_ENFORCED, "true");
		SqlFactory<CheckConstraint> factory =
				sqlFactoryRegistry.getSqlFactory(check, SqlType.CREATE);
		String sql = factory.createSql(check).get(0).getSqlText();
		assertTrue(sql.contains("NOT ENFORCED"), sql);
	}

	@Test
	void testForeignKeyNotEnforced() {
		Table parent = table("PARENT");
		Table child = table("CHILD");
		ForeignKeyConstraint fk = child.getConstraints()
				.addForeignKeyConstraint("FK_CHILD", child.getColumns().get("ID"),
						parent.getColumns().get("ID"));
		fk.getSpecifics().put(
				Postgres180CreateCheckConstraintFactory.NOT_ENFORCED, "true");
		SqlFactory<ForeignKeyConstraint> factory =
				sqlFactoryRegistry.getSqlFactory(fk, SqlType.CREATE);
		String sql = factory.createSql(fk).get(0).getSqlText();
		assertTrue(sql.contains("NOT ENFORCED"), sql);
	}

	private Table table(String name) {
		Table table = new Table(name);
		table.setDialect(dialect);
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("AMOUNT").setDataType(DataType.INT));
		table.setPrimaryKey(table.getColumns().get("ID"));
		return table;
	}
}
