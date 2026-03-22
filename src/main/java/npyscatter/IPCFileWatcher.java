package npyscatter;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import org.jetbrains.bio.npy.NpyFile;

public class IPCFileWatcher extends Thread {

	Path ipcFile;
	final AtomicBoolean stopRequested = new AtomicBoolean(false);
	final ArrayList<Consumer<int[]>> listeners = new ArrayList<Consumer<int[]>>();
	
	public IPCFileWatcher(Path ipcfile) {
		this.ipcFile = ipcfile;
	}

	public void run() {
		try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
			Path dir = ipcFile.getParent() != null ? ipcFile.getParent() : ipcFile.toAbsolutePath().getParent();
			dir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);

			while (!stopRequested.get()) {
				WatchKey key = watcher.poll(10, TimeUnit.MILLISECONDS); // returns null on timeout
				if (key == null) {
					Thread.yield();
					continue; // re-check stopRequested
				}
				for (WatchEvent<?> event : key.pollEvents()) {
					Path changed = (Path) event.context();
					if (changed.equals(ipcFile.getFileName())) {
						int[] indices = ipcFile.endsWith(".npy") 
								? 
								NpyFile.read(ipcFile, 1024).asIntArray() 
								:
								Files.readAllLines(ipcFile).stream().mapToInt(Integer::parseInt).toArray();
						SwingUtilities.invokeLater(()->{notifyListeners(indices);});
					}
				}
				key.reset();
				Thread.yield();
			}
		} catch(IOException e) {
			e.printStackTrace();
		} catch(InterruptedException e) {
			// NOOP
		}
	}
	
	public void notifyListeners(int[] arr) {
		for(var l: listeners) {
			l.accept(arr);
		}
	}
	
	@Override
	public void interrupt() {
		this.stopRequested.set(true);
		Thread.yield();
		super.interrupt();
	}

}
