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
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.jetbrains.bio.npy.NpyArray;
import org.jetbrains.bio.npy.NpyFile;

import hageldave.jplotter.canvas.BlankCanvas;
import hageldave.jplotter.canvas.BlankCanvasFallback;
import hageldave.jplotter.canvas.FBOCanvas;
import hageldave.jplotter.canvas.JPlotterCanvas;
import hageldave.jplotter.charts.ScatterPlot;
import hageldave.jplotter.color.DefaultColorMap;
import hageldave.jplotter.interaction.kml.KeyMaskListener;
import hageldave.jplotter.renderers.AdaptableView;
import hageldave.jplotter.renderers.CompleteRenderer;
import hageldave.jplotter.renderers.PointsRenderer;
import hageldave.jplotter.renderers.Renderer;
import hageldave.jplotter.util.Pair;

public class NPYScatter {
	
	public static void main(String[] args) {
		System.out.println(Arrays.toString(args));
		Optional<String> coords_file = findArg(args, ".npy");
		if(coords_file.isEmpty() || coords_file.get().contains("color_values=")) {
			System.err.println("missing *.npy file argument.");
			System.exit(1);
		}
		
//		NpyArray arr = NpyFile.read(
//				FileSystems.getDefault().getPath(
//						"/home/david/git/aitchison/output/sensitivity/CALPHAD/projections/", 
//						"PCA_dcls_30.npy"
//				), 
//				1024
//		);
		NpyArray arr = NpyFile.read(FileSystems.getDefault().getPath(coords_file.get()), 1024);
		NumpyArray data = new NumpyArray(arr);
		
		NumpyArray colorData = null;
		Optional<String> color_file = findArg(args, "color_values=");
		if(color_file.isPresent()) {
			NpyArray arr_color = NpyFile.read(
					FileSystems.getDefault().getPath(color_file.get().split("=")[1]),
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
			Optional<String> color_idx = findArg(args, "color_value_idx=");
			int cidx = color_idx.isEmpty() ? 0:Integer.parseInt(color_idx.get().split("=")[1]);
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
		
		ScatterPlot scatter = new ScatterPlot(useFallback(args));
		scatter.getDataModel().addData(
				IntStream.range(0, data.shape[0])
				.mapToObj(i->data.slice1D(i,null))
				.toArray(double[][]::new), 0, 1, "");
		if(colorData != null){
			scatter.setVisualMapping(new ScatterPlot.ScatterPlotVisualMapping() {
				@Override
				public int getColorForDataPoint(int chunkIdx, String chunkDescr, double[][] dataChunk, int pointIdx) {
					double v = colorValues[pointIdx];
					return v < 0 ? 0x33ff00ff : DefaultColorMap.S_TURBO.interpolate(v);
				}
			});
		}
		scatter.alignCoordsys(1.1);
		Optional<String> ipc_file = findArg(args, "ipc_file=");
		Path ipcFilePath = ipc_file.map(s -> FileSystems.getDefault().getPath(s.split("=")[1])).orElse(null);

		Optional<String> pointsize = findArg(args, "point_size=");
		if(pointsize.isPresent()) {
			double s = Double.parseDouble(pointsize.get().split("=")[1]);
			for(CompleteRenderer r : Arrays.asList(
					scatter.getContent(), 
					scatter.getContentLayer0(),
					scatter.getContentLayer1(),
					scatter.getContentLayer2()))
			{
				r.points.setGlyphScaling(s);
			}
		}
		scatter.addRectangularPointSetSelector(new KeyMaskListener(KeyEvent.VK_SHIFT));
		scatter.addPointSetSelectionListener(new ScatterPlot.PointSetSelectionListener() {
			
			@Override
			public void onPointSetSelectionChanged(ArrayList<Pair<Integer, TreeSet<Integer>>> selectedPoints,
					Shape selectionArea) {
				scatter.highlight(
						selectedPoints.stream()
						.flatMap(p->p.second.stream().map(p_->Pair.of(p.first, p_)))
						.collect(Collectors.toList())
				);
			}
		});
		scatter.addScrollZoom();
		scatter.addPanning();
		
		scatter.addPointSetSelectionListener(new ScatterPlot.PointSetSelectionListener() {
			
			@Override
			public void onPointSetSelectionChanged(ArrayList<Pair<Integer, TreeSet<Integer>>> selectedPoints,
					Shape selectionArea) {
				if (ipcFilePath == null) return;
				int[][] selection = slectionToIndices(selectedPoints);
				// flatten to 1D: extract the point index (second element) from each pair
				int[] indices = Arrays.stream(selection)
						.mapToInt(pair -> pair[1])
						.toArray();
				try {
					writeSelectionToFile(ipcFilePath, indices);
				} catch (IOException e) {
					System.err.println("IPC write failed: " + e.getMessage());
				}
			}
		});
		
		
		JFrame frame = createJFrameWithBoilerPlate("Numpy Array Scatter Plot");
		frame.getContentPane().add(scatter.getCanvas().asComponent());
		scatter.getCanvas().addCleanupOnWindowClosingListener(frame);
		
		if(findArg(args, "no_axes=true").isPresent()) {
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
	
	static boolean useFallback(String[] args) {
		return Arrays.stream(args).filter(arg->"jplotter_fallback=true".equals(arg)).findFirst().isPresent();
	}
	
	static Optional<String> findArg(String[] args, String argToFind) {
		return Arrays.stream(args).filter(arg->arg.contains(argToFind)).findFirst();
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
