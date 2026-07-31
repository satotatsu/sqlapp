package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Table;

class Postgres180ConstraintDropBuilderTest {

	@Test
	void testDropOnlyFromPartitionedParent() {
		CheckConstraint constraint = constraint();

		assertEquals(
				"ALTER TABLE ONLY sales.orders DROP CONSTRAINT ck_orders_total",
				new Postgres180ConstraintDropBuilder(
						DialectHolder.postgreSQL180)
						.dropOnly(constraint));
	}

	@Test
	void testDropOnlyWithIfExistsAndCascade() {
		assertEquals(
				"ALTER TABLE ONLY sales.orders DROP CONSTRAINT IF EXISTS ck_orders_total CASCADE",
				new Postgres180ConstraintDropBuilder(
						DialectHolder.postgreSQL180)
						.drop(constraint(), true, true, true));
	}

	@Test
	void testRejectDropOnlyBeforePostgres18() {
		Postgres180ConstraintDropBuilder builder =
				new Postgres180ConstraintDropBuilder(
						DialectHolder.postgreSQL170);

		assertThrows(IllegalArgumentException.class,
				() -> builder.dropOnly(constraint()));
	}

	@Test
	void testOrdinaryDropRemainsAvailableBeforePostgres18() {
		assertEquals(
				"ALTER TABLE sales.orders DROP CONSTRAINT ck_orders_total",
				new Postgres180ConstraintDropBuilder(
						DialectHolder.postgreSQL170)
						.drop(constraint()));
	}

	private CheckConstraint constraint() {
		Table table = new Table("orders").setSchemaName("sales");
		CheckConstraint constraint = new CheckConstraint(
				"ck_orders_total", "total >= 0");
		table.getConstraints().add(constraint);
		return constraint;
	}
}
