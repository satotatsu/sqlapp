package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;

class IntervalXmlPreservationTest {
	@Test
	void retainsIntervalModifiersForEveryColumnLikeObject() throws Exception {
		for (DataType type : new DataType[] { DataType.INTERVAL, DataType.INTERVAL_YEAR, DataType.INTERVAL_DAY_TO_SECOND }) {
			for (int precision : new int[] { 0, 3, 9 }) {
				verify(new Column("value").setDataType(type).setLength(precision).setScale(0).setArrayDimension(2));
				verify(new Domain("value").setDataType(type).setLength(precision).setScale(6).setArrayDimension(1));
				verify(new TypeColumn("value").setDataType(type).setLength(precision).setScale(3));
				verify(new NamedArgument("value").setDataType(type).setLength(precision).setScale(0));
			}
		}
	}

	@Test
	void distinguishesIntervalPrecisionChanges() {
		for (DataType type : new DataType[] { DataType.INTERVAL, DataType.INTERVAL_YEAR, DataType.INTERVAL_DAY_TO_SECOND }) {
			Column original = new Column("value").setDataType(type).setLength(0);
			assertNotEquals(original, original.clone().setLength(3));
			assertEquals(original, original.clone());
		}
	}

	@Test
	void retainsAbsentModifiersInExistingXml() throws Exception {
		for (DataType type : new DataType[] { DataType.INTERVAL, DataType.INTERVAL_YEAR, DataType.INTERVAL_DAY_TO_SECOND }) {
			Column restored = SchemaUtils.readXml(new java.io.StringReader(new Column("value").setDataType(type).asXml()));
			assertNull(restored.getLength()); assertNull(restored.getScale());
		}
	}

	private void verify(AbstractColumn<?> original) throws Exception {
		AbstractColumn<?> restored;
		if (original instanceof TypeColumn column) {
			Type owner = new Type("type"); owner.getColumns().add(column);
			Type copy = SchemaUtils.readXml(new java.io.StringReader(owner.asXml()));
			restored = copy.getColumns().get(0);
		} else if (original instanceof NamedArgument argument) {
			Procedure owner = new Procedure("procedure"); owner.getArguments().add(argument);
			Procedure copy = SchemaUtils.readXml(new java.io.StringReader(owner.asXml()));
			restored = copy.getArguments().get(0);
		} else {
			restored = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
		}
		assertEquals(original.getClass(), restored.getClass());
		assertEquals(original.getDataType(), restored.getDataType());
		assertEquals(original.getLength(), restored.getLength(), original.asXml());
		assertEquals(original.getScale(), restored.getScale(), original.asXml());
		assertEquals(original.getArrayDimension(), restored.getArrayDimension());
	}
}
