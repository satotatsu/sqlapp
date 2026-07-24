/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.elk;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.core.IGraphLayoutEngine;
import org.eclipse.elk.core.RecursiveGraphLayoutEngine;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.options.EdgeRouting;
import org.eclipse.elk.core.options.PortConstraints;
import org.eclipse.elk.core.options.PortSide;
import org.eclipse.elk.core.util.BasicProgressMonitor;
import org.eclipse.elk.graph.ElkBendPoint;
import org.eclipse.elk.graph.ElkEdge;
import org.eclipse.elk.graph.ElkEdgeSection;
import org.eclipse.elk.graph.ElkLabel;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.ElkPort;
import org.eclipse.elk.graph.util.ElkGraphUtil;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableViewOrderSorter;
import com.sqlapp.elk.schemas.ForeignKeyConstraintNode;
import com.sqlapp.elk.schemas.SchemaNode;
import com.sqlapp.elk.schemas.TableNode;
import com.sqlapp.elk.util.EdgeUtils;
import com.sqlapp.elk.util.EscapeUtils;
import com.sqlapp.elk.util.IndentStringBuilder;
import com.sqlapp.elk.util.SVGTextBuilder;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;

public class TableSvgCreator {

	public static final double HEADER_HEIGHT = 32.0;
	public static final double ROW_HEIGHT = 24.0;
	public static final double ROW_BORDER_BOTTOM = 1.0;

//	public static final double TABLE_NAME_PADDING = 24;
//	public static final double COLUMN_NAME_PADDING = 16;
//	public static final double COLUMN_TYPE_PADDING = 16;
	public static final double TABLE_NAME_PADDING = 12;
	public static final double COLUMN_PREFIX_PADDING = 4;
	public static final double COLUMN_NAME_PADDING = 16;
	public static final double COLUMN_TYPE_PADDING = 20;
	public static final double COLUMN_SUFFIX_PADDING = 4;
	public static final double FONT_SIZE = 12;

	private static String SVG_DEFS = """
			<defs>
				<marker id="manyLeft" markerWidth="18" markerHeight="18" refX="0" refY="9" orient="auto">
				    <path d="
				        M17,3  L3,9
				        M17,9  L3,9
				        M17,15 L3,9"
				        fill="none" stroke="#444" stroke-width="1.4"/>
				</marker>
				<marker id="manyRight" markerWidth="18" markerHeight="18" refX="0" refY="9" orient="auto">
				    <path d="
				        M1,3  L15,9
				        M1,9  L15,9
				        M1,15 L15,9"
				        fill="none" stroke="#444" stroke-width="1.4"/>
				</marker>
				<marker id="many" markerWidth="18" markerHeight="18" refX="18" refY="9" orient="auto">
				    <path d="
				        M17,3  L3,9
				        M17,9  L3,9
				        M17,15 L3,9"
				        fill="none" stroke="#444" stroke-width="1.4"/>
				</marker>
				<marker id='one' markerWidth='15' markerHeight='15' refX='0' refY='7.5' orient='auto'>
					<path d='M6,0 L6,15 M11,0 L11,15' fill='none' stroke='#444' stroke-width='1.5'/>
				</marker>
				<marker id='inherits' markerWidth='12' markerHeight='12' refX='12' refY='6' orient='auto'>
					<path d='M0,0 L12,6 L0,12 Z' fill='#fff' stroke='#555' stroke-width='1.5'/>
				</marker>
			</defs>
			""";

	private static String SVG_STYLE = """
			<style>
			  .table-container { font-family: sans-serif; font-size: 11px; border-collapse: collapse; width: 100%; height: 100%; background: #fff; box-sizing: border-box; }
			  .table-th { background: #283593; font-weight: bold; height: 32px; line-height: 32px; font-size: 12px; text-align: left; }
			  .table-th-link { display: block; width: 100%; height: 100%; padding: 0 8px; color: white; text-decoration: none; box-sizing: border-box; }
			  .table-th-link:hover { background: #1a237e; opacity: 0.9; }
			  .table-row { display: grid; height: 24px; line-height: 24px; border-bottom: 1px solid #e8e8e8; box-sizing: border-box; }
			  .table-cell { padding: 0 4px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
			  .pk { background:#58bcfa; }
			  .fk { background:#D5D9FA; }
			  a { text-decoration: none; color: #000000;}
			  .uk { font-weight: bold; color: #E53935; text-align: center; }
			  .name { color: #000000; text-align: left; font-size: 10px; padding-right: 4px; }
			  .type { color: #595656; text-align: right; font-size: 10px; padding-right: 4px; }
			  .edge-label { font-family: sans-serif; font-size: 10px; font-weight: 500; }
			</style>
			""";

