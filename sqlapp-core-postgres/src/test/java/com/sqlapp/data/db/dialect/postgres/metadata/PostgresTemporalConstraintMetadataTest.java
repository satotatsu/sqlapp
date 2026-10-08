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
	void ignoresSyntaxWordsInsideIdentifiersAndCheckLiterals() {
		var fk = new ForeignKeyConstraint("fk");
		PostgresTemporalConstraintMetadata.apply(fk,
				"FOREIGN KEY (period_id, \"PERIOD value\") REFERENCES \"PERIOD\" (id, other)");
		assertNull(fk.getSpecifics().get("period"));
		var unique = new UniqueConstraint("uk", false);
		PostgresTemporalConstraintMetadata.apply(unique, "UNIQUE (\"WITHOUT OVERLAPS\")");
		assertNull(unique.getSpecifics().get("withoutOverlaps"));
		var check = new CheckConstraint("ck", "label <> 'NOT ENFORCED'");
		PostgresTemporalConstraintMetadata.apply(check, "CHECK (label <> 'NOT ENFORCED')");
		assertNull(check.getSpecifics().get("notEnforced"));
		PostgresTemporalConstraintMetadata.apply(fk,
				"FOREIGN KEY (id, PERIOD \"Valid Range\") REFERENCES parent(id, PERIOD \"Valid Range\")");
		assertEquals("true", fk.getSpecifics().get("period"));
	}

	@Test
	void testRestoreWithoutOverlaps() {
		UniqueConstraint constraint = new UniqueConstraint("PK_ASSIGNMENTS", true);
		PostgresTemporalConstraintMetadata.apply(constraint, "PRIMARY KEY (employee_id, valid_at WITHOUT OVERLAPS)");
		assertEquals("true", constraint.getSpecifics().get(Postgres180CreateUniqueConstraintFactory.WITHOUT_OVERLAPS));
	}

	@Test
	void testRestorePeriodForeignKey() {
		ForeignKeyConstraint constraint = new ForeignKeyConstraint("FK_ASSIGNMENTS");
		PostgresTemporalConstraintMetadata.apply(constraint,
				"FOREIGN KEY (employee_id, PERIOD valid_at) " + "REFERENCES employees (employee_id, PERIOD valid_at)");
		assertEquals("true", constraint.getSpecifics().get(Postgres180CreateForeignKeyConstraintFactory.PERIOD));
	}

	@Test
	void testNormalConstraintsRemainUnmarked() {
		UniqueConstraint unique = new UniqueConstraint("UK_CODE", false);
		PostgresTemporalConstraintMetadata.apply(unique, "UNIQUE (code)");
		assertNull(unique.getSpecifics().get(Postgres180CreateUniqueConstraintFactory.WITHOUT_OVERLAPS));

		ForeignKeyConstraint foreignKey = new ForeignKeyConstraint("FK_PARENT");
		PostgresTemporalConstraintMetadata.apply(foreignKey, "FOREIGN KEY (parent_id) REFERENCES parent (id)");
		assertNull(foreignKey.getSpecifics().get(Postgres180CreateForeignKeyConstraintFactory.PERIOD));
	}

	@Test
	void testRestoreNotEnforced() {
		CheckConstraint check = new CheckConstraint("CK_AMOUNT", "amount >= 0");
		PostgresTemporalConstraintMetadata.apply(check, "CHECK ((amount >= 0)) NOT ENFORCED");
		assertEquals("true", check.getSpecifics()
				.get(com.sqlapp.data.db.dialect.postgres.sql.Postgres180CreateCheckConstraintFactory.NOT_ENFORCED));

		ForeignKeyConstraint foreignKey = new ForeignKeyConstraint("FK_PARENT");
		PostgresTemporalConstraintMetadata.apply(foreignKey,
				"FOREIGN KEY (parent_id) REFERENCES parent(id) NOT ENFORCED");
		assertEquals("true", foreignKey.getSpecifics()
				.get(com.sqlapp.data.db.dialect.postgres.sql.Postgres180CreateCheckConstraintFactory.NOT_ENFORCED));
	}
	@Test
	void readsTerminalNotValidWithoutMatchingCheckLiterals() {
		for (String definition : new String[] {"CHECK (id > 0) NOT VALID", "CHECK (id > 0) NOT ENFORCED NOT VALID", "CHECK (id > 0) NOT VALID NOT ENFORCED"}) {
			var check = new CheckConstraint("positive","id > 0");
			PostgresTemporalConstraintMetadata.apply(check,definition);
			assertEquals("true",check.getSpecifics().get("notValid"));
		}
		var check = new CheckConstraint("message","label <> 'NOT VALID'");
		PostgresTemporalConstraintMetadata.apply(check,"CHECK (label <> 'NOT VALID')");
		assertNull(check.getSpecifics().get("notValid"));
		var fk = new ForeignKeyConstraint("fk");
		PostgresTemporalConstraintMetadata.apply(fk,"FOREIGN KEY (id) REFERENCES parent(id) NOT VALID");
		assertEquals("true",fk.getSpecifics().get("notValid"));
	}

}
