# NPYScatter
Application for viewing .npy (numpy array) files in a scatter plot.
```bash
# basic usage
npyscatter example_data/iris_data.npy

# select dimensions, colorize
npyscatter example_data/iris_data.npy -x 0 -y 2 --color-values example_data/iris_labels.npy --cmap SET2
```

<img width="808" height="441" alt="npyscatter" src="https://github.com/user-attachments/assets/2cbe6c9c-7a6b-4818-a8b8-f1c954302abc" />

## Interactive Controls
The coordinate system can be moved around and zoomed in and out, and a rectangular selection of points can be made (triggering highlighting).
|Action|Key|
|---|---|
|Panning|`CTRL` + `LMB`|
|Zooming|`ALT` + `SCROLL`|
|Selecting|`SHIFT` + `LMB`|

## Installation

NPYScatter is a Java application which is built with Maven. To build it, use
```
mvn clean install
```
which will compile the code and assemble a runnable .jar file in a newly created subdirectory `target/`.
The application can then be run via `java -jar target/npyscatter-0.0.1-SNAPSHOT-jar-with-dependencies.jar`.

> **Note:** It is recommended to create a script (bash/powershell, depending on OS) that contains this command and make it available globally.
> This way you can use a concise abbreviation like `npyscatter` as used in this readme.
> When updating your build, nothing needs to be moved or replaced.

### Ubuntu Script Example
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

## Brushing & Linking via IPC

NPYScatter implements file-based inter process communication (IPC) for brushing and linking.
It can write the currently selected point indices to a `.npy` or text file whenever the selection changes. 
Other applications can watch this file and react accordingly (e.g. for brushing & linking across views). 
Infact, NPYScatter watches this file and updates the highlighted points on change accordingly. 
This mechanism allows multiple instances of NPYScatter to be linked easily when they share the same selection file.

### Usage

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

#### Example: Java consumer (using `WatchService`)

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


#### Example: Python consumer (using polling)

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
