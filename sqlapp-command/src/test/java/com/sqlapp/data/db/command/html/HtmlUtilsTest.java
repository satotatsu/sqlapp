package com.sqlapp.data.db.command.html;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.LinkedProperties;
import com.sqlapp.util.StringComparator;

class HtmlUtilsTest {

	@Test
	void test() {
		HtmlUtils.setLocale(Locale.JAPAN);
		assertEquals("テーブル", HtmlUtils.getMessage("Table"));
		assertEquals("tableAAaAA", HtmlUtils.getMessage("tableAAaAA"));
		assertEquals("AA AA", HtmlUtils.getMessage("AA AA"));
		assertEquals("ユニーク制約", HtmlUtils.getMessage("Unique Constraints"));
	}

	@Test
	void testMenu() {
		HtmlUtils.setLocale(Locale.ENGLISH);
		ResourceBundle bundle = HtmlUtils.getResourceBundle();
		Set<String> keys = CommonUtils.set();
		Enumeration<String> enm = bundle.getKeys();
		while (enm.hasMoreElements()) {
			keys.add(enm.nextElement());
		}
		for (MenuDefinition menu : MenuDefinition.values()) {
			if (!keys.contains(menu.name())) {
				// System.out.println(menu.name() + "=" + menu.name());
				assertTrue(false, "" + menu.name() + "=" + menu.name());
			}
		}
		for (MenuDefinition menu : MenuDefinition.values()) {
			String val = SchemaUtils.getSingularName(menu.name());
			if (!keys.contains(val)) {
				// System.out.println(val + "=" + val);
				assertTrue(false, val + "=" + val);
			}
		}
	}

	@Test
	void sortOutput() throws IOException {
		LinkedProperties props = new LinkedProperties();
		String path = FileUtils.combinePath("./src/main/resources",
				HtmlUtils.class.getPackage().getName().replace(".", "/"), "resources", "messages.properties");
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
			props.load(reader);
			props.sort(new StringComparator());
			props.forEach((k, v) -> {
				// System.out.println(k + "=" + v);
			});
		}
	}

	@Test
	void splitBr() throws IOException {
		assertEquals("a", HtmlUtils.splitBr("a", "_"));
		assertEquals("a<br/>b", HtmlUtils.splitBr("a_b", "_"));
		assertEquals("a<br/>b<br/>c", HtmlUtils.splitBr("a_b_c", "_"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void analyze() {
		ParametersContext context = HtmlUtils.analyzeAllProperties(createColumns());
		Map<String, Object> specifics = (Map<String, Object>) context.get("specifics");
		context.forEach((k, v) -> {
			System.out.println(k + "=" + v);
		});
	}

	private List<Column> createColumns() {
		Table table = new Table("TabA");
		table.getColumns().add(c -> {
			c.setName("colA");
			c.setDataType(DataType.BIGINT);
			setSpesifics(c, 90);
			setStatistics(c, 5);
		});
		table.getColumns().add(c -> {
			c.setName("colB");
			c.setDataType(DataType.BIGINT);
			setSpesifics(c, 80);
			setStatistics(c, 3);
		});
		table.getColumns().add(c -> {
			c.setName("colC");
			c.setDataType(DataType.BIGINT);
			setSpesifics(c, 80);
		});
		return table.getColumns();
	}

	private void setSpesifics(Column c, Object val) {
		c.getSpecifics().put("FILL_FACTOR", val);
	}

	private void setStatistics(Column c, Object val) {
		c.getStatistics().put("AUTO_ANALYZE_COUNT", val);
	}

}
