package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;

class Postgres180TemporalConstraintTest extends AbstractPostgresSqlFactoryTest {
	@Override
	protected int getMajorVersion() {
		return 18;
	}

	@Test
	void testWithoutOverlapsPrimaryKey() {
		Table table = table("EMPLOYEE_ASSIGNMENTS");
		UniqueConstraint pk = table.getPrimaryKeyConstraint();
		pk.getSpecifics().put(
				Postgres180CreateUniqueConstraintFactory.WITHOUT_OVERLAPS, "true");
		SqlFactory<UniqueConstraint> factory =
				sqlFactoryRegistry.getSqlFactory(pk, SqlType.CREATE);
		String sql = normalize(factory.createSql(pk).get(0).getSqlText());
		assertTrue(sql.contains(
				"PRIMARY KEY (EMPLOYEE_ID, VALID_AT WITHOUT OVERLAPS)"), sql);
	}

	@Test
	void testPeriodForeignKey() {
		Table parent = table("EMPLOYEES");
		Table child = table("ASSIGNMENTS");
		ForeignKeyConstraint fk = child.getConstraints()
				.addForeignKeyConstraint("FK_ASSIGNMENTS",
						new com.sqlapp.data.schemas.Column[] {
							child.getColumns().get("EMPLOYEE_ID"),
							child.getColumns().get("VALID_AT") },
						new com.sqlapp.data.schemas.Column[] {
							parent.getColumns().get("EMPLOYEE_ID"),
							parent.getColumns().get("VALID_AT") });
		fk.getSpecifics().put(
				Postgres180CreateForeignKeyConstraintFactory.PERIOD, "true");
		SqlFactory<ForeignKeyConstraint> factory =
				sqlFactoryRegistry.getSqlFactory(fk, SqlType.CREATE);
		String sql = normalize(factory.createSql(fk).get(0).getSqlText());
		assertTrue(sql.contains(
				"FOREIGN KEY (EMPLOYEE_ID, PERIOD VALID_AT)"), sql);
		assertTrue(sql.contains(
				"REFERENCES EMPLOYEES (EMPLOYEE_ID, PERIOD VALID_AT)"), sql);
	}

	@Test
	void testNormalConstraintRemainsNormal() {
		Table table = table("EMPLOYEES");
		UniqueConstraint pk = table.getPrimaryKeyConstraint();
		SqlFactory<UniqueConstraint> factory =
				sqlFactoryRegistry.getSqlFactory(pk, SqlType.CREATE);
		assertFalse(factory.createSql(pk).get(0).getSqlText()
				.contains("WITHOUT OVERLAPS"));
	}

	private Table table(String name) {
		Table table = new Table(name);
		table.setDialect(dialect);
		table.getColumns().add("EMPLOYEE_ID",
				c -> c.setDataType(DataType.INT));
		table.getColumns().add("VALID_AT",
				c -> c.setDataType(DataType.OTHER).setDataTypeName("daterange"));
		table.setPrimaryKey(table.getColumns().get("EMPLOYEE_ID"),
				table.getColumns().get("VALID_AT"));
		return table;
	}

	private String normalize(String sql) {
		return sql.replace("\"", "").replaceAll("\\s+", " ")
				.replace("( ", "(").replace(" )", ")");
	}
}
