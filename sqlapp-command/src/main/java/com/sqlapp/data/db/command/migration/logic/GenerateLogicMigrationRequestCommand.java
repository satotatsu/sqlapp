/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.logic;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationDirectory;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Changes;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Database;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Eligibility;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Exclusion;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.InputFile;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Inputs;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Manifest;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.ObjectEntry;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.OriginFingerprints;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Result;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.State;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.MessageDigests;

import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes a conversion request for database triggers (milestone M1 of the AI
 * assisted database logic migration).
 * <p>
 * The command reads saved source and target Schema XML, classifies every source
 * trigger, and publishes a new work directory with the request files. It does
 * not connect to a database or to an AI service and does not change its inputs.
 * This version supports Oracle to PostgreSQL only.
 *
 * <pre>
 * workDir/
 *   request-state.json      sqlapp-side record (request ID, manifest fingerprint)
 *   request/manifest.json   objects, eligibility and input fingerprints
 *   request/request.md      instructions for the converter
 *   request/source.xml      source Schema XML without row data
 *   request/target.xml      target Schema XML without row data
 *   request/changes.json    table and column correspondences
 *   response/               empty; the converter writes its result here
 * </pre>
 */
@Getter
@Setter
public class GenerateLogicMigrationRequestCommand extends AbstractCommand {
	static final String SOURCE_PRODUCT = "Oracle";
	static final String TARGET_PRODUCT = "PostgreSQL";
	static final String VERIFICATION_PROFILE = "oracle19-postgresql16";

	/** Source Schema or Catalog XML (Oracle) containing tables and triggers. */
	private File sourceSchemaFile;
	/** Target Schema or Catalog XML (PostgreSQL) produced for the transfer. */
	private File targetSchemaFile;
	/** New directory to publish. It must not exist. */
	private File workDir;
	/** Optional user decisions not to convert specific triggers. */
	private List<Exclusion> exclusions = new ArrayList<>();
	@Setter(lombok.AccessLevel.NONE)
	private Result result;

	@Override
	protected void doRun() {
		result = null;
		requireFile(sourceSchemaFile, "sourceSchemaFile");
		requireFile(targetSchemaFile, "targetSchemaFile");
		if (workDir == null) {
			throw new CommandException("workDir is required");
		}
		final Path directory = workDir.toPath().toAbsolutePath().normalize();
		if (Files.exists(directory)) {
			throw new CommandException(
					"workDir already exists; create a new work directory for each request: " + directory);
		}
		try {
			final String sourceOrigin = fingerprint(sourceSchemaFile);
			final String targetOrigin = fingerprint(targetSchemaFile);
			final DbCommonObject<?> sourceRoot = read(sourceSchemaFile, "sourceSchemaFile");
			final DbCommonObject<?> targetRoot = read(targetSchemaFile, "targetSchemaFile");
			final List<Schema> sourceSchemas = schemas(sourceRoot, "sourceSchemaFile");
			final List<Schema> targetSchemas = schemas(targetRoot, "targetSchemaFile");
			final Database source = database(sourceRoot, sourceSchemas, "sourceSchemaFile");
			final Database target = database(targetRoot, targetSchemas, "targetSchemaFile");
			if (!SOURCE_PRODUCT.equalsIgnoreCase(source.product())
					|| !TARGET_PRODUCT.equalsIgnoreCase(target.product())) {
				throw new CommandException("Only " + SOURCE_PRODUCT + " to " + TARGET_PRODUCT
						+ " is supported in this version: source=" + source.product() + ", target=" + target.product());
			}
			final String profile = profile(source, target);
			final var inventory = LogicTriggerInventory.build(sourceSchemas, targetSchemas, exclusions);
			removeRows(sourceSchemas);
			removeRows(targetSchemas);
			final String requestId = newRequestId();
			final JsonMapper json = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
			final Manifest[] manifest = new Manifest[1];
			final String[] manifestFingerprint = new String[1];
			AtomicMigrationDirectory.writeNew(directory, temporary -> {
				final Path request = Files.createDirectories(temporary.resolve("request"));
				Files.createDirectories(temporary.resolve("response"));
				final Path sourceXml = request.resolve("source.xml");
				final Path targetXml = request.resolve("target.xml");
				final Path changes = request.resolve("changes.json");
				final Path prompt = request.resolve("request.md");
				sourceRoot.writeXml(sourceXml.toFile());
				targetRoot.writeXml(targetXml.toFile());
				json.writeValue(changes.toFile(), new Changes(LogicMigrationRequestModel.CHANGES_FORMAT,
						LogicMigrationRequestModel.FORMAT_VERSION, "NAME_MATCH", inventory.tableChanges()));
				Files.writeString(prompt, LogicRequestPrompt.build(requestId, source, target, inventory.objects()),
						StandardCharsets.UTF_8);
				final Inputs inputs = new Inputs(input(request, sourceXml), input(request, targetXml),
						input(request, changes), input(request, prompt));
				manifest[0] = new Manifest(LogicMigrationRequestModel.REQUEST_FORMAT,
						LogicMigrationRequestModel.FORMAT_VERSION, requestId, source, target, profile, inputs,
						inventory.objects(), inventory.diagnostics());
				final Path manifestFile = request.resolve("manifest.json");
				json.writeValue(manifestFile.toFile(), manifest[0]);
				manifestFingerprint[0] = fingerprint(manifestFile.toFile());
				json.writeValue(temporary.resolve("request-state.json").toFile(),
						new State(LogicMigrationRequestModel.STATE_FORMAT, LogicMigrationRequestModel.FORMAT_VERSION,
								requestId, new InputFile("request/manifest.json", manifestFingerprint[0]),
								new OriginFingerprints(sourceOrigin, targetOrigin), Instant.now().toString()));
				if (!sourceOrigin.equals(fingerprint(sourceSchemaFile))
						|| !targetOrigin.equals(fingerprint(targetSchemaFile))) {
					throw new CommandException("An input Schema XML changed while the request was written; retry");
				}
			});
			result = new Result(requestId, directory, manifest[0], manifestFingerprint[0]);
		} catch (final Exception e) {
			throw e instanceof CommandException commandException ? commandException
					: new CommandException("Logic migration request failed: " + e.getMessage(), e);
		}
		report();
	}

