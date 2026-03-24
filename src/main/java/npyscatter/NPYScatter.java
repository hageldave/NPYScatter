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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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
import hageldave.jplotter.util.Pair;

public class NPYScatter {
	
	public static void main(String[] args) {
		// Define all options
		Options options = new Options();
		options.addOption(Option.builder("h").longOpt("help").desc("Print this help message and exit.").build());
		options.addOption(Option.builder().longOpt("x-idx").hasArg().argName("N").desc("Column index for X axis (default: 0).").build());
		options.addOption(Option.builder().longOpt("y-idx").hasArg().argName("N").desc("Column index for Y axis (default: 1).").build());
		options.addOption(Option.builder().longOpt("color-values").hasArg().argName("path").desc("Path to .npy file with color values.").build());
		options.addOption(Option.builder().longOpt("color-value-idx").hasArg().argName("N").desc("Column index in color-values array (default: 0).").build());
		options.addOption(Option.builder().longOpt("cmap").hasArg().argName("name").desc("Color map name (default: S_TURBO).").build());
		options.addOption(Option.builder().longOpt("ipc-file").hasArg().argName("path").desc("Path to IPC file for selection exchange.").build());
		options.addOption(Option.builder().longOpt("point-size").hasArg().argName("N").desc("Point glyph scaling factor.").build());
		options.addOption(Option.builder().longOpt("fallback").desc("Use JPlotter fallback canvas.").build());
		options.addOption(Option.builder().longOpt("no-axes").desc("Hide coordinate axes.").build());

		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd;
		try {
			cmd = new DefaultParser().parse(options, args);
		} catch (ParseException e) {
			System.err.println("Error: " + e.getMessage());
			formatter.printHelp("npyscatter <coords.npy> [options]", options);
			System.exit(1);
			return;
		}

		if (cmd.hasOption("help")) {
			formatter.printHelp("npyscatter <coords.npy> [options]", options);
			System.exit(0);
		}

		String[] positional = cmd.getArgs();
		if (positional.length == 0) {
			System.err.println("Error: missing required positional argument <coords.npy>");
			formatter.printHelp("npyscatter <coords.npy> [options]", options);
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
			formatter.printHelp("npyscatter <coords.npy> [options]", options);
			System.exit(1);
			return;
		}
		String colorValuesPath = cmd.getOptionValue("color-values");
		String cmapName = cmd.getOptionValue("cmap", "S_TURBO");
		String ipcFilePath = cmd.getOptionValue("ipc-file");
		String pointSizeStr = cmd.getOptionValue("point-size");
		boolean fallback = cmd.hasOption("fallback");
		boolean noAxes = cmd.hasOption("no-axes");

		// Print parsed arguments
		System.out.println("coords file : " + coordsFile);
		System.out.println("x-idx       : " + x_idx);
		System.out.println("y-idx       : " + y_idx);
		System.out.println("color-values: " + colorValuesPath);
		System.out.println("color-val-idx: " + colorValueIdx);
		System.out.println("cmap        : " + cmapName);
		System.out.println("ipc-file    : " + ipcFilePath);
		System.out.println("point-size  : " + pointSizeStr);
		System.out.println("fallback    : " + fallback);
		System.out.println("no-axes     : " + noAxes);

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
				.filter(candidate -> candidate.name().equals(cmapName))
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
		scatter.alignCoordsys(1.1);
		Path ipcPath = ipcFilePath != null ? FileSystems.getDefault().getPath(ipcFilePath) : null;

		if (pointSizeStr != null) {
			double s;
			try {
				s = Double.parseDouble(pointSizeStr);
			} catch (NumberFormatException e) {
				System.err.println("Error: --point-size must be a number. " + e.getMessage());
				formatter.printHelp("npyscatter <coords.npy> [options]", options);
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
				@Override
				public void selectionChanged(SortedSet<Pair<Integer, Integer>> selection) {
					// flatten to 1D: extract the point index (second element) from each pair
					int[] indices = selection.stream().mapToInt(pair -> pair.second).toArray();
					try {
						writeSelectionToFile(ipcPath, indices);
					} catch (IOException e) {
						System.err.println("IPC write failed: " + e.getMessage());
					}
				}
			});
			IPCFileWatcher watcher = new IPCFileWatcher(ipcPath);
			watcher.listeners.add((int[] selection) -> {
				selectionModel.setSelection(
						Arrays.stream(selection).mapToObj(i->Pair.of(0, i)).collect(Collectors.toList())
				);
			});
			watcher.start();
		}
		
		JFrame frame = createJFrameWithBoilerPlate("Numpy Array Scatter Plot");
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
	}
	
	static JPlotterCanvas mkCanvas(boolean fallback, JPlotterCanvas contextShareParent) {
		return fallback ? new BlankCanvasFallback() : new BlankCanvas((FBOCanvas)contextShareParent);
	}
	
	public static JFrame createJFrameWithBoilerPlate(String title) {
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(new BorderLayout());
		frame.setTitle(title);
		frame.setMinimumSize(new Dimension(100, 100));
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
