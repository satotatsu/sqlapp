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
import java.util.List;
import java.util.Map;
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
import com.sqlapp.elk.schemas.SchemaNode;
import com.sqlapp.elk.schemas.TableNode;
import com.sqlapp.elk.util.IndentStringBuilder;
import com.sqlapp.elk.util.SVGTextBuilder;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;

public class TableSvgCreator {

	private static final double HEADER_HEIGHT = 32.0;
	private static final double ROW_HEIGHT = 24.0;

	private static String SVG_DEFS = """
			<defs>
			  <marker id='many' markerWidth='15' markerHeight='15' refX='0' refY='7.5' orient='auto'>
			    <path d='M15,2 L2,7.5 L15,13 M2,0 L2,15' fill='none' stroke='#444' stroke-width='1.5'/>
			  </marker>
			  <marker id='one' markerWidth='15' markerHeight='15' refX='15' refY='7.5' orient='auto'>
			    <path d='M6,0 L6,15 M11,0 L11,15' fill='none' stroke='#444' stroke-width='1.5'/>
			  </marker>
			  <marker id='inherits' markerWidth='12' markerHeight='12' refX='6' refY='12' orient='auto'>
			    <path d='M0,12 L6,0 L12,12 Z' fill='#fff' stroke='#555' stroke-width='1.5'/>
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
			  a { text-decoration: none; color: #000000;}
			  .uk { font-weight: bold; color: #E53935; text-align: center; }
			  .name { color: #000000; text-align: left; font-size: 10px; padding-right: 4px; }
			  .type { color: #595656; text-align: right; font-size: 10px; padding-right: 4px; }
			  .edge-label { font-family: sans-serif; font-size: 10px; font-weight: 500; }
			</style>
			""";

	private double padding = 60.0;
	private Map<Table, TableNode> tableNodes = CommonUtils.linkedMap();
	private Map<ElkLabel, SVGTextBuilder> labelSVGTextMap = CommonUtils.linkedMap();

	private Consumer<TableNode> tableNodeConsumer = t -> {
	};

	private java.util.function.Function<Table, String> urlFunction = (t) -> t.getName() + ".html";

	private final SVGDrawMode svgDrawMode;

