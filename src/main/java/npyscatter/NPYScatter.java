package npyscatter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
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

import hageldave.imagingkit.core.Img;
import hageldave.imagingkit.core.util.ImageFrame;
import hageldave.jplotter.canvas.BlankCanvas;
import hageldave.jplotter.canvas.BlankCanvasFallback;
import hageldave.jplotter.canvas.FBOCanvas;
import hageldave.jplotter.canvas.JPlotterCanvas;
import hageldave.jplotter.charts.ScatterPlot;
import hageldave.jplotter.charts.ScatterPlot.PointSetSelectionListener;
import hageldave.jplotter.color.DefaultColorMap;
import hageldave.jplotter.font.FontProvider;
import hageldave.jplotter.interaction.SimpleSelectionModel;
import hageldave.jplotter.interaction.SimpleSelectionModel.SimpleSelectionListener;
import hageldave.jplotter.interaction.kml.KeyMaskListener;
import hageldave.jplotter.renderers.AdaptableView;
import hageldave.jplotter.renderers.CompleteRenderer;
import hageldave.jplotter.renderers.Renderer;
import hageldave.jplotter.util.ExportUtil;
import hageldave.jplotter.util.Pair;

public class NPYScatter {
	
	static final String HELP = "help";
	static final String CMAP_LIST = "cmap-list";
	static final String CMAP_SHOW = "cmap-show";
	
	
	static void showColorMaps() {
		int numcmaps = DefaultColorMap.values().length;
		int h = 30;
		int w = 300;
		Img img = new Img(w, h*numcmaps);
		for(int i=0; i<numcmaps; i++) {
			DefaultColorMap cmap = DefaultColorMap.values()[i];
			Img cmapimg = cmap.toImg(w, h, true, !cmap.name().startsWith("Q"));
			cmapimg.paint(g2d->{
				g2d.setColor(new Color(0x66ffffff, true));
				g2d.setFont(FontProvider.getUbuntuMono(14, Font.PLAIN));
				Rectangle stringBounds = g2d.getFontMetrics().getStringBounds(cmap.name(), g2d).getBounds();
				g2d.fillRect(3, 27-stringBounds.height, 4+stringBounds.width, stringBounds.height);
				g2d.setColor(java.awt.Color.BLACK);
				g2d.drawString(cmap.name(), 5, 24);
			});
			cmapimg.copyArea(0, 0, w, h, img, 0, i*h);
		}
		ImageFrame frame = new ImageFrame();
		frame.setTitle("NPYS - Color Maps");
		frame.setPreferredSize(new Dimension(w,1000));
		frame.useDefaultSettings();
		SwingUtilities.invokeLater( () -> {
			frame.setImg(img);
			frame.setVisible(true);
		});
	}
	
	
	static int[] loadOrGenerateDrawOrder(String drawOrderSpec, int requiredLen) {
		if (drawOrderSpec != null) {
			if(drawOrderSpec.endsWith(".npy")) {
				int[] loadedOrder = null;
				try {
					NpyArray arr_order = readNpyArray(FileSystems.getDefault().getPath(drawOrderSpec));
					loadedOrder = Arrays.stream(NumpyArray.to_double(arr_order.getData())).mapToInt(v -> (int) v).toArray();
					if (loadedOrder.length != requiredLen) {
						System.err.println("Error: draw-order length (" + loadedOrder.length + ") must match number of points (" + requiredLen + ").");
						System.exit(1);
					}
				} catch (IOException e) {
					System.err.println("Error reading draw order file (" + e.getClass().getSimpleName() + "): " + e.getMessage());
					System.exit(1);
					return null;
				}
				return loadedOrder;
			} else {
				// try to parse as random seed
				long seed = drawOrderSpec.startsWith("0x") ? Long.parseLong(drawOrderSpec.substring(2), 16) : Long.parseLong(drawOrderSpec);
				Random rand = new Random(seed);
				int[] randvalues = rand.ints().limit(requiredLen).toArray();
				return argsort(randvalues);
			}
		} else {
			return IntStream.range(0, requiredLen).toArray();
		}
	}
	
