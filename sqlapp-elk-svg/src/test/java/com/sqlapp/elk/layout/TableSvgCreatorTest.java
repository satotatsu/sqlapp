package com.sqlapp.elk.layout;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;

class TableSvgCreatorTest {

	@Test
	void test() throws IOException {
		Catalog catalog = SchemaUtils.readXml(new File("./src/test/resources/catalog.xml"));
		Schema schema = catalog.getSchemas().get("PUBLIC");
		TableSvgCreator creator = new TableSvgCreator();
		String svg = creator.generateSvg(schema.getTables());
		String filename = "移植版.svg";
		try (FileWriter writer = new FileWriter(filename)) {
			writer.write(svg);
		}
		System.out.println("FK名表示対応版ER図の生成に成功しました。 " + filename);
	}

}
