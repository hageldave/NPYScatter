# NPYScatter
Desktop application for viewing .npy (numpy array) files in a scatter plot.  
```bash
# basic usage
npyscatter example_data/iris_data.npy

# select dimensions, colorize
npyscatter example_data/iris_data.npy -x 0 -y 2 --color-values example_data/iris_labels.npy --cmap SET2
```
<img width="808" height="441" alt="npyscatter" src="https://github.com/user-attachments/assets/2cbe6c9c-7a6b-4818-a8b8-f1c954302abc" />

## Table of Contents
- [Usage](#usage)
  - [Interactive Controls](#interactive-controls)
  - [CLI options](#cli-options)
    - [Color Mapping Strategies](#color-mapping)
 - [Installation](#installation)
   - [Ubuntu Script Example](#ubuntu-script-example)
- [Brushing & Linking](#brushing--linking)
  - [Usage](#usage-1)
  - [Example Snippets](#example-snippets)
    - [Java Consumer](#java-consumer)
    - [Python Consumer](#python-consumer)

# Usage
`npyscatter <point_coordinates.npy> [options]` or  
`npyscatter [options] <point_coordinates.npy>` or  
`npyscatter [options] <point_coordinates.npy> [options]`

https://github.com/user-attachments/assets/579b418e-76ab-456c-aed0-133cc3c325f5

## Interactive Controls
The coordinate system can be moved around and zoomed in and out, and a rectangular selection of points can be made (triggering highlighting).

<table>
  <tr>
    <td><img width="500" height="275" alt="npyscatter_neww" src="https://github.com/user-attachments/assets/eb92c9cd-110d-484c-b297-bd201e81f4fa" /></td>
    <td>
      <table>
        <tr>
          <td><b>Action</b></td>
          <td><b>Key</b></td>
        </tr><tr>
          <td>Panning</td>
          <td><code>CTRL</code> + <code>LMB</code></td>
        </tr><tr>
          <td>Zooming</td>
          <td><code>ALT</code> + <code>SCROLL</code></td>
        </tr><tr>
          <td>Selecting</td>
          <td><code>SHIFT</code> + <code>LMB</code></td>
        </tr>
      </table>
    </td>
  </tr>
</table>

## CLI Options
| Option | Description |
|---|---|
| <img width=550/> |  |
| `-h`, `--help` | Print help message and exit.|
| `-x`, `--x-idx <N>` | Column index for X axis (default: 0).|
| `-y`, `--y-idx <N>` | Column index for Y axis (default: 1).|
| `--x-label <name>` | Label for X axis. Default is 'Dim N' where N is the x-idx.|
| `--y-label <name>` | Label for Y axis. Default is 'Dim N' where N is the y-idx.|
| `-p`, `--point-size <N>` | Point glyph scaling factor.|
| `-s`, `--size <N,N>` | Size of the canvas <Width,Height>.|
| `-v`, `--view <N,N,N,N>` | Coordinate view limits (view port) `<MinX,MaxX,MinY,MaxY>`. Make sure the argument is properly escaped so that negative values are not recognized as options (e.g. `'"-1,1,-1,1"'`). Defaults to bounding box of data if not provided.|
| `-o`, `--output <path>` | Path to output file (*.png, *.svg, *.pdf). Non-interactive, export then exit.|
| `-i`, `--ipc-file <path>` | Path to IPC file for selection exchange. For best performance, choose a location on a RAM disk.|
| `--color-values <path>` | Path to .npy file with values to be mapped to color.|
| `--color-value-idx <N>` | Column index in color-values array (default: 0).|
| `--cmap <name>` | Color map name (default: S_TURBO). Color map names are prefixed with **S**, **D**, **Q** indicating their type *sequential*, *diverging*, *qualitative (discrete)*. Based on the type and `color-values` array, different mapping strategies are applied.|
| `--cmap-list` | List available color maps and exit.|
| `--cmap-show` | Shows available color maps in a GUI.|
| `--jitter <N>` | Add jitter to scatter points. Value in pixels.|
| `--draw-order <path/seed>` | Path to .npy file with point index ordering OR random seed to generate a permutation (long, '0x' prefix for hex otherwise decimal).|
| `--no-axes <true/false>` | Hide coordinate system. View stretches over whole canvas.|
| `--fallback <true/false>` | Use JPlotter fallback canvas. Use when OpenGL is not supported (e.g. MacOS).|
| `--cont-select <true/false>` | Continuously fire selection events while dragging selection shape. Default is 'false'. When used in conjunction with --ipc-file, it is recommended to locate the file on a RAM disk (e.g. /dev/shm on Linux) to reduce latency and wear on physical storage devices.|

### Color Mapping
The available color maps are those shipped with the [JPlotter](https://github.com/hageldave/JPlotter/wiki/Color-Maps) libarary.
|Prefix/Type|Array characteristics|Strategy|
|---|---|---|
|**S** sequential| * | [min,max] range of values is mapped with color interpolation |
|**D** diverging| values >= 0 | same behavior as **S** |
|**D** diverging| 0 >= values | same behavior as **S** |
|**D** diverging| * | value 0 is used as diverging point, [-max(abs(values)), +max(abs(values))] range is mapped with color interpolation |
|**Q** qualitative| integers | every value is mapped to a color of the map, no interpolation, colors are repeated when there are more distinct values than colors |
|**Q** qualitative| integers, min == -1 | same as general integer case, but -1 is mapped to a transparent magenta color indicating invalid/noise cluster |
|**Q** qualitative| * | every distinct value is mapped to a color, no interpolation, colors are repeated when there are more distinct values than colors |


# Installation

NPYScatter is a Java application which is built with Maven. To build it, use
```
mvn clean install
```
which will compile the code and assemble a runnable .jar file in a newly created subdirectory `target/`.
The application can then be run via `java -jar target/npyscatter-0.0.1-SNAPSHOT-jar-with-dependencies.jar`.

> **Note:** It is recommended to create a script (bash/powershell, depending on OS) that contains this command and make it available globally.
> This way you can use a concise abbreviation like `npyscatter` as used in this readme.
> When updating your build, nothing needs to be moved or replaced.

## Ubuntu Script Example
Create a file `npyscatter`, containing
```bash
#!/usr/bin/env bash
java -jar ~/git/NPYScatter/target/npyscatter-0.0.1-SNAPSHOT-jar-with-dependencies.jar $*
```
make it executable, then move it to ~/.local/bin/ which should be on your PATH by default.
```bash
chmod a+x npyscatter
mv npyscatter ~/.local/bin/ # or ~/bin/ or another directory included on $PATH that you can write to
```

### Wayland vs X11
OpenGL is powered by LWJGL in this application. 
When your display server is Wayland, LWJGL switches to a different GL function provider by default. 
When this happens, AWT/Swing cannot communicate with the OpenGL context. 
To prevent this, the system property `org.lwjgl.opengl.contextAPI` has to be set to `native`. When launching the app, this property can be provided like this:
```bash
java -Dorg.lwjgl.opengl.contextAPI=native -jar target/npyscatter-0.0.1-SNAPSHOT-jar-with-dependencies.jar`
```

# Brushing & Linking
NPYScatter implements file-based **inter process communication** (IPC) for brushing and linking.
It can write the currently selected point indices to a `.npy` or text file whenever the selection changes. 
Other applications can watch this file and react accordingly (e.g. for brushing & linking across views). 
Infact, NPYScatter watches this file and updates the highlighted points on change accordingly. 
This mechanism allows multiple instances of NPYScatter to be linked easily when they share the same selection file.

> **Note:** It is recommended to choose a location on a RAM disk for the ipc file for best performance.  
> In Linux, `/dev/shm` is a common location for tmpfs, but you should check via `df -h` to make sure.  
> In Windows, you likely need 3rd party software to create a RAM disk (e.g. aim-toolkit).  

## Usage

Pass the file as the `-i` or `--ipc-file` option's argument when launching:

```bash
npyscatter data.npy --ipc-file /tmp/selection.npy
```

The file contains a **1D int32 NumPy array** of the selected point indices.
An empty selection writes an array of shape `(0,)`.

A plain text file can also be used.
```bash
npyscatter data.npy -i selection.txt
```

## Example Snippets
While NPYScatter already watches the selection file and reacts to changes, here are some example snippets to use for your own code to get you started.

### Java consumer

```java
import org.jetbrains.bio.npy.NpyFile;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

Path ipcFile = Path.of("/tmp/selection.npy");
WatchService watcher = FileSystems.getDefault().newWatchService();
ipcFile.getParent().register(watcher, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);

AtomicBoolean stopRequested = new AtomicBoolean(false);

System.out.println("Watching for selection changes...");
while (!stopRequested.get()) {
    WatchKey key = watcher.poll(100, TimeUnit.MILLISECONDS); // returns null on timeout
    if (key == null) {
        Thread.yield();
        continue; // re-check stopRequested
    }
    for (WatchEvent<?> event : key.pollEvents()) {
        Path changed = (Path) event.context();
        if (changed.equals(ipcFile.getFileName())) {
            int[] indices = NpyFile.read(ipcFile).asIntArray();
            System.out.println("Selection updated: " + indices.length + " points");
            // TODO: react to the new selection
        }
    }
    key.reset();
    Thread.yield();
}
watcher.close();
```

> **Note:** `WatchService` watches the **parent directory** for changes, then
> filters by filename. Both `ENTRY_CREATE` and `ENTRY_MODIFY` must be registered
> because NPYScatter writes selections via an atomic rename (temp file → target):
> this triggers an `ENTRY_CREATE` event on the target file, not `ENTRY_MODIFY`.
>
> `watcher.poll(timeout, unit)` is used instead of `watcher.take()` so that
> the `stopRequested` condition is checked regularly even when no events arrive,
> rather than blocking indefinitely.


### Python consumer

```python
import numpy as np
import time, os

ipc_file = "/tmp/selection.npy"
last_mtime = None

while True:
    try:
        mtime = os.path.getmtime(ipc_file)
        if mtime != last_mtime:
            last_mtime = mtime
            indices = np.load(ipc_file)
            print(f"Selection updated: {len(indices)} points → {indices}")
    except FileNotFoundError:
        pass  # file not written yet
    time.sleep(0.1)
```
