package com.sqlapp.data.db.command.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

class GeneratorSettingWorkbookTest {

	@Test
	void test() {
		ResourceBundle bundle = GeneratorSettingWorkbook.getResourceBundle(Locale.ENGLISH);
		assertEquals("Table Name", bundle.getString("tableName"));
	}

}
