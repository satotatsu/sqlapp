package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.NotNullConstraint;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class Postgres180NotNullConstraintBuilderTest {
	private final Postgres180NotNullConstraintBuilder builder =
			new Postgres180NotNullConstraintBuilder(
					DialectHolder.postgreSQL180);

	@Test
	void testConstraintStateAlterations() {
		NotNullConstraint constraint = constraint();
		assertEquals(
				"ALTER TABLE public.customers ALTER CONSTRAINT nn_customer_id NOT VALID",
				builder.setNotValid(constraint));
		assertEquals(
				"ALTER TABLE public.customers VALIDATE CONSTRAINT nn_customer_id",
				builder.validate(constraint));
		assertEquals(
				"ALTER TABLE public.customers ALTER CONSTRAINT nn_customer_id NO INHERIT",
				builder.setNoInherit(constraint, true));
		assertEquals(
				"ALTER TABLE public.customers ALTER CONSTRAINT nn_customer_id INHERIT",
				builder.setNoInherit(constraint, false));
	}

	@Test
	void testVersionAndParentValidation() {
		Postgres180NotNullConstraintBuilder postgres17 =
				new Postgres180NotNullConstraintBuilder(
						DialectHolder.postgreSQL170);
		assertThrows(IllegalArgumentException.class,
				() -> postgres17.validate(constraint()));
		assertThrows(IllegalArgumentException.class,
				() -> builder.validate(
						new NotNullConstraint("nn_customer_id")));
	}

	private NotNullConstraint constraint() {
		Schema schema = new Schema("public");
		Table table = new Table("customers");
		Column column = new Column("customer_id");
		schema.getTables().add(table);
		table.getColumns().add(column);
		NotNullConstraint constraint = new NotNullConstraint(
				"nn_customer_id", column);
		table.getConstraints().add(constraint);
		return constraint;
	}
}
