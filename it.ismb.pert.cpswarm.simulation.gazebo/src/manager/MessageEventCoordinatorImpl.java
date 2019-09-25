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
import eu.cpswarm.optimization.messages.ReplyMessage;
import eu.cpswarm.optimization.messages.SimulationResultMessage;
import eu.cpswarm.optimization.messages.SimulatorConfiguredMessage;
import simulation.xmpp.AbstractMessageEventCoordinator;
import simulation.SimulationManager;

//factory component for creating instance per requestor
@Component(factory = "it.ismb.pert.cpswarm.gazeboMessageEventCoordinatorImpl.factory")
public class MessageEventCoordinatorImpl extends AbstractMessageEventCoordinator {

	private String packageName = null;
	private SimulationManager parent = null;
	private ComponentFactory scriptLauncherFactory;
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
	public void getScriptLauncherFactory(final ComponentFactory s) {
		this.scriptLauncherFactory = s;

	}

	@Reference(target = "(component.factory=it.ismb.pert.cpswarm.rosCommand.factory)")
	public void getRosCommandFactory(final ComponentFactory s) {
		this.rosCommandFactory = s;

	}

	@Override
	protected void handleCandidate(final EntityBareJid sender, final String candidate, final String candidateType) {
		try {
			packageName = parent.getSCID();
			packageFolder = parent.getRosFolder() + packageName;
			if (sender.equals(JidCreate.entityBareFrom(parent.getOptimizationJID()))) {
				if (candidate.equals("test")) {
					parent.setTestResult("optimization");
					return;
				}
				if (!serializeCandidate(candidate)) {
					parent.publishFitness(
							new SimulationResultMessage(parent.getOptimizationID(), false, parent.getSimulationID(), BAD_FITNESS));
					return;
				}
				if(SimulationManager.CURRENT_VERBOSITY_LEVEL.equals(SimulationManager.VERBOSITY_LEVELS.ALL)) {
					System.out.println("handling candidate: ...."+ /*candidate +*/" , for SCID: "+parent.getSCID());
				}
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
					System.out.println("Compilation finished, " + result);

					if (result) {
						if(SimulationManager.CURRENT_VERBOSITY_LEVEL.equals(SimulationManager.VERBOSITY_LEVELS.ALL)) {
							System.out.println("Compilation finished, success = "+result);
						}
						runSimulation(true);
						if(SimulationManager.CURRENT_VERBOSITY_LEVEL.equals(SimulationManager.VERBOSITY_LEVELS.ALL)) {
							System.out.println("simulation "+this.parent.getSimulationID()+" done");
						}
					} else {
						System.out.println("Compilation with error, success = "+result);
						parent.publishFitness(new SimulationResultMessage(parent.getOptimizationID(), false, parent.getSimulationID(),
								BAD_FITNESS));  // ? publish or not
					}
				}
			} else { // sender is SOO for a single simulation  
				if (candidate.equals("test")) {
					parent.setTestResult("simulation");
					return;
				}
				if (!serializeCandidate(candidate)) {
					if (parent.isOrchestratorAvailable()) {
						try { // send error result to SOO
							final ChatManager chatmanager = ChatManager.getInstanceFor(parent.getConnection());
							final Chat newChat = chatmanager.chatWith(parent.getOrchestratorJID().asEntityBareJidIfPossible());
							SimulationResultMessage reply = new SimulationResultMessage(parent.getOptimizationID(), false, parent.getSimulationID(), BAD_FITNESS);
							MessageSerializer serializer = new MessageSerializer();
							newChat.send(serializer.toJson(reply));
						} catch (final Exception e) {
							e.printStackTrace();
						}
					}
					return;
				}
				runSimulation(false);  // TODO, ? for a single simulation, no result sent back to SOO, it can not setSimulationDone(true)
										// solution: pass the RunSimulation Sender as the argument of runSimulation(sender) method, to decide if publish to OT or send result to SOO
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void runSimulation(boolean calcFitness) throws IOException, InterruptedException {
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
			script.setCanRun(false);
			thread.join();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (instance != null)
				instance.dispose();
		}
		if (calcFitness) {   // if true, publish calculated fitness to OT, so a claculator is needed, if false, no need calculation, do nothing,    ( or send calculated result/1.0 to SOO , NO!)
			parent.publishFitness(new SimulationResultMessage(parent.getOptimizationID(), true, parent.getSimulationID(), 100.0));
		} else {
			if (parent.isOrchestratorAvailable()) {
				try { // send final result to SOO for setting simulation done
					final ChatManager chatmanager = ChatManager.getInstanceFor(parent.getConnection());
					final Chat newChat = chatmanager.chatWith(parent.getOrchestratorJID().asEntityBareJidIfPossible());
					SimulationResultMessage reply = new SimulationResultMessage(parent.getOptimizationID(), true, parent.getSimulationID(), 100.0);
					MessageSerializer serializer = new MessageSerializer();
					newChat.send(serializer.toJson(reply));
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}
		}

	}

	public void launchSimulation() {
		ComponentInstance commandInstance = null;
		try {
			System.out.println("Launching the simulation for package: " + packageName + " with params: "
					+ parent.getSimulationConfiguration());
			// roslaunch emergency_exit gazebo.launch visual:=true
			Properties props = new Properties();
			props.put("rosWorkspace", parent.getCatkinWS());
			props.put("ros.package", packageName);
			props.put("ros.node", "gazebo.launch");
			props.put("ros.mappings", parent.getSimulationConfiguration());
			commandInstance = this.rosCommandFactory.newInstance((Dictionary) props);
			RosCommand roslaunch = (RosCommand) commandInstance.getInstance();
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
