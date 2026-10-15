/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;

class ColumnDomainReferenceTest {
	@Test
	void referenceResolvesAfterXmlAndCloneAndTracksDomainRename() throws Exception {
		Schema schema = new Schema("APP");
		Domain domain = new Domain("D").setDataType(DataType.INT);
		schema.getDomains().add(domain);
		Table table = new Table("T"); schema.getTables().add(table);
		Column column = new Column("C").setDataType(DataType.INT).setDomainName("D");
		table.getColumns().add(column);
		assertSame(domain, column.getDomain());
		domain.setName("RENAMED");
		assertEquals("RENAMED", column.getDomainName());
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		restored.getDomains().get("RENAMED").setName("AFTER_XML");
		assertEquals("AFTER_XML", restored.getTables().get("T").getColumns().get("C").getDomainName());
		assertSame(restored.getDomains().get("AFTER_XML"), restored.getTables().get("T").getColumns().get("C").getDomain());
		Schema clone = schema.clone();
		Column copied = clone.getTables().get("T").getColumns().get("C");
		assertSame(clone.getDomains().get("RENAMED"), copied.getDomain());
		assertNotSame(domain, copied.getDomain());
		copied.setDomainName("OTHER");
		assertEquals("RENAMED", domain.getName());
		assertNotEquals(column, copied);
	}
}
