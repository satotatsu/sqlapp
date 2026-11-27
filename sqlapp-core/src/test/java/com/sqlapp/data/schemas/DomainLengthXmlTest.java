/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;

class DomainLengthXmlTest {
	@Test
	void retainsSourceSizesForDomainsAndArrayDomains() throws Exception {
		for (DataType dataType : List.of(DataType.BLOB, DataType.CLOB, DataType.NCLOB,
				DataType.DECIMALFLOAT, DataType.DATALINK)) {
			Domain domain = new Domain("D").setDataType(dataType).setLength(34L).setOctetLength(68L);
			domain.setArrayDimension(1).setArrayDimensionUpperBound(17);
			Domain restored = SchemaUtils.readXml(new StringReader(domain.asXml()));
			assertEquals(34L, restored.getLength(), dataType.toString());
			assertEquals(68L, restored.getOctetLength(), dataType.toString());
			assertEquals(17, restored.getArrayDimensionUpperBound());
			assertEquals(34L, domain.clone().getLength());
		}
	}

	@Test
	void preservesOldXmlWithoutSizeAndAvoidsDuplicateAttributes() throws Exception {
		Domain original = new Domain("D").setDataType(DataType.CLOB);
		Domain restored = SchemaUtils.readXml(new StringReader(original.asXml()));
		assertNull(restored.getLength());
		Domain decimal = new Domain("N").setDataType(DataType.DECIMAL).setLength(12L).setScale(2);
		Domain roundTrip = SchemaUtils.readXml(new StringReader(decimal.asXml()));
		assertEquals(12L, roundTrip.getLength());
		assertEquals(2, roundTrip.getScale());
	}
}
