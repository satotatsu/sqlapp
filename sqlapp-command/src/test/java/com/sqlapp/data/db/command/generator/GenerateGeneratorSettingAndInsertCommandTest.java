package com.sqlapp.data.db.command.generator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

class GenerateGeneratorSettingAndInsertCommandTest extends AbstractGeneratorCommandTest {

	@Test
	void test() {
		test(command -> {

		});
	}

	private void test(Consumer<GenerateGeneratorSettingAndInsertCommand> cons) {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateGeneratorSettingAndInsertCommand command = new GenerateGeneratorSettingAndInsertCommand();
			command.setDataSource(ds);
			// command.setOutputDirectory(new File("./"));
			command.setIncludeTables("PRODUCTS", "PRODUCT_PRICES");
			command.setCloseDataSource(false);
			command.setOutputDirectory(testProjectDir);
			dropTables(command, "PRODUCTS", "PRODUCT_PRICES");
			String sql = this.getResource("create_table_products.sql");
			this.executeSql(command, sql);
			sql = this.getResource("create_table_product_prices.sql");
			this.executeSql(command, sql);
			cons.accept(command);
			command.run();
			dropTables(command, "PRODUCT_PRICES", "PRODUCTS");
			File file = new File(testProjectDir,
					"PRODUCT_PRICES." + command.getFileType().getWorkbookFileType().getFileExtension());
			assertTrue(file.exists());
		} finally {
			ds.close();
		}
	}

}
