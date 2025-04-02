package com.sqlapp.data.db.dialect.mysql.db.datatype.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.TypeInformation;

class MysqlUnsignedNumberColumnTypeMatcherTest {

	@Test
	void test() {
		ColumnTypeMatcher columnTypeNameMatcher = createMatcher("INT", "INTEGER");
		TypeInformation column = createCoumn("int unsigned zerofill", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertTrue(column.getDataTypeName().isEmpty());
		assertTrue(column.getSpecifics().isPresent());
		assertEquals("true", column.getSpecifics().get().get("zerofill"));
		//
		column = createCoumn("integer unsigned zerofill", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertTrue(column.getDataTypeName().isEmpty());
		assertTrue(column.getSpecifics().isPresent());
		assertEquals("true", column.getSpecifics().get().get("zerofill"));
		//
		column = createCoumn("integer unsigned", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertTrue(column.getDataTypeName().isEmpty());
		assertTrue(column.getSpecifics().isEmpty());
		Optional<TypeInformation> optional = createCoumnOptional("INTEGER zerofill", columnTypeNameMatcher);
		assertTrue(optional.isEmpty());
		//
		columnTypeNameMatcher = createMatcher("BIGINT");
		column = createCoumn("bigint unsigned zerofill", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertTrue(column.getDataTypeName().isEmpty());
		assertTrue(column.getSpecifics().isPresent());
		assertEquals("true", column.getSpecifics().get().get("zerofill"));
	}

	private ColumnTypeMatcher createMatcher(String... dataTypeName) {
		ColumnTypeMatcher internalMatcher = new MysqlUnsignedNumberColumnTypeMatcher(dataTypeName);
		return internalMatcher;
	}

	private Optional<TypeInformation> createCoumnOptional(String value, ColumnTypeMatcher columnTypeMatcher) {
		Optional<TypeInformation> columnOp = columnTypeMatcher.match(value);
		return columnOp;
	}

	private TypeInformation createCoumn(String value, ColumnTypeMatcher columnTypeMatcher) {
		Optional<TypeInformation> columnOp = columnTypeMatcher.match(value);
		return columnOp.get();
	}

}
