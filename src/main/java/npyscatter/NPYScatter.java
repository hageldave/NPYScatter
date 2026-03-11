package npyscatter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.nio.file.FileSystems;
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
		Optional<String> pointsize = findArg(args, "point_size=");
		if(pointsize.isPresent()) {
			scatter.getContent().points.setGlyphScaling(Double.parseDouble(pointsize.get().split("=")[1]));
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
		
		JFrame frame = createJFrameWithBoilerPlate("Numpy Array Scatter Plot");
		frame.getContentPane().add(scatter.getCanvas().asComponent());
		scatter.getCanvas().addCleanupOnWindowClosingListener(frame);
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
	
	
}
