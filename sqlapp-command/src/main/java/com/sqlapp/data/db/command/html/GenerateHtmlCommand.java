/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command.html;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import com.sqlapp.data.db.command.export.TableFileReader;
import com.sqlapp.data.db.command.export.TableFileReader.TableFilesPair;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AbstractDbObject;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.properties.DisplayNameProperty;
import com.sqlapp.data.schemas.properties.DisplayRemarksProperty;
import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.exceptions.InvalidFontNameException;
import com.sqlapp.graphviz.Graph;
import com.sqlapp.graphviz.command.DotRuntime;
import com.sqlapp.graphviz.command.OutputFormat;
import com.sqlapp.graphviz.schemas.SchemaGraphBuilder;
import com.sqlapp.graphviz.schemas.TableLabelBuilder;
import com.sqlapp.graphviz.schemas.TableNodeBuilder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.FontUtils;
import com.sqlapp.util.LinkedProperties;

public class GenerateHtmlCommand extends AbstractSchemaFileCommand {

	/**
	 * template path
	 */
	private File templatePath;

	private File diagramsPath;

	private RenderOptions renderOptions = new RenderOptions();

	private transient boolean graphvizInstalled = false;

	private String dot = null;

	private String diagramFont = null;

	private OutputFormat diagramFormat=OutputFormat.svg;

	private ExecutorService executorService=null;

	private boolean multiThread = true;

	private String placeholderPrefix="${";

	private String placeholderSuffix="}";

	private boolean placeholders=false;
	/**file directory*/
	private File fileDirectory=null;
	/**
	 * Data file Direcroty
	 */
	private File directory=null;
	
	private boolean useSchemaNameDirectory=false;

	private boolean useTableNameDirectory=false;
	/**file filter*/
	private Predicate<File> fileFilter=f->true;
	/**Virtual foreign Key definitions*/
	private File foreignKeyDefinitionDirectory=null;

	private int cpu;
	
	private DotRuntime dotRuntime = new DotRuntime();

	private TableFileReader createTableFileReader(){
		TableFileReader tableFileReader=new TableFileReader();
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
		tableFileReader.setUseTableNameDirectory(this.isUseTableNameDirectory());
		return tableFileReader;
	}

	private VirtualForeignKeyLoader createVirtualForeignKeyLoader(){
		VirtualForeignKeyLoader loader=new VirtualForeignKeyLoader();
		return loader;
	}
	
	/**
	 * file
	 */
	private File outputDirectory = new File("./");

	public GenerateHtmlCommand() {
		cpu = Runtime.getRuntime().availableProcessors();
	}

