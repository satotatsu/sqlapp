package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.NotNullConstraint;
import com.sqlapp.data.schemas.Table;

class Postgres180NotNullConstraintFactoryTest {
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	void testAlterTableAddNamedNotNullConstraint() {
		Table table = new Table("CUSTOMERS");
		Column column = new Column("CUSTOMER_ID")
				.setDataType(DataType.BIGINT);
		table.getColumns().add(column);
		NotNullConstraint constraint = new NotNullConstraint(
				"NN_CUSTOMERS_CUSTOMER_ID", column)
				.setNoInherit(true).setValidated(false);
		table.getConstraints().add(constraint);

		SqlFactory factory = DialectHolder.postgreSQL180
				.createSqlFactoryRegistry()
				.getSqlFactory(constraint, SqlType.CREATE);
		String sql = ((SqlOperation) factory.createSql(constraint).get(0))
				.getSqlText();
		assertTrue(sql.contains("ALTER TABLE \"CUSTOMERS\" ADD CONSTRAINT"),
				sql);
		assertTrue(sql.contains(
				"\"NN_CUSTOMERS_CUSTOMER_ID\" NOT NULL \"CUSTOMER_ID\" NO INHERIT NOT VALID"),
				sql);
	}

	@Test
	void testFactoryIsPostgres18Only() {
		Table table = new Table("CUSTOMERS");
		Column column = new Column("CUSTOMER_ID");
		table.getColumns().add(column);
		NotNullConstraint constraint = new NotNullConstraint("NN_ID",
				column);
		table.getConstraints().add(constraint);
		assertTrue(DialectHolder.postgreSQL170.createSqlFactoryRegistry()
				.createSql(constraint, SqlType.CREATE).isEmpty());
	}
}
