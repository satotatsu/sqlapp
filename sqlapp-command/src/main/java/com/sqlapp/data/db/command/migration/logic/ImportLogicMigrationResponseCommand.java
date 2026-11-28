/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.logic;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationDirectory;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Diagnostic;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Eligibility;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.InputFile;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.Manifest;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.ObjectEntry;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.State;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationRequestModel.TableIdentity;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationResponseModel.Artifact;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationResponseModel.ArtifactKind;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationResponseModel.ImportStatus;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationResponseModel.ImportedArtifact;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationResponseModel.ImportedObject;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationResponseModel.Outputs;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationResponseModel.ParsedDeclaration;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationResponseModel.Report;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationResponseModel.Response;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationResponseModel.Result;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationResponseModel.ResultStatus;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationResponseModel.Suggestion;
import com.sqlapp.data.db.command.migration.logic.LogicMigrationResponseModel.SuggestionType;
import com.sqlapp.data.db.command.migration.logic.PostgresCreateStatement.Parsed;
import com.sqlapp.data.db.command.migration.logic.PostgresCreateStatement.QualifiedName;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.MessageDigests;

import lombok.Getter;
import lombok.Setter;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Imports a converter response into a request work directory (milestone M2 of
 * the AI assisted database logic migration).
 * <p>
 * The command verifies the request written by
 * {@link GenerateLogicMigrationRequestCommand}, reads {@code response/}, and
 * identifies the outer CREATE statement of every returned SQL file. Results
 * that pass the structural checks are added as {@code definition} to the target
 * Schema model, written as Schema XML, read back and regenerated through the
 * target SQL factory. It does not connect to a database.
 * <p>
 * Problems with the request files or the response structure fail the whole
 * import and write nothing. Problems with one returned object reject that
 * object only. The new {@code import/} directory keeps a copy of the response,
 * the evidence {@code import.json}, the merged Schema XML and the regenerated
 * SQL. Accepted objects are candidates; they were not created, reviewed or
 * verified.
 */
