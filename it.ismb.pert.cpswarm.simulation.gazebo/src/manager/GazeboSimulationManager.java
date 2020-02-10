package manager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Dictionary;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.w3c.dom.Document;
import com.google.gson.Gson;

import messages.server.Capabilities;
import messages.server.Server;
import simulation.SimulationManager;
import simulation.SimulationManager.VERBOSITY_LEVELS;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentFactory;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true, service = { GazeboSimulationManager.class })
public class GazeboSimulationManager extends SimulationManager {

	/* create an instance per requestor by using a Factory Component */
	private ComponentInstance coordinatorInstance = null;
	private ComponentInstance fileTransferListenerInstace = null;
	private ComponentFactory messageEventCoordinatorFactory;
	private ComponentFactory fileTransferListenerImplFactory;

	@Reference(target = "(component.factory=it.ismb.pert.cpswarm.gazeboMessageEventCoordinatorImpl.factory)")
	public void getMessageEventCoordinatorFactory(final ComponentFactory s) {
		this.messageEventCoordinatorFactory = s;

	}

	@Reference(target = "(component.factory=it.ismb.pert.cpswarm.gazeboFileTransferListenerImpl.factory)")
	public void getFileTransferListenerFactory(final ComponentFactory s) {
		this.fileTransferListenerImplFactory = s;

	}