	static double[] loadColorValues(Path colorValuesPath, int colorValueIdx) {
		NumpyArray colorData = null;
		if (colorValuesPath != null) {
			try {
				NpyArray arr_color = readNpyArray(colorValuesPath);
				int[] shape = arr_color.getShape();
				if(shape.length == 1) {
					colorData = new NumpyArray(NumpyArray.to_double(arr_color.getData()), new int[]{shape[0], 1});
				} else {
					colorData = new NumpyArray(arr_color);
				}
			} catch (IOException e) {
				System.err.println("Error reading color values file (" + e.getClass().getSimpleName() + "): " + e.getMessage());
				System.exit(1);
				return null;
			}
		}
		return colorData == null ? null:colorData.slice1D(null, colorValueIdx);
	}
	
	
	public static void main(String[] args) {
		// Define all options
		Options options = new Options();
		options.addOption(Option.builder("h").longOpt(HELP).desc("Print this help message and exit.").get());
		for(Configuration c: Configuration.values()) {
			options.addOption(c.toOption());
		}
		options.addOption(Option.builder().longOpt(CMAP_LIST).desc("List available color maps and exit.").get());
		options.addOption(Option.builder().longOpt(CMAP_SHOW).desc("Shows available color maps in a GUI.").get());
		
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

		if (cmd.hasOption(HELP)) {
			printHelp(formatter,options,true);
			System.exit(0);
		}
		
		if (cmd.hasOption(CMAP_LIST)) {
			System.out.println("Available color maps (can also specify only part of the name, first match will be used):");
			Arrays.stream(DefaultColorMap.values())
			.map(DefaultColorMap::name)
			.forEach(System.out::println);
			System.exit(0);
		}
		
		if(cmd.hasOption(CMAP_SHOW)) {
			showColorMaps();
			return;
		}

		String[] positional = cmd.getArgs();
		if (positional.length == 0) {
			System.err.println("Error: missing required positional argument <coords.npy>");
			printHelp(formatter,options,false);
			System.exit(1);
		}
		String coordsFile = positional[0];
		
		for(Configuration c: Configuration.values()){
			try {
				c.setValueFromCmdline(cmd);
				c.validateValue();
			} catch(RuntimeException e) {
				System.err.println("Error parsing argument for --" + c.longOption + ": " + e.getMessage());
				printHelp(formatter,options,false);
				System.exit(1);
			}
		}
		

		int x_idx = Configuration.x_idx.get();
		int y_idx = Configuration.y_idx.get();
		int colorValueIdx = Configuration.color_value_idx.get();
		int[] size = Configuration.size.get();
		double[] view = Configuration.view.get();
		Path colorValuesPath = Configuration.color_values.get();
		DefaultColorMap cmap = Configuration.cmap.get();
		Path ipcFilePath = Configuration.ipc_file.get();
		double pointsize = Configuration.point_size.get();
		boolean fallback = Configuration.fallback.get();
		boolean noAxes = Configuration.no_axes.get();
		Path outputPath = Configuration.output.get();
		Double jitter = Configuration.jitter.get();
		String xLabel = Configuration.x_label.getOrElse("Dim " + x_idx);
		String yLabel = Configuration.y_label.getOrElse("Dim " + y_idx);
		String drawOrderSpec = Configuration.draw_order.get();

		
		NpyArray arr;
		try {
			arr = readNpyArray(FileSystems.getDefault().getPath(coordsFile));
		} catch (IOException e) {
			System.err.println("Error reading coordinates file (" + e.getClass().getSimpleName() + "): " + e.getMessage());
			System.exit(1);
			return;
		}
		NumpyArray data = new NumpyArray(arr);
		
		final int[] order = loadOrGenerateDrawOrder(drawOrderSpec, data.shape[0]);
		final int[] invOrder = new int[order.length];
		for (int i = 0; i < order.length; i++) 
			invOrder[order[i]] = i;
		
		double[] colorValues = loadColorValues(colorValuesPath, colorValueIdx);
		double[] cminmax = {0,1};
		if(colorValues != null) {
			cminmax[0] = Arrays.stream(colorValues).min().getAsDouble();
			cminmax[1] = Arrays.stream(colorValues).min().getAsDouble();
		}
		
		
		ScatterPlot scatter = new ScatterPlot(!fallback);
		scatter.getDataModel().addData(
				IntStream.range(0, data.shape[0])
				.map(i -> order[i])
				.mapToObj(i->data.slice1D(i, null))
				.toArray(double[][]::new), 
				x_idx, 
				y_idx, 
				"");
		
		scatter.getCoordsys().setxAxisLabel(xLabel);
		scatter.getCoordsys().setyAxisLabel(yLabel);
		
		if(colorValues != null){
			// check if color values are integer valued and colormap is discrete
			boolean integerValued = Arrays.stream(colorValues).allMatch(v -> v == Math.floor(v));
			boolean disctretecmap = cmap.name().startsWith("Q");
			if(disctretecmap && integerValued) {
				scatter.setVisualMapping(new ScatterPlot.ScatterPlotVisualMapping() {
					@Override
					public int getColorForDataPoint(int chunkIdx, String chunkDescr, double[][] dataChunk, int pointIdx) {
						double v = colorValues[order[pointIdx]];
						return v < 0 ? 0x33ff00ff : cmap.getColor(((int)v)%cmap.numColors());
					}
				});
			} else if(disctretecmap && !integerValued) {
				System.err.println("Warning: color values are not integer valued but colormap is discrete. Mapping unique values to discrete colors");
				// find unique values and map to discrete colors
				double[] uniqueValues = Arrays.stream(colorValues).distinct().toArray();
				Arrays.sort(uniqueValues);
				int[] discretecolorvalues = Arrays.stream(colorValues)
						.mapToInt(v -> Arrays.binarySearch(uniqueValues, v))
						.toArray();
				scatter.setVisualMapping(new ScatterPlot.ScatterPlotVisualMapping() {
					@Override
					public int getColorForDataPoint(int chunkIdx, String chunkDescr, double[][] dataChunk, int pointIdx) {
						int v = discretecolorvalues[order[pointIdx]];
						return cmap.getColor(v%cmap.numColors());
					}
				});
			} else {
				/* continuous colormap case: using interpolation */
				if(cminmax[0] == -1 && integerValued) {
					/* special case: -1 usually indicating missing label (e.g. noise cluster)
					 * therefore we assume that the actual minimum is 0 and all values < 0 should be mapped to a 
					 * special color (magenta in this case).
					 */
					cminmax[0] = 0;
				} else if(cmap.name().startsWith("D") && cminmax[0] < 0 && cminmax[1] > 0) {
					/* diverging colormap with values around 0: we assume that the diverging point is at 0.
					 */
					double absmax = Math.max(Math.abs(cminmax[0]), Math.abs(cminmax[1]));
					cminmax[0] = -absmax;
					cminmax[1] = absmax;
				}
				scatter.setVisualMapping(new ScatterPlot.ScatterPlotVisualMapping() {
					double div_by_range = 1.0/(cminmax[1]-cminmax[0]);
					@Override
					public int getColorForDataPoint(int chunkIdx, String chunkDescr, double[][] dataChunk, int pointIdx) {
						double v = (colorValues[order[pointIdx]]-cminmax[0])*div_by_range;
						return v < 0 ? 0x33ff00ff : cmap.interpolate(v);
					}
				});
			}
		}
		if(view != null) {
			scatter.getCoordsys().setCoordinateView(view[0], view[2], view[1], view[3]);
		} else {
			scatter.alignCoordsys(1.1);
		}
		
		if (pointsize != 1.0) {
			for(CompleteRenderer r : Arrays.asList(
					scatter.getContent(), 
					scatter.getContentLayer0(),
					scatter.getContentLayer1(),
					scatter.getContentLayer2()))
			{
				r.points.setGlyphScaling(pointsize);
			}
		}
		SimpleSelectionModel<Pair<Integer, Integer>> selectionModel = new SimpleSelectionModel<Pair<Integer,Integer>>();
		scatter.addRectangularPointSetSelector(new KeyMaskListener(KeyEvent.VK_SHIFT));
		PointSetSelectionListener pssl = new ScatterPlot.PointSetSelectionListener() {
			
			@Override
			public void onPointSetSelectionChanged(ArrayList<Pair<Integer, TreeSet<Integer>>> selectedPoints,
					Shape selectionArea) {
				List<Pair<Integer, Integer>> selection = selectedPoints.stream()
				.flatMap(p->p.second.stream().map(p_->Pair.of(p.first, p_)))
				.collect(Collectors.toList());
				selectionModel.setSelection(selection);
			}
		};
		scatter.addPointSetSelectionListener(pssl);
		//scatter.addPointSetSelectionOngoingListener(pssl);
		scatter.addScrollZoom();
		scatter.addPanning();
		
		selectionModel.addSelectionListener(new SimpleSelectionListener<Pair<Integer,Integer>>() {
			@Override
			public void selectionChanged(SortedSet<Pair<Integer, Integer>> selection) {
				scatter.highlight(selection);
			}
		});
		
		// file based IPC stuff
		if(ipcFilePath != null) {
			long[] last_write_time = {-1};
			
			IPCFileWatcher watcher = new IPCFileWatcher(ipcFilePath);
			watcher.listeners.add((int[] selection, Long mtime) -> {
				if(mtime <= last_write_time[0]) {
					return; // this change was triggered by our own write, ignore
				}
				selectionModel.setSelection(
						Arrays.stream(selection)
						.map(j -> invOrder[j])
						.mapToObj(i->Pair.of(0, i))
						.collect(Collectors.toList())
				);
			});
			if(ipcFilePath.toFile().exists()) {
				try {
					int[] initialSelection = IPCFileWatcher.readIndices(ipcFilePath);
					watcher.notifyListeners(initialSelection, 0);
				} catch (IOException e) {
					System.err.println("Error reading initial selection from IPC file ("+ e.getClass().getSimpleName() +"): " + e.getMessage());
				}
			}
			watcher.start();
			
			selectionModel.addSelectionListener(new SimpleSelectionListener<Pair<Integer,Integer>>() {
				
				private ExecutorService exec = Executors.newSingleThreadExecutor();
				AtomicReference<int[]> pendingWrite = new AtomicReference<>(null);
				
				@Override
				public void selectionChanged(SortedSet<Pair<Integer, Integer>> selection) {
					// flatten to 1D: extract the point index (second element) from each pair
					int[] indices = selection.stream().mapToInt(pair -> order[pair.second]).toArray();
					Arrays.sort(indices);
					if(Arrays.equals(indices, watcher.lastReadIndices)) {
						return; // selection is the same as the last read selection, skip writing
					}
					pendingWrite.set(indices);
					exec.submit(() -> {
							int[] to_write = pendingWrite.getAndSet(null);
							if(to_write == null)
								return; // another job already writing this selection, skip
							try {
								writeSelectionToFile(ipcFilePath, to_write);
								last_write_time[0] = System.currentTimeMillis();
							} catch (IOException e) {
								System.err.println("IPC write failed: " + e.getMessage());
							}
					});
				}
			});
		}
		
		JFrame frame = createJFrameWithBoilerPlate("NPYS - " + (coordsFile.length() > 30 ? "..." + coordsFile.substring(coordsFile.length()-(30-3)) : coordsFile));
		scatter.getCanvas().asComponent().setPreferredSize(new Dimension(size[0], size[1]));
		frame.getContentPane().add(scatter.getCanvas().asComponent());
		scatter.getCanvas().addCleanupOnWindowClosingListener(frame);
		
		if(noAxes) {
			Renderer content = scatter.getCoordsys().getContent();
			scatter.getCanvas().setRenderer(content);
			((AdaptableView)content).setView(scatter.getCoordsys().getCoordinateView());
		}
		
		if(outputPath != null) {
			/* non interactive mode, only render to file and exit. Therfore:
			 * make frame undecorated to allow for canvas sizes larger than screen size 
			 * without triggering automatic resizing by the OS
			 */
			frame.setUndecorated(true);
		}
		
		SwingUtilities.invokeLater(()->{
			frame.pack();
			frame.setVisible(true);
		});
		
		if(jitter != null) {
			// determine viewport of scatter points
			Rectangle2D[] viewport = {null};
			scatter.getCanvas().scheduleRepaint();
			try {
				SwingUtilities.invokeAndWait(()->{
					if(noAxes) {
						viewport[0] = scatter.getCanvas().asComponent().getBounds();
					} else {
						viewport[0] = scatter.getCoordsys().getCoordSysArea();
					}
				});
			} catch (InvocationTargetException | InterruptedException e) {
				System.err.println("Error during jitter setup: " + e.getMessage());
				System.exit(1);
				return;
			}
			// compute jitter magnitudes
			Rectangle2D view_rect = scatter.getCoordsys().getCoordinateView();
			double xjitter = jitter * view_rect.getWidth() / viewport[0].getWidth();
			double yjitter = jitter * view_rect.getHeight() / viewport[0].getHeight();
			// apply jitter by overriding the scatter plot's point positions with a random translation
			double[][] curr_data = scatter.getDataModel().getDataChunk(0);
			Random rand = new Random(0xC0FFEE);
			for(int i=0; i<curr_data.length; i++) {
				double angle = rand.nextDouble() * 2 * Math.PI;
				double r = Math.sqrt(0.16 + 0.84 * rand.nextDouble());
				curr_data[i][x_idx] += Math.cos(angle) * r * xjitter;
				curr_data[i][y_idx] += Math.sin(angle) * r * yjitter;
			}
			scatter.getDataModel().setDataChunk(0, curr_data);
		}
		
		if(outputPath != null) {
			scatter.getCanvas().scheduleRepaint();
			SwingUtilities.invokeLater(() -> {
				try {
					if(outputPath.toString().toLowerCase().endsWith(".png")) {
						ExportUtil.canvasToPNG(scatter.getCanvas(), outputPath.toString());
					}
					else if(outputPath.toString().toLowerCase().endsWith(".svg")) {
						ExportUtil.canvasToSVG(scatter.getCanvas(), outputPath.toString());
					}
					else if(outputPath.toString().toLowerCase().endsWith(".pdf")) {
						ExportUtil.canvasToPDF(scatter.getCanvas(), outputPath.toString());
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
		Files.write(file, Arrays.stream(arr).mapToObj(i -> "" + i).collect(Collectors.toList()));
	}

	public static void writeSelectionToFile(Path ipcFile, int[] indices) throws IOException {
		// Create a uniquely named temp file in the same directory as the target,
		// so that the subsequent atomic move stays on the same filesystem.
		Path dir = ipcFile.getParent() != null ? ipcFile.getParent() : ipcFile.toAbsolutePath().getParent();
		Path tmp = Files.createTempFile(dir, "npyscatter_sel_", ".tmp");
		try {
			if (ipcFile.endsWith(".npy"))
				NpyFile.write(tmp, indices);
			else
				write(tmp, indices);
			boolean atomicmoveImpossible = false;
			try {
				Files.move(tmp, ipcFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				atomicmoveImpossible = true;
			}
			boolean moveImpossible = false;
			if(atomicmoveImpossible) {
				// fallback to non-atomic move
				try {
					Files.move(tmp, ipcFile, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					moveImpossible = true;
				}
			}
			if (moveImpossible) {
				Files.copy(tmp, ipcFile, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			// Clean up the temp file in case the move failed
			Files.deleteIfExists(tmp);
		}
	}
	
	/* Utility method that adds throws declaration cause NpyFile is kotlin and does not specify it correctly */
	public static NpyArray readNpyArray(Path file) throws IOException {
		return NpyFile.read(file, 1024);
	}
	
	public static int[] argsort(int[] arr) {
		return IntStream.range(0, arr.length)
				.boxed()
				.sorted((i, j) -> Integer.compare(arr[i], arr[j]))
				.mapToInt(Integer::intValue)
				.toArray();
	}

}
