/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class JdbcUrlBuilderPanelTest {
	@Test
	void validatesPreviewAndAppliesOnlyOnExplicitUse() throws Exception {
		final var selected = new AtomicReference<String>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JdbcUrlBuilderPanel("", selected::set, () -> {
			});
			assertNull(view.type.getSelectedItem());
			assertFalse(view.use.isEnabled());
			view.type.setSelectedItem(JdbcUrlBuilder.Database.POSTGRESQL);
			assertEquals("5432", view.port.getText());
			view.host.setText("db.example");
			view.database.setText("sales");
			assertTrue(view.use.isEnabled());
			assertNull(selected.get());
			assertFalse(view.preview.isEditable());
			assertEquals("jdbc:postgresql://db.example:5432/sales", view.preview.getText());
			view.host.setText("user@db");
			assertFalse(view.use.isEnabled());
			assertEquals("", view.preview.getText());
			view.host.setText("db.example");
			view.use.doClick(0);
			assertEquals(view.preview.getText(), selected.get());
		});
	}

	@Test
	void supportsServiceNamesAndCancellationWithoutParsingExistingProperties() throws Exception {
		final var cancelled = new AtomicInteger();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JdbcUrlBuilderPanel("jdbc:oracle:thin:@custom?password=secret",
					url -> fail("Cancelled builder must not apply"), cancelled::incrementAndGet);
			assertEquals(JdbcUrlBuilder.Database.ORACLE_SERVICE, view.type.getSelectedItem());
			assertEquals("1521", view.port.getText());
			assertEquals("Service name", view.databaseLabel.getText());
			assertEquals("", view.host.getText());
			assertEquals("", view.database.getText());
			assertEquals("", view.preview.getText());
			view.cancel.doClick(0);
			view.getActionMap().get("cancel").actionPerformed(new java.awt.event.ActionEvent(view, 0, ""));
		});
		assertEquals(2, cancelled.get());
	}

	@Test
	void appliesOnlyUrlToSelectedConnectionAndNeverConnects() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel();
			view.source.jdbcUrl.setText("jdbc:original");
			view.target.jdbcUrl.setText("jdbc:target");
			view.source.username.setText("user");
			view.source.password.setText("secret");
			view.source.driverClass.setText("custom.Driver");
			view.view.summary.setText("Old review");
			view.buildConnectionUrl(view.source, current -> {
				assertEquals("jdbc:original", current);
				assertTrue(view.isBusy());
				assertFalse(view.source.test.isEnabled());
				return "jdbc:postgresql://db:5432/sales";
			});
			assertEquals("jdbc:postgresql://db:5432/sales", view.source.jdbcUrl.getText());
			assertEquals("jdbc:target", view.target.jdbcUrl.getText());
			assertEquals("user", view.source.username.getText());
			assertArrayEquals("secret".toCharArray(), view.source.password.getPassword());
			assertEquals("custom.Driver", view.source.driverClass.getText());
			assertEquals("", view.view.summary.getText());
			assertFalse(view.apply.isEnabled());
			assertFalse(view.isBusy());
		});
	}

	@Test
	void cancelAndChangedInputsCannotReplaceConnectionOrRetainedReview() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel();
			view.source.jdbcUrl.setText("jdbc:original");
			view.view.summary.setText("Old review");
			view.buildConnectionUrl(view.source, current -> null);
			assertEquals("jdbc:original", view.source.jdbcUrl.getText());
			assertEquals("Old review", view.view.summary.getText());
			view.buildConnectionUrl(view.source, current -> {
				view.source.jdbcUrl.setText("jdbc:newer");
				return "jdbc:stale";
			});
			assertEquals("jdbc:newer", view.source.jdbcUrl.getText());
			assertTrue(view.status.getText().contains("inputs changed"));
			view.buildConnectionUrl(view.target, current -> {
				view.buildConnectionUrl(view.source, nested -> {
					throw new AssertionError("Duplicate builder must not open");
				});
				return null;
			});
			assertFalse(view.isBusy());
		});
	}

	private static DataMigrationPlanPanel panel() {
		return new DataMigrationPlanPanel((request, source, password, target, targetPassword) -> {
			throw new AssertionError("URL builder must not inspect a migration");
		}, (result, path) -> {
			throw new AssertionError("URL builder must not save a job");
		}, (connection, password) -> {
			throw new AssertionError("URL builder must not connect");
		});
	}
}
