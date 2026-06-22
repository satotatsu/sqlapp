package com.sqlapp.elk.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.ReferenceColumnCollection;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.elk.schemas.TableNode;
import com.sqlapp.elk.util.IndentStringBuilder;
import com.sqlapp.elk.util.SVGTextBuilder;
import com.sqlapp.util.CommonUtils;

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
	private double totalWidth = 0;
	private double totalHeight = 0;
	private ElkNode rootNode;

	private Map<Table, TableNode> tableNodes = CommonUtils.linkedMap();
	private Map<ElkLabel, SVGTextBuilder> labelSVGTextMap = CommonUtils.linkedMap();

	private java.util.function.Function<Table, String> urlFunction = (t) -> t.getName() + ".html";

	private void createRootNode() {
		rootNode = ElkGraphUtil.createGraph();
		// 1. ELKレイアウトの設定チューニング
		rootNode.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered");
		rootNode.setProperty(CoreOptions.DIRECTION, Direction.RIGHT);
		rootNode.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL);

		rootNode.setProperty(CoreOptions.SPACING_NODE_NODE, 30.0);
		rootNode.setProperty(CoreOptions.SPACING_EDGE_NODE, 20.0);
		rootNode.setProperty(CoreOptions.SPACING_EDGE_EDGE, 12.0);

		rootNode.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, 20.0);
		rootNode.setProperty(LayeredOptions.SPACING_EDGE_NODE_BETWEEN_LAYERS, 30.0);
		rootNode.setProperty(LayeredOptions.SPACING_EDGE_EDGE_BETWEEN_LAYERS, 16.0);
		rootNode.setProperty(LayeredOptions.MERGE_EDGES, false);
	}

	public String generateSvg(List<Table> tables) {
		createRootNode();

		// 2. ノードの登録
		for (Table table : tables) {
			ElkNode node = ElkGraphUtil.createNode(rootNode);
			TableNode tableNode = new TableNode(table, node);

			double height = HEADER_HEIGHT + (table.getColumns().size() * ROW_HEIGHT) + 2.0;
			node.setWidth(tableNode.getTotalWidth()); // 動的な幅を設定
			node.setHeight(height);
			node.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_SIDE);
			tableNodes.put(table, tableNode);
		}
		// 3. ポートとエッジの定義
		addEdges();
		// 4. 配置計算の実行
		IGraphLayoutEngine engine = new RecursiveGraphLayoutEngine();
		engine.layout(rootNode, new BasicProgressMonitor());

		caluculateCanvasSize();
		// SVGレンダリング
		String svg = toSvg();
		return svg;
	}

	private void caluculateCanvasSize() {
		// 5. キャンバスサイズ計算
		double maxRight = 0;
		double maxBottom = 0;
		for (Map.Entry<Table, TableNode> entry : tableNodes.entrySet()) {
			ElkNode node = entry.getValue().getElkNode();
			if (node.getX() + node.getWidth() > maxRight)
				maxRight = node.getX() + node.getWidth();
			if (node.getY() + node.getHeight() > maxBottom)
				maxBottom = node.getY() + node.getHeight();
		}
		totalWidth = maxRight + (padding * 2);
		totalHeight = maxBottom + (padding * 2);
	}

	private void addEdges() {
		// 3. ポートとエッジの定義
		for (Map.Entry<Table, TableNode> entry : tableNodes.entrySet()) {
			addInheritEdges(entry.getValue());
			addForiegnKeyEdges(entry.getValue());
		}
	}

	private void addInheritEdges(TableNode tableNode) {
		Table table = tableNode.getTable();
		ElkNode currentLayoutNode = tableNode.getElkNode();
		// --- 継承関係 (IS-A) ---
		for (Table inherit : table.getInherits()) {
			TableNode parentTableNode = tableNodes.get(inherit);
			if (parentTableNode == null) {
				continue;
			}
			ElkNode parentLayoutNode = parentTableNode.getElkNode();
			ElkPort parentPort = ElkGraphUtil.createPort(parentLayoutNode);

			// 【修正】COLUMN_WIDTH固定値から、動的な親ノードの幅を基準にするよう変更
			parentPort.setX(parentLayoutNode.getWidth() / 2.0);
			parentPort.setY(parentLayoutNode.getHeight());
			parentPort.setProperty(CoreOptions.PORT_SIDE, PortSide.SOUTH);

			ElkPort childPort = ElkGraphUtil.createPort(currentLayoutNode);
			// 【修正】動的な自身のノードの幅を基準にするよう変更
			childPort.setX(currentLayoutNode.getWidth() / 2.0);
			childPort.setY(0.0);
			childPort.setProperty(CoreOptions.PORT_SIDE, PortSide.NORTH);

			ElkEdge edge = ElkGraphUtil.createEdge(rootNode);
			edge.getSources().clear();
			edge.getTargets().clear();

			edge.getSources().add(childPort);
			edge.getTargets().add(parentPort);
		}
	}

	private void addForiegnKeyEdges(TableNode tableNode) {
		Table table = tableNode.getTable();
		// --- FKリレーション (子 -> 親) ---
		for (ForeignKeyConstraint fk : table.getConstraints().getForeignKeyConstraints()) {
			TableNode repatedTableNode = tableNodes.get(fk.getRelatedTable());
			if (repatedTableNode == null) {
				continue;
			}
			ElkNode srcNode = tableNode.getElkNode();
			ElkNode tgtNode = repatedTableNode.getElkNode();

			double srcY = calulucateY(fk.getTable(), fk.getColumns());
			double tgtY = calulucateY(fk.getRelatedTable(), fk.getRelatedColumns());

			ElkPort srcPort = ElkGraphUtil.createPort(srcNode);
			// 【修正】COLUMN_WIDTH固定値から、動的なソースノードの幅（右端）に変更
			srcPort.setX(srcNode.getWidth());
			srcPort.setY(srcY);
			srcPort.setProperty(CoreOptions.PORT_SIDE, PortSide.EAST);

			ElkPort tgtPort = ElkGraphUtil.createPort(tgtNode);
			tgtPort.setX(0.0);
			tgtPort.setY(tgtY);
			tgtPort.setProperty(CoreOptions.PORT_SIDE, PortSide.WEST);

			// --- FKリレーション (子 -> 親) の定義箇所内 ---
			ElkEdge edge = ElkGraphUtil.createEdge(rootNode);
			edge.getSources().add(srcPort);
			edge.getTargets().add(tgtPort);

			edge.setProperty(CoreOptions.SEPARATE_CONNECTED_COMPONENTS, isIdentifying(fk));

			// ★ここを修正：ElkLabelを作成してエッジに追加する
			String text = tableNode.getForeignKey(fk);
			ElkLabel label = ElkGraphUtil.createLabel(edge);
			SVGTextBuilder builder = tableNode.getForeignKeyBuilder().setText(label, text);
			labelSVGTextMap.put(label, builder);
//			String repText = text.replace("\n", "<br/>");
//			label.setText(repText);
//			// サイズを必ず設定
//			label.setWidth(text.length() * 7.0);
//			label.setHeight(args.length * 12.0);
		}
	}

	// カラム位置中心Y座標計算
	private double calulucateY(Table table, Column[] columns) {
		if (CommonUtils.isEmpty(columns)) {
			return HEADER_HEIGHT;
		}
		double sumY = 0;
		for (int i = 0; i < columns.length; i++) {
			Column column = columns[i];
			int idx = table.getColumns().indexOf(column);
			if (idx >= 0) {
				sumY += HEADER_HEIGHT + (idx * ROW_HEIGHT) + (ROW_HEIGHT / 2.0);
			}
		}
		return (sumY / columns.length);
	}

	private double calulucateY(Table table, ReferenceColumnCollection columns) {
		if (CommonUtils.isEmpty(columns)) {
			return HEADER_HEIGHT;
		}
		double sumY = 0;
		for (int i = 0; i < columns.size(); i++) {
			ReferenceColumn referenceColumn = columns.get(i);
			Column column = table.getColumns().get(referenceColumn.getName());
			int idx = table.getColumns().indexOf(column);
			if (idx >= 0) {
				sumY += HEADER_HEIGHT + (idx * ROW_HEIGHT) + (ROW_HEIGHT / 2.0);
			}
		}
		return (sumY / columns.size());
	}

	private boolean isIdentifying(ForeignKeyConstraint fk) {
		int i = 0;
		for (i = 0; i < fk.getColumns().length; i++) {
			Column column = fk.getColumns()[i];
			if (column.isPrimaryKey()) {
				continue;
			}
			if (!column.isNotNull()) {
				return false;
			}
		}
		return true;
	}

	private String toSvg() {
		// SVGレンダリング
		IndentStringBuilder svg = new IndentStringBuilder();
		svg.append(String.format("<svg xmlns='http://www.w3.org/2000/svg' width='%f' height='%f'>\n", totalWidth,
				totalHeight));
		svg.lineBreak();
		svg.append(SVG_DEFS);
		svg.append(SVG_STYLE);
		// テーブル描画 (省略なし)
		for (Map.Entry<Table, TableNode> entry : tableNodes.entrySet()) {
			String svgPart = toSvg(entry.getValue());
			svg.append(svgPart);
		}
		// 線の描画
		for (ElkEdge edge : rootNode.getContainedEdges()) {
			ElkPort srcPort = (ElkPort) edge.getSources().get(0);
			Boolean identifyingProp = edge.getProperty(CoreOptions.SEPARATE_CONNECTED_COMPONENTS);
			boolean isIdentifying = (identifyingProp != null) && identifyingProp;
			boolean isInheritance = (srcPort.getProperty(CoreOptions.PORT_SIDE) == PortSide.SOUTH);

			// ELK計算済みラベルを使用

			for (ElkEdgeSection section : edge.getSections()) {
				StringBuilder pathData = new StringBuilder();
				double startX = section.getStartX() + padding;
				double startY = section.getStartY() + padding;
				pathData.append(String.format("M%f,%f ", startX, startY));
				List<Double> allPointsX = new ArrayList<>();
				List<Double> allPointsY = new ArrayList<>();
				allPointsX.add(startX);
				allPointsY.add(startY);

				if (section.getBendPoints() != null) {
					for (ElkBendPoint bp : section.getBendPoints()) {
						double bx = bp.getX() + padding;
						double by = bp.getY() + padding;
						pathData.append(String.format("L%f,%f ", bx, by));
						allPointsX.add(bx);
						allPointsY.add(by);
					}
				}
				double endX = section.getEndX() + padding;
				double endY = section.getEndY() + padding;
				pathData.append(String.format("L%f,%f", endX, endY));
				allPointsX.add(endX);
				allPointsY.add(endY);

				// エッジ自体の描画
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
				double x = label.getX() + padding;
				double y = 0;
				// 少し上へ
				if (builder != null) {
					y = label.getY() + padding - 12.0 * builder.getCount();
				} else {
					y = label.getY() + padding - 12.0; // 少し上へ
				}
				svg.appendLine(String.format(
						"<rect x='%f' y='%f' width='%f' height='%f' " + "fill='white' opacity='0.85' rx='2'/>", x - 2,
						y - 9, label.getWidth() + 4, label.getHeight() + 2));
				// svg.appendLine(String.format("<text x='%f' y='%f'
				// class='edge-label'>%s</text>", x, y,
				// escapeXml(label.getText())));
				if (!CommonUtils.isEmpty(label.getText())) {
					svg.appendLine(
							String.format("<text x='%f' y='%f' class='edge-label'>%s</text>", x, y, label.getText()));
				}
			}
		}
		svg.append("</svg>");
		return svg.toString();
	}

	private String toSvg(TableNode tableNode) {
		IndentStringBuilder svg = new IndentStringBuilder();
		ElkNode node = tableNode.getElkNode();
		Table table = tableNode.getTable();

		// 【追加】計算済みの横幅レイアウト情報を取得
		double nameColumnWidth = tableNode.getNameWidth();
		double typeColumnWidth = tableNode.getTypeWidth();

		double x = node.getX() + padding;
		double y = node.getY() + padding;
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
		svg.appendLine(String.format("<div style='padding: 0 8px; color: white;'>%s</div>", table.getName()));
		svg.addIndentLevel(-1);
		svg.appendLine("</div>");

		for (Column col : table.getColumns()) {
			String pkText = tableNode.getPkText(col);
			// 【修正】インラインスタイルで動的に割り当てたグリッド列幅を指定
			if (CommonUtils.isEmpty(pkText)) {
				svg.appendLine(String.format("<div class='table-row' style='grid-template-columns: %fpx %fpx;'>",
						nameColumnWidth, typeColumnWidth));
			} else {
				svg.appendLine(String.format("<div class='table-row pk' style='grid-template-columns: %fpx %fpx;'>",
						nameColumnWidth, typeColumnWidth));
			}
			svg.addIndentLevel(1);
			svg.appendLine(String.format("<div class='table-cell name'>%s</div>", tableNode.getName(col)));
			svg.appendLine(String.format("<div class='table-cell'>%s</div>", tableNode.getType(col)));
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
}