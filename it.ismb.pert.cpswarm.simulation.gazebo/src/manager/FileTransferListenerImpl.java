package manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import simulation.xmpp.AbstractFileTransferListener;
import simulation.SimulationManager;

//factory component for creating instance per requestor
@Component(factory = "it.ismb.pert.cpswarm.gazeboFileTransferListenerImpl.factory")
public class FileTransferListenerImpl extends AbstractFileTransferListener {

	protected SimulationManager parent = null;
	protected String dataFolder = null;
	protected String rosFolder = null;

	@Activate
	protected void activate(Map<String, Object> properties) throws Exception {
		for (Entry<String, Object> entry : properties.entrySet()) {
			String key = entry.getKey();
			if (key.equals("SimulationManager")) {
				try {
					parent = (SimulationManager) entry.getValue();
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			}
		}
		assert parent != null;
		if (parent != null) {
			if (SimulationManager.CURRENT_VERBOSITY_LEVEL.equals(SimulationManager.VERBOSITY_LEVELS.ALL)) {
				System.out.println(" Instantiate a gazebo FileTransferListenerImpl");
			}
			setSimulationManager(parent);
		}
	}

	@Deactivate
	public void deactivate() {
		if (SimulationManager.CURRENT_VERBOSITY_LEVEL.equals(SimulationManager.VERBOSITY_LEVELS.ALL)) {
			System.out.println(" stopping a gazebo FileTransferListenerImpl");
		}
	}

	@Override
	protected boolean unzipFiles(final String fileToReceive) {
		String packagePath = null;
		try {
			byte[] buffer = new byte[1024];
			ZipInputStream zis = new ZipInputStream(new FileInputStream(fileToReceive));
			ZipEntry zipEntry = zis.getNextEntry();
			Process proc = null;
			File newFile = null;
			String fileName = null;
			FileOutputStream fos = null;
			proc = Runtime.getRuntime().exec(new String[] { "/bin/bash", "-c",
					"source " + parent.getCatkinWS() + "devel/setup.bash ; rospack find " + packageName });

			Runtime.getRuntime().addShutdownHook(new Thread(proc::destroy));
			BufferedReader input = new BufferedReader(new InputStreamReader(proc.getInputStream()));

			if ((packagePath = input.readLine()) != null && !packagePath.startsWith("[rospack]")) {
				while (zipEntry != null) {
					fileName = zipEntry.getName();
					newFile = null;
					// The wrapper is copied to the ROS folder
					if (fileName.endsWith(".cpp")) {
						newFile = new File(rosFolder + fileName);
					} else {
						newFile = new File(dataFolder + fileName);
					}
					fos = new FileOutputStream(newFile);
					int len;
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
					fos.close();
					zipEntry = zis.getNextEntry();
					fileName = null;
				}
				zis.closeEntry();
				zis.close();
			} else {
				System.out.println("Error: the " + packageName + " package doesn't exist");
				return false;
			}
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}
}
