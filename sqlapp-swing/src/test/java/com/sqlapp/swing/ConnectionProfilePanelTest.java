/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectionProfilePanelTest {
	@TempDir
	Path directory;
	private final ConnectionProfileService.Profile loaded = new ConnectionProfileService.Profile(1,
			"jdbc:hsqldb:mem:profile", "SA", "org.hsqldb.jdbc.JDBCDriver");

	@Test
	void disablingAndReenablingPanelUpdatesActionsImmediately() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var panel = view(file -> {
				fail("Disabled load");
				return loaded;
			}, (profile, file) -> fail("Disabled save"));
			panel.setEnabled(false);
			assertFalse(panel.load.isEnabled());
			assertFalse(panel.save.isEnabled());
			panel.loadFrom(directory.resolve("profile.json"));
			panel.saveTo(directory.resolve("profile.json"));
			assertFalse(panel.isBusy());
			panel.setEnabled(true);
			assertTrue(panel.load.isEnabled());
			assertTrue(panel.save.isEnabled());
		});
	}

	@Test
	void savingEarlierSettingsReportsNewerInputsAndIgnoresPasswordOnlyEdits() throws Exception {
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<ConnectionProfilePanel>();
		final var written = new AtomicReference<ConnectionProfileService.Profile>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = view(file -> loaded, (profile, file) -> {
				written.set(profile);
				if (!release.await(5, TimeUnit.SECONDS))
					throw new IllegalStateException("Timeout");
			});
			panel.set(view);
			onEnabled(view.save, finished);
			view.saveTo(directory.resolve("profile.json"));
			fields[1].setText("new-user");
			view.setEnabled(false);
			view.setEnabled(true);
			assertFalse(view.save.isEnabled());
			assertTrue(view.isBusy());
		});
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		final var secondSave = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("original-user", written.get().username());
			assertEquals("new-user", fields[1].getText());
			assertEquals("Profile saved; newer connection inputs are not saved", panel.get().status.getText());
			assertTrue(panel.get().statusPanel.detailsText().contains("profile.json"));
			onEnabled(panel.get().save, secondSave);
			panel.get().saveTo(directory.resolve("profile.json"));
			fields[3].setText("new-secret");
		});
		assertTrue(secondSave.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("new-user", written.get().username());
			assertEquals("Profile saved", panel.get().status.getText());
			assertArrayEquals("new-secret".toCharArray(), ((JPasswordField) fields[3]).getPassword());
		});
	}

	@Test
	void readsOffEdtAndAppliesOnlyAfterValidationClearingPassword() throws Exception {
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<ConnectionProfilePanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = view(file -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				return loaded;
			}, (profile, file) -> {
			});
			panel.set(view);
			onEnabled(view.load, finished);
			view.loadFrom(directory.resolve("profile.json"));
			assertFalse(view.load.isEnabled());
			assertTrue(view.isBusy());
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertEquals("Loaded; enter password", view.status.getText());
			assertFalse(view.isBusy());
			assertEquals(directory.toFile(), view.chooser(false).getCurrentDirectory());
			assertEquals(directory.resolve("connection.json").toFile(), view.chooser(true).getSelectedFile());
			assertTrue(view.statusPanel.detailsText().contains("profile.json"));
			// Check all associated inputs after the asynchronous load.
			assertEquals(loaded.jdbcUrl(), fields[0].getText());
			assertEquals(loaded.username(), fields[1].getText());
			assertEquals(loaded.driverClass(), fields[2].getText());
			assertEquals(0, ((JPasswordField) fields[3]).getPassword().length);
		});
	}

	@Test
	void saveCapturesSettingsWithoutReadingOrClearingPassword() throws Exception {
		final var finished = new CountDownLatch(1);
		final var written = new AtomicReference<ConnectionProfileService.Profile>();
		SwingUtilities.invokeAndWait(() -> {
			final var password = new JPasswordField("secret") {
				@Override
				public char[] getPassword() {
					throw new AssertionError("Profile saving must not read passwords");
				}
			};
			final var view = new ConnectionProfilePanel(new JTextField("jdbc:hsqldb:mem:test"), new JTextField("SA"),
					new JTextField(""), password, file -> loaded, (profile, file) -> {
						assertFalse(SwingUtilities.isEventDispatchThread());
						written.set(profile);
					});
			onEnabled(view.save, finished);
			view.saveTo(directory.resolve("profile.json"));
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertEquals("jdbc:hsqldb:mem:test", written.get().jdbcUrl());
		assertEquals("SA", written.get().username());
	}

	@Test
	void staleLoadDoesNotOverwriteEditedInputs() throws Exception {
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<ConnectionProfilePanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = view(file -> {
				if (!release.await(5, TimeUnit.SECONDS))
					throw new IllegalStateException("Timeout");
				return loaded;
			}, (profile, file) -> {
			});
			panel.set(view);
			onEnabled(view.load, finished);
			view.loadFrom(directory.resolve("profile.json"));
			fields[0].setText("jdbc:edited");
		});
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("jdbc:edited", fields[0].getText());
			assertArrayEquals("secret".toCharArray(), ((JPasswordField) fields[3]).getPassword());
			assertEquals("Inputs changed; load again", panel.get().status.getText());
		});
	}

	@Test
	void disabledFormDiscardsPendingLoadWithoutReenablingButtons() throws Exception {
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<ConnectionProfilePanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = view(file -> {
				if (!release.await(5, TimeUnit.SECONDS))
					throw new IllegalStateException("Timeout");
				return loaded;
			}, (profile, file) -> {
			});
			panel.set(view);
			view.status.addPropertyChangeListener("text", event -> {
				if ("Inputs changed; load again".equals(event.getNewValue()))
					finished.countDown();
			});
			view.loadFrom(directory.resolve("profile.json"));
			view.setEnabled(false);
		});
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("jdbc:original", fields[0].getText());
			assertFalse(panel.get().load.isEnabled());
			assertFalse(panel.get().save.isEnabled());
		});
	}

	@Test
	void failedReadPreservesAllInputsAndRestoresActions() throws Exception {
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<ConnectionProfilePanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = view(file -> {
				throw new IllegalArgumentException("Invalid profile",
						new java.io.IOException("Unsupported file version"));
			}, (profile, file) -> {
			});
			panel.set(view);
			onEnabled(view.load, finished);
			view.loadFrom(directory.resolve("profile.json"));
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("jdbc:original", fields[0].getText());
			assertEquals("original-user", fields[1].getText());
			assertArrayEquals("secret".toCharArray(), ((JPasswordField) fields[3]).getPassword());
			assertEquals("Profile operation failed", panel.get().status.getText());
			assertFalse(panel.get().isBusy());
			assertTrue(panel.get().statusPanel.detailsText().contains("Unsupported file version"));
			assertEquals("Profile operation failed", panel.get().status.getToolTipText());
			assertTrue(panel.get().save.isEnabled());
			fields[0].setText("jdbc:edited");
			assertEquals("Connection inputs changed", panel.get().status.getText());
			assertEquals(panel.get().status.getText(), panel.get().statusPanel.detailsText());
		});
	}

	@Test
	void onePortableProfileLoadsIntoSchemaSourceAndTargetWithoutConnecting() throws Exception {
		final Path file = directory.resolve("profile.json");
		new ConnectionProfileService().write(loaded, file);
		final var finished = new CountDownLatch(3);
		final var schema = new AtomicReference<SchemaMigrationPlanPanel>();
		final var data = new AtomicReference<DataMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			schema.set(new SchemaMigrationPlanPanel((request, url, user, secret, driver) -> {
				fail("Loading a profile must not connect");
				return null;
			}, (result, path) -> {
			}));
			data.set(new DataMigrationPlanPanel((request, source, sourcePassword, target, targetPassword) -> {
				fail("Loading a profile must not connect");
				return null;
			}, (result, path) -> {
			}));
			for (final var view : new ConnectionProfilePanel[] { schema.get().profile, data.get().source.profile,
					data.get().target.profile }) {
				onEnabled(view.load, finished);
				view.loadFrom(file);
			}
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(loaded.jdbcUrl(), schema.get().jdbcUrl.getText());
			assertEquals(loaded.jdbcUrl(), data.get().source.jdbcUrl.getText());
			assertEquals(loaded.jdbcUrl(), data.get().target.jdbcUrl.getText());
			assertFalse(schema.get().apply.isEnabled());
			assertFalse(data.get().apply.isEnabled());
		});
	}

	@Test
	void loadingProfileInvalidatesExistingReviewsAndPreservesJobAndSqlPaths() throws Exception {
		final Path file = directory.resolve("profile.json");
		final Path job = java.nio.file.Files.writeString(directory.resolve("job.yaml"), "{}");
		new ConnectionProfileService().write(loaded, file);
		final var schema = new AtomicReference<SchemaMigrationPlanPanel>();
		final var data = new AtomicReference<DataMigrationPlanPanel>();
		final var planned = new CountDownLatch(2);
		final var finished = new CountDownLatch(2);
		SwingUtilities.invokeAndWait(() -> {
			final var artifact = com.sqlapp.data.db.command.migration.schema.MigrationPlanArtifact
					.of(new com.sqlapp.data.db.command.migration.schema.MigrationPlan(false, null, 30L, 0, 0,
							java.util.List.of(), java.util.List.of(), null));
			schema.set(new SchemaMigrationPlanPanel(
					(request, url, user, password, driver) -> new SchemaMigrationPlanningService.Result(artifact,
							new ReportReviewService().schemaPlan(artifact)),
					(result, path) -> {
					}));
			final var report = new com.sqlapp.data.db.command.migration.bulk.BulkMigrationOperationalReport(3,
					java.time.Instant.now(), "job", "plan", true, 0, 0, 0, java.util.List.of(), java.util.List.of(),
					null, null, java.util.List.of(), null);
			data.set(new DataMigrationPlanPanel((request, source, sourcePassword, target,
					targetPassword) -> new DataMigrationPlanningService.Result(job, report,
							new ReportReviewService().operational(report)),
					(result, path) -> {
					}));
			schema.get().jdbcUrl.setText("jdbc:old");
			schema.get().upDirectory.setText(directory.toString());
			data.get().configurationFile.setText(job.toString());
			data.get().source.jdbcUrl.setText("jdbc:old-source");
			data.get().target.jdbcUrl.setText("jdbc:old-target");
			onEnabled(schema.get().build, planned);
			onEnabled(data.get().build, planned);
			schema.get().build.doClick();
			data.get().build.doClick();
		});
		assertTrue(planned.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(schema.get().save.isEnabled());
			assertTrue(data.get().save.isEnabled());
			onEnabled(schema.get().profile.load, finished);
			onEnabled(data.get().target.profile.load, finished);
			schema.get().profile.loadFrom(file);
			data.get().target.profile.loadFrom(file);
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(schema.get().save.isEnabled());
			assertFalse(data.get().save.isEnabled());
			assertEquals("", schema.get().view.details.getText());
			assertEquals("", data.get().view.details.getText());
			assertEquals(directory.toString(), schema.get().upDirectory.getText());
			assertEquals(job.toString(), data.get().configurationFile.getText());
			assertEquals("jdbc:old-source", data.get().source.jdbcUrl.getText());
		});
	}

	private JTextField[] fields;

	private ConnectionProfilePanel view(final ConnectionProfilePanel.Reader reader,
			final ConnectionProfilePanel.Writer writer) {
		fields = new JTextField[] { new JTextField("jdbc:original"), new JTextField("original-user"),
				new JTextField("original.Driver"), new JPasswordField("secret") };
		return new ConnectionProfilePanel(fields[0], fields[1], fields[2], (JPasswordField) fields[3], reader, writer);
	}

	private static void onEnabled(final javax.swing.JButton button, final CountDownLatch finished) {
		button.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				finished.countDown();
		});
	}
}
