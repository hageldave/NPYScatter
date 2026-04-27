package npyscatter;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import hageldave.jplotter.color.DefaultColorMap;

public enum Configuration {
	x_idx("x", 
			"Column index for X axis (default: 0).",
			"N", 
			Integer::parseInt, Configuration::requireNonNegative,
			0),
	y_idx("x", 
			"Column index for Y axis (default: 1).", 
			"N", 
			Integer::parseInt, Configuration::requireNonNegative,
			1),
	x_label(
			"Label for X axis. Default is 'Dim N' where N is the x-idx.", 
			"name", 
			Configuration::identity, Configuration::allowAny),
	y_label(
			"Label for Y axis. Default is 'Dim N' where N is the y-idx.", 
			"name", 
			Configuration::identity, Configuration::allowAny),
	color_values(
			"Path to .npy file with values to be mapped to color.",
			"path",
			Configuration::parsePath, Configuration::npyFileExists),
	color_value_idx(
			"Column index in color-values array (default: 0).",
			"N",
			Integer::parseInt, Configuration::requireNonNegative,
			0),
	cmap(
			"Color map name (default: S_TURBO). Use --cmap-list to get a list of available color maps.",
			"name",
			Configuration::parseColorMap, Configuration::cmapExists,
			DefaultColorMap.S_TURBO),
	point_size("p",
			"Point glyph scaling factor.",
			"N",
			Double::parseDouble, Configuration::requireNonNegative,
			1.0d),
	fallback(
			"Use JPlotter fallback canvas.",
			"bool",
			Boolean::parseBoolean, Configuration::allowAny,
			false),
	no_axes(
			"Hide coordinate system.",
			"bool",
			Boolean::parseBoolean, Configuration::allowAny,
			false),
	size("s",
			"Size of the canvas <Width,Height> (default 400,400).",
			"N,N",
			Configuration::parseIntArray, Configuration::require2posInts,
			new int[]{400,400}),
	view("v",
			"Coordinate view limits (view port) <MinX,MaxX,MinY,MaxY>. " + 
			"Make sure the argument is properly escaped so that negative values are not recognized as options (e.g. '\"-1,1,-1,1\"'). " + 
			"Defaults to bounding box of data if not provided.",
			"N,N,N,N",
			Configuration::parseDoubleArray, Configuration::require4doubles),
	ipc_file("i",
			"Path to IPC file for selection exchange.",
			"path",
			Configuration::parsePath, Configuration::isNotDirectory),
	output("o",
			"Path to output file (*.png, *.svg, *.pdf).",
			"path",
			Configuration::parsePath, Configuration::isNotDirectory),
	jitter(
			"Add jitter to scatter points. Value in pixels.",
			"N",
			Double::parseDouble, Configuration::requireNonNegative),
	draw_order(
			"Path to .npy file with draw order (integer array of point indices 0 to N-1 specifying the draw sequence) or random seed to generate a permutation.",
			"path or seed",
			Configuration::identity, Configuration::npyFileExistsOrIsSeed);
	
	String longOption;
	String shortOption;
	String description;
	String argName;
	Function<String, Object> parser;
	Consumer<?> validator;
	Object value;
	Object default_value;
	
	Configuration(String shortOption, String descr, String argName, Function<String, Object> parser, Consumer<?> validator, Object defaultValue) {
		this.shortOption = shortOption;
		this.description = descr;
		this.argName = argName;
		this.parser = parser;
		this.validator = validator;
		this.longOption = this.name().replace('_', '-');
		this.default_value = defaultValue;
		this.value = defaultValue;
	}
	
	Configuration(String descr, String argName, Function<String, Object> parser, Consumer<?> validator, Object defaultValue) {
		this(null, descr, argName, parser, validator, defaultValue);
	}
	
	Configuration(String shortOption, String descr, String argName, Function<String, Object> parser, Consumer<?> validator) {
		this(shortOption, descr, argName, parser, validator, null);
	}
	
	Configuration(String descr, String argName, Function<String, Object> parser, Consumer<?> validator) {
		this(null, descr, argName, parser, validator, null);
	}
	
