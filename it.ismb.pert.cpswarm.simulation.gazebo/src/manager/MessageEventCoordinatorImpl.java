package manager;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map.Entry;

import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.osgi.service.component.ComponentFactory;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import be.iminds.iot.ros.util.RosCommand;
import eu.cpswarm.optimization.messages.MessageSerializer;
import eu.cpswarm.optimization.messages.ParameterSet;
import eu.cpswarm.optimization.messages.ReplyMessage;
import eu.cpswarm.optimization.messages.SimulationResultMessage;
import simulation.xmpp.AbstractMessageEventCoordinator;
import simulation.SimulationManager;

//factory component for creating instance per requestor
@Component(factory = "it.ismb.pert.cpswarm.gazeboMessageEventCoordinatorImpl.factory")
public class MessageEventCoordinatorImpl extends AbstractMessageEventCoordinator {

	private SimulationManager parent = null;
	private ComponentFactory scriptLauncherFactory;
	private ComponentFactory rosCommandFactory; // used to catkin build the workspace
	private Process process;
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
		this.scriptLauncherFactory = s;

	}

	@Reference(target = "(component.factory=it.ismb.pert.cpswarm.rosCommand.factory)")
	public void getRosCommandFactory(final ComponentFactory s) {
		this.rosCommandFactory = s;

	}

	@Override
	protected void handleCandidate(final EntityBareJid sender, final ParameterSet parameterSet) {
		try {
			packageName = parent.getOptimizationID().substring(0, parent.getOptimizationID().indexOf("!"));
			if (sender.equals(JidCreate.entityBareFrom(parent.getOptimizationJID()))) {
				if (parameterSet.getParameters().get(0).getName().equals("test")) {
					parent.setTestResult("optimization");
					return;
				}
				if (!serializeCandidate(parameterSet)) {
					parent.publishFitness(
							new SimulationResultMessage(parent.getOptimizationID(), "Error serializing the candidate",
									ReplyMessage.Status.ERROR, parent.getSimulationID(), BAD_FITNESS), null, 0);
					return;
				}
				runSimulation(true);
			} else { // SOO
				if (parameterSet.getParameters().get(0).getName().equals("test")) {
					parent.setTestResult("simulation");
					return;
				}
				if (!serializeCandidate(parameterSet)) {
					try { // send error result to SOO
						final ChatManager chatmanager = ChatManager.getInstanceFor(parent.getConnection());
						final Chat newChat = chatmanager.chatWith(parent.getOrchestratorJID().asEntityBareJidIfPossible());
						SimulationResultMessage reply = new SimulationResultMessage(parent.getOptimizationID(), "Error serializing the candidate",
								ReplyMessage.Status.ERROR, parent.getSimulationID(), BAD_FITNESS);
						MessageSerializer serializer = new MessageSerializer();
						newChat.send(serializer.toJson(reply));
					} catch (final Exception e) {
						e.printStackTrace();
					}
					return;
				}
				runSimulation(false);
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void runSimulation(boolean calcFitness) throws IOException, InterruptedException {
		try {
			process = Runtime.getRuntime().exec(new String[] { "/bin/bash", "-c", "source /opt/ros/kinetic/setup.bash; rosclean purge -y; rm "+parent.getBagPath()+"*.bag" });
			process.waitFor();
			process = null;
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		Properties props = new Properties();
		props.put("rosWorkspace", parent.getCatkinWS());
				
		ComponentInstance instance = this.scriptLauncherFactory.newInstance((Dictionary) props);
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
			instance.dispose();
			thread.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (calcFitness) {
			parent.publishFitness(new SimulationResultMessage(parent.getOptimizationID(), "fitness score",
					ReplyMessage.Status.OK, parent.getSimulationID(), 100.0), params.toString(), null);
		}

	}

	public void launchSimulation() {
		ComponentInstance commandInstance = null;
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
				roslaunch.startSimulation();
				System.out.println("Simulation finished");
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
			proc = Runtime.getRuntime().exec("killall -2 roslaunch");
			proc.waitFor();
			proc.destroy();
			proc = null;
		} catch (IOException | InterruptedException ex) {
			ex.printStackTrace();
		}

	}

}
