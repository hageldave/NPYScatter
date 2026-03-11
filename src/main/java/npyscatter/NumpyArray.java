package npyscatter;

import java.util.Arrays;

import org.jetbrains.bio.npy.NpyArray;

public class NumpyArray {
	
	public final double[] arr;
	public final int[] shape;
	
	
	public NumpyArray(double[] arr, int[] shape) {
		this.arr=arr;
		this.shape=shape;
	}
	
	public NumpyArray(int[] shape) {
		this(
			new double[Arrays.stream(shape).reduce((a,b)->a*b).getAsInt()], 
			shape
		);
	}
	
	public NumpyArray(NpyArray arr) {
		this(to_double(arr.getData()), arr.getShape());
	}
	
	public double get(int... coords) {
		return this.arr[idx(this.shape, coords)];
	}
	
	public double[] slice1D(Integer...coords) {
		int dim = -1;
		int[] coord = new int[shape.length];
		for(int i=0; i<shape.length; i++) {
			if(coords[i] == null)
				coord[dim=i] = 0;
			else 
				coord[i] = coords[i].intValue();
		}
		int size = shape[dim];
		double[] result = new double[size];
		coords[dim] = 0;
		for(int i=0; i<size; i++) {
			coord[dim] = i;
			result[i] = get(coord);
		}
		return result;
	}
	
	public static int idx(int[] shape, int... coords) {
		int index = 0;
		int blocksize = 1;
		for(int i=shape.length-1; i>=0; i--) {
			int idx = coords[i];
			index += idx*blocksize;
			blocksize *= shape[i];
		}
		return index;
	}
	
	public int[] shape() {
		return this.shape;
	}
	
	public double min(Integer... slice_coords) {
		if(slice_coords.length == 0)
			return Arrays.stream(arr).min().getAsDouble();
		else
			return Arrays.stream(slice1D(slice_coords)).min().getAsDouble();
	}
	
	public double max(Integer... slice_coords) {
		if(slice_coords.length == 0)
			return Arrays.stream(arr).max().getAsDouble();
		else
			return Arrays.stream(slice1D(slice_coords)).max().getAsDouble();
	}
	
	public static double[] to_double(Object data) {
		if(data instanceof double[])
			return (double[]) data;
		else if(data instanceof float[]) {
			float[] f = (float[]) data;
			double[] d = new double[f.length];
			for(int i=0; i<f.length;i++) {
				d[i] = f[i];
			}
			return d;
		} else if(data instanceof int[]) {
			int[] f = (int[]) data;
			double[] d = new double[f.length];
			for(int i=0; i<f.length;i++) {
				d[i] = f[i];
			}
			return d;
		} else if(data instanceof long[]) {
			long[] f = (long[]) data;
			double[] d = new double[f.length];
			for(int i=0; i<f.length;i++) {
				d[i] = f[i];
			}
			return d;
		} else if(data instanceof short[]) {
			short[] f = (short[]) data;
			double[] d = new double[f.length];
			for(int i=0; i<f.length;i++) {
				d[i] = f[i];
			}
			return d;
		} else if(data instanceof boolean[]) {
			boolean[] f = (boolean[]) data;
			double[] d = new double[f.length];
			for(int i=0; i<f.length;i++) {
				d[i] = f[i] ? 1:0;
			}
			return d;
		} else 
			throw new RuntimeException("Unsupported array type: " + data.getClass());
	}
	
}