	@Activate
	public void activate(BundleContext context) {
					
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder documentBuilder;
		InetAddress serverURI = null;
		String serverName = "";
		String serverPassword = "";
		String dataFolder = "";
		String optimizationUser = "";
		String orchestratorUser = "";
		String rosFolder = "";
		String uuid = "";
		int timeout = 90000;
		boolean debug = false;
		boolean monitoring = false;
		String mqttBroker = "";
		String verbosity = "2";
		String launchFile = null;

		Server serverInfo = new Server();
		try {
			if(context.getProperty("verbosity")!=null){
				verbosity = context.getProperty("verbosity");
			}
			int verbosityI = Integer.parseInt(verbosity);
			if(verbosityI>2) {
				System.out.println("Invalid verbosity level, using the default one: ALL");
			} else {
				CURRENT_VERBOSITY_LEVEL = VERBOSITY_LEVELS.values()[verbosityI];
			}
			if(context.getProperty("simulation.launch.file")!=null){
				launchFile = context.getProperty("simulation.launch.file");
			}
			if (launchFile == null) {
				System.out.println("launchFile = null");
				deactivate();
			}
			if(SimulationManager.CURRENT_VERBOSITY_LEVEL.equals(SimulationManager.VERBOSITY_LEVELS.ALL)) {
				System.out.println("Instantiate a GazeboSimulationManager .....");
			}
			
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			String managerConfigFile = context.getProperty("Manager.config.file.manager.xml");
			if (managerConfigFile == null) {
				System.out.println("managerConfigFile = null");
				deactivate();
			}
			Document document = null;
			FileInputStream s = new FileInputStream(managerConfigFile);
			document = documentBuilder.parse(s);
			serverURI = InetAddress.getByName(document.getElementsByTagName("serverURI").item(0).getTextContent());
			serverName = document.getElementsByTagName("serverName").item(0).getTextContent();
			serverPassword = document.getElementsByTagName("serverPassword").item(0).getTextContent();
			dataFolder = document.getElementsByTagName("dataFolder").item(0).getTextContent();
			if (document.getElementsByTagName("uuid").getLength() != 0) {
				uuid = document.getElementsByTagName("uuid").item(0).getTextContent();
			}
			if (document.getElementsByTagName("timeout").getLength() != 0) {
				timeout = Integer.parseInt(document.getElementsByTagName("timeout").item(0).getTextContent());
			}
			if (document.getElementsByTagName("debug").getLength() != 0) {
				debug = Boolean.parseBoolean(document.getElementsByTagName("debug").item(0).getTextContent());
			}
			Capabilities capabilities = new Capabilities();
			capabilities
					.setDimensions(Long.valueOf(document.getElementsByTagName("dimensions").item(0).getTextContent()));
			capabilities
					.setMaxAgents(Long.valueOf(document.getElementsByTagName("maxAgents").item(0).getTextContent()));
			serverInfo.setCapabilities(capabilities);
			optimizationUser = document.getElementsByTagName("optimizationUser").item(0).getTextContent();
			orchestratorUser = document.getElementsByTagName("orchestratorUser").item(0).getTextContent();
			rosFolder = document.getElementsByTagName("rosFolder").item(0).getTextContent();
			if (document.getElementsByTagName("debug").getLength() != 0) {
				debug = Boolean.parseBoolean(document.getElementsByTagName("debug").item(0).getTextContent());
			}
			if (document.getElementsByTagName("monitoring").getLength() != 0) {
				monitoring = Boolean.parseBoolean(document.getElementsByTagName("monitoring").item(0).getTextContent());
			}
			if (monitoring) {
				mqttBroker = document.getElementsByTagName("mqttBroker").item(0).getTextContent();
			}
			if (!dataFolder.endsWith(File.separator)) {
				dataFolder += File.separator;
			}
			if (!new File(dataFolder).isDirectory()) {
				System.out.println("Data folder must be a folder");
				deactivate();
			}

		} catch (ParserConfigurationException e1) {
			e1.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		boolean connected = connectToXMPPserver(serverURI, serverName, serverPassword, dataFolder, rosFolder, serverInfo, optimizationUser,
				orchestratorUser, uuid, debug, monitoring, mqttBroker, timeout, Boolean.FALSE, launchFile);
		if(connected) {
			publishPresence(serverURI, serverName, serverPassword, dataFolder, rosFolder, serverInfo, optimizationUser,
				orchestratorUser, uuid, debug, monitoring, mqttBroker, timeout);
		} else
			deactivate();
	}

	public void publishPresence(final InetAddress serverURI, final String serverName, final String serverPassword,
			final String dataFolder, final String rosFolder, final Server serverInfo, final String optimizationUser,
			final String orchestratorUser, final String uuid, final boolean debug, final boolean monitoring,
			final String mqttBroker, final int timeout) {
		Properties props = new Properties();
		props.put("SimulationManager", this);
		coordinatorInstance = this.messageEventCoordinatorFactory.newInstance((Dictionary) props);
		MessageEventCoordinatorImpl coordinator = (MessageEventCoordinatorImpl) coordinatorInstance.getInstance();
		this.addMessageEventCoordinator(coordinator);

		fileTransferListenerInstace = this.fileTransferListenerImplFactory.newInstance((Dictionary) props);
		FileTransferListenerImpl fileTransferListener = (FileTransferListenerImpl) fileTransferListenerInstace
				.getInstance();
		this.addFileTransfer(fileTransferListener);
		ProcessBuilder builder = null;
		Process process = null;
		boolean result = true;
		try {
			builder = new ProcessBuilder(new String[] { "/bin/bash", "-c",
					"source /opt/ros/kinetic/setup.bash; cd " + this.getCatkinWS() + " ; catkin build " });
			if (CURRENT_VERBOSITY_LEVEL.equals(VERBOSITY_LEVELS.ALL))	
				builder.inheritIO();
			process = builder.start();
			process.waitFor();
		} catch (IOException |InterruptedException err) {
			result = false;
			System.err.println("Error when building workspace: " + this.getCatkinWS());
			err.printStackTrace();
		} finally {
			if (process != null) {
				process.destroy();
				process = null;
			}
		}
		if (CURRENT_VERBOSITY_LEVEL.equals(VERBOSITY_LEVELS.ALL))
			System.out.println("Compilation finished, with succeed = " + result);
		if (result) {
			serverInfo.setServer(clientJID.asUnescapedString());
			ServiceDiscoveryManager disco = ServiceDiscoveryManager.getInstanceFor(this.getConnection());
			disco.addFeature("http://jabber.org/protocol/si/profile/file-transfer");
			final Presence presence = new Presence(Presence.Type.available);
			Gson gson = new Gson();
			if (SimulationManager.CURRENT_VERBOSITY_LEVEL.equals(SimulationManager.VERBOSITY_LEVELS.ALL)) {
				System.out.println("\nGazebo SM : the server info is " + gson.toJson(serverInfo, Server.class));
			}
			presence.setStatus(gson.toJson(serverInfo, Server.class));
			try {
				this.getConnection().sendStanza(presence);
			} catch (final NotConnectedException | InterruptedException e) {
				e.printStackTrace();
			}
		} else {
			return;
		}
	}

	public MessageEventCoordinatorImpl getCoordinator() {
		assert coordinatorInstance != null;
		return (MessageEventCoordinatorImpl) coordinatorInstance.getInstance();
	}

	@Deactivate
	public void deactivate() {
		if (coordinatorInstance != null)
			coordinatorInstance.dispose();
		if (fileTransferListenerInstace != null)
			fileTransferListenerInstace.dispose();
		if(SimulationManager.CURRENT_VERBOSITY_LEVEL.equals(SimulationManager.VERBOSITY_LEVELS.ALL)) {
			System.out.println(" \n stoping Gazebo simulation manager \n");
		}		
	}

}