	private double padding = 30.0;
	private Consumer<TableNode> tableNodeConsumer = t -> {
	};

	private java.util.function.Function<Table, String> urlFunction = (t) -> t.getName() + ".html";

	private final SVGDrawMode svgDrawMode;

	private NameMode nameMode = NameMode.NORMAL;

	private static final String ALGORITHM = "org.eclipse.elk.layered";

	public TableSvgCreator(SVGDrawMode svgDrawMode, NameMode nameMode) {
		this.svgDrawMode = svgDrawMode;
		this.nameMode = nameMode;
	}

	public TableSvgCreator(SVGDrawMode svgDrawMode) {
		this.svgDrawMode = svgDrawMode;
	}

	public TableSvgCreator() {
		this(SVGDrawMode.NORMAL);
	}

	private ElkNode createRootNodeForTable() {
		ElkNode rootNode = ElkGraphUtil.createGraph();
		rootNode.setProperty(CoreOptions.ALGORITHM, ALGORITHM);

		rootNode.setProperty(CoreOptions.DIRECTION, Direction.RIGHT);
		// rootNode.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL);
		rootNode.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.POLYLINE);
		// rootNode.setProperty(LayeredOptions.CROSSING_MINIMIZATION_FORCE_NODE_MODEL_ORDER,
		// true);
		rootNode.setProperty(CoreOptions.SPACING_EDGE_NODE, 20.0);
		rootNode.setProperty(CoreOptions.SPACING_EDGE_EDGE, 4.0);
		rootNode.setProperty(CoreOptions.SPACING_NODE_NODE, 20.0); // スキーマ間のスペースを少し広めに

