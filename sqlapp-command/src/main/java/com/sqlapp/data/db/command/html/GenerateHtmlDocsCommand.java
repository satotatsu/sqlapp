/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.html;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.sqlapp.data.db.command.export.TableFileReader;
import com.sqlapp.data.db.command.export.TableFileReader.TableFilesPair;
import com.sqlapp.data.db.command.properties.DictionaryFileDirectoryProperty;
import com.sqlapp.data.db.command.properties.DirectoryProperty;
import com.sqlapp.data.db.command.properties.FileDirectoryProperty;
import com.sqlapp.data.db.command.properties.ForeignKeyDefinitionDirectoryProperty;
import com.sqlapp.data.db.command.properties.OutputDirectoryProperty;
import com.sqlapp.data.db.command.properties.PlaceholderProperty;
import com.sqlapp.data.db.command.properties.UseSchemaNameDirectoryProperty;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AbstractDbObject;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.VirtualForeignKeyLoader;
import com.sqlapp.data.schemas.properties.DisplayNameProperty;
import com.sqlapp.data.schemas.properties.DisplayRemarksProperty;
import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.elk.NameMode;
import com.sqlapp.elk.SVGDrawMode;
import com.sqlapp.elk.TableSvgCreator;
import com.sqlapp.elk.TableSvgCreator.SVGResult;
import com.sqlapp.elk.schemas.TableNode;
import com.sqlapp.elk.util.SchemaElkUtils;
import com.sqlapp.exceptions.InvalidFontNameException;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.FontUtils;
import com.sqlapp.util.LinkedProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenerateHtmlDocsCommand extends AbstractSchemaFileCommand
		implements PlaceholderProperty, FileDirectoryProperty, DirectoryProperty, OutputDirectoryProperty,
		DictionaryFileDirectoryProperty, UseSchemaNameDirectoryProperty, ForeignKeyDefinitionDirectoryProperty {

	/**
	 * template path
	 */
	private File templatePath;

	private File diagramsPath;

	private RenderOptions renderOptions = new RenderOptions();

	private String diagramFont = null;

	private File dictionaryFileDirectory;

	private final String diagramFormat = "svg";

	private ExecutorService executorService = null;

	private boolean multiThread = true;

	private String placeholderPrefix = "${";

	private String placeholderSuffix = "}";

	private boolean placeholders = false;
	/** file directory */
	private File fileDirectory = null;
	/**
	 * Data file Directory
	 */
	private File directory = null;

	private boolean useSchemaNameDirectory = false;

	private boolean useTableNameDirectory = false;
	/** file filter */
	private Predicate<File> fileFilter = f -> true;
	/** Virtual foreign Key definitions */
	private File foreignKeyDefinitionDirectory = null;

	private Function<ForeignKeyConstraint, String> virtualForeignKeyLabel = fk -> getMessage("Virtual");

	private int cpu;

	private StyleRenderer styleRenderer = new StyleRenderer();

	public static ResourceBundle getResourceBundle(Locale locale) {
		String path = GenerateHtmlDocsCommand.class.getPackageName();
		ResourceBundle resourceBundle = ResourceBundle.getBundle(path + ".messages", locale);
		return resourceBundle;
	}

	public static String getMessage(String key) {
		String value = getResourceBundle(Locale.getDefault()).getString(key);
		return value;
	}

	public static String getMessage(Locale locale, String key) {
		String value = getResourceBundle(locale).getString(key);
		return value;
	}

	private TableFileReader createTableFileReader() {
		TableFileReader tableFileReader = new TableFileReader();
		tableFileReader.setContext(this.getContext());
		tableFileReader.setCsvEncoding(this.getCsvEncoding());
		tableFileReader.setDirectory(this.getDirectory());
		tableFileReader.setFileDirectory(this.getFileDirectory());
		tableFileReader.setFileFilter(this.getFileFilter());
		tableFileReader.setJsonConverter(this.getJsonConverter());
		tableFileReader.setPlaceholderPrefix(this.getPlaceholderPrefix());
		tableFileReader.setPlaceholders(this.isPlaceholders());
		tableFileReader.setPlaceholderSuffix(this.getPlaceholderSuffix());
		tableFileReader.setUseSchemaNameDirectory(this.isUseSchemaNameDirectory());
		return tableFileReader;
	}

	private VirtualForeignKeyLoader createVirtualForeignKeyLoader() {
		VirtualForeignKeyLoader loader = new VirtualForeignKeyLoader();
		return loader;
	}

	private static final Function<Table, String> TABLE_HTML_FUNCTION = ((t) -> {
		return SchemaElkUtils.getName(t);
	});

	/**
	 * file
	 */
	private File outputDirectory = new File("./");

	public GenerateHtmlDocsCommand() {
		cpu = Runtime.getRuntime().availableProcessors();
	}

	@Override
	protected void create(Catalog catalog) throws Exception {
		checkInput(catalog);
		if (this.isMultiThread() && executorService == null) {
			executorService = Executors.newFixedThreadPool(cpu);
		}
		TableFileReader tableFileReader = createTableFileReader();
		List<TableFilesPair> tfs = tableFileReader.getTableFilesPairs(catalog);
		tableFileReader.setFiles(tfs);
		VirtualForeignKeyLoader virtualForeignKeyLoader = createVirtualForeignKeyLoader();
		virtualForeignKeyLoader.load(catalog, this.getForeignKeyDefinitionDirectory());
		diagramsPath = new File(this.getOutputDirectory(), "diagrams");
		setProperties(catalog);
		Menu rootMenu = createMenu(catalog);
		ParametersContext context = new ParametersContext();
		createCommon(context, rootMenu);
		List<Future<?>> futures = CommonUtils.list();
		for (MenuDefinition menuDefinition : MenuDefinition.values()) {
			switch (menuDefinition) {
			case Relationships:
				createRelationshipHtml(catalog, context.clone(), rootMenu.clone(), futures);
				break;
			case Schemas:
				writeSchemas(catalog, context.clone(), rootMenu.clone(), futures);
				break;
			case Tables:
				writeTables(catalog, context.clone(), rootMenu.clone(), futures);
				break;
			case Packages:
			case Types:
				outputMenuAndDetailWithBodys(catalog, context.clone(), rootMenu.clone(), menuDefinition, futures);
				break;
			default:
				outputMenuAndDetail(catalog, context.clone(), rootMenu.clone(), menuDefinition, futures);
			}
		}
		this.await(futures);
	}

	private void outputMenuAndDetailWithBodys(Catalog catalog, ParametersContext context, Menu rootMenu,
			MenuDefinition menuDefinition, List<Future<?>> futures) throws InterruptedException, ExecutionException {
		List<Object> list = getList(catalog, menuDefinition);
		ParametersContext cloneContext = context.clone();
		execute(() -> {
			outputMenu(catalog, cloneContext, rootMenu, menuDefinition, list);
		}, futures);
		outputMenuDetailWithBodys(catalog, cloneContext.clone(), rootMenu.clone(), menuDefinition, list, futures);
	}

	private void outputMenuAndDetail(Catalog catalog, ParametersContext context, Menu rootMenu,
			MenuDefinition menuDefinition, List<Future<?>> futures) throws InterruptedException, ExecutionException {
		List<Object> list = getList(catalog, menuDefinition);
		ParametersContext cloneContext = context.clone();
		execute(() -> {
			outputMenu(catalog, cloneContext, rootMenu, menuDefinition, list);
		}, futures);
		if (menuDefinition.hasDetails()) {
			outputMenuDetails(catalog, cloneContext.clone(), rootMenu.clone(), menuDefinition, list, futures);
		}
	}

	private void createRelationshipHtml(Catalog catalog, ParametersContext context, Menu rootMenu,
			List<Future<?>> futures) throws InterruptedException, ExecutionException {
		execute(() -> {
			List<Future<?>> internalFutures = CommonUtils.list();
			createImages(catalog, context, rootMenu, internalFutures);
			this.await(internalFutures);
			outputMenu(catalog, context, rootMenu, MenuDefinition.Relationships, Collections.emptyList());
		}, futures);
	}

	private void execute(Execute execute, List<Future<?>> futures) {
		Future<?> future = this.submit(() -> {
			try {
				execute.run();
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		futures.add(future);
	}

	@FunctionalInterface
	static interface Execute {
		void run() throws Exception;
	}

	@FunctionalInterface
	static interface ExecuteWithMenuDifinition {
		void run(MenuDefinition menuDefinition) throws Exception;
	}

	private void checkInput(Catalog catalog) {
		if (!CommonUtils.isEmpty(this.getDiagramFont())) {
			if (!FontUtils.isValidFontName(this.getDiagramFont())) {
				throw new InvalidFontNameException(this.getDiagramFont());
			}
		}
	}

	private void setProperties(Catalog catalog) throws Exception {
		List<Future<?>> futures = CommonUtils.list();
		for (MenuDefinition menuDefinition : MenuDefinition.values()) {
			execute(() -> {
				List<Object> list = menuDefinition.getDatas(catalog);
				if (list.isEmpty()) {
					return;
				}
				if (!(list.get(0) instanceof NameProperty)) {
					return;
				}
				Properties properties = new LinkedProperties();
				loadProperties(this.getDictionaryFileDirectory(), menuDefinition, properties);
				if (properties.size() == 0) {
					return;
				}
				for (Object obj : list) {
					String fullName = this.getFullName(obj, true);
					String fullNameWithoutSchemaName = this.getFullName(obj, false);
					String name = this.getName(obj);
					String value = getValue(properties, SchemaProperties.DISPLAY_NAME.getLabel(), fullName,
							fullNameWithoutSchemaName, name);
					if (!CommonUtils.isEmpty(value)) {
						((DisplayNameProperty<?>) obj).setDisplayName(value);
					}
					value = getValue(properties, SchemaProperties.DISPLAY_REMARKS.getLabel(), fullName,
							fullNameWithoutSchemaName, name);
					if (!CommonUtils.isEmpty(value)) {
						((DisplayRemarksProperty<?>) obj).setDisplayRemarks(value);
					}
				}
			}, futures);
		}
		await(futures);
	}

	private String getValue(Properties properties, String type, String... names) {
		for (String name : names) {
			String value = properties.getProperty(name + "." + type);
			if (!CommonUtils.isEmpty(value)) {
				return value;
			}
		}
		return null;
	}

	protected StyleRenderer createCommon(ParametersContext context, Menu rootMenu) {
		String text = styleRenderer.render(context);
		context.put("style", text);
		context.put("createdAt", new Date());
		context.put("_diagramExtension", this.getDiagramFormat());
		return styleRenderer;
	}

	protected Menu createMenu(Catalog catalog) {
		Menu rootMenu = new Menu();
		MenuDefinition.toMenus(catalog).forEach(menu -> {
			rootMenu.addChild(menu);
		});
		return rootMenu;
	}

	private void createImages(Catalog catalog, ParametersContext context, Menu rootMenu, List<Future<?>> futures)
			throws InterruptedException, ExecutionException {
		List<Table> list = MenuDefinition.Tables.getDatas(catalog);
		final String largeName = "_summary_relations_large";
		execute(() -> {
			RelationImageHolder holder = createCatalogRelationImage(largeName, catalog, false);
			context.put(largeName, holder);
		}, futures);
		//
		final String largeNameLogical = largeName + "_logical";
		execute(() -> {
			RelationImageHolder holder = createCatalogRelationImage(largeNameLogical, catalog, true);
			if (holder != null) {
				context.put(largeNameLogical, holder);
			}
		}, futures);
		//
		final String smallName = "_summary_relations_small";
		execute(() -> {
			RelationImageHolder imapSmall = createRelationSmallImage(smallName, list, false);
			if (imapSmall != null) {
				context.put(smallName, imapSmall);
			}
		}, futures);
		//
		final String smallNameLogical = smallName + "_logical";
		execute(() -> {
			RelationImageHolder imapSmall = createRelationSmallImage(smallNameLogical, list, true);
			if (imapSmall != null) {
				context.put(smallNameLogical, imapSmall);
			}
		}, futures);
	}

	private Future<?> submit(Runnable ｒunnable) {
		if (this.isMultiThread() && cpu > 1) {
			Future<?> future = this.executorService.submit(ｒunnable);
			return future;
		} else {
			ｒunnable.run();
			Future<?> future = new Future<Object>() {
				@Override
				public boolean cancel(boolean mayInterruptIfRunning) {
					return false;
				}

				@Override
				public boolean isCancelled() {
					return false;
				}

				@Override
				public boolean isDone() {
					return true;
				}

				@Override
				public Object get() throws InterruptedException, ExecutionException {
					return null;
				}

				@Override
				public Object get(long timeout, TimeUnit unit)
						throws InterruptedException, ExecutionException, TimeoutException {
					return null;
				}
			};
			return future;
		}
	}

	private RelationImageHolder createCatalogRelationImage(String name, Catalog catalog, boolean logical) {
		if (logical) {
			Optional<Table> optional = catalog.getSchemas().stream().flatMap(s -> s.getTables().stream())
					.filter(t -> hasDisplayName(t)).findFirst();
			if (!optional.isPresent()) {
				return null;
			}
		}
		if (logical) {
			TableSvgCreator svgCreator = createCreateSvgCreator(SVGDrawMode.NORMAL, NameMode.LOGICAL, "../tables/",
					t -> {
					});
			return createImage(name, catalog, svgCreator);
		} else {
			TableSvgCreator svgCreator = createCreateSvgCreator(SVGDrawMode.NORMAL, NameMode.NORMAL, "../tables/",
					t -> {
					});
			return createImage(name, catalog, svgCreator);
		}
	}

	private boolean hasDisplayName(Collection<Table> c) {
		Optional<Table> optional = c.stream().filter(t -> hasDisplayName(t)).findFirst();
		return optional.isPresent();
	}

	private RelationImageHolder createRelationSmallImage(String name, List<Table> list, boolean logical) {
		if (logical) {
			if (!hasDisplayName(list)) {
				return null;
			}
		}
		Optional<Table> optional = list.stream().filter(t -> t.getConstraints().getForeignKeyConstraints().size() > 0)
				.findFirst();
		if (!optional.isPresent()) {
			return null;
		}
		if (logical) {
			TableSvgCreator svgCreator = createCreateSvgCreator(SVGDrawMode.SIMPLE, NameMode.LOGICAL, "../tables/",
					t -> {
					});
			return createImage(name, list, svgCreator);
		} else {
			TableSvgCreator svgCreator = createCreateSvgCreator(SVGDrawMode.SIMPLE, NameMode.NORMAL, "../tables/",
					t -> {
					});
			return createImage(name, list, svgCreator);
		}
	}

	private RelationImageHolder createImage(String name, Catalog catalog, TableSvgCreator svgCreator) {
		File imageFile = new File(diagramsPath, name + "." + getDiagramFormat());
		SVGResult svgResult = svgCreator.generateSchemaSvg(catalog.getSchemas());
		FileUtils.writeText(imageFile.getAbsolutePath(), "UTF8", svgResult.getImage());
		String iframeStyle = toIframeStyle(svgResult);
		RelationImageHolder holder = new RelationImageHolder(imageFile, svgResult.getImage(), iframeStyle);
		return holder;
	}

	public static String toIframeStyle(SVGResult svgResult) {
		// ② width / height fallback
		int width = (int) svgResult.getTotalWidth();
		int height = (int) svgResult.getTotalHeight();
		return buildStyle(width, height);
	}

	private static String buildStyle(int w, int h) {
		return "width:" + (w + 5) + "px;height:" + (h + 5) + "px";
	}

	private RelationImageHolder createImage(String name, Collection<Table> list, TableSvgCreator svgCreator) {
		File imageFile = new File(diagramsPath, name + "." + getDiagramFormat());
		SVGResult svgResult = svgCreator.generateSvg(list);
		FileUtils.writeText(imageFile.getAbsolutePath(), "UTF8", svgResult.getImage());
		String iframeStyle = toIframeStyle(svgResult);
		RelationImageHolder holder = new RelationImageHolder(imageFile, svgResult.getImage(), iframeStyle);
		return holder;
	}

	private TableSvgCreator createCreateSvgCreator(SVGDrawMode svgDrawMode, NameMode nameMode, String path,
			Consumer<TableNode> consumer) {
		TableSvgCreator svgCreator = svgDrawMode.createTableSvgCreator();
		svgCreator.setNameMode(nameMode);
		svgCreator.setUrlFunction(t -> {
			return FileUtils.combinePath(CommonUtils.coalesce(path, ""), TABLE_HTML_FUNCTION.apply(t) + ".html");
		});
		svgCreator.setTableNodeConsumer(consumer);
		return svgCreator;
	}

	protected void initialize(Renderer renderer) {
	}

	protected void writeSchemas(Catalog catalog, ParametersContext context, Menu rootMenu, List<Future<?>> futures)
			throws InterruptedException, ExecutionException {
		List<Schema> list = getList(catalog, MenuDefinition.Schemas);
		execute(() -> {
			outputMenu(catalog, context, rootMenu, MenuDefinition.Schemas, list);
		}, futures);
		//
		outputMenuDetails(catalog, context.clone(), rootMenu.clone(), MenuDefinition.Schemas, list, (con, obj) -> {
			Schema val = (Schema) obj;
			RelationImageHolder holder = createSchemaRelation(val, false);
			if (holder != null) {
				con.put("relations", holder);
			}
			//
			holder = createSchemaRelation(val, true);
			if (holder != null) {
				con.put("relations_logical", holder);
			}
		}, futures);
	}

	/**
	 * create tables htmls
	 * 
	 * @param catalog
	 * @param context
	 * @param rootMenu
	 * @param futures
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	protected void writeTables(Catalog catalog, ParametersContext context, Menu rootMenu, List<Future<?>> futures)
			throws InterruptedException, ExecutionException {
		List<Table> list = getList(catalog, MenuDefinition.Tables);
		execute(() -> {
			outputMenu(catalog, context, rootMenu, MenuDefinition.Tables, list);
		}, futures);
		//
		outputMenuDetails(catalog, context.clone(), rootMenu.clone(), MenuDefinition.Tables, list, (con, obj) -> {
			Table val = (Table) obj;
			if (!hasRelation(val)) {
				return;
			}
			RelationImageHolder holder = createTableRelationImage(val, false, "../tables/");
			if (holder != null) {
				con.put("relations", holder);
			}
			//
			holder = createTableRelationImage(val, true, "../tables/");
			if (holder != null) {
				con.put("relations_logical", holder);
			}
		}, futures);
	}

	private boolean hasRelation(Table table) {
		if (!CommonUtils.isEmpty(table.getChildRelations())) {
			return true;
		}
		if (!CommonUtils.isEmpty(table.getConstraints().getForeignKeyConstraints())) {
			return true;
		}
		if (!CommonUtils.isEmpty(table.getInherits())) {
			return true;
		}
		if (table.getPartitionParent() != null) {
			return true;
		}
		if (table.getPartitioning() != null && !CommonUtils.isEmpty(table.getPartitioning().getPartitionTables())) {
			return true;
		}
		return false;
	}

	protected void outputMenuDetailWithBodys(Catalog catalog, ParametersContext context, Menu rootMenu,
			MenuDefinition menuDefinition, List<?> list, List<Future<?>> futures)
			throws InterruptedException, ExecutionException {
		HtmlRenderer htmlRenderer = createHtmlDetailRenderer();
		String name = SchemaUtils.getSingularName(menuDefinition.toString()).toLowerCase();
		htmlRenderer.setTemplateResource(name + ".html");
		File path = new File(getOutputDirectory(), name + "s");
		File bodyPath = new File(getOutputDirectory(), name + "bodies");
		HtmlRenderer detailHtmlRenderer = new HtmlDetailRenderer();
		detailHtmlRenderer.setTemplateResource(name + "body.html");
		for (Object obj : list) {
			execute(() -> {
				ParametersContext con = context.clone();
				Menu menu = rootMenu.clone();
				@SuppressWarnings("unchecked")
				AbstractDbObject<?> body = ((com.sqlapp.data.schemas.Body<AbstractDbObject<?>>) obj).getBody();
				String detailName = HtmlUtils.objectFullPath(obj);
				writeDetail(htmlRenderer, path, obj, con, menu, detailName);
				if (body != null) {
					String detailBodyName = HtmlUtils.objectFullPath(body);
					writeDetail(detailHtmlRenderer, bodyPath, body, con, menu, detailBodyName);
				}
			}, futures);
		}
	}

	private HtmlRenderer createHtmlRenderer() {
		HtmlRenderer htmlRenderer = new HtmlRenderer();
		htmlRenderer.setRenderOptions(this.createRenderOption());
		return htmlRenderer;
	}

	private HtmlRenderer createHtmlDetailRenderer() {
		HtmlRenderer htmlRenderer = new HtmlDetailRenderer();
		htmlRenderer.setRenderOptions(this.createRenderOption());
		return htmlRenderer;
	}

	private RenderOptions createRenderOption() {
		RenderOptions renderOptions = this.getRenderOptions().clone();
		return renderOptions;
	}

	protected void outputMenuDetails(Catalog catalog, ParametersContext context, Menu rootMenu,
			MenuDefinition menuDefinition, List<?> list, BiConsumer<ParametersContext, Object> consumer,
			List<Future<?>> futures) throws InterruptedException, ExecutionException {
		HtmlRenderer htmlRenderer = createHtmlDetailRenderer();
		String name = SchemaUtils.getSingularName(menuDefinition.toString()).toLowerCase();
		htmlRenderer.setTemplateResource(name + ".html");
		File path = new File(getOutputDirectory(), menuDefinition.toString().toLowerCase());
		for (Object obj : list) {
			execute(() -> {
				Menu menu = rootMenu.clone();
				ParametersContext con = context.clone();
				if (consumer != null) {
					consumer.accept(con, obj);
				}
				String detailName = HtmlUtils.objectFullPath(obj);
				writeDetail(htmlRenderer, path, obj, con, menu, detailName);
			}, futures);
		}
	}

	private void await(List<Future<?>> futures) throws InterruptedException, ExecutionException {
		for (Future<?> future : futures) {
			future.get();
		}
	}

	protected void outputMenuDetails(Catalog catalog, ParametersContext context, Menu rootMenu,
			MenuDefinition menuDefinition, List<?> list, List<Future<?>> futures)
			throws InterruptedException, ExecutionException {
		outputMenuDetails(catalog, context, rootMenu, menuDefinition, list, null, futures);
	}

	protected void writeDetail(HtmlRenderer renderer, File path, Object obj, ParametersContext context, Menu rootMenu,
			String name) {
		context.put("obj", obj);
		context.put("context", context);
		rootMenu.setActiveRecursive(false);
		rootMenu.setRelativePathRecursive("../");
		context.put("rootMenu", rootMenu);
		String text = render(renderer, context);
		writeText(new File(path, name + ".html"), text);
		rootMenu.setRelativePath(null);
	}

	private String render(HtmlRenderer renderer, ParametersContext context) {
		try {
			return renderer.render(context);
		} catch (Exception e) {
			throw new RuntimeException("template=" + renderer.getTemplateResource(), e);
		}
	}

	private RelationImageHolder createTableRelationImage(Table table, boolean logical, String path) {
		Set<Table> tables = CommonUtils.set();
		tables.add(table);
		table.getChildRelations().forEach(fk -> {
			tables.add(fk.getTable());
		});
		table.getConstraints().getForeignKeyConstraints().forEach(fk -> {
			tables.add(fk.getRelatedTable());
		});
		setParentPartitionTables(table, tables);
		setChildPartitionTables(table, tables);
		if (tables.size() == 0) {
			return null;
		}
		if (logical) {
			if (!hasDisplayName(tables)) {
				return null;
			}
		}
		Consumer<TableNode> cons = (tNode -> {
			if (tNode.getTable() == table) {
				SVGDrawMode.NORMAL.reset(tNode);
			}
		});
		if (logical) {
			TableSvgCreator svgCreator = createCreateSvgCreator(SVGDrawMode.SIMPLE, NameMode.LOGICAL, path, cons);
			return createImage(HtmlUtils.objectFullPath(table) + "_logical", tables, svgCreator);
		} else {
			TableSvgCreator svgCreator = createCreateSvgCreator(SVGDrawMode.SIMPLE, NameMode.NORMAL, path, cons);
			return createImage(HtmlUtils.objectFullPath(table), tables, svgCreator);
		}
	}

	private void setParentPartitionTables(Table table, Set<Table> tables) {
		if (table == null) {
			return;
		}
		if (table.getPartitionParent() == null) {
			return;
		}
		tables.add(table.getPartitionParent().getTable());
		setParentPartitionTables(table.getPartitionParent().getTable(), tables);
	}

	private void setChildPartitionTables(Table table, Set<Table> tables) {
		if (table == null) {
			return;
		}
		if (table.getPartitioning() != null && !CommonUtils.isEmpty(table.getPartitioning().getPartitionTables())) {
			table.getPartitioning().getPartitionTables().forEach(t -> {
				tables.add(t);
			});
		}
	}

	private RelationImageHolder createSchemaRelation(Schema schema, boolean logical) {
		if (schema.getTables().size() == 0) {
			return null;
		}
		if (logical) {
			if (!hasDisplayName(schema.getTables())) {
				return null;
			}
		}
		if (logical) {
			TableSvgCreator svgCreator = createCreateSvgCreator(SVGDrawMode.SIMPLE, NameMode.LOGICAL, "../tables/",
					t -> {
					});
			return createImage(HtmlUtils.objectFullPath(schema) + "_logical", schema.getTables(), svgCreator);
		} else {
			TableSvgCreator svgCreator = createCreateSvgCreator(SVGDrawMode.SIMPLE, NameMode.NORMAL, "../tables/",
					t -> {
					});
			return createImage(HtmlUtils.objectFullPath(schema), schema.getTables(), svgCreator);
		}
	}

	private boolean hasDisplayName(Table table) {
		if (table.getDisplayName() != null) {
			return true;
		}
		for (com.sqlapp.data.schemas.Column column : table.getColumns()) {
			if (column.getDisplayName() != null) {
				return true;
			}
		}
		return false;
	}

	private void outputMenu(Catalog catalog, ParametersContext context, Menu rootMenu, MenuDefinition menuDefinition,
			List<?> list) {
		if (!menuDefinition.hasData(catalog)) {
			return;
		}
		String name = menuDefinition.toString().toLowerCase();
		HtmlRenderer renderer = createHtmlRenderer();
		renderer.setTemplateResource(menuDefinition.getHtmlName());
		initialize(renderer);
		rootMenu.setActive(menuDefinition);
		context.put("rootMenu", rootMenu);
		context.put("catalog", catalog);
		context.put("title", menuDefinition.getDisplayName());
		context.put("name", name);
		context.put(name, list);
		context.put("list", list);
		context.put("context", context);
		Map<String, ?> map = HtmlUtils.analyzeAllProperties(list);
		context.put("_prefix", map);
		map.forEach((k, v) -> {
			context.put(k, v);
		});
		if (menuDefinition == MenuDefinition.Columns || menuDefinition == MenuDefinition.Domains) {
			context.put("columns", map);
		}
		rootMenu.setRelativePathRecursive(null);
		String text = render(renderer, context);
		writeText(new File(getOutputDirectory(), menuDefinition.getHtmlName()), text);
	}

	private <T> List<T> getList(Catalog catalog, MenuDefinition menuDefinition) {
		if (!menuDefinition.hasData(catalog)) {
			return Collections.emptyList();
		}
		List<T> list = menuDefinition.getDatas(catalog);
		return list;
	}

	private void writeText(File file, String text) {
		String[] args = text.split("\n");
		List<String> texts = CommonUtils.list();
		for (String arg : args) {
			if (arg.trim().length() != 0) {
				texts.add(arg);
			}
		}
		FileUtils.writeText(file, "UTF8", texts.toArray(new String[0]));
	}

	public void setVirtualForeignKeyLabel(Function<ForeignKeyConstraint, String> virtualForeignKeyLabel) {
		this.virtualForeignKeyLabel = virtualForeignKeyLabel;
	}

	/**
	 * @param virtualForeignKeyLabel the virtualForeignKeyLabel to set
	 */
	public void setVirtualForeignKeyLabel(String virtualForeignKeyLabel) {
		this.virtualForeignKeyLabel = fk -> virtualForeignKeyLabel;
	}

}