	@SuppressWarnings("unchecked")
	public <T> T get() {
		return (T) value;
	}
	
	public <T> T getOrElse(T defaultVal) {
		T v = get();
		return v != null ? v:defaultVal; 
	}
	
	private static String identity(String value) {
		return value;
	}
	
	public static Path parsePath(String value) {
		return FileSystems.getDefault().getPath(value);
	}
	
	public static DefaultColorMap parseColorMap(String value) {
		return Arrays.stream(DefaultColorMap.values())
				.filter(candidate -> candidate.name().contains(value))
				.findFirst()
				.orElse(null);
	}
	
	public static int[] parseIntArray(String value) {
		String[] parts = value.split(",");
		int[] result = new int[parts.length];
		for(int i = 0; i < parts.length; i++) {
			result[i] = Integer.parseInt(parts[i]);
		}
		return result;
	}
	
	public static double[] parseDoubleArray(String value) {
		String[] parts = value.split(",");
		double[] result = new double[parts.length];
		for(int i = 0; i < parts.length; i++) {
			result[i] = Double.parseDouble(parts[i]);
		}
		return result;
	}
	
	public Option toOption() {
		return Option.builder(shortOption)
				.longOpt(longOption)
				.hasArg()
				.argName(argName)
				.desc(description)
				.get();
	}
	
	public void setValue(Object value) {
		this.value = value;
	}
	
	public void setValueFromCmdline(CommandLine cmd) {
		String optionValue = cmd.getOptionValue(longOption);
		if(optionValue != null) {
			setValue(parser.apply(optionValue));
		}
	}
	
	public void validateValue() {
		if(value == null && default_value == null) {
			return; // null is allowed if no default value is defined
		}	
		validator.accept(get());
	}
	
	static void requireNonNegative(Object value) {
		if(((Number)value).doubleValue() < 0) {
			throw new IllegalArgumentException("Value must be >= 0.");
		}
	}
	
	static void require2posInts(Object value) {
		if(((int[])value).length != 2) {
			throw new IllegalArgumentException("Value must be two integers, but got " + ((int[])value).length + ".");
		}
		if(((int[])value)[0] <= 0 || ((int[])value)[1] <= 0) {
			throw new IllegalArgumentException("Values must be positive integers. Got " + ((int[])value)[0] + ", " + ((int[])value)[1] + ".");
		}
	}
	
	static void require4doubles(Object value) {
		if(((double[])value).length != 4) {
			throw new IllegalArgumentException("Value must be four doubles, but got " + ((double[])value).length + ".");
		}
	}
	
	static void allowAny(Object value) {
		// no validation
	}
	
	static void npyFileExists(Object value) {
		Path path = (Path) value;
		if(!path.toFile().exists()) {
			throw new IllegalArgumentException("File does not exist: " + path);
		}
		if(!path.toFile().isFile()) {
			throw new IllegalArgumentException("Not a file: " + path);
		}
		if(!path.toString().endsWith(".npy")) {
			throw new IllegalArgumentException("File must have .npy extension: " + path);
		}
	}
	
	static void isNotDirectory(Object value) {
		Path path = (Path) value;
		if(path.toFile().exists() && path.toFile().isDirectory()) {
			throw new IllegalArgumentException("Path must not be a directory: " + path);
		}
	}
	
	static void cmapExists(Object value) {
		if(value == null) {
			throw new IllegalArgumentException("No matching colormap found.");
		}
	}
	
	static void npyFileExistsOrIsSeed(Object value) {
		String strValue = (String) value;
		try {
			@SuppressWarnings("unused")
			long seed = strValue.startsWith("0x") ? Long.parseLong(strValue.substring(2), 16) : Long.parseLong(strValue);
			// if parsing as long succeeds, we treat it as a seed and skip file validation
			return;
		} catch(NumberFormatException e) {
			// not an integer, continue with file validation
		}
		try {
			npyFileExists(value);
		} catch(IllegalArgumentException e) {
			throw new IllegalArgumentException("Value must be either a valid random seed or a path to an existing .npy file. Got: " + strValue);
		}
	}
	
}
