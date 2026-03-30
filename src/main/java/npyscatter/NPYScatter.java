package npyscatter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;
import org.jetbrains.bio.npy.NpyArray;
import org.jetbrains.bio.npy.NpyFile;

import hageldave.jplotter.canvas.BlankCanvas;
import hageldave.jplotter.canvas.BlankCanvasFallback;
import hageldave.jplotter.canvas.FBOCanvas;
import hageldave.jplotter.canvas.JPlotterCanvas;
import hageldave.jplotter.charts.ScatterPlot;
import hageldave.jplotter.color.DefaultColorMap;
import hageldave.jplotter.interaction.SimpleSelectionModel;
import hageldave.jplotter.interaction.SimpleSelectionModel.SimpleSelectionListener;
import hageldave.jplotter.interaction.kml.KeyMaskListener;
import hageldave.jplotter.renderers.AdaptableView;
import hageldave.jplotter.renderers.CompleteRenderer;
import hageldave.jplotter.renderers.Renderer;
import hageldave.jplotter.util.ExportUtil;
import hageldave.jplotter.util.Pair;

public class NPYScatter {
	
	public static void main(String[] args) {
		// Define all options
		Options options = new Options();
		options.addOption(Option.builder("h").longOpt("help").desc("Print this help message and exit.").get());
		options.addOption(Option.builder("x").longOpt("x-idx").hasArg().argName("N").desc("Column index for X axis (default: 0).").get());
		options.addOption(Option.builder("y").longOpt("y-idx").hasArg().argName("N").desc("Column index for Y axis (default: 1).").get());
		options.addOption(Option.builder().longOpt("color-values").hasArg().argName("path").desc("Path to .npy file with values to be mapped to color.").get());
		options.addOption(Option.builder().longOpt("color-value-idx").hasArg().argName("N").desc("Column index in color-values array (default: 0).").get());
		options.addOption(Option.builder().longOpt("cmap").hasArg().argName("name").desc("Color map name (default: S_TURBO)").get());
		options.addOption(Option.builder("i").longOpt("ipc-file").hasArg().argName("path").desc("Path to IPC file for selection exchange.").get());
		options.addOption(Option.builder("p").longOpt("point-size").hasArg().argName("N").desc("Point glyph scaling factor.").get());
		options.addOption(Option.builder().longOpt("fallback").desc("Use JPlotter fallback canvas.").get());
		options.addOption(Option.builder().longOpt("no-axes").desc("Hide coordinate axes.").get());
		options.addOption(Option.builder("s").longOpt("size").hasArg().argName("N,N").desc("Size of the canvas <Width,Height>.").get());
		options.addOption(Option.builder("v").longOpt("view").hasArg().argName("N,N,N,N").desc("Coordinate view limits (view port) <MinX,MaxX,MinY,MaxY>. " + 
				"Make sure the argument is properly escaped so that negative values are not recognized as options (e.g. '\"-1,1,-1,1\"'). " + 
				"Defaults to bounding box of data if not provided.")
				.get());
		options.addOption(Option.builder("o").longOpt("output").hasArg().argName("path").desc("Path to output file (*.png, *.svg, *.pdf).").get());
		
		
		
		HelpFormatter formatter = HelpFormatter.builder().get();
		CommandLine cmd;
		try {
			cmd = new DefaultParser().parse(options, args);
		} catch (ParseException e) {
			System.err.println("Error: " + e.getMessage());
			printHelp(formatter,options,false);
			System.exit(1);
			return;
		}

		if (cmd.hasOption("help")) {
			printHelp(formatter,options,true);
			System.exit(0);
		}

		String[] positional = cmd.getArgs();
		if (positional.length == 0) {
			System.err.println("Error: missing required positional argument <coords.npy>");
			printHelp(formatter,options,false);
			System.exit(1);
		}
		String coordsFile = positional[0];

		int x_idx;
		int y_idx;
		int colorValueIdx;
		try {
			x_idx = Integer.parseInt(cmd.getOptionValue("x-idx", "0"));
			y_idx = Integer.parseInt(cmd.getOptionValue("y-idx", "1"));
			colorValueIdx = Integer.parseInt(cmd.getOptionValue("color-value-idx", "0"));
		} catch (NumberFormatException e) {
			System.err.println("Error: --x-idx, --y-idx, and --color-value-idx must be integers. " + e.getMessage());
			printHelp(formatter,options, false);
			System.exit(1);
			return;
		}
		int width=400;
		int height=400;
		String size = cmd.getOptionValue("size");
		if(size != null) {
			try {
				width = Integer.parseInt(size.split(",")[0]);
				height= Integer.parseInt(size.split(",")[1]);
			} catch(Exception e) {
				System.err.println("Error: size argument malformed. " + e.getMessage());
				printHelp(formatter,options, false);
				System.exit(1);
			}
		}
		double[] view = null;
		String viewStr = cmd.getOptionValue("view");
		if(viewStr != null) {
			try {
				view = Arrays.stream(viewStr.split(",")).mapToDouble(Double::parseDouble).toArray();
				if(view.length != 4) {
					throw new IllegalArgumentException("Expected 4 comma separated numbers for view limits.");
				}
			} catch(Exception e) {
				System.err.println("Error: view argument malformed. " + e.getMessage());
				printHelp(formatter,options, false);
				System.exit(1);
			}
		}
		
		String colorValuesPath = cmd.getOptionValue("color-values");
		String cmapName = cmd.getOptionValue("cmap", "S_TURBO");
		String ipcFilePath = cmd.getOptionValue("ipc-file");
		String pointSizeStr = cmd.getOptionValue("point-size");
		boolean fallback = cmd.hasOption("fallback");
		boolean noAxes = cmd.hasOption("no-axes");
		String outputPath = cmd.getOptionValue("output");

		NpyArray arr = NpyFile.read(FileSystems.getDefault().getPath(coordsFile), 1024);
		NumpyArray data = new NumpyArray(arr);
		
		NumpyArray colorData = null;
		if (colorValuesPath != null) {
			NpyArray arr_color = NpyFile.read(
					FileSystems.getDefault().getPath(colorValuesPath),
					1024
			);
			if(arr_color.getShape().length == 1) {
				colorData = new NumpyArray(NumpyArray.to_double(arr_color.getData()), new int[]{data.shape[0], 1});
			} else {
				colorData = new NumpyArray(arr_color);
			}
		}
		double[] colorValues;
		if(colorData != null) {
			int cidx = colorValueIdx;
			double min = colorData.min(null,cidx);
			double max = colorData.max(null,cidx);
			if(min == -1) {
				colorValues = Arrays.stream(colorData.slice1D(null,cidx))
						.map(v->(v)/(max)).toArray();
			} else {
				colorValues = Arrays.stream(colorData.slice1D(null,cidx))
						.map(v->(v-min)/(max-min)).toArray();
			}
		} else {
			colorValues = null;
		}
		
		ScatterPlot scatter = new ScatterPlot(fallback);
		scatter.getDataModel().addData(
				IntStream.range(0, data.shape[0])
				.mapToObj(i->data.slice1D(i,null))
				.toArray(double[][]::new), 
				x_idx, 
				y_idx, 
				"");
		
		DefaultColorMap cmap = Arrays.stream(DefaultColorMap.values())
				.filter(candidate -> candidate.name().contains(cmapName))
				.findFirst()
				.orElse(null);
		if (cmap == null) {
			System.err.println("cmap " + cmapName + " is unknown. Available names are:");
			Arrays.stream(DefaultColorMap.values())
			.map(DefaultColorMap::name)
			.forEach(System.err::println);
			System.exit(1);
		}
		if(colorData != null){
			scatter.setVisualMapping(new ScatterPlot.ScatterPlotVisualMapping() {
				@Override
				public int getColorForDataPoint(int chunkIdx, String chunkDescr, double[][] dataChunk, int pointIdx) {
					double v = colorValues[pointIdx];
					return v < 0 ? 0x33ff00ff : cmap.interpolate(v);
				}
			});
		}
		if(view != null) {
			scatter.getCoordsys().setCoordinateView(view[0], view[2], view[1], view[3]);
		} else {
			scatter.alignCoordsys(1.1);
		}
		Path ipcPath = ipcFilePath != null ? FileSystems.getDefault().getPath(ipcFilePath) : null;

		if (pointSizeStr != null) {
			double s;
			try {
				s = Double.parseDouble(pointSizeStr);
			} catch (NumberFormatException e) {
				System.err.println("Error: --point-size must be a number. " + e.getMessage());
				printHelp(formatter,options, false);
				System.exit(1);
				return;
			}
			for(CompleteRenderer r : Arrays.asList(
					scatter.getContent(), 
					scatter.getContentLayer0(),
					scatter.getContentLayer1(),
					scatter.getContentLayer2()))
			{
				r.points.setGlyphScaling(s);
			}
		}
		SimpleSelectionModel<Pair<Integer, Integer>> selectionModel = new SimpleSelectionModel<Pair<Integer,Integer>>();
		scatter.addRectangularPointSetSelector(new KeyMaskListener(KeyEvent.VK_SHIFT));
		scatter.addPointSetSelectionListener(new ScatterPlot.PointSetSelectionListener() {
			
			@Override
			public void onPointSetSelectionChanged(ArrayList<Pair<Integer, TreeSet<Integer>>> selectedPoints,
					Shape selectionArea) {
				List<Pair<Integer, Integer>> selection = selectedPoints.stream()
				.flatMap(p->p.second.stream().map(p_->Pair.of(p.first, p_)))
				.collect(Collectors.toList());
				selectionModel.setSelection(selection);
			}
		});
		scatter.addScrollZoom();
		scatter.addPanning();
		
		selectionModel.addSelectionListener(new SimpleSelectionListener<Pair<Integer,Integer>>() {
			@Override
			public void selectionChanged(SortedSet<Pair<Integer, Integer>> selection) {
				scatter.highlight(selection);
			}
		});
		
		// file based IPC stuff
		if(ipcPath != null) {
			selectionModel.addSelectionListener(new SimpleSelectionListener<Pair<Integer,Integer>>() {
				
				private ExecutorService exec = Executors.newSingleThreadExecutor();
				AtomicReference<int[]> pendingWrite = new AtomicReference<>(null);
				
				@Override
				public void selectionChanged(SortedSet<Pair<Integer, Integer>> selection) {
					// flatten to 1D: extract the point index (second element) from each pair
					int[] indices = selection.stream().mapToInt(pair -> pair.second).toArray();
					pendingWrite.set(indices);
					exec.submit(() -> {
						int[] to_write = pendingWrite.getAndSet(null);
						if(to_write == null)
							return; // another job already writing this selection, skip
						try {
							writeSelectionToFile(ipcPath, to_write);
						} catch (IOException e) {
							System.err.println("IPC write failed: " + e.getMessage());
						}
					});
				}
			});
			IPCFileWatcher watcher = new IPCFileWatcher(ipcPath);
			watcher.listeners.add((int[] selection) -> {
				selectionModel.setSelection(
						Arrays.stream(selection).mapToObj(i->Pair.of(0, i)).collect(Collectors.toList())
				);
			});
			if(ipcPath.toFile().exists()) {
				try {
					int[] initialSelection = IPCFileWatcher.readIndices(ipcPath);
					watcher.notifyListeners(initialSelection);
				} catch (IOException e) {
					System.err.println("Error reading initial selection from IPC file: " + e.getMessage());
				}
			}
			watcher.start();
		}
		
		JFrame frame = createJFrameWithBoilerPlate("NPYS - " + (coordsFile.length() > 30 ? "..." + coordsFile.substring(coordsFile.length()-(30-3)) : coordsFile));
		scatter.getCanvas().asComponent().setPreferredSize(new Dimension(width, height));
		frame.getContentPane().add(scatter.getCanvas().asComponent());
		scatter.getCanvas().addCleanupOnWindowClosingListener(frame);
		
		if(noAxes) {
			Renderer content = scatter.getCoordsys().getContent();
			scatter.getCanvas().setRenderer(content);
			((AdaptableView)content).setView(scatter.getCoordsys().getCoordinateView());
		}
		
		SwingUtilities.invokeLater(()->{
			frame.pack();
			frame.setVisible(true);
		});
		
		if(outputPath != null) {
			scatter.getCanvas().scheduleRepaint();
			SwingUtilities.invokeLater(() -> {
				try {
					if(outputPath.toLowerCase().endsWith(".png")) {
						ExportUtil.canvasToPNG(scatter.getCanvas(), outputPath);
					}
					else if(outputPath.toLowerCase().endsWith(".svg")) {
						ExportUtil.canvasToSVG(scatter.getCanvas(), outputPath);
					}
					else if(outputPath.toLowerCase().endsWith(".pdf")) {
						ExportUtil.canvasToPDF(scatter.getCanvas(), outputPath);
					}
					else {
						System.err.println("Error: output file extension not recognized. Supported extensions are .png, .svg, and .pdf");
						System.exit(1);
					}
				} catch (RuntimeException e) {
					System.err.println("Error saving output file: " + e.getMessage());
					System.exit(1);
				}
				System.exit(0);
			});
		}
	}
	
