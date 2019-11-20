package manager;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map.Entry;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.osgi.service.component.ComponentFactory;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import be.iminds.iot.ros.util.RosCommand;
import eu.cpswarm.optimization.messages.ReplyMessage;
import eu.cpswarm.optimization.messages.SimulationResultMessage;
import simulation.xmpp.AbstractMessageEventCoordinator;
import simulation.SimulationManager;

//factory component for creating instance per requestor
@Component(factory = "it.ismb.pert.cpswarm.gazeboMessageEventCoordinatorImpl.factory")
public class MessageEventCoordinatorImpl extends AbstractMessageEventCoordinator {

	private String packageName = null;
	private SimulationManager parent = null;
	private ComponentFactory simulationLauncherFactory;
	private ComponentFactory rosCommandFactory; // used to catkin build the workspace

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
		assert (parent) != null;
		if (parent != null) {
			if(SimulationManager.CURRENT_VERBOSITY_LEVEL.equals(SimulationManager.VERBOSITY_LEVELS.ALL)) {
				System.out.println(" Instantiate a gazebo MessageEventCoordinatorImpl");
			}
			setSimulationManager(parent);
		}
	}

	@Reference(target = "(component.factory=it.ismb.pert.cpswarm.scriptLauncher.factory)")
	public void getSimulationLauncherFactory(final ComponentFactory s) {
		this.simulationLauncherFactory = s;

	}

	@Reference(target = "(component.factory=it.ismb.pert.cpswarm.rosCommand.factory)")
	public void getRosCommandFactory(final ComponentFactory s) {
		this.rosCommandFactory = s;

	}

	@Override
	protected void handleCandidate(final EntityBareJid sender, final String candidate) {
		try {
			packageName = parent.getOptimizationID().substring(0, parent.getOptimizationID().indexOf("!"));
			packageFolder = parent.getRosFolder() + packageName;
			if (sender.equals(JidCreate.entityBareFrom(parent.getOptimizationJID()))) {
				if (candidate.equals("test")) {
					parent.setTestResult("optimization");
					return;
				}
				if (!serializeCandidate(candidate)) {
					parent.publishFitness(
							new SimulationResultMessage(parent.getOptimizationID(), "Error serializing the candidate",
									ReplyMessage.Status.ERROR, parent.getSimulationID(), BAD_FITNESS));
					return;
				}
				System.out.println("handling candidate:"+ /*candidate +*/" , for Task ID: "+parent.getOptimizationID());
				if (!candidate.isEmpty()) {
					System.out.println("Compiling the package "+packageName);
					String catkinWS = parent.getCatkinWS();
					Properties props = new Properties();
					props.put("ros.buildWorkspace", catkinWS);
					ComponentInstance instance = null;
					boolean result = true;
					try {
						instance = this.rosCommandFactory.newInstance((Dictionary) props);
						RosCommand catkinBuild = (RosCommand) instance.getInstance();
						catkinBuild.buildWorkspace();
					} catch (Exception err) {
						result = false;
						System.err.println("Error building workspace: " + catkinWS);
						err.printStackTrace();
					} finally {
						if (instance != null)
							instance.dispose();
					}
					System.out.println("Compilation finished, with succeed = " + result);

					if (result) {
						runSimulation(true);
					} else {
						parent.publishFitness(new SimulationResultMessage(parent.getOptimizationID(),
								"Error calculating fitness score", ReplyMessage.Status.ERROR, parent.getSimulationID(),
								BAD_FITNESS));
					}
				}
			} else {
				if (candidate.equals("test")) {
					parent.setTestResult("simulation");
					return;
				}
				if (!serializeCandidate(candidate)) {
					parent.publishFitness(
							new SimulationResultMessage(parent.getOptimizationID(), "Error serializing the candidate",
									ReplyMessage.Status.ERROR, parent.getSimulationID(), BAD_FITNESS));
					return;
				}
				runSimulation(false);
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void runSimulation(boolean calcFitness) throws IOException, InterruptedException {
		Properties props = new Properties();
		props.put("rosWorkspace", parent.getCatkinWS());
				
		ComponentInstance instance = this.simulationLauncherFactory.newInstance((Dictionary) props);
		ScriptLauncher script = (ScriptLauncher) instance.getInstance();
		try {
			final Thread thread = new Thread(script);
			Timer timer = new Timer();
			timer.schedule(new TimerTask() {

				@Override
				public void run() {
					thread.start();
				}
			}, 60000);

			launchSimulation();
			script.setCanRun(false);
			thread.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (calcFitness) {
			parent.publishFitness(new SimulationResultMessage(parent.getOptimizationID(), "fitness score",
					ReplyMessage.Status.OK, parent.getSimulationID(), 1.0));
		}

	}

	public void launchSimulation() {
		ComponentInstance commandInstance = null;
		try {
			try {
				System.out.println("Launching the simulation for package: " + packageName + " with params: "+ parent.getSimulationConfiguration());
				// roslaunch emergency_exit gazebo.launch visual:=true
				Properties props = new Properties();
				props.put("rosWorkspace", parent.getCatkinWS());
				props.put("ros.package", packageName);
				props.put("ros.node", parent.getLaunchFile());
				props.put("ros.mappings", parent.getSimulationConfiguration());
				commandInstance = this.rosCommandFactory.newInstance((Dictionary) props);
				RosCommand roslaunch = (RosCommand) commandInstance.getInstance();
				System.out.println("Launching finished");
			} catch (Exception e) {
				e.printStackTrace();
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (commandInstance != null)
				commandInstance.dispose();
		}
	}

	@Deactivate
	public void deactivate() {
		if(SimulationManager.CURRENT_VERBOSITY_LEVEL.equals(SimulationManager.VERBOSITY_LEVELS.ALL)) {
			System.out.println(" MessageEventCoordinator is deactived ");
		}
		Process proc;
		try {
			proc = Runtime.getRuntime().exec("killall " + packageName);
			proc.waitFor();
			proc.destroy();
			proc = null;
		} catch (IOException | InterruptedException ex) {
			ex.printStackTrace();
		}

	}

}
