package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.sql.Postgres180CreateForeignKeyConstraintFactory;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres180CreateUniqueConstraintFactory;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.UniqueConstraint;

class PostgresTemporalConstraintMetadataTest {

	@Test
	void testRestoreWithoutOverlaps() {
		UniqueConstraint constraint = new UniqueConstraint("PK_ASSIGNMENTS", true);
		PostgresTemporalConstraintMetadata.apply(constraint,
				"PRIMARY KEY (employee_id, valid_at WITHOUT OVERLAPS)");
		assertEquals("true", constraint.getSpecifics().get(
				Postgres180CreateUniqueConstraintFactory.WITHOUT_OVERLAPS));
	}

	@Test
	void testRestorePeriodForeignKey() {
		ForeignKeyConstraint constraint =
				new ForeignKeyConstraint("FK_ASSIGNMENTS");
		PostgresTemporalConstraintMetadata.apply(constraint,
				"FOREIGN KEY (employee_id, PERIOD valid_at) "
						+ "REFERENCES employees (employee_id, PERIOD valid_at)");
		assertEquals("true", constraint.getSpecifics().get(
				Postgres180CreateForeignKeyConstraintFactory.PERIOD));
	}

	@Test
	void testNormalConstraintsRemainUnmarked() {
		UniqueConstraint unique = new UniqueConstraint("UK_CODE", false);
		PostgresTemporalConstraintMetadata.apply(unique, "UNIQUE (code)");
		assertNull(unique.getSpecifics().get(
				Postgres180CreateUniqueConstraintFactory.WITHOUT_OVERLAPS));

		ForeignKeyConstraint foreignKey = new ForeignKeyConstraint("FK_PARENT");
		PostgresTemporalConstraintMetadata.apply(foreignKey,
				"FOREIGN KEY (parent_id) REFERENCES parent (id)");
		assertNull(foreignKey.getSpecifics().get(
				Postgres180CreateForeignKeyConstraintFactory.PERIOD));
	}

	@Test
	void testRestoreNotEnforced() {
		CheckConstraint check = new CheckConstraint("CK_AMOUNT", "amount >= 0");
		PostgresTemporalConstraintMetadata.apply(check,
				"CHECK ((amount >= 0)) NOT ENFORCED");
		assertEquals("true", check.getSpecifics().get(
				com.sqlapp.data.db.dialect.postgres.sql.Postgres180CreateCheckConstraintFactory.NOT_ENFORCED));

		ForeignKeyConstraint foreignKey = new ForeignKeyConstraint("FK_PARENT");
		PostgresTemporalConstraintMetadata.apply(foreignKey,
				"FOREIGN KEY (parent_id) REFERENCES parent(id) NOT ENFORCED");
		assertEquals("true", foreignKey.getSpecifics().get(
				com.sqlapp.data.db.dialect.postgres.sql.Postgres180CreateCheckConstraintFactory.NOT_ENFORCED));
	}
}
