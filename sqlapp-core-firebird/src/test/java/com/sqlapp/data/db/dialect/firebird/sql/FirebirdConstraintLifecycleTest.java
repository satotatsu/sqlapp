/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class FirebirdConstraintLifecycleTest {
	@Test
	void namedConstraintsDropThroughTheirOwningTableAcrossSupportedVersions() {
		for (var dialect : List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50)) {
			var table = new Table("owner \" table").setSchemaName("logical schema");
			for (Constraint constraint : List.<Constraint>of(new UniqueConstraint("primary \" key", true),
					new UniqueConstraint("unique key", false), new CheckConstraint("check key", null),
					new ForeignKeyConstraint("foreign key"))) {
				table.getConstraints().add(constraint);
				var operations = dialect.createSqlFactoryRegistry().createSql(constraint, SqlType.DROP);
				assertEquals(1, operations.size());
				assertEquals(
						"ALTER TABLE \"owner \"\" table\" DROP CONSTRAINT \""
								+ constraint.getName().replace("\"", "\"\"") + "\"",
						operations.getFirst().getSqlText());
				assertEquals(SqlType.DROP, operations.getFirst().getSqlType());
				assertSame(constraint, operations.getFirst().getOriginal());
			}
		}
	}

	@Test
	void identityOnlyMetadataNeedsNoConstraintExpressionOrColumnDuplication() {
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		for (Constraint constraint : List.<Constraint>of(new UniqueConstraint("PK", true),
				new CheckConstraint("CK", null), new ForeignKeyConstraint("FK"))) {
			constraint.setTableName("T");
			assertEquals("ALTER TABLE T DROP CONSTRAINT " + constraint.getName(),
					registry.createSql(constraint, SqlType.DROP).getFirst().getSqlText());
		}
	}

	@Test
	void missingOwnerAndInvalidIdentifiersFailBeforeReturningSql() {
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		for (Constraint constraint : List.<Constraint>of(new UniqueConstraint("PK", true),
				new CheckConstraint("CK", null), new ForeignKeyConstraint("FK")))
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(constraint, SqlType.DROP));
		var invalid = new CheckConstraint("bad\0name", null);
		invalid.setTableName("T");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(invalid, SqlType.DROP));
	}

	@Test
	void columnChecksResolveTheColumnOwnerWithoutRebuildingTheColumn() {
		var table = new Table("T");
		var column = new Column("ID").setDataType(com.sqlapp.data.db.datatype.DataType.INT);
		table.getColumns().add(column);
		var check = new CheckConstraint("CK", "ID > 0");
		column.setCheckConstraint(check);
		var operations = DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(check, SqlType.DROP);
		assertEquals(List.of("ALTER TABLE T DROP CONSTRAINT CK"),
				operations.stream().map(operation -> operation.getSqlText()).toList());
	}
}