	static void printHelp(HelpFormatter formatter, Options options, boolean example) {
		try {
			formatter.printHelp("npyscatter <coords.npy> [options]","", options, "", example);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	static JPlotterCanvas mkCanvas(boolean fallback, JPlotterCanvas contextShareParent) {
		return fallback ? new BlankCanvasFallback() : new BlankCanvas((FBOCanvas)contextShareParent);
	}
	
	public static JFrame createJFrameWithBoilerPlate(String title) {
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(new BorderLayout());
		frame.setTitle(title);
		frame.setMinimumSize(new Dimension(10, 10));
		return frame;
	}
	
	public static int[][] slectionToIndices(ArrayList<Pair<Integer, TreeSet<Integer>>> selectedPoints) {
		return selectedPoints.stream()
				.flatMap(p->p.second.stream().map(i->new int[]{p.first, i}))
				.toArray(int[][]::new);
	}
	
	public static void write(Path file, int[] arr) throws IOException {
	  Files.write(file, Arrays.stream(arr).mapToObj(i->""+i).collect(Collectors.toList()));
	}

	public static void writeSelectionToFile(Path ipcFile, int[] indices) throws IOException {
		// Create a uniquely named temp file in the same directory as the target,
		// so that the subsequent atomic move stays on the same filesystem.
		Path dir = ipcFile.getParent() != null ? ipcFile.getParent() : ipcFile.toAbsolutePath().getParent();
		Path tmp = Files.createTempFile(dir, "npyscatter_sel_", ".tmp");
		try {
		  if(ipcFile.endsWith(".npy"))
			  NpyFile.write(tmp, indices);
			else
			  write(tmp, indices);
			Files.move(tmp, ipcFile,
				StandardCopyOption.REPLACE_EXISTING,
				StandardCopyOption.ATOMIC_MOVE);
		} finally {
			// Clean up the temp file in case the move failed
			Files.deleteIfExists(tmp);
		}
	}

}