	private void report() {
		final Map<Eligibility, Integer> counts = new EnumMap<>(Eligibility.class);
		for (final ObjectEntry object : result.manifest().objects()) {
			counts.merge(object.eligibility(), 1, Integer::sum);
		}
		info("Logic migration request ", result.requestId(), " written to ", result.workDir(), ": ", counts);
		if (result.manifest().verificationProfile() == null) {
			info("WARN: The database versions are outside the verified profile ", VERIFICATION_PROFILE,
					"; automatic application will not be available for this request");
		}
		result.manifest().diagnostics()
				.forEach(diagnostic -> info(diagnostic.level(), ": ", diagnostic.code(), ": ", diagnostic.message()));
	}

	private static void requireFile(final File file, final String name) {
		if (file == null || !file.isFile()) {
			throw new CommandException(name + " must be an existing Schema or Catalog XML file");
		}
	}

	private static DbCommonObject<?> read(final File file, final String name) throws Exception {
		final DbCommonObject<?> root = SchemaUtils.readXml(file);
		if (!(root instanceof Schema) && !(root instanceof Catalog)) {
			throw new CommandException(name + " root must be Schema or Catalog");
		}
		return root;
	}

	private static List<Schema> schemas(final DbCommonObject<?> root, final String name) {
		final List<Schema> result = new ArrayList<>();
		if (root instanceof Schema schema) {
			result.add(schema);
		} else {
			result.addAll(((Catalog) root).getSchemas());
		}
		if (result.isEmpty()) {
			throw new CommandException(name + " must contain at least one Schema");
		}
		return result;
	}

	/**
	 * Resolves the product and version recorded in Schema XML. The version is never
	 * guessed; a missing or inconsistent value is an error.
	 */
	private static Database database(final DbCommonObject<?> root, final List<Schema> schemas, final String name) {
		final Catalog catalog = root instanceof Catalog value ? value : null;
		String product = null;
		String version = null;
		for (final Schema schema : schemas) {
			final String schemaProduct = schema.getProductName() != null ? schema.getProductName()
					: catalog == null ? null : catalog.getProductName();
			final Integer major = schema.getProductMajorVersion() != null ? schema.getProductMajorVersion()
					: catalog == null ? null : catalog.getProductMajorVersion();
			if (schemaProduct == null || major == null) {
				throw new CommandException(name + " does not record the database product and major version of schema "
						+ schema.getName() + "; export the Schema XML from the database or set productName and "
						+ "productMajorVersion");
			}
			final Integer minor = schema.getProductMajorVersion() != null ? schema.getProductMinorVersion()
					: catalog.getProductMinorVersion();
			final Integer revision = schema.getProductMajorVersion() != null ? schema.getProductRevision()
					: catalog.getProductRevision();
			final String schemaVersion = major + (minor == null ? "" : "." + minor)
					+ (minor == null || revision == null ? "" : "." + revision);
			if (product == null) {
				product = schemaProduct;
				version = schemaVersion;
			} else if (!product.equalsIgnoreCase(schemaProduct) || !version.equals(schemaVersion)) {
				throw new CommandException(name + " mixes database products or versions: " + product + " " + version
						+ " and " + schemaProduct + " " + schemaVersion);
			}
		}
		return new Database(product, version);
	}

	private static String profile(final Database source, final Database target) {
		return "19".equals(major(source)) && "16".equals(major(target)) ? VERIFICATION_PROFILE : null;
	}

	private static String major(final Database database) {
		final int dot = database.version().indexOf('.');
		return dot < 0 ? database.version() : database.version().substring(0, dot);
	}

	private static void removeRows(final List<Schema> schemas) {
		for (final Schema schema : schemas) {
			for (final Table table : schema.getTables()) {
				table.getRows().clear();
			}
		}
	}

	private static InputFile input(final Path request, final Path file) throws Exception {
		return new InputFile(request.relativize(file).toString().replace('\\', '/'), fingerprint(file.toFile()));
	}

	private static String newRequestId() {
		final byte[] bytes = new byte[8];
		new SecureRandom().nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	static String fingerprint(final File file) throws Exception {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(file);
	}
}
