package manager;

import java.util.Map;
import java.util.Map.Entry;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

/**
 * A Runnable class used to launch the script that allow resetting the map of
 * the simulation
 *
 */

@Component(factory = "it.ismb.pert.cpswarm.scriptLauncher.factory")
public class ScriptLauncher implements Runnable {
	private String catkinWS = null;
	private boolean canRun = true;
	private Process proc = null;

	@Activate
	public void activate(BundleContext context, Map<String, Object> properties) throws Exception {
		System.out.println(" Instantiate a Script Launcher ");
		for (Entry<String, Object> entry : properties.entrySet()) {
			String key = entry.getKey();
			if (key.equals("rosWorkspace")) {
				this.catkinWS = (String) entry.getValue();
			}
		}
	}

	@Override
	public void run() {
		System.out.println("Launching the thread to reset the map");
		try {
			System.out.println("Launching script: /bin/bash " + catkinWS + "costmap_clear.sh");
			proc = Runtime.getRuntime().exec("/bin/bash " + catkinWS + "costmap_clear.sh");
			Runtime.getRuntime().addShutdownHook(new Thread(proc::destroy));

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Deactivate
	void deactivate() {
		System.out.println("Simulation launcher is deactived");
		if (proc != null) {
			proc.destroy();
			proc = null;
		}
	}

	public synchronized void setCanRun(boolean canRun) {
		this.canRun = canRun;
		deactivate();
	}

}