@Getter
@Setter
public class ImportLogicMigrationResponseCommand extends AbstractCommand {
	static final long MAX_JSON_BYTES = 1_048_576L;
	static final long MAX_SQL_BYTES = 1_048_576L;
	private static final Pattern ARTIFACT_ID = Pattern.compile("a[1-9][0-9]{0,5}");
	private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}\\.sql");

	/** Work directory written by GenerateLogicMigrationRequestCommand. */
	private File workDir;
	@Setter(lombok.AccessLevel.NONE)
	private Report report;

	private record Loaded(State state, String stateFingerprint, Manifest manifest, String manifestFingerprint,
			Response response, String responseFingerprint) {
	}

	private record SourceWork(ObjectEntry object, Result result, List<Artifact> artifacts, Map<String, String> sql,
			Map<String, Parsed> parsed, Map<String, List<String>> problems, List<String> sourceProblems) {
	}

	@Override
	protected void doRun() {
		report = null;
		if (workDir == null || !workDir.isDirectory()) {
			throw new CommandException("workDir must be an existing logic migration work directory");
		}
		final Path root = workDir.toPath().toAbsolutePath().normalize();
		final Path importDir = root.resolve("import");
		if (Files.exists(importDir, LinkOption.NOFOLLOW_LINKS)) {
			throw new CommandException("The response was already imported: " + importDir
					+ ". Create a new request work directory to import " + "another response");
		}
		try {
			final JsonMapper strict = strictMapper();
			final Loaded loaded = load(root, strict);
			final Map<String, ObjectEntry> objects = new LinkedHashMap<>();
			loaded.manifest().objects().forEach(object -> objects.put(object.sourceId(), object));
			final Path responseDir = root.resolve("response");
			final Map<String, Artifact> artifacts = validateStructure(loaded.response(), objects, responseDir);
			final DbCommonObject<?> target = SchemaUtils.readXml(
					root.resolve("request").resolve(loaded.manifest().inputs().targetSchema().path()).toFile());
			final List<Schema> targetSchemas = schemas(target);
			final List<Diagnostic> diagnostics = new ArrayList<>();
			final Map<String, SourceWork> work = inspect(loaded, objects, artifacts, responseDir, targetSchemas,
					diagnostics);
			// Build, write to XML, read back and regenerate. Objects whose regenerated DDL
			// differs from the returned DDL are rejected and the model is rebuilt without
			// them.
			Built built = build(loaded.manifest(), work, responseDir.getParent());
			if (!built.mismatches().isEmpty()) {
				built.mismatches().forEach((sourceId, message) -> work.get(sourceId).sourceProblems().add(message));
				built = build(loaded.manifest(), work, responseDir.getParent());
			}
			final Built result = built;
			final List<ImportedObject> imported = importedObjects(loaded.manifest(), work, result, responseDir);
			final JsonMapper writer = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
			final Report[] holder = new Report[1];
			AtomicMigrationDirectory.writeNew(importDir, temporary -> {
				final Path frozen = Files.createDirectories(temporary.resolve("response"));
				Files.copy(responseDir.resolve("response.json"), frozen.resolve("response.json"));
				final Map<String, String> sqlFingerprints = new HashMap<>();
				imported.forEach(object -> object.artifacts().forEach(
						artifact -> sqlFingerprints.put(artifact.artifactId(), artifact.file().fingerprint())));
				for (final Artifact artifact : artifacts.values()) {
					final Path copy = frozen.resolve(artifact.file());
					Files.copy(responseDir.resolve(artifact.file()), copy);
					if (!fingerprint(copy).equals(sqlFingerprints.get(artifact.artifactId()))) {
						throw new CommandException("response/" + artifact.file() + " changed during the import; retry");
					}
				}
				final Path xml = temporary.resolve("target-with-logic.xml");
				Files.writeString(xml, result.xml(), StandardCharsets.UTF_8);
				final Path sql = temporary.resolve("logic.sql");
				Files.writeString(sql, result.sql(), StandardCharsets.UTF_8);
				holder[0] = new Report(LogicMigrationResponseModel.IMPORT_FORMAT,
						LogicMigrationResponseModel.FORMAT_VERSION, loaded.manifest().requestId(),
						new InputFile("../request/manifest.json", loaded.manifestFingerprint()),
						new InputFile("response/response.json", fingerprint(frozen.resolve("response.json"))), imported,
						result.applyOrder(), new Outputs(new InputFile("target-with-logic.xml", fingerprint(xml)),
								new InputFile("logic.sql", fingerprint(sql))),
						List.copyOf(diagnostics));
				writer.writeValue(temporary.resolve("import.json").toFile(), holder[0]);
				if (!loaded.responseFingerprint().equals(holder[0].response().fingerprint())) {
					throw new CommandException("response.json changed during the import; retry");
				}
			});
			report = holder[0];
		} catch (final Exception e) {
			throw e instanceof CommandException commandException ? commandException
					: new CommandException("Logic migration import failed: " + e.getMessage(), e);
		}
		final Map<ImportStatus, Integer> counts = new EnumMap<>(ImportStatus.class);
		report.objects().forEach(object -> counts.merge(object.status(), 1, Integer::sum));
		info("Logic migration response imported into ", importDir, ": ", counts);
		report.diagnostics()
				.forEach(diagnostic -> info(diagnostic.level(), ": ", diagnostic.code(), ": ", diagnostic.message()));
	}

	static JsonMapper strictMapper() {
		return JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
				.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
				.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
	}

	/** Verifies the request files and reads the response document. */
	private static Loaded load(final Path root, final JsonMapper json) throws Exception {
		final Path stateFile = root.resolve("request-state.json");
		if (!Files.isRegularFile(stateFile, LinkOption.NOFOLLOW_LINKS)) {
			throw new CommandException("request-state.json is missing in " + root
					+ ". The request cannot be verified; generate a new request");
		}
		final State state = readJson(json, stateFile, State.class, "request-state.json");
		if (!LogicMigrationRequestModel.STATE_FORMAT.equals(state.format())
				|| state.formatVersion() != LogicMigrationRequestModel.FORMAT_VERSION) {
			throw new CommandException(
					"Unsupported request-state.json format: " + state.format() + " " + state.formatVersion());
		}
		if (state.manifest() == null || !"request/manifest.json".equals(state.manifest().path())) {
			throw new CommandException("request-state.json must refer to request/manifest.json");
		}
		final Path manifestFile = root.resolve("request/manifest.json");
		requireRegularFile(manifestFile, "request/manifest.json");
		final String manifestFingerprint = fingerprint(manifestFile);
		if (!manifestFingerprint.equals(state.manifest().fingerprint())) {
			throw new CommandException("request/manifest.json does not match the fingerprint in request-state.json");
		}
		final Manifest manifest = readJson(json, manifestFile, Manifest.class, "request/manifest.json");
		if (!LogicMigrationRequestModel.REQUEST_FORMAT.equals(manifest.format())
				|| manifest.formatVersion() != LogicMigrationRequestModel.FORMAT_VERSION) {
			throw new CommandException(
					"Unsupported request manifest format: " + manifest.format() + " " + manifest.formatVersion());
		}
		if (!Objects.equals(state.requestId(), manifest.requestId())) {
			throw new CommandException("The request IDs of request-state.json and manifest.json differ");
		}
		if (manifest.inputs() == null || manifest.objects() == null || manifest.diagnostics() == null) {
			throw new CommandException("request/manifest.json lacks inputs, objects or diagnostics");
		}
		final Path request = root.resolve("request");
		for (final InputFile input : List.of(manifest.inputs().sourceSchema(), manifest.inputs().targetSchema(),
				manifest.inputs().changes(), manifest.inputs().prompt())) {
			if (input == null || input.path() == null || !FILE_NAME_ANY.matcher(input.path()).matches()) {
				throw new CommandException("request/manifest.json has an invalid input path: " + input);
			}
			final Path file = request.resolve(input.path());
			requireRegularFile(file, "request/" + input.path());
			if (!fingerprint(file).equals(input.fingerprint())) {
				throw new CommandException("request/" + input.path() + " does not match the request manifest");
			}
		}
		final Set<String> ids = new HashSet<>();
		for (final ObjectEntry object : manifest.objects()) {
			if (object == null || object.sourceId() == null || object.identity() == null || object.eligibility() == null
					|| !ids.add(object.sourceId())) {
				throw new CommandException("request/manifest.json has an invalid or duplicate object: " + object);
			}
			if (object.eligibility() == Eligibility.REQUESTED
					&& (object.identity().table() == null || object.targetTable() == null)) {
				throw new CommandException(
						"A REQUESTED object must have its table and target table: " + object.sourceId());
			}
		}
		final Path responseFile = root.resolve("response/response.json");
		requireRegularFile(responseFile, "response/response.json");
		if (Files.size(responseFile) > MAX_JSON_BYTES) {
			throw new CommandException("response/response.json exceeds " + MAX_JSON_BYTES + " bytes");
		}
		final String responseFingerprint = fingerprint(responseFile);
		final Response response = readJson(json, responseFile, Response.class, "response/response.json");
		if (!LogicMigrationResponseModel.RESPONSE_FORMAT.equals(response.format())
				|| response.formatVersion() != LogicMigrationResponseModel.FORMAT_VERSION) {
			throw new CommandException("Unsupported response format: " + response.format() + " "
					+ response.formatVersion() + "; expected " + LogicMigrationResponseModel.RESPONSE_FORMAT + " "
					+ LogicMigrationResponseModel.FORMAT_VERSION);
		}
		if (!manifest.requestId().equals(response.requestId())) {
			throw new CommandException("response.json belongs to request " + response.requestId()
					+ ", not to this request " + manifest.requestId());
		}
		if (response.results() == null || response.artifacts() == null) {
			throw new CommandException("response.json requires results and artifacts (use [] when empty)");
		}
		return new Loaded(state, fingerprint(stateFile), manifest, manifestFingerprint, response, responseFingerprint);
	}

	private static final Pattern FILE_NAME_ANY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}");

	private static <T> T readJson(final JsonMapper json, final Path file, final Class<T> type, final String name) {
		try {
			return json.readValue(file.toFile(), type);
		} catch (final Exception e) {
			throw new CommandException(
					name + " is not a valid " + type.getSimpleName() + " document: " + e.getMessage(), e);
		}
	}

	private static void requireRegularFile(final Path file, final String name) {
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
			throw new CommandException(name + " is missing or is not a regular file");
		}
	}

	/**
	 * Structural checks of response.json. Every violation is collected and the
	 * import fails as a whole when any is found.
	 */
	private static Map<String, Artifact> validateStructure(final Response response,
			final Map<String, ObjectEntry> objects, final Path responseDir) throws Exception {
		final List<String> errors = new ArrayList<>();
		final Map<String, Artifact> artifacts = new LinkedHashMap<>();
		final Set<String> files = new HashSet<>();
		for (final Artifact artifact : response.artifacts()) {
			if (artifact == null || artifact.artifactId() == null) {
				errors.add("An artifact has no artifactId");
				continue;
			}
			final String id = artifact.artifactId();
			if (!ARTIFACT_ID.matcher(id).matches()) {
				errors.add("Invalid artifactId " + id + "; use a1, a2, ...");
			}
			if (artifacts.putIfAbsent(id, artifact) != null) {
				errors.add("Duplicate artifactId " + id);
			}
			if (artifact.kind() == null || blank(artifact.schema()) || blank(artifact.name())
					|| artifact.dependsOn() == null) {
				errors.add(id + ": kind, schema, name and dependsOn are required");
			}
			if (artifact.kind() == ArtifactKind.FUNCTION) {
				if (artifact.argumentTypes() == null) {
					errors.add(id + ": argumentTypes is required for FUNCTION (use [])");
				}
				if (artifact.table() != null) {
					errors.add(id + ": table is not allowed for FUNCTION");
				}
			} else if (artifact.kind() == ArtifactKind.TRIGGER) {
				if (artifact.table() == null || blank(artifact.table().schema()) || blank(artifact.table().name())) {
					errors.add(id + ": table with schema and name is required for TRIGGER");
				}
				if (artifact.argumentTypes() != null) {
					errors.add(id + ": argumentTypes is not allowed for TRIGGER");
				}
			}
			if (artifact.file() == null || !FILE_NAME.matcher(artifact.file()).matches()) {
				errors.add(id + ": file must be a plain .sql file name in response/, found " + artifact.file());
			} else if (!files.add(artifact.file())) {
				errors.add(id + ": file " + artifact.file() + " is referenced by several artifacts");
			} else {
				final Path file = responseDir.resolve(artifact.file());
				if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
					errors.add(id + ": response/" + artifact.file() + " is missing or is not a regular file");
				} else if (Files.size(file) > MAX_SQL_BYTES) {
					errors.add(id + ": response/" + artifact.file() + " exceeds " + MAX_SQL_BYTES + " bytes");
				}
			}
		}
		final Map<String, String> owner = new HashMap<>();
		final Set<String> resultIds = new HashSet<>();
		for (final Result result : response.results()) {
			if (result == null || result.sourceId() == null) {
				errors.add("A result has no sourceId");
				continue;
			}
			final String sourceId = result.sourceId();
			final ObjectEntry object = objects.get(sourceId);
			if (object == null) {
				errors.add("Unknown sourceId " + sourceId);
			} else if (object.eligibility() != Eligibility.REQUESTED) {
				errors.add(sourceId + " was not requested (" + object.eligibility() + ")");
			}
			if (!resultIds.add(sourceId)) {
				errors.add("Duplicate result for " + sourceId);
			}
			if (result.status() == null || result.artifactIds() == null || result.notes() == null) {
				errors.add(sourceId + ": status, artifactIds and notes are required");
				continue;
			}
			if (result.status() == ResultStatus.CONVERTED && result.artifactIds().isEmpty()) {
				errors.add(sourceId + ": CONVERTED requires at least one artifact");
			}
			if (result.status() == ResultStatus.UNRESOLVED
					&& result.notes().stream().allMatch(ImportLogicMigrationResponseCommand::blank)) {
				errors.add(sourceId + ": UNRESOLVED requires notes that explain the unresolved issues");
			}
			validateSuggestion(sourceId, result, errors);
			for (final String artifactId : result.artifactIds()) {
				if (!artifacts.containsKey(artifactId)) {
					errors.add(sourceId + ": unknown artifact " + artifactId);
				} else if (owner.putIfAbsent(artifactId, sourceId) != null) {
					errors.add("Artifact " + artifactId + " is referenced by " + owner.get(artifactId) + " and "
							+ sourceId + "; helper objects cannot be shared");
				}
			}
		}
		for (final Artifact artifact : artifacts.values()) {
			final String sourceId = owner.get(artifact.artifactId());
			if (sourceId == null) {
				errors.add("Artifact " + artifact.artifactId() + " is not referenced by any result");
				continue;
			}
			if (artifact.dependsOn() == null) {
				continue;
			}
			for (final String dependency : artifact.dependsOn()) {
				if (!sourceId.equals(owner.get(dependency))) {
					errors.add(artifact.artifactId() + " depends on " + dependency
							+ ", which is not an artifact of the same source object");
				}
			}
		}
		if (errors.isEmpty()) {
			final String cycle = cycle(artifacts);
			if (cycle != null) {
				errors.add("Circular dependsOn: " + cycle);
			}
		}
		try (Stream<Path> entries = Files.list(responseDir)) {
			for (final Path entry : (Iterable<Path>) entries.sorted()::iterator) {
				final String name = entry.getFileName().toString();
				if (!name.equals("response.json") && !files.contains(name)) {
					errors.add("Unexpected entry in response/: " + name
							+ " (only response.json and the files listed in artifacts are allowed)");
				}
			}
		}
		final Set<String> names = new HashSet<>();
		for (final Artifact artifact : artifacts.values()) {
			if (artifact.kind() == null || artifact.schema() == null || artifact.name() == null) {
				continue;
			}
			final String key = artifact.kind() == ArtifactKind.TRIGGER && artifact.table() != null
					? "TRIGGER " + artifact.table().schema() + "." + artifact.table().name() + " " + artifact.name()
					: artifact.kind() + " " + artifact.schema() + "." + artifact.name();
			if (!names.add(key)) {
				errors.add("Several artifacts declare " + key);
			}
		}
		if (!errors.isEmpty()) {
			throw new CommandException("response.json is not acceptable (" + errors.size() + " problems):\n- "
					+ String.join("\n- ", errors));
		}
		return artifacts;
	}

	private static void validateSuggestion(final String sourceId, final Result result, final List<String> errors) {
		final Suggestion suggestion = result.suggestion();
		if (suggestion == null) {
			return;
		}
		if (result.status() != ResultStatus.UNRESOLVED) {
			errors.add(sourceId + ": a suggestion requires status UNRESOLVED");
		}
		if (suggestion.type() == null || blank(suggestion.reason()) || suggestion.checks() == null) {
			errors.add(sourceId + ": suggestion requires type, reason and checks");
		}
		if (suggestion.type() == SuggestionType.REPLACE_WITH_SCHEMA
				&& (suggestion.targetColumn() == null || blank(suggestion.targetColumn().schema())
						|| blank(suggestion.targetColumn().table()) || blank(suggestion.targetColumn().name()))) {
			errors.add(sourceId + ": REPLACE_WITH_SCHEMA requires targetColumn with schema, table and name");
		}
		if (suggestion.type() == SuggestionType.NOT_NEEDED && suggestion.targetColumn() != null) {
			errors.add(sourceId + ": NOT_NEEDED must not have targetColumn");
		}
	}

	private static String cycle(final Map<String, Artifact> artifacts) {
		final Map<String, Integer> state = new HashMap<>();
		for (final String id : artifacts.keySet()) {
			final List<String> path = new ArrayList<>();
			if (visit(id, artifacts, state, path)) {
				return String.join(" -> ", path);
			}
		}
		return null;
	}

	private static boolean visit(final String id, final Map<String, Artifact> artifacts,
			final Map<String, Integer> state, final List<String> path) {
		final Integer current = state.get(id);
		if (current != null) {
			if (current == 1) {
				path.add(id);
				return true;
			}
			return false;
		}
		state.put(id, 1);
		path.add(id);
		for (final String dependency : artifacts.get(id).dependsOn()) {
			if (artifacts.containsKey(dependency) && visit(dependency, artifacts, state, path)) {
				return true;
			}
		}
		path.removeLast();
		state.put(id, 2);
		return false;
	}

	/**
	 * Reads and identifies every returned SQL file and checks it against the
	 * request.
	 */
	private static Map<String, SourceWork> inspect(final Loaded loaded, final Map<String, ObjectEntry> objects,
			final Map<String, Artifact> artifacts, final Path responseDir, final List<Schema> targetSchemas,
			final List<Diagnostic> diagnostics) throws Exception {
		final Map<String, Result> results = new HashMap<>();
		loaded.response().results().forEach(result -> results.put(result.sourceId(), result));
		final Map<String, SourceWork> work = new LinkedHashMap<>();
		for (final ObjectEntry object : loaded.manifest().objects()) {
			if (object.eligibility() != Eligibility.REQUESTED) {
				continue;
			}
			final Result result = results.get(object.sourceId());
			final List<Artifact> own = result == null ? List.of()
					: result.artifactIds().stream().map(artifacts::get).toList();
			final SourceWork source = new SourceWork(object, result, own, new HashMap<>(), new HashMap<>(),
					new LinkedHashMap<>(), new ArrayList<>());
			work.put(object.sourceId(), source);
			for (final Artifact artifact : own) {
				final List<String> problems = new ArrayList<>();
				source.problems().put(artifact.artifactId(), problems);
				final String sql;
				try {
					sql = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
							.onUnmappableCharacter(CodingErrorAction.REPORT)
							.decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(responseDir.resolve(artifact.file()))))
							.toString();
				} catch (final CharacterCodingException e) {
					problems.add("The file is not valid UTF-8");
					continue;
				}
				source.sql().put(artifact.artifactId(), sql);
				try {
					final Parsed parsed = PostgresCreateStatement.parse(sql);
					source.parsed().put(artifact.artifactId(), parsed);
					checkDeclaration(object, artifact, parsed, problems, targetSchemas);
				} catch (final PostgresCreateStatement.Rejected e) {
					problems.add(e.getMessage());
				}
			}
			if (result != null) {
				checkSource(source, artifacts, diagnostics);
			}
		}
		return work;
	}

	private static void checkDeclaration(final ObjectEntry object, final Artifact artifact, final Parsed parsed,
			final List<String> problems, final List<Schema> targetSchemas) {
		if (!artifact.kind().name().equals(parsed.kind().name())) {
			problems.add("Declared " + artifact.kind() + " but the SQL creates a " + parsed.kind());
			return;
		}
		if (artifact.kind() == ArtifactKind.FUNCTION) {
			requireName(parsed.name(), artifact.schema(), artifact.name(), "function", problems);
			if (!artifact.argumentTypes().isEmpty() || parsed.argumentCount() != 0) {
				problems.add("Only helper functions without arguments are accepted in this version");
			}
			if (parsed.securityDefiner()) {
				problems.add("SECURITY DEFINER is not accepted; privilege changes need an explicit review");
			}
			final Schema schema = schema(targetSchemas, artifact.schema());
			if (schema == null) {
				problems.add("Schema " + artifact.schema() + " is not in the target model");
			} else if (schema.getFunctions().stream().anyMatch(f -> f.getName().equals(artifact.name()))) {
				problems.add("Function " + artifact.schema() + "." + artifact.name()
						+ " already exists in the target model");
			}
			return;
		}
		final TableIdentity expected = object.targetTable();
		if (!Objects.equals(expected.catalog(), artifact.table().catalog())
				|| !expected.schema().equals(artifact.table().schema())
				|| !expected.name().equals(artifact.table().name())) {
			problems.add("The trigger must be on the target table " + expected.schema() + "." + expected.name()
					+ ", declared " + artifact.table().schema() + "." + artifact.table().name());
		}
		if (!artifact.schema().equals(artifact.table().schema())) {
			problems.add("The trigger schema must be the schema of its table");
		}
		if (!parsed.name().name().equals(artifact.name())) {
			problems.add("The SQL creates trigger " + parsed.name().name() + ", declared " + artifact.name());
		}
		requireName(parsed.table(), artifact.table().schema(), artifact.table().name(), "trigger table", problems);
		final Schema schema = schema(targetSchemas, expected.schema());
		final Table table = schema == null ? null
				: schema.getTables().stream().filter(t -> t.getName().equals(expected.name())).findFirst().orElse(null);
		if (table == null) {
			problems.add("Table " + expected.schema() + "." + expected.name() + " is not in the target model");
		} else if (schema.getTriggers().stream()
				.anyMatch(t -> t.getName().equals(artifact.name()) && expected.name().equals(t.getTableName()))) {
			problems.add("Trigger " + artifact.name() + " already exists on " + expected.schema() + "."
					+ expected.name() + " in the target model");
		}
	}

	private static void requireName(final QualifiedName parsed, final String schema, final String name,
			final String what, final List<String> problems) {
		if (parsed.schema() == null) {
			problems.add("The " + what + " must be schema-qualified in the SQL: " + parsed.name());
		} else if (!parsed.schema().equals(schema) || !parsed.name().equals(name)) {
			problems.add("The SQL names the " + what + " " + parsed + ", declared " + schema + "." + name);
		}
	}

	/** Checks that apply to the artifacts of one source object together. */
	private static void checkSource(final SourceWork source, final Map<String, Artifact> artifacts,
			final List<Diagnostic> diagnostics) {
		final List<Artifact> triggers = source.artifacts().stream().filter(a -> a.kind() == ArtifactKind.TRIGGER)
				.toList();
		if (source.result().status() == ResultStatus.CONVERTED && triggers.isEmpty()) {
			source.sourceProblems().add("A converted trigger must return at least one TRIGGER artifact");
		}
		final Set<String> called = new HashSet<>();
		for (final Artifact trigger : triggers) {
			final Parsed parsed = source.parsed().get(trigger.artifactId());
			if (parsed == null || parsed.function() == null) {
				continue;
			}
			final Artifact function = source.artifacts().stream().filter(a -> a.kind() == ArtifactKind.FUNCTION
					&& a.schema().equals(parsed.function().schema()) && a.name().equals(parsed.function().name()))
					.findFirst().orElse(null);
			if (parsed.function().schema() == null) {
				source.problems().get(trigger.artifactId())
						.add("The called function must be schema-qualified in the SQL: " + parsed.function().name());
			} else if (function == null) {
				source.problems().get(trigger.artifactId()).add("The trigger calls " + parsed.function()
						+ ", which is not a FUNCTION artifact of the same source object");
			} else {
				called.add(function.artifactId());
				if (!trigger.dependsOn().contains(function.artifactId())) {
					source.problems().get(trigger.artifactId())
							.add("The trigger must list " + function.artifactId() + " in dependsOn");
				}
			}
			final ObjectEntry object = source.object();
			if (!Objects.equals(object.timing(), parsed.timing())
					|| !Objects.equals(object.orientation(), parsed.orientation())
					|| !Objects.equals(object.events(), parsed.events().stream().sorted().toList())) {
				diagnostics.add(new Diagnostic("WARN", "TRIGGER_FORM_CHANGED",
						source.object().sourceId() + " " + trigger.artifactId() + ": source " + object.timing() + " "
								+ object.events() + " " + object.orientation() + ", converted " + parsed.timing() + " "
								+ parsed.events() + " " + parsed.orientation() + "; review the firing behaviour"));
			}
		}
		for (final Artifact artifact : source.artifacts()) {
			if (artifact.kind() == ArtifactKind.FUNCTION && source.parsed().containsKey(artifact.artifactId())
					&& !called.contains(artifact.artifactId())) {
				source.problems().get(artifact.artifactId())
						.add("The helper function is not called by any trigger of the same source object");
			}
		}
	}

	private record Built(String xml, String sql, List<String> applyOrder, Map<String, String> generated,
			Map<String, String> mismatches) {
	}

	private static boolean accepted(final SourceWork source) {
		return source.result() != null && source.result().status() == ResultStatus.CONVERTED
				&& source.sourceProblems().isEmpty() && source.problems().values().stream().allMatch(List::isEmpty);
	}

	/**
	 * Adds accepted objects to the target model, writes and re-reads Schema XML,
	 * and regenerates their DDL through the target SQL factory.
	 */
	private static Built build(final Manifest manifest, final Map<String, SourceWork> work, final Path root)
			throws Exception {
		final DbCommonObject<?> target = SchemaUtils
				.readXml(root.resolve("request").resolve(manifest.inputs().targetSchema().path()).toFile());
		final List<Schema> schemas = schemas(target);
		final List<String> order = new ArrayList<>();
		final Map<String, String> owners = new HashMap<>();
		for (final SourceWork source : work.values()) {
			if (!accepted(source)) {
				continue;
			}
			for (final Artifact artifact : ordered(source.artifacts())) {
				order.add(artifact.artifactId());
				owners.put(artifact.artifactId(), source.object().sourceId());
				final Parsed parsed = source.parsed().get(artifact.artifactId());
				final Schema schema = schema(schemas, artifact.schema());
				if (artifact.kind() == ArtifactKind.FUNCTION) {
					final Function function = new Function(artifact.name());
					function.setDefinition(source.sql().get(artifact.artifactId()));
					schema.getFunctions().add(function);
				} else {
					final Trigger trigger = new Trigger(artifact.name());
					trigger.setTableSchemaName(artifact.table().schema());
					trigger.setTableName(artifact.table().name());
					trigger.setActionTiming(parsed.timing());
					trigger.setActionOrientation(parsed.orientation());
					trigger.addEventManipulation(parsed.events().toArray(String[]::new));
					trigger.setDefinition(source.sql().get(artifact.artifactId()));
					schema.getTriggers().add(trigger);
				}
			}
		}
		final StringWriter xml = new StringWriter();
		target.writeXml(xml);
		final DbCommonObject<?> reread = SchemaUtils.readXml(new StringReader(xml.toString()));
		final List<Schema> rereadSchemas = schemas(reread);
		final SqlFactoryRegistry registry = order.isEmpty() ? null
				: dialect(manifest.targetDatabase()).createSqlFactoryRegistry();
		final Map<String, Artifact> byId = new HashMap<>();
		work.values().forEach(source -> source.artifacts().forEach(a -> byId.put(a.artifactId(), a)));
		final StringBuilder sql = new StringBuilder();
		final Map<String, String> generated = new HashMap<>();
		final Map<String, String> mismatches = new LinkedHashMap<>();
		for (final String id : order) {
			final Artifact artifact = byId.get(id);
			final Schema schema = schema(rereadSchemas, artifact.schema());
			final DbObject<?> object = artifact
					.kind() == ArtifactKind.FUNCTION
							? schema.getFunctions().stream().filter(f -> f.getName().equals(artifact.name()))
									.findFirst().orElse(null)
							: schema.getTriggers().stream()
									.filter(t -> t.getName().equals(artifact.name())
											&& artifact.table().name().equals(t.getTableName()))
									.findFirst().orElse(null);
			if (object == null) {
				mismatches.put(owners.get(id), id + ": the object was lost when the Schema XML was read back");
				continue;
			}
			final String text = generate(registry, object);
			generated.put(id, text);
			final String original = work.get(owners.get(id)).sql().get(id);
			if (!normalize(text).equals(normalize(original))) {
				mismatches.put(owners.get(id),
						id + ": the DDL regenerated by the SQL factory differs from the returned DDL");
				continue;
			}
			sql.append("-- ").append(owners.get(id)).append(' ').append(id).append(' ').append(artifact.kind())
					.append(' ').append(artifact.schema()).append('.').append(artifact.name()).append('\n');
			sql.append(normalize(text)).append("\n;\n\n");
		}
		return new Built(xml.toString(), sql.toString(), List.copyOf(order), generated, mismatches);
	}

	/**
	 * Resolves the target dialect from the product and version recorded in the
	 * request. A missing minor version is treated as 0.
	 */
	static Dialect dialect(final LogicMigrationRequestModel.Database database) {
		final String[] parts = database.version().split("\\.");
		try {
			final int major = Integer.parseInt(parts[0]);
			final int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
			final Integer revision = parts.length > 2 ? Integer.valueOf(parts[2]) : null;
			final Dialect dialect = DialectResolver.getInstance().getDialect(database.product(), major, minor,
					revision);
			if (dialect == null || !database.product().equalsIgnoreCase(dialect.getProductName())) {
				throw new CommandException("No " + database.product() + " dialect is available to regenerate the DDL; "
						+ "add the dialect module to the classpath");
			}
			return dialect;
		} catch (final NumberFormatException e) {
			throw new CommandException("Invalid target database version in the request: " + database.version(), e);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static String generate(final SqlFactoryRegistry registry, final DbObject<?> object) {
		final SqlFactory factory = registry.getSqlFactory(object, SqlType.CREATE);
		if (factory == null) {
			throw new CommandException(
					"The target dialect has no CREATE factory for " + object.getClass().getSimpleName());
		}
		final List<SqlOperation> operations = factory.createSql(object);
		final StringBuilder builder = new StringBuilder();
		for (final SqlOperation operation : operations) {
			if (!builder.isEmpty()) {
				builder.append("\n;\n");
			}
			builder.append(operation.getSqlText());
		}
		return builder.toString();
	}

	/** Ignores surrounding whitespace and one trailing semicolon. */
	static String normalize(final String sql) {
		String value = sql.strip();
		if (value.endsWith(";")) {
			value = value.substring(0, value.length() - 1).stripTrailing();
		}
		return value;
	}

	/**
	 * Orders artifacts so that dependencies come first, keeping the returned order
	 * otherwise.
	 */
	private static List<Artifact> ordered(final List<Artifact> artifacts) {
		final Map<String, Artifact> byId = new LinkedHashMap<>();
		artifacts.forEach(artifact -> byId.put(artifact.artifactId(), artifact));
		final Set<String> done = new LinkedHashSet<>();
		for (final String id : byId.keySet()) {
			order(id, byId, done);
		}
		return done.stream().map(byId::get).toList();
	}

	private static void order(final String id, final Map<String, Artifact> byId, final Set<String> done) {
		if (done.contains(id)) {
			return;
		}
		for (final String dependency : byId.get(id).dependsOn()) {
			if (byId.containsKey(dependency)) {
				order(dependency, byId, done);
			}
		}
		done.add(id);
	}

	private static List<ImportedObject> importedObjects(final Manifest manifest, final Map<String, SourceWork> work,
			final Built built, final Path responseDir) throws Exception {
		final List<ImportedObject> result = new ArrayList<>();
		for (final ObjectEntry object : manifest.objects()) {
			final SourceWork source = work.get(object.sourceId());
			if (source == null) {
				result.add(new ImportedObject(object.sourceId(), object.identity(), object.eligibility(),
						ImportStatus.NOT_REQUESTED, null, List.of(), null, List.of(), List.of()));
				continue;
			}
			if (source.result() == null) {
				result.add(new ImportedObject(object.sourceId(), object.identity(), object.eligibility(),
						ImportStatus.MISSING, null, List.of(), null, List.of(),
						List.of("The response has no result for this requested object")));
				continue;
			}
			final List<ImportedArtifact> artifacts = new ArrayList<>();
			for (final Artifact artifact : source.artifacts()) {
				final Parsed parsed = source.parsed().get(artifact.artifactId());
				final String generated = built.generated().get(artifact.artifactId());
				artifacts.add(new ImportedArtifact(artifact.artifactId(), artifact.kind(), artifact.schema(),
						artifact.name(), artifact.table(), List.copyOf(artifact.dependsOn()),
						new InputFile("response/" + artifact.file(), fingerprint(responseDir.resolve(artifact.file()))),
						parsed == null ? null : declaration(parsed),
						generated == null ? null : LogicTriggerInventory.sha256(generated),
						List.copyOf(source.problems().getOrDefault(artifact.artifactId(), List.of()))));
			}
			final ImportStatus status = source.result().status() == ResultStatus.UNRESOLVED ? ImportStatus.UNRESOLVED
					: accepted(source) ? ImportStatus.ACCEPTED_CANDIDATE : ImportStatus.REJECTED;
			result.add(new ImportedObject(object.sourceId(), object.identity(), object.eligibility(), status,
					source.result().status(), List.copyOf(source.result().notes()), source.result().suggestion(),
					List.copyOf(artifacts), List.copyOf(source.sourceProblems())));
		}
		return List.copyOf(result);
	}

	private static ParsedDeclaration declaration(final Parsed parsed) {
		return new ParsedDeclaration(parsed.orReplace(), parsed.name().schema(), parsed.name().name(),
				parsed.kind() == PostgresCreateStatement.Kind.FUNCTION ? parsed.argumentCount() : null,
				parsed.returns(), parsed.language(), parsed.securityDefiner(), parsed.timing(), parsed.events(),
				parsed.orientation(),
				parsed.table() == null ? null : new TableIdentity(null, parsed.table().schema(), parsed.table().name()),
				parsed.function() == null ? null : parsed.function().schema(),
				parsed.function() == null ? null : parsed.function().name());
	}

	private static List<Schema> schemas(final DbCommonObject<?> root) {
		if (root instanceof Schema schema) {
			return List.of(schema);
		}
		if (root instanceof Catalog catalog) {
			return new ArrayList<>(catalog.getSchemas());
		}
		throw new CommandException("The target Schema XML root must be Schema or Catalog");
	}

	private static Schema schema(final List<Schema> schemas, final String name) {
		return schemas.stream().filter(schema -> Objects.equals(schema.getName(), name)).findFirst().orElse(null);
	}

	private static boolean blank(final String value) {
		return value == null || value.isBlank();
	}

	static String fingerprint(final Path file) throws Exception {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(file.toFile());
	}
}