	@Override
	protected void create(Catalog catalog) throws Exception {
		checkInput(catalog);
		if (this.isMultiThread()&&executorService==null) {
			executorService = Executors.newFixedThreadPool(cpu);
		}
		TableFileReader tableFileReader=createTableFileReader();
		List<TableFilesPair> tfs=tableFileReader.getTableFilePairs(catalog);
		try {
			tableFileReader.setFiles(tfs);
		} catch (EncryptedDocumentException | InvalidFormatException | IOException | XMLStreamException e) {
			this.getExceptionHandler().handle(e);
		}
		VirtualForeignKeyLoader virtualForeignKeyLoader=createVirtualForeignKeyLoader();
		virtualForeignKeyLoader.load(catalog, this.getForeignKeyDefinitionDirectory());
		diagramsPath = new File(this.getOutputDirectory(), "diagrams");
		dotRuntime.setOutputFormat(this.getDiagramFormat());
		dotRuntime.setDir(diagramsPath);
		if (this.dot != null) {
			dotRuntime.setDot(dot);
		}
		String version = dotRuntime.getVersion();
		if (version != null) {
			graphvizInstalled = true;
		}
		setProperties(catalog);
		Menu rootMenu = createMenu(catalog);
		ParametersContext context = new ParametersContext();
		createCommon(context, rootMenu);
		List<Future<?>> futures = CommonUtils.list();
		for (MenuDefinition menuDefinition : MenuDefinition.values()) {
			switch (menuDefinition) {
			case Relationships:
				createRelationship(catalog, context.clone(), rootMenu.clone(), futures);
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

	private void outputMenuAndDetailWithBodys(Catalog catalog, ParametersContext context, Menu rootMenu,MenuDefinition menuDefinition, List<Future<?>> futures) throws InterruptedException, ExecutionException{
		List<Object> list=getList(catalog, menuDefinition);
		ParametersContext cloneContext=context.clone();
		execute(() -> {
			outputMenu(catalog, cloneContext, rootMenu, menuDefinition, list);
		}, futures);
		outputMenuDetailWithBodys(catalog, cloneContext.clone(), rootMenu.clone(), menuDefinition, list, futures);
	}

	private void outputMenuAndDetail(Catalog catalog, ParametersContext context, Menu rootMenu,MenuDefinition menuDefinition, List<Future<?>> futures) throws InterruptedException, ExecutionException{
		List<Object> list=getList(catalog, menuDefinition);
		ParametersContext cloneContext=context.clone();
		execute(() -> {
			outputMenu(catalog, cloneContext, rootMenu, menuDefinition, list);
		}, futures);
		if (menuDefinition.hasDetails()) {
			outputMenuDetails(catalog, cloneContext.clone(), rootMenu.clone(), menuDefinition, list, futures);
		}
	}

	private void createRelationship(Catalog catalog, ParametersContext context, Menu rootMenu, List<Future<?>> futures) throws InterruptedException, ExecutionException{
		if (!graphvizInstalled) {
			return;
		}
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
				loadProperties(menuDefinition, this.getDictionaryFileType(), properties);
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

	protected void createCommon(ParametersContext context, Menu rootMenu) {
		StyleRenderer styleRenderer = new StyleRenderer();
		initialize(styleRenderer);
		String text = styleRenderer.render(context);
		context.put("style", text);
		context.put("createdAt", new Date());
		context.put("_diagramExtension", this.getDiagramFormat().getExtension());
	}

	protected Menu createMenu(Catalog catalog) {
		Menu rootMenu = new Menu();
		MenuDefinition.toMenus(catalog).forEach(menu -> {
			rootMenu.addChild(menu);
		});
		return rootMenu;
	}

	protected void createImages(Catalog catalog, ParametersContext context, Menu rootMenu, List<Future<?>> futures)
			throws InterruptedException, ExecutionException {
		List<Table> list = MenuDefinition.Tables.getDatas(catalog);
		final String largeName = "_summary_relations_large";
		execute(() -> {
			SchemaGraphBuilder schemaGraphBuilder = createSchemaGraphBuilder();
			RelationImageHolder holder = createRelationLargeImages(largeName, catalog, schemaGraphBuilder, false);
			context.put(largeName, holder);
		}, futures);
		//
		final String largeNameLogical = largeName + "_logical";
		execute(() -> {
			SchemaGraphBuilder schemaGraphBuilder = createSchemaGraphBuilder();
			RelationImageHolder holder = createRelationLargeImages(largeNameLogical, catalog, schemaGraphBuilder, true);
			if (holder != null) {
				context.put(largeNameLogical, holder);
			}
		}, futures);
		//
		final String smallName = "_summary_relations_small";
		execute(() -> {
			SchemaGraphBuilder schemaGraphBuilder = createSchemaGraphBuilder();
			RelationImageHolder imapSmall = createRelationSmallImage(smallName, list, schemaGraphBuilder, false);
			if (imapSmall != null) {
				context.put(smallName, imapSmall);
			}
		}, futures);
		//
		final String smallNameLogical = smallName + "_logical";
		execute(() -> {
			SchemaGraphBuilder schemaGraphBuilder = createSchemaGraphBuilder();
			RelationImageHolder imapSmall = createRelationSmallImage(smallNameLogical, list, schemaGraphBuilder, true);
			if (imapSmall != null) {
				context.put(smallNameLogical, imapSmall);
			}
		}, futures);
	}

	private Future<?> submit(Runnable ｒunnable) {
		if (this.isMultiThread()&&cpu>1) {
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

	private SchemaGraphBuilder createSchemaGraphBuilder() {
		SchemaGraphBuilder schemaGraphBuilder = new SchemaGraphBuilder();
		if (!CommonUtils.isEmpty(this.getDiagramFont())) {
			schemaGraphBuilder.drawOption().setFont(this.getDiagramFont());
		}
		return schemaGraphBuilder;
	}

	protected RelationImageHolder createRelationLargeImages(String name, Catalog catalog, SchemaGraphBuilder schemaGraphBuilder,
			boolean logical) {
		if (logical) {
			Optional<Table> optional= catalog.getSchemas().stream().flatMap(s -> s.getTables().stream())
					.filter(t -> hasDisplayName(t)).findFirst();
			if (!optional.isPresent()){
				return null;
			}
		}
		schemaGraphBuilder.drawOption().setWithRelationName(true);
		schemaGraphBuilder.drawOption().setTableFilter(table -> true);
		schemaGraphBuilder.tableNodeBuilder(TableNodeBuilder.create().labelBuilder(t -> {
			TableLabelBuilder tableLabelBuilder = TableLabelBuilder.create();
			if (logical) {
				tableLabelBuilder.tableTableHeaderBuilder()
						.name(tbl -> CommonUtils.coalesce(tbl.getDisplayName(), tbl.getName()));
				tableLabelBuilder.tableColumnCellBuilder()
						.name(c -> CommonUtils.coalesce(c.getDisplayName(), c.getName()));
			}
			return tableLabelBuilder;
		}));
		return createRelationImages(name, catalog, schemaGraphBuilder);
	}

	private RelationImageHolder createRelationSmallImage(String name, List<Table> list, SchemaGraphBuilder schemaGraphBuilder,
			boolean logical) {
		if (logical) {
			if (!hasDisplayName(list)){
				return null;
			}
		}
		Optional<Table> optional = list.stream().filter(t -> t.getConstraints().getForeignKeyConstraints().size()>0).findFirst();
		if (!optional.isPresent()){
			return null;
		}
		schemaGraphBuilder.drawOption().setWithRelationName(false);
		schemaGraphBuilder.drawOption().setTableFilter(table -> table.getChildRelations().size() > 0
				|| table.getConstraints().getForeignKeyConstraints().size() > 0);
		schemaGraphBuilder.tableNodeBuilder(TableNodeBuilder.create().labelBuilder(t -> {
			TableLabelBuilder tableLabelBuilder = TableLabelBuilder.createSimple();
			if (logical) {
				tableLabelBuilder.tableTableHeaderBuilder()
						.name(tbl -> CommonUtils.coalesce(tbl.getDisplayName(), tbl.getName()));
				tableLabelBuilder.tableColumnCellBuilder()
						.name(c -> CommonUtils.coalesce(c.getDisplayName(), c.getName()));
			}
			return tableLabelBuilder;
		}));
		schemaGraphBuilder.drawOption().setColumnFilter(column -> {
			if (column.isPrimaryKey()) {
				return true;
			}
			if (column.isForeignKey()) {
				return true;
			}
			if (column.getOrdinal() < 5) {
				return true;
			}
			return false;
		});
		return createRelationImages(name, list, schemaGraphBuilder, null);
	}

	private boolean hasDisplayName(Collection<Table> c){
		Optional<Table> optional = c.stream().filter(t -> hasDisplayName(t)).findFirst();
		return optional.isPresent();
	}
	
	protected RelationImageHolder createRelationImages(String name, Catalog catalog, SchemaGraphBuilder schemaGraphBuilder) {
		Graph graph = schemaGraphBuilder.createGraph(name);
		catalog.getSchemas().forEach(s -> {
			schemaGraphBuilder.create(s, graph);
		});
		if (!CommonUtils.isEmpty(this.getDiagramFont())){
			schemaGraphBuilder.drawOption().setFont(this.getDiagramFont());
		}
		File dotFile = new File(diagramsPath, name + ".dot");
		File imageFile = new File(diagramsPath, name + "."+getDiagramFormat().getExtension());
		FileUtils.writeText(dotFile.getAbsolutePath(), "UTF8", graph.toString());
		dotRuntime.setOutputFormat(getDiagramFormat());
		String imageMap = dotRuntime.execute(dotFile.getAbsolutePath(), imageFile.getAbsolutePath());
		String convertedImageMap=convertImageMap(imageMap, null);
		String imageMapId=getImageMap(imageMap);
		RelationImageHolder holder=new RelationImageHolder(imageFile, imageMapId, convertedImageMap);
		if (getDiagramFormat().isText()){
			holder.setContent(FileUtils.readText(imageFile, "UTF8"));
		}
		return holder;
	}

	protected RelationImageHolder createRelationImages(String name, Collection<Table> list, SchemaGraphBuilder schemaGraphBuilder, String path) {
		Graph graph = schemaGraphBuilder.createGraph(name);
		if (!CommonUtils.isEmpty(this.getDiagramFont())) {
			schemaGraphBuilder.drawOption().setFont(this.getDiagramFont());
		}
		schemaGraphBuilder.create(list, graph);
		File dotFile = new File(diagramsPath, name + ".dot");
		File imageFile = new File(diagramsPath, name + "."+getDiagramFormat().getExtension());
		FileUtils.writeText(dotFile.getAbsolutePath(), "UTF8", graph.toString());
		dotRuntime.setOutputFormat(getDiagramFormat());
		String imageMap = dotRuntime.execute(dotFile.getAbsolutePath(), imageFile.getAbsolutePath());
		String convertedImageMap=convertImageMap(imageMap, path);
		String imageMapId=getImageMap(imageMap);
		RelationImageHolder holder=new RelationImageHolder(imageFile, imageMapId, convertedImageMap);
		if (getDiagramFormat().isText()){
			holder.setContent(FileUtils.readText(imageFile, "UTF8"));
		}
		return holder;
	}

	private String getImageMap(String imageMap) {
		String imageMapId=null;
		if (!CommonUtils.isEmpty(imageMap)){
			Matcher matcher = ID_PATTERN.matcher(imageMap);
			if (matcher.matches()) {
				imageMapId=matcher.group(1);
			}
		}
		return imageMapId;
	}

	private String convertImageMap(String imageMap, String path) {
		if (!CommonUtils.isEmpty(imageMap)){
			imageMap = imageMap.replace(">\\<", "><");
			imageMap = imageMap.replace(">/<", "><");
			imageMap = imageMap.replace("</map>\\", "</map>");
			imageMap = imageMap.replace("</map>/", "</map>");
			if (path!=null) {
				imageMap=imageMap.replace("\"tables/", "\""+path);
			}
			return imageMap;
		}
		return imageMap;
	}

	
	protected void initialize(Renderer renderer) {
	}
	
	protected void writeSchemas(Catalog catalog, ParametersContext context, Menu rootMenu, List<Future<?>> futures)
			throws InterruptedException, ExecutionException {
		List<Schema> list=getList(catalog, MenuDefinition.Schemas);
		execute(() -> {
			outputMenu(catalog, context, rootMenu, MenuDefinition.Schemas, list);
		}, futures);
		//
		outputMenuDetails(catalog, context.clone(), rootMenu.clone(), MenuDefinition.Schemas, list, (con, obj) -> {
			SchemaGraphBuilder schemaGraphBuilder = createSchemaGraphBuilder();
			Schema val = (Schema) obj;
			RelationImageHolder holder = createSchemaRelation(val, schemaGraphBuilder, false);
			if (holder != null&&holder.getImageMap()!=null) {
				con.put("relations", holder);
			}
			//
			holder = createSchemaRelation(val, schemaGraphBuilder, true);
			if (holder != null) {
				con.put("relations_logical", holder);
			}
		}, futures);
	}

	/**
	 * create tables htmls
	 * @param catalog
	 * @param context
	 * @param rootMenu
	 * @param futures
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	protected void writeTables(Catalog catalog, ParametersContext context, Menu rootMenu, List<Future<?>> futures)
			throws InterruptedException, ExecutionException {
		List<Table> list=getList(catalog, MenuDefinition.Tables);
		execute(() -> {
			outputMenu(catalog, context, rootMenu, MenuDefinition.Tables, list);
		}, futures);
		//
		outputMenuDetails(catalog, context.clone(), rootMenu.clone(), MenuDefinition.Tables, list, (con, obj) -> {
			SchemaGraphBuilder schemaGraphBuilder = createSchemaGraphBuilder();
			Table val = (Table) obj;
			if (!hasRelation(val)) {
				return;
			}
			RelationImageHolder holder = createTableRelationImage(val, schemaGraphBuilder, false, "./");
			if (holder != null) {
				con.put("relations", holder);
			}
			//
			holder = createTableRelationImage(val, schemaGraphBuilder, true, "./");
			if (holder != null) {
				con.put("relations_logical", holder);
			}
		}, futures);
	}
	
	private boolean hasRelation(Table table) {
		if (!CommonUtils.isEmpty(table.getChildRelations())){
			return true;
		}
		if (!CommonUtils.isEmpty(table.getConstraints().getForeignKeyConstraints())){
			return true;
		}
		if (!CommonUtils.isEmpty(table.getInherits())){
			return true;
		}
		if (table.getPartitionParent()!=null){
			return true;
		}
		if (table.getPartitioning()!=null&&!CommonUtils.isEmpty(table.getPartitioning().getPartitionTables())){
			return true;
		}
		return false;
	}

	private static final Pattern ID_PATTERN = Pattern.compile("<map.?\\sid=\"(.*?)\".*",
			Pattern.DOTALL + Pattern.MULTILINE);

	protected void outputMenuDetailWithBodys(Catalog catalog, ParametersContext context, Menu rootMenu,
			MenuDefinition menuDefinition, List<?> list, List<Future<?>> futures) throws InterruptedException, ExecutionException {
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

	private HtmlRenderer createHtmlRenderer(){
		HtmlRenderer htmlRenderer = new HtmlRenderer();
		htmlRenderer.setRenderOptions(this.getRenderOptions());
		return htmlRenderer;
	}

	private HtmlRenderer createHtmlDetailRenderer(){
		HtmlRenderer htmlRenderer = new HtmlDetailRenderer();
		htmlRenderer.setRenderOptions(this.getRenderOptions());
		return htmlRenderer;
	}

	protected void outputMenuDetails(Catalog catalog, ParametersContext context, Menu rootMenu,
			MenuDefinition menuDefinition, List<?> list, BiConsumer<ParametersContext, Object> consumer, List<Future<?>> futures)
			throws InterruptedException, ExecutionException {
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
			MenuDefinition menuDefinition, List<?> list, List<Future<?>> futures) throws InterruptedException, ExecutionException {
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

	private RelationImageHolder createTableRelationImage(Table table, SchemaGraphBuilder schemaGraphBuilder, boolean logical, String path) {
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
			if (!hasDisplayName(tables)){
				return null;
			}
		}
		schemaGraphBuilder.drawOption().setWithRelationName(false);
		schemaGraphBuilder.drawOption().setTableFilter(t -> tables.contains(t));
		schemaGraphBuilder.tableNodeBuilder(TableNodeBuilder.create().labelBuilder(t -> {
			TableLabelBuilder tableLabelBuilder;
			if (t == table) {
				tableLabelBuilder = TableLabelBuilder.create();
			} else {
				tableLabelBuilder = TableLabelBuilder.createSimple();
			}
			if (logical) {
				tableLabelBuilder.tableTableHeaderBuilder()
						.name(tbl -> CommonUtils.coalesce(tbl.getDisplayName(), tbl.getName()));
				tableLabelBuilder.tableColumnCellBuilder()
						.name(c -> CommonUtils.coalesce(c.getDisplayName(), c.getName()));
			}
			return tableLabelBuilder;
		}));
		if (logical) {
			return createRelationImages(HtmlUtils.objectFullPath(table) + "_logical", tables, schemaGraphBuilder, path);
		} else {
			return createRelationImages(HtmlUtils.objectFullPath(table), tables, schemaGraphBuilder, path);
		}
	}
	
	private void setParentPartitionTables(Table table, Set<Table> tables) {
		if (table==null) {
			return;
		}
		if (table.getPartitionParent()==null) {
			return;
		}
		tables.add(table.getPartitionParent().getTable());
		setParentPartitionTables(table.getPartitionParent().getTable(), tables);
	}

	private void setChildPartitionTables(Table table, Set<Table> tables) {
		if (table==null) {
			return;
		}
		if (table.getPartitioning()!=null&&!CommonUtils.isEmpty(table.getPartitioning().getPartitionTables())) {
			table.getPartitioning().getPartitionTables().forEach(t->{
				tables.add(t);
			});
		}
	}

	private RelationImageHolder createSchemaRelation(Schema schema, SchemaGraphBuilder schemaGraphBuilder, boolean logical) {
		if (schema.getTables().size() == 0) {
			return null;
		}
		if (logical) {
			if (!hasDisplayName(schema.getTables())){
				return null;
			}
		}
		schemaGraphBuilder.drawOption().setWithRelationName(false);
		schemaGraphBuilder.tableNodeBuilder(TableNodeBuilder.create().labelBuilder(t -> {
			TableLabelBuilder tableLabelBuilder = TableLabelBuilder.createSimple();
			if (logical) {
				tableLabelBuilder.tableTableHeaderBuilder()
						.name(tbl -> CommonUtils.coalesce(tbl.getDisplayName(), tbl.getName()));
				tableLabelBuilder.tableColumnCellBuilder()
						.name(c -> CommonUtils.coalesce(c.getDisplayName(), c.getName()));
			}
			return tableLabelBuilder;
		}));
		if (logical) {
			return createRelationImages(HtmlUtils.objectFullPath(schema) + "_logical", schema.getTables(), schemaGraphBuilder, "../tables/");
		} else {
			return createRelationImages(HtmlUtils.objectFullPath(schema), schema.getTables(), schemaGraphBuilder, "../tables/");
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

	private void outputMenu(Catalog catalog, ParametersContext context, Menu rootMenu,
			MenuDefinition menuDefinition, List<?> list) {
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
		map.forEach((k,v)->{
			context.put(k,v);
		});
		if (menuDefinition == MenuDefinition.Columns || menuDefinition == MenuDefinition.Domains) {
			context.put("columns", map);
		}
		rootMenu.setRelativePathRecursive(null);
		String text = render(renderer, context);
		writeText(new File(getOutputDirectory(), menuDefinition.getHtmlName()), text);
	}
	
	private <T> List<T> getList(Catalog catalog, MenuDefinition menuDefinition){
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

	/**
	 * @return the outputDirectory
	 */
	public File getOutputDirectory() {
		return outputDirectory;
	}

	/**
	 * @return the diagramFont
	 */
	public String getDiagramFont() {
		return diagramFont;
	}

	/**
	 * @param diagramFont
	 *            the diagramFont to set
	 */
	public void setDiagramFont(String diagramFont) {
		this.diagramFont = diagramFont;
	}

	/**
	 * @param outputDirectory
	 *            the outputDirectory to set
	 */
	public void setOutputDirectory(File outputDirectory) {
		this.outputDirectory = outputDirectory;
	}

	/**
	 * @return the templatePath
	 */
	public File getTemplatePath() {
		return templatePath;
	}

	/**
	 * @param templatePath
	 *            the templatePath to set
	 */
	public void setTemplatePath(File templatePath) {
		this.templatePath = templatePath;
	}

	/**
	 * @return the renderOptions
	 */
	public RenderOptions getRenderOptions() {
		return renderOptions;
	}

	/**
	 * @param renderOptions
	 *            the renderOptions to set
	 */
	public void setRenderOptions(RenderOptions renderOptions) {
		this.renderOptions = renderOptions;
	}

	/**
	 * @return the dot
	 */
	public String getDot() {
		return dot;
	}

	/**
	 * @param dot
	 *            the dot to set
	 */
	public void setDot(String dot) {
		this.dot = dot;
	}

	/**
	 * @return the multiThread
	 */
	public boolean isMultiThread() {
		return multiThread;
	}

	/**
	 * @param multiThread
	 *            the multiThread to set
	 */
	public void setMultiThread(boolean multiThread) {
		this.multiThread = multiThread;
	}

	/**
	 * @return the placeholderPrefix
	 */
	public String getPlaceholderPrefix() {
		return placeholderPrefix;
	}

	/**
	 * @param placeholderPrefix the placeholderPrefix to set
	 */
	public void setPlaceholderPrefix(String placeholderPrefix) {
		this.placeholderPrefix = placeholderPrefix;
	}

	/**
	 * @return the placeholderSuffix
	 */
	public String getPlaceholderSuffix() {
		return placeholderSuffix;
	}

	/**
	 * @param placeholderSuffix the placeholderSuffix to set
	 */
	public void setPlaceholderSuffix(String placeholderSuffix) {
		this.placeholderSuffix = placeholderSuffix;
	}

	/**
	 * @return the placeholders
	 */
	public boolean isPlaceholders() {
		return placeholders;
	}

	/**
	 * @param placeholders the placeholders to set
	 */
	public void setPlaceholders(boolean placeholders) {
		this.placeholders = placeholders;
	}

	/**
	 * @return the directory
	 */
	public File getDirectory() {
		return directory;
	}

	/**
	 * @param directory the directory to set
	 */
	public void setDirectory(File directory) {
		this.directory = directory;
	}

	/**
	 * @return the fileDirectory
	 */
	public File getFileDirectory() {
		return fileDirectory;
	}

	/**
	 * @param fileDirectory the fileDirectory to set
	 */
	public void setFileDirectory(File fileDirectory) {
		this.fileDirectory = fileDirectory;
	}

	/**
	 * @return the useSchemaNameDirectory
	 */
	public boolean isUseSchemaNameDirectory() {
		return useSchemaNameDirectory;
	}

	/**
	 * @param useSchemaNameDirectory the useSchemaNameDirectory to set
	 */
	public void setUseSchemaNameDirectory(boolean useSchemaNameDirectory) {
		this.useSchemaNameDirectory = useSchemaNameDirectory;
	}

	/**
	 * @return the useTableNameDirectory
	 */
	public boolean isUseTableNameDirectory() {
		return useTableNameDirectory;
	}

	/**
	 * @param useTableNameDirectory the useTableNameDirectory to set
	 */
	public void setUseTableNameDirectory(boolean useTableNameDirectory) {
		this.useTableNameDirectory = useTableNameDirectory;
	}

	/**
	 * @return the fileFilter
	 */
	public Predicate<File> getFileFilter() {
		return fileFilter;
	}

	/**
	 * @param fileFilter the fileFilter to set
	 */
	public void setFileFilter(Predicate<File> fileFilter) {
		this.fileFilter = fileFilter;
	}

	/**
	 * @return the foreignKeyDefinitionDirectory
	 */
	public File getForeignKeyDefinitionDirectory() {
		return foreignKeyDefinitionDirectory;
	}

	/**
	 * @param foreignKeyDefinitionDirectory the foreignKeyDefinitionDirectory to set
	 */
	public void setForeignKeyDefinitionDirectory(File foreignKeyDefinitionDirectory) {
		this.foreignKeyDefinitionDirectory = foreignKeyDefinitionDirectory;
	}

	/**
	 * @return the diagramFormat
	 */
	public OutputFormat getDiagramFormat() {
		return diagramFormat;
	}

	/**
	 * @param diagramFormat the diagramFormat to set
	 */
	public void setDiagramFormat(OutputFormat diagramFormat) {
		this.diagramFormat = diagramFormat;
	}

	/**
	 * @param diagramFormat the diagramFormat to set
	 */
	public void setDiagramFormat(String diagramFormat) {
		this.diagramFormat = OutputFormat.parse(diagramFormat);
	}

}
