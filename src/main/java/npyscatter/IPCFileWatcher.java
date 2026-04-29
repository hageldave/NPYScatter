package npyscatter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import org.jetbrains.bio.npy.NpyFile;

public class IPCFileWatcher extends Thread {

	Path ipcFile;
	final AtomicBoolean stopRequested = new AtomicBoolean(false);
	final ArrayList<Consumer<int[]>> listeners = new ArrayList<Consumer<int[]>>();
	public int[] lastReadIndices = null;
	
	
	public IPCFileWatcher(Path ipcfile) {
		this.ipcFile = ipcfile;
	}

	public void run() {
		long lastMtime = -1;
		while (!stopRequested.get()) {
			if (ipcFile.toFile().exists()) {
				long mtime = ipcFile.toFile().lastModified();
				if (mtime != lastMtime) {
					lastMtime = mtime;
					try {
						int[] indices = readIndices(ipcFile);
						Arrays.sort(indices);
						lastReadIndices = indices;
						SwingUtilities.invokeLater(()->{notifyListeners(indices);});
					} catch (IOException e) {
						System.err.println("Failed to read IPC file: " + e.getMessage());
					}
				}
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {}
		}
	}
	
	public static int[] readIndices(Path ipcFile) throws IOException {
		return ipcFile.endsWith(".npy")
		? 
		NpyFile.read(ipcFile, 1024).asIntArray() 
		:
		Files.readAllLines(ipcFile).stream().mapToInt(Integer::parseInt).toArray();
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