	private NameMode nameMode = NameMode.NORMAL;

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
		rootNode.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered");
		rootNode.setProperty(CoreOptions.DIRECTION, Direction.RIGHT);
		rootNode.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL);

		rootNode.setProperty(CoreOptions.SPACING_EDGE_NODE, 20.0);
		rootNode.setProperty(CoreOptions.SPACING_EDGE_EDGE, 12.0);
		rootNode.setProperty(CoreOptions.SPACING_NODE_NODE, 40.0); // スキーマ間のスペースを少し広めに

		rootNode.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, 30.0);
		rootNode.setProperty(LayeredOptions.SPACING_EDGE_NODE_BETWEEN_LAYERS, 30.0);
		rootNode.setProperty(LayeredOptions.SPACING_EDGE_EDGE_BETWEEN_LAYERS, 16.0);
		rootNode.setProperty(LayeredOptions.MERGE_EDGES, false);
		return rootNode;
	}

	private ElkNode createRootNodeForSchema() {
		ElkNode rootNode = ElkGraphUtil.createGraph();
		rootNode.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered");
		rootNode.setProperty(CoreOptions.DIRECTION, Direction.RIGHT);
		rootNode.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL);

		rootNode.setProperty(CoreOptions.SPACING_EDGE_NODE, 20.0);
		rootNode.setProperty(CoreOptions.SPACING_EDGE_EDGE, 12.0);
		rootNode.setProperty(CoreOptions.SPACING_NODE_NODE, 120.0); // スキーマ間のスペースを少し広めに

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
		return toSvg(totalHolder, rootNode);
	}

	private List<TableNode> createTableNodes(ElkNode parentNode, Collection<Table> tables) {
		List<TableNode> tableNodeList = CommonUtils.list();
		for (Table table : tables) {
			ElkNode node = createElkNode(parentNode, table);
			TableNode tableNode = createTableNode(table, parentNode, node);
			tableNode.setNameMode(nameMode);
			tableNode.calculateTableLayoutInfo();
			double height = HEADER_HEIGHT + (tableNode.getColumnSize() * ROW_HEIGHT) + 2.0;
			node.setWidth(tableNode.getTotalWidth()); // 動的な幅を設定
			node.setHeight(height);
			node.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_SIDE);
			tableNodes.put(table, tableNode);
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

	public SVGResult generateSchemaSvg(List<Schema> schemas) {
		ElkNode rootNode = createRootNodeForSchema();
		tableNodes.clear();
		labelSVGTextMap.clear();
		List<SchemaNode> schemaNodes = CommonUtils.list();

		for (Schema schema : schemas) {
			ElkNode schemaElkNode = createElkNode(rootNode, schema);

			// スキーマの内部（子ノード間）の配置アルゴリズムを設定
			schemaElkNode.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered");
			schemaElkNode.setProperty(CoreOptions.DIRECTION, Direction.RIGHT);

			SchemaNode schemaNode = svgDrawMode.createSchemaNode(schema, rootNode, schemaElkNode);
			List<TableNode> tableNodeList = createTableNodes(schemaElkNode, schema.getTables());
			schemaNode.getTableNodes().addAll(tableNodeList);
			schemaNodes.add(schemaNode);
			addEdges(tableNodeList);

			// 【修正】レイアウトエンジンが親レイヤで正しく計算できるように、内部コンテンツから仮のサイズを割り当てる
			// 1つのスキーマ内の簡易サイズ計測
			double innerMaxX = 100;
			double innerMaxY = 100;
			for (TableNode tn : tableNodeList) {
				innerMaxX = Math.max(innerMaxX, tn.getTotalWidth() + 40);
				innerMaxY += tn.getNode().getHeight() + 30;
			}
			schemaElkNode.setWidth(innerMaxX + 60);
			schemaElkNode.setHeight(innerMaxY + 60);
		}

		// 全体の配置計算（再帰的に内部の子ノードサイズに合わせて親ノードもアジャストされます）
		IGraphLayoutEngine engine = new RecursiveGraphLayoutEngine();
		engine.layout(rootNode, new BasicProgressMonitor());

		// 【修正】レイアウト確定後の正しい座標からキャンバスサイズを計算
		TotalHolder totalHolder = caluculateScehmaCanvasSize(schemaNodes);
		return toSchemaSvg(totalHolder, rootNode, schemaNodes);

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

	private SVGResult toSchemaSvg(TotalHolder totalHolder, ElkNode rootNode, List<SchemaNode> schemaNodes) {
		// SVGレンダリング
		IndentStringBuilder svg = new IndentStringBuilder();
		// 【修正】widthがtotalHeightになっていたバグを修正
		svg.append(String.format("<svg xmlns='http://www.w3.org/2000/svg' width='%f' height='%f'>\n",
				totalHolder.totalWidth, totalHolder.totalHeight));
		svg.lineBreak();
		svg.append(SVG_DEFS);
		svg.append(SVG_STYLE);
		drawSchemaBackgrounds(svg, schemaNodes);
		// テーブル描画
		for (Map.Entry<Table, TableNode> entry : tableNodes.entrySet()) {
			String svgPart = toSvg(entry.getValue());
			svg.append(svgPart);
		}
		for (SchemaNode schemaNode : schemaNodes) {
			addEdge(schemaNode.getNode(), svg);
		}
		svg.append("</svg>");
		return new SVGResult(svg.toString(), totalHolder);
	}

	private void drawSchemaBackgrounds(IndentStringBuilder svg, List<SchemaNode> schemaNodes) {
		final double margin = 30;
		for (SchemaNode schemaNode : schemaNodes) {
			// スキーマ自体の絶対座標（親ノードの座標）を取得
			ElkNode sNode = schemaNode.getNode();
			double schemaX = sNode.getX();
			double schemaY = sNode.getY();

			double minX = Double.MAX_VALUE;
			double minY = Double.MAX_VALUE;
			double maxX = Double.MIN_VALUE;
			double maxY = Double.MIN_VALUE;
			boolean found = false;

			for (TableNode tableNode : schemaNode.getTableNodes()) {
				found = true;
				ElkNode n = tableNode.getNode();
				// サブノードのX, Yはスキーマノードからの相対座標であるため、スキーマの絶対座標を加算する
				double absX = schemaX + n.getX();
				double absY = schemaY + n.getY();

				minX = Math.min(minX, absX);
				minY = Math.min(minY, absY);
				maxX = Math.max(maxX, absX + n.getWidth());
				maxY = Math.max(maxY, absY + n.getHeight());
			}
			if (!found) {
				continue;
			}
			minX += padding - margin;
			minY += padding - margin;
			maxX += padding + margin;
			maxY += padding + margin;
			svg.appendLine(String.format(
					"<rect x='%f' y='%f' width='%f' height='%f' rx='10' "
							+ "fill='#f5f5f5' stroke='#bdbdbd' stroke-width='2'/>",
					minX, minY, maxX - minX, maxY - minY));
			svg.appendLine(String.format("<text x='%f' y='%f' font-size='16' " + "font-weight='bold'>%s</text>",
					minX + 10, minY + 22, schemaNode.getName()));
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

	private TotalHolder caluculateScehmaCanvasSize(Collection<SchemaNode> schemaNodes) {
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
		for (TableNode obj : list) {
			addInheritEdges(obj);
			addForiegnKeyEdges(obj);
		}
	}

	private void addInheritEdges(TableNode tableNode) {
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
		}
	}

	private void addForiegnKeyEdges(TableNode tableNode) {
		Table table = tableNode.getTable();
		for (ForeignKeyConstraint fk : table.getConstraints().getForeignKeyConstraints()) {
			TableNode repatedTableNode = tableNodes.get(fk.getRelatedTable());
			if (repatedTableNode == null) {
				continue;
			}
			ElkNode srcNode = tableNode.getNode();
			ElkNode tgtNode = repatedTableNode.getNode();

			double srcY = calulucateY(tableNode, fk.getColumns());
			double tgtY = calulucateY(repatedTableNode, fk.getRelatedColumns());

			ElkPort srcPort = ElkGraphUtil.createPort(srcNode);
			srcPort.setX(srcNode.getWidth());
			srcPort.setY(srcY);
			srcPort.setProperty(CoreOptions.PORT_SIDE, PortSide.EAST);

			ElkPort tgtPort = ElkGraphUtil.createPort(tgtNode);
			tgtPort.setX(0.0);
			tgtPort.setY(tgtY);
			tgtPort.setProperty(CoreOptions.PORT_SIDE, PortSide.WEST);

			ElkEdge edge = ElkGraphUtil.createEdge(tableNode.getRootNode());
			edge.getSources().add(srcPort);
			edge.getTargets().add(tgtPort);

			edge.setProperty(CoreOptions.SEPARATE_CONNECTED_COMPONENTS, isIdentifying(fk));

			String text = tableNode.build(fk);
			ElkLabel label = ElkGraphUtil.createLabel(edge);
			SVGTextBuilder builder = tableNode.getForeignKeyBuilder().setText(label, text);
			labelSVGTextMap.put(label, builder);
		}
	}

	private double calulucateY(TableNode tableNode, List<Column> columns) {
		int columnSize = tableNode.getColumnSize();
		if (CommonUtils.isEmpty(columnSize)) {
			return HEADER_HEIGHT;
		}
		Table table = tableNode.getTable();
		double sumY = 0;
		for (int i = 0; i < columns.size(); i++) {
			Column column = columns.get(i);
			if (!tableNode.test(column)) {
				continue;
			}
			int idx = table.getColumns().indexOf(column);
			if (idx >= 0) {
				sumY += HEADER_HEIGHT + (idx * ROW_HEIGHT) + (ROW_HEIGHT / 2.0);
			}
		}
		return (sumY / columns.size());
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

	private SVGResult toSvg(TotalHolder totalHolder, ElkNode rootNode) {
		IndentStringBuilder svg = new IndentStringBuilder();
		svg.append(String.format("<svg xmlns='http://www.w3.org/2000/svg' width='%f' height='%f'>\n",
				totalHolder.totalWidth, totalHolder.totalHeight));
		svg.lineBreak();
		svg.append(SVG_DEFS);
		svg.append(SVG_STYLE);
		for (Map.Entry<Table, TableNode> entry : tableNodes.entrySet()) {
			String svgPart = toSvg(entry.getValue());
			svg.append(svgPart);
		}
		addEdge(rootNode, svg);
		svg.append("</svg>");
		return new SVGResult(svg.toString(), totalHolder);
	}

	private void addEdge(ElkNode parentNode, IndentStringBuilder svg) {
		// 【修正】スキーマ階層を考慮し、親ノードがルートでない場合は相対座標のオフセットを加算できるようにする
		double offsetX = padding;
		double offsetY = padding;
		if (parentNode.getParent() != null) {
			offsetX += parentNode.getX();
			offsetY += parentNode.getY();
		}

		for (ElkEdge edge : parentNode.getContainedEdges()) {
			ElkPort srcPort = (ElkPort) edge.getSources().get(0);
			Boolean identifyingProp = edge.getProperty(CoreOptions.SEPARATE_CONNECTED_COMPONENTS);
			boolean isIdentifying = (identifyingProp != null) && identifyingProp;
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
							"<path d='%s' fill='none' stroke='#333' stroke-width='1.5' marker-start='url(#many)' marker-end='url(#one)' />",
							pathData.toString()));
				} else {
					svg.appendLine(String.format(
							"<path d='%s' fill='none' stroke='#2E7D32' stroke-width='1.3' stroke-dasharray='4,3' marker-start='url(#many)' marker-end='url(#one)' />",
							pathData.toString()));
				}
			}
			for (ElkLabel label : edge.getLabels()) {
				SVGTextBuilder builder = labelSVGTextMap.get(label);
				double x = label.getX() + offsetX;
				double y = 0;
				if (builder != null) {
					y = label.getY() + offsetY - 12.0 * builder.getCount();
				} else {
					y = label.getY() + offsetY - 12.0;
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

	private String toSvg(TableNode tableNode) {
		IndentStringBuilder svg = new IndentStringBuilder();
		ElkNode node = tableNode.getNode();
		Table table = tableNode.getTable();

		double nameColumnWidth = tableNode.getNameWidth();
		double typeColumnWidth = tableNode.getTypeWidth();

		// 【修正】テーブルが所属するスキーマノードの座標分オフセットさせる
		double offsetX = padding;
		double offsetY = padding;
		if (node.getParent() != null && node.getParent().getParent() != null) {
			offsetX += node.getParent().getX();
			offsetY += node.getParent().getY();
		}

		double x = node.getX() + offsetX;
		double y = node.getY() + offsetY;
		String url = urlFunction.apply(table);
		svg.appendLine(String.format("<g transform='translate(%f, %f)'>", x, y));
		svg.addIndentLevel(1);
		svg.appendLine(
				String.format("<rect width='%f' height='%f' fill='#fff' stroke='#455A64' stroke-width='1.2' rx='4'/>",
						node.getWidth(), node.getHeight()));
		svg.appendLine(String.format("<foreignObject width='%f' height='%f'>", node.getWidth(), node.getHeight()));
		svg.addIndentLevel(1);
		svg.appendLine("<div class='table-container' xmlns='http://www.w3.org/1999/xhtml'>");
		svg.addIndentLevel(1);
		if (!CommonUtils.isEmpty(url)) {
			svg.appendLine(String.format("<a href='%s' target='_blank'>", url));
		}
		svg.appendLine("<div class='table-th'>");
		svg.addIndentLevel(1);
		svg.appendLine(String.format("<div style='padding: 0 8px; color: white;'>%s</div>", tableNode.getName()));
		svg.addIndentLevel(-1);
		svg.appendLine("</div>");

		for (Column col : table.getColumns()) {
			if (!col.isPrimaryKey()) {
				svg.appendLine(String.format("<div class='table-row' style='grid-template-columns: %fpx %fpx;'>",
						nameColumnWidth, typeColumnWidth));
			} else {
				svg.appendLine(String.format("<div class='table-row pk' style='grid-template-columns: %fpx %fpx;'>",
						nameColumnWidth, typeColumnWidth));
			}
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
		svg.appendLine("</foreignObject>");
		svg.addIndentLevel(-1);
		svg.appendLine("</g>");
		return svg.toString();
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