		rootNode.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, 30.0);
		rootNode.setProperty(LayeredOptions.SPACING_EDGE_NODE_BETWEEN_LAYERS, 30.0);
		rootNode.setProperty(LayeredOptions.SPACING_EDGE_EDGE_BETWEEN_LAYERS, 16.0);
		rootNode.setProperty(LayeredOptions.MERGE_EDGES, false);
		return rootNode;
	}

	private ElkNode createRootNodeForSchema() {
		ElkNode rootNode = ElkGraphUtil.createGraph();
		rootNode.setProperty(CoreOptions.ALGORITHM, ALGORITHM);
		rootNode.setProperty(CoreOptions.DIRECTION, Direction.RIGHT);
		rootNode.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.POLYLINE);

		rootNode.setProperty(CoreOptions.SPACING_EDGE_NODE, 20.0);
		rootNode.setProperty(CoreOptions.SPACING_EDGE_EDGE, 4.0);
		rootNode.setProperty(CoreOptions.SPACING_EDGE_LABEL, 10.0);
		rootNode.setProperty(CoreOptions.SPACING_NODE_NODE, 20.0); // スキーマ間のスペースを少し広めに

		rootNode.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, 30.0);
		rootNode.setProperty(LayeredOptions.SPACING_EDGE_NODE_BETWEEN_LAYERS, 30.0);
		rootNode.setProperty(LayeredOptions.SPACING_EDGE_EDGE_BETWEEN_LAYERS, 16.0);
		rootNode.setProperty(LayeredOptions.MERGE_EDGES, false);
		return rootNode;
	}

	public SVGResult generateSvg(Collection<Table> tables) {
		ElkNode rootNode = createRootNodeForTable();
		// 2. ノードの登録
		List<TableNode> tableNodeList = createTableNodes(rootNode, tables);
		// 3. ポートとエッジの定義
		addEdges(tableNodeList);
		// 4. 配置計算の実行
		IGraphLayoutEngine engine = new RecursiveGraphLayoutEngine();
		engine.layout(rootNode, new BasicProgressMonitor());
		TotalHolder totalHolder = caluculateCanvasSize(tableNodeList);
		// SVGレンダリング
		return toSvg(totalHolder, tableNodeList, rootNode, false);
	}

	private List<TableNode> createTableNodes(ElkNode parentNode, Collection<Table> tables) {
		List<Table> sorted = TableViewOrderSorter.sort(tables, t -> t);
		List<TableNode> tableNodeList = CommonUtils.list();
		for (Table table : sorted) {
			ElkNode node = createElkNode(parentNode, table);
			TableNode tableNode = createTableNode(table, parentNode, node);
			tableNode.setNameMode(nameMode);
			tableNode.calculateTableLayoutInfo();
			double height = HEADER_HEIGHT + (tableNode.getColumnSize() * ROW_HEIGHT) + 2.0;
			node.setWidth(tableNode.getTotalWidth()); // 動的な幅を設定
			node.setHeight(height);
			node.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_SIDE);
			tableNodeList.add(tableNode);
		}
		return tableNodeList;
	}

	private TableNode createTableNode(Table table, ElkNode parentNode, ElkNode node) {
		TableNode tableNode = svgDrawMode.createTableNode(table, parentNode, node);
		tableNode.setNameMode(nameMode);
		tableNodeConsumer.accept(tableNode);
		return tableNode;
	}

	@FunctionalInterface
	public interface CreateTableNode {
		TableNode apply(Table table, ElkNode parentNode, ElkNode node);
	}

	@Getter
	public static class SVGResult {
		public SVGResult(String image, TotalHolder totalHolder) {
			this.image = image;
			this.totalWidth = totalHolder.totalWidth;
			this.totalHeight = totalHolder.totalHeight;
		}

		private final double totalWidth;
		private final double totalHeight;
		private final String image;
	}

	class SchemaLayoutResult {
		SchemaNode schemaNode;
		List<TableNode> tables;
		double width;
		double height;
	}

	static class CrossSchemaRelation {
		SchemaNode referencedSchema;
		TableNode referencedTable;
		SchemaNode referencingSchema;
		TableNode referencingTable;
		ForeignKeyConstraint foreignKeyConstraint;
	}

	public SVGResult generateSchemaSvg(List<Schema> schemas) {
		List<SchemaNode> schemaNodes = CommonUtils.list();
		ElkNode rootNode = createRootNodeForSchema();
		for (Schema schema : schemas) {
			SchemaLayoutResult layoutResult = layoutSchema(schema);
			schemaNodes.add(layoutResult.schemaNode);
			ElkNode schemaElkNode = createElkNode(rootNode, schema);
			// 事前計算したサイズを入れる
			schemaElkNode.setWidth(layoutResult.width);
			schemaElkNode.setHeight(layoutResult.height);
			layoutResult.schemaNode.setNode(schemaElkNode);
		}
		List<CrossSchemaRelation> crossSchemaRelations = collectCrossSchemaRelations(schemaNodes);
		addCrossSchemaLayoutEdges(rootNode, crossSchemaRelations);
		// 全体の配置計算（再帰的に内部の子ノードサイズに合わせて親ノードもアジャストされます）
		IGraphLayoutEngine engine = new RecursiveGraphLayoutEngine();
		engine.layout(rootNode, new BasicProgressMonitor());
		TotalHolder totalHolder = caluculateSchemaCanvasSize(schemaNodes);
		return toSchemaSvg(totalHolder, rootNode, schemaNodes, crossSchemaRelations, false);
	}

	private SchemaLayoutResult layoutSchema(Schema schema) {
		ElkNode rootNode = createRootNodeForTable();
		SchemaNode schemaNode = svgDrawMode.createSchemaNode(schema, rootNode, rootNode);
		List<TableNode> tableNodeList = createTableNodes(rootNode, schema.getTables());
		schemaNode.getTableNodes().addAll(tableNodeList);
		addEdges(tableNodeList);

		// 全体の配置計算（再帰的に内部の子ノードサイズに合わせて親ノードもアジャストされます）
		IGraphLayoutEngine engine = new RecursiveGraphLayoutEngine();
		engine.layout(rootNode, new BasicProgressMonitor());
		double maxX = 0;
		double maxY = 0;
		for (TableNode tn : tableNodeList) {
			ElkNode n = tn.getNode();
			maxX = Math.max(maxX, n.getX() + n.getWidth());
			maxY = Math.max(maxY, n.getY() + n.getHeight());
		}
		SchemaLayoutResult schemaLayoutResult = new SchemaLayoutResult();
		schemaLayoutResult.schemaNode = schemaNode;
		schemaLayoutResult.tables = tableNodeList;
		schemaLayoutResult.width = maxX + padding * 2;
		schemaLayoutResult.height = maxY + padding * 2;
		return schemaLayoutResult;
	}

	private List<CrossSchemaRelation> collectCrossSchemaRelations(List<SchemaNode> schemaNodes) {
		final Map<Table, TableNode> tableNodes = CommonUtils.map();
		final Map<Table, SchemaNode> tableSchemaNodes = CommonUtils.map();
		for (SchemaNode schemaNode : schemaNodes) {
			for (TableNode tableNode : schemaNode.getTableNodes()) {
				tableNodes.put(tableNode.getTable(), tableNode);
				tableSchemaNodes.put(tableNode.getTable(), schemaNode);
			}
		}
		List<CrossSchemaRelation> relations = CommonUtils.list();
		for (SchemaNode referencingSchema : schemaNodes) {
			for (TableNode referencingTable : referencingSchema.getTableNodes()) {
				for (ForeignKeyConstraint fk : referencingTable.getTable().getConstraints()
						.getForeignKeyConstraints()) {
					Table referencedTableObject = fk.getRelatedTable();
					TableNode referencedTable = tableNodes.get(referencedTableObject);
					SchemaNode referencedSchema = tableSchemaNodes.get(referencedTableObject);
					if (referencedTable == null || referencedSchema == null || referencedSchema == referencingSchema) {
						continue;
					}
					CrossSchemaRelation relation = new CrossSchemaRelation();
					relation.referencedSchema = referencedSchema;
					relation.referencedTable = referencedTable;
					relation.referencingSchema = referencingSchema;
					relation.referencingTable = referencingTable;
					relation.foreignKeyConstraint = fk;
					relations.add(relation);
				}
			}
		}
		return relations;
	}

	private void addCrossSchemaLayoutEdges(ElkNode rootNode, List<CrossSchemaRelation> relations) {
		Set<String> pairs = new HashSet<>();
		for (CrossSchemaRelation relation : relations) {
			String referencedId = relation.referencedSchema.getNode().getIdentifier();
			String referencingId = relation.referencingSchema.getNode().getIdentifier();
			String key = referencedId + "\u0000" + referencingId;
			if (!pairs.add(key)) {
				continue;
			}
			ElkEdge edge = ElkGraphUtil.createEdge(rootNode);
			edge.getSources().add(relation.referencedSchema.getNode());
			edge.getTargets().add(relation.referencingSchema.getNode());
		}
	}

	private ElkNode createElkNode(ElkNode rootNode, Schema schema) {
		ElkNode node = ElkGraphUtil.createNode(rootNode);
		node.setIdentifier(createIdentifier(schema));
		return node;
	}

	private String createIdentifier(Schema schema) {
		StringBuilder builder = new StringBuilder();
		if (schema.getCatalogName() != null) {
			builder.append(schema.getCatalogName());
		}
		if (schema.getName() != null) {
			if (builder.length() > 0) {
				builder.append(".");
			}
			builder.append(schema.getName());
		}
		return builder.toString();
	}

	private ElkNode createElkNode(ElkNode rootNode, Table table) {
		ElkNode node = ElkGraphUtil.createNode(rootNode);
		node.setIdentifier(createIdentifier(table));
		return node;
	}

	private String createIdentifier(Table table) {
		StringBuilder builder = new StringBuilder();
		if (table.getCatalogName() != null) {
			builder.append(table.getCatalogName());
		}
		if (table.getSchemaName() != null) {
			if (builder.length() > 0) {
				builder.append(".");
			}
			builder.append(table.getSchemaName());
		}
		if (table.getName() != null) {
			if (builder.length() > 0) {
				builder.append(".");
			}
			builder.append(table.getName());
		}
		return builder.toString();
	}

	private SVGResult toSchemaSvg(TotalHolder totalHolder, ElkNode rootNode, List<SchemaNode> schemaNodes,
			List<CrossSchemaRelation> crossSchemaRelations, boolean withOffset) {
		// SVGレンダリング
		IndentStringBuilder svg = new IndentStringBuilder();
		// 【修正】widthがtotalHeightになっていたバグを修正
		svg.append(String.format("<svg xmlns='http://www.w3.org/2000/svg' width='%f' height='%f'>",
				totalHolder.totalWidth, totalHolder.totalHeight));
		svg.append(SVG_DEFS);
		svg.append(SVG_STYLE);
		drawSchemas(svg, schemaNodes, withOffset);
		drawCrossSchemaRelations(svg, crossSchemaRelations);
		// テーブル描画
		svg.appendLine("</svg>");
		return new SVGResult(svg.toString(), totalHolder);
	}

	private void drawCrossSchemaRelations(IndentStringBuilder svg, List<CrossSchemaRelation> relations) {
		for (CrossSchemaRelation relation : relations) {
			ElkNode referencedNode = relation.referencedTable.getNode();
			ElkNode referencingNode = relation.referencingTable.getNode();
			double referencedCenterX = getAbsoluteX(relation.referencedSchema, relation.referencedTable)
					+ referencedNode.getWidth() / 2.0;
			double referencingCenterX = getAbsoluteX(relation.referencingSchema, relation.referencingTable)
					+ referencingNode.getWidth() / 2.0;
			boolean referencedIsLeft = referencedCenterX <= referencingCenterX;

			double startX = getAbsoluteX(relation.referencedSchema, relation.referencedTable)
					+ (referencedIsLeft ? referencedNode.getWidth() : 0.0);
			double startY = getAbsoluteY(relation.referencedSchema, relation.referencedTable)
					+ EdgeUtils.calulucateY(relation.referencedTable,
							relation.foreignKeyConstraint.getRelatedColumns());
			double endX = getAbsoluteX(relation.referencingSchema, relation.referencingTable)
					+ (referencedIsLeft ? 0.0 : referencingNode.getWidth());
			double endY = getAbsoluteY(relation.referencingSchema, relation.referencingTable)
					+ EdgeUtils.calulucateY(relation.referencingTable, relation.foreignKeyConstraint.getColumns());
			double middleX = (startX + endX) / 2.0;
			String pathData = String.format("M%f,%f L%f,%f L%f,%f L%f,%f", startX, startY, middleX, startY,
					middleX, endY, endX, endY);
			String constraintName = EscapeUtils.escapeXml(relation.foreignKeyConstraint.getName());
			svg.appendLine(String.format(
					"<path class='relation cross-schema' data-constraint='%s' d='%s' fill='none' "
							+ "stroke='#1565C0' stroke-width='1.5' marker-start='url(#one)' marker-end='url(#many)' />",
					constraintName, pathData));
		}
	}

	private double getAbsoluteX(SchemaNode schemaNode, TableNode tableNode) {
		return schemaNode.getNode().getX() + (padding * 2) + tableNode.getNode().getX();
	}

	private double getAbsoluteY(SchemaNode schemaNode, TableNode tableNode) {
		return schemaNode.getNode().getY() + (padding * 2) + tableNode.getNode().getY();
	}

	private void drawSchemas(IndentStringBuilder svg, List<SchemaNode> schemaNodes, boolean withOffset) {
		for (SchemaNode schemaNode : schemaNodes) {
			// スキーマ自体の絶対座標（親ノードの座標）を取得
			ElkNode sNode = schemaNode.getNode();
			svg.appendLine(
					String.format("<g id=\"" + schemaNode.getSchema().getName() + "\" transform=\"translate(%f,%f)\">",
							sNode.getX() + padding, sNode.getY() + padding));
			svg.addIndentLevel(1);
			svg.appendLine(String.format(
					"<rect x='%f' y='%f' width='%f' height='%f' rx='10' "
							+ "fill='#f5f5f5' stroke='#bdbdbd' stroke-width='2'/>",
					0.0, 0.0, sNode.getWidth(), sNode.getHeight()));
			svg.appendLine(String.format("<text x='%f' y='%f' font-size='16' " + "font-weight='bold'>%s</text>", 10.0,
					22.0, schemaNode.getName()));
			//
			for (TableNode tableNode : schemaNode.getTableNodes()) {
				drawSvg(svg, tableNode, withOffset);
			}
			for (TableNode tableNode : schemaNode.getTableNodes()) {
				drawEdge(svg, schemaNode.getNode(), tableNode, withOffset);
			}
			svg.addIndentLevel(-1);
			svg.appendLine("</g>");
		}
	}

	static class SizeHolder {
		public double maxRight = 0;
		public double maxBottom = 0;
	}

	static class TotalHolder {
		public double totalWidth = 0;
		public double totalHeight = 0;
	}

	private TotalHolder caluculateCanvasSize(Collection<TableNode> tableNodes) {
		SizeHolder holder = new SizeHolder();
		for (TableNode tableNode : tableNodes) {
			ElkNode node = tableNode.getNode();
			if (node.getX() + node.getWidth() > holder.maxRight)
				holder.maxRight = node.getX() + node.getWidth();
			if (node.getY() + node.getHeight() > holder.maxBottom)
				holder.maxBottom = node.getY() + node.getHeight();
		}
		TotalHolder total = new TotalHolder();
		total.totalWidth = holder.maxRight + (padding * 2);
		total.totalHeight = holder.maxBottom + (padding * 2);
		return total;
	}

	private TotalHolder caluculateSchemaCanvasSize(Collection<SchemaNode> schemaNodes) {
		// 【修正】各スキーマノードのELK配置確定後のグローバル座標（絶対座標）を元にキャンバスサイズを計算
		double maxRight = 0;
		double maxBottom = 0;

		for (SchemaNode schemaNode : schemaNodes) {
			ElkNode sNode = schemaNode.getNode();
			double schemaX = sNode.getX();
			double schemaY = sNode.getY();

			for (TableNode tableNode : schemaNode.getTableNodes()) {
				ElkNode n = tableNode.getNode();
				double absRight = schemaX + n.getX() + n.getWidth();
				double absBottom = schemaY + n.getY() + n.getHeight();

				if (absRight > maxRight) {
					maxRight = absRight;
				}
				if (absBottom > maxBottom) {
					maxBottom = absBottom;
				}
			}
		}

		TotalHolder total = new TotalHolder();
		// マージン分（背景Rectの余白30px + padding）を考慮
		total.totalWidth = maxRight + (padding * 2) + 30;
		total.totalHeight = maxBottom + (padding * 2) + 30;
		return total;
	}

	private void addEdges(List<TableNode> list) {
		final Map<Table, TableNode> tableNodes = CommonUtils.map();
		list.forEach(node -> {
			tableNodes.put(node.getTable(), node);
		});
		for (TableNode obj : list) {
			addInheritEdges(tableNodes, obj);
			addForiegnKeyEdges(tableNodes, obj);
		}
	}

	private void addInheritEdges(final Map<Table, TableNode> tableNodes, TableNode tableNode) {
		Table table = tableNode.getTable();
		ElkNode currentLayoutNode = tableNode.getNode();
		for (Table inherit : table.getInherits()) {
			TableNode parentTableNode = tableNodes.get(inherit);
			if (parentTableNode == null) {
				continue;
			}
			ElkNode parentLayoutNode = parentTableNode.getNode();
			ElkPort parentPort = ElkGraphUtil.createPort(parentLayoutNode);
			parentPort.setX(parentLayoutNode.getWidth() / 2.0);
			parentPort.setY(parentLayoutNode.getHeight());
			parentPort.setProperty(CoreOptions.PORT_SIDE, PortSide.SOUTH);

			ElkPort childPort = ElkGraphUtil.createPort(currentLayoutNode);
			childPort.setX(currentLayoutNode.getWidth() / 2.0);
			childPort.setY(0.0);
			childPort.setProperty(CoreOptions.PORT_SIDE, PortSide.NORTH);

			ElkEdge edge = ElkGraphUtil.createEdge(tableNode.getRootNode());
			edge.getSources().clear();
			edge.getTargets().clear();

			edge.getSources().add(childPort);
			edge.getTargets().add(parentPort);
			tableNode.getInheritanceEdges().add(edge);
		}
	}

	private void addForiegnKeyEdges(final Map<Table, TableNode> tableNodes, TableNode tableNode) {
		Table table = tableNode.getTable();
		for (ForeignKeyConstraint fk : table.getConstraints().getForeignKeyConstraints()) {
			TableNode relatedTableNode = tableNodes.get(fk.getRelatedTable());
			if (relatedTableNode == null) {
				continue;
			}
			// PK
			ElkNode srcNode = relatedTableNode.getNode();
			// FK
			ElkNode tgtNode = tableNode.getNode();

			// srcNode.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_POS);
			// tgtNode.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_POS);

			double srcY = EdgeUtils.calulucateY(relatedTableNode, fk.getRelatedColumns());
			double tgtY = EdgeUtils.calulucateY(tableNode, fk.getColumns());

			ElkPort srcPort = ElkGraphUtil.createPort(srcNode);
			srcPort.setX(srcNode.getWidth());
			srcPort.setY(srcY);
			srcPort.setProperty(CoreOptions.PORT_SIDE, PortSide.EAST);

			ElkPort tgtPort = ElkGraphUtil.createPort(tgtNode);
			tgtPort.setX(0);
			tgtPort.setY(tgtY);
			tgtPort.setProperty(CoreOptions.PORT_SIDE, PortSide.WEST);

			srcPort.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_POS);
			tgtPort.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_POS);

			ElkEdge edge = ElkGraphUtil.createEdge(tableNode.getRootNode());
			edge.getSources().add(srcPort);
			edge.getTargets().add(tgtPort);

			String text = tableNode.build(fk);
			ElkLabel label = ElkGraphUtil.createLabel(edge);
			SVGTextBuilder builder = tableNode.getForeignKeyBuilder().setText(label, text);

			ForeignKeyConstraintNode fNode = new ForeignKeyConstraintNode(fk, edge, builder);
			tableNode.getForeignKeyConstraintNodes().add(fNode);
		}
	}

	private boolean isIdentifying(ForeignKeyConstraint fk) {
		for (int i = 0; i < fk.getColumns().size(); i++) {
			Column column = fk.getColumns().get(i);
			if (column.isPrimaryKey()) {
				continue;
			}
			if (!column.isNotNull()) {
				return false;
			}
		}
		return true;
	}

	private SVGResult toSvg(TotalHolder totalHolder, List<TableNode> tableNodeList, ElkNode rootNode,
			boolean withOffset) {
		IndentStringBuilder svg = new IndentStringBuilder();
		svg.append(String.format("<svg xmlns='http://www.w3.org/2000/svg' width='%f' height='%f'>",
				totalHolder.totalWidth, totalHolder.totalHeight));
		svg.lineBreak();
		svg.append(SVG_DEFS);
		svg.append(SVG_STYLE);
		for (TableNode tableNode : tableNodeList) {
			String svgPart = toSvg(tableNode, withOffset);
			svg.append(svgPart);
		}
		for (TableNode tableNode : tableNodeList) {
			drawEdge(svg, rootNode, tableNode, withOffset);
		}
		svg.appendLine("</svg>");
		return new SVGResult(svg.toString(), totalHolder);
	}

	private void drawEdge(IndentStringBuilder svg, ElkNode parentNode, TableNode tableNode, boolean withOffset) {
		// 【修正】スキーマ階層を考慮し、親ノードがルートでない場合は相対座標のオフセットを加算できるようにする
		double offsetX = padding;
		double offsetY = padding;
		if (withOffset) {
			if (parentNode.getParent() != null) {
				offsetX += parentNode.getX();
				offsetY += parentNode.getY();
			}
		}
		for (ElkEdge edge : tableNode.getInheritanceEdges()) {
			for (ElkEdgeSection section : edge.getSections()) {
				StringBuilder pathData = new StringBuilder();
				pathData.append(String.format("M%f,%f ", section.getStartX() + offsetX,
						section.getStartY() + offsetY));
				if (section.getBendPoints() != null) {
					for (ElkBendPoint bp : section.getBendPoints()) {
						pathData.append(String.format("L%f,%f ", bp.getX() + offsetX, bp.getY() + offsetY));
					}
				}
				pathData.append(String.format("L%f,%f", section.getEndX() + offsetX,
						section.getEndY() + offsetY));
				svg.appendLine(String.format(
						"<path class='relation inherits' d='%s' fill='none' stroke='#555' "
								+ "stroke-width='1.5' stroke-dasharray='4' marker-end='url(#inherits)' />",
						pathData.toString()));
			}
		}
		for (ForeignKeyConstraintNode fkNode : tableNode.getForeignKeyConstraintNodes()) {
			ElkEdge edge = fkNode.getEdge();
			ElkPort srcPort = (ElkPort) edge.getSources().get(0);
			boolean isIdentifying = isIdentifying(fkNode.getForeignKeyConstraint());
			boolean isInheritance = (srcPort.getProperty(CoreOptions.PORT_SIDE) == PortSide.SOUTH);

			for (ElkEdgeSection section : edge.getSections()) {
				StringBuilder pathData = new StringBuilder();
				double startX = section.getStartX() + offsetX;
				double startY = section.getStartY() + offsetY;
				pathData.append(String.format("M%f,%f ", startX, startY));

				if (section.getBendPoints() != null) {
					for (ElkBendPoint bp : section.getBendPoints()) {
						double bx = bp.getX() + offsetX;
						double by = bp.getY() + offsetY;
						pathData.append(String.format("L%f,%f ", bx, by));
					}
				}
				double endX = section.getEndX() + offsetX;
				double endY = section.getEndY() + offsetY;
				pathData.append(String.format("L%f,%f", endX, endY));

				if (isInheritance) {
					svg.appendLine(String.format(
							"<path d='%s' fill='none' stroke='#555' stroke-width='1.5' stroke-dasharray='4' marker-end='url(#inherits)' />",
							pathData.toString()));
				} else if (isIdentifying) {
					svg.appendLine(String.format(
							"<path d='%s' fill='none' stroke='#333' stroke-width='1.5' marker-start='url(#one)' marker-end='url(#many)' />",
							pathData.toString()));
				} else {
					svg.appendLine(String.format(
							"<path d='%s' fill='none' stroke='#2E7D32' stroke-width='1.3' stroke-dasharray='4,3' marker-start='url(#one)' marker-end='url(#many)' />",
							pathData.toString()));
				}
			}
			for (ElkLabel label : edge.getLabels()) {
				SVGTextBuilder builder = fkNode.getSvgTextBuilder();
				double x = label.getX() + offsetX;
				double y = 0;
				if (builder != null) {
					y = label.getY() + offsetY - TableSvgCreator.FONT_SIZE * builder.getCount();
				} else {
					y = label.getY() + offsetY - TableSvgCreator.FONT_SIZE;
				}
				svg.appendLine(String.format(
						"<rect x='%f' y='%f' width='%f' height='%f' " + "fill='white' opacity='0.85' rx='2'/>", x - 2,
						y - 9, label.getWidth() + 4, label.getHeight() + 2));
				if (!CommonUtils.isEmpty(label.getText())) {
					svg.appendLine(
							String.format("<text x='%f' y='%f' class='edge-label'>%s</text>", x, y, label.getText()));
				}
			}
		}

	}

	private String toSvg(TableNode tableNode, boolean withOffset) {
		IndentStringBuilder svg = new IndentStringBuilder();
		drawSvg(svg, tableNode, withOffset);
		return svg.toString();
	}

	private void drawSvg(IndentStringBuilder svg, TableNode tableNode, boolean withOffset) {
		ElkNode node = tableNode.getNode();
		Table table = tableNode.getTable();

		double nameColumnWidth = tableNode.getNameWidth();
		double typeColumnWidth = tableNode.getTypeWidth();

		// 【修正】テーブルが所属するスキーマノードの座標分オフセットさせる
		double offsetX = padding;
		double offsetY = padding;
		if (withOffset) {
			if (node.getParent() != null && node.getParent().getParent() != null) {
				offsetX += node.getParent().getX();
				offsetY += node.getParent().getY();
			}
		}
		double x = node.getX() + offsetX;
		double y = node.getY() + offsetY;
		String url = urlFunction.apply(table);
		svg.appendLine(String.format("<g transform='translate(%f, %f)'>", x, y));
		svg.addIndentLevel(1);
		svg.appendLine(
				String.format("<rect width='%f' height='%f' fill='#fff' stroke='#455A64' stroke-width='1.2' rx='4'/>",
						node.getWidth(), node.getHeight()));
		svg.addIndentLevel(1);
		svg.appendLine(String.format("<foreignObject width='%f' height='%f'>", node.getWidth(), node.getHeight()));
		svg.appendLine("<div class='table-container' xmlns='http://www.w3.org/1999/xhtml'>");
		svg.addIndentLevel(1);
		if (!CommonUtils.isEmpty(url)) {
			svg.appendLine(String.format("<a href='%s' target='_blank'>", url));
		}
		svg.appendLine("<div class='table-th'>");
		svg.addIndentLevel(1);
		svg.appendLine(String.format("<div style='padding: 0 8px; color: white;text-align: center;'>%s</div>",
				tableNode.getName()));
		svg.addIndentLevel(-1);
		svg.appendLine("</div>");

		for (Column col : table.getColumns()) {
//			if (!col.isPrimaryKey()) {
//				if (isForeignKeyColumn(table, col)) {
//					svg.appendLine(String.format("<div class='table-row fk' style='grid-template-columns: %fpx %fpx;'>",
//							nameColumnWidth, typeColumnWidth));
//				} else {
//					svg.appendLine(String.format("<div class='table-row' style='grid-template-columns: %fpx %fpx;'>",
//							nameColumnWidth, typeColumnWidth));
//				}
//			} else {
//				svg.appendLine(String.format("<div class='table-row pk' style='grid-template-columns: %fpx %fpx;'>",
//						nameColumnWidth, typeColumnWidth));
//			}
			svg.appendLine(String.format("<div class='table-row' style='grid-template-columns: %fpx %fpx;'>",
					nameColumnWidth, typeColumnWidth));
			svg.addIndentLevel(1);
			svg.appendLine(String.format("<div class='table-cell name'>%s</div>", tableNode.getName(col)));
			svg.appendLine(String.format("<div class='table-cell'>%s</div>", tableNode.build(col)));
			svg.addIndentLevel(-1);
			svg.appendLine("</div>");
		}
		if (!CommonUtils.isEmpty(url)) {
			svg.append(String.format("</a>"));
		}
		svg.addIndentLevel(-1);
		svg.appendLine("</div>");
		svg.addIndentLevel(-1);
		svg.appendLine("</foreignObject>");
		svg.addIndentLevel(-1);
		svg.appendLine("</g>");
	}

	private boolean isForeignKeyColumn(Table table, Column column) {
		for (ForeignKeyConstraint fk : table.getConstraints().getForeignKeyConstraints()) {
			List<Column> cols = fk.getColumns();
			for (Column col : cols) {
				if (col == column) {
					return true;
				}
			}
		}
		return false;
	}

	public void setPadding(double padding) {
		this.padding = padding;
	}

	public void setUrlFunction(java.util.function.Function<Table, String> urlFunction) {
		this.urlFunction = urlFunction;
	}

	public void setNameMode(NameMode nameMode) {
		this.nameMode = nameMode;
	}

	public void setTableNodeConsumer(Consumer<TableNode> tableNodeConsumer) {
		this.tableNodeConsumer = tableNodeConsumer;
	}
}
