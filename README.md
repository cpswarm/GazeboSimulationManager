## GazeboManagerBundle

This repository contains the OSGI bundles only for creating Gazebo simulation manager, the necessary dependency bundles from the [`CPswarm-common`](https://git.pertforge.ismb.it/rzhao/cpswarm-common) repository are added in the `local` repository of this `cnf` project which sets up the bnd workspace

## Setup
Install Ros system and set up the Ros environment variable `ROS_MASTER_URI=http://localhost:11311` by default in order to set up your local machine as a ROS master.
``` bash
$ source /opt/ros/kinetic/setup.bash
$ printenv | grep ROS
```
Be sure you have installed the BND tool and [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) in your system
``` bash
sudo apt-get update
sudo apt-get install bnd
```
Install BNDTool IDE in Eclipse
``` bash
Help-> Eclipse Markerplace-> search 'Bndtools'-> Installed->Restart Eclipse.
```
## Installation

You can directly import all sub-projects in this repository by using the option `Paste Repository Path or URI` from the Git Repositories View in Eclipse.

>Note: the `cnf` project is a fixed name in the source code of Bnd IDE, it makes a directory a workspace, just like the .git directory does for git. So don't change its name.\
>So when you want to import the second bnd repository which also contains a cnf project, you must manually clone&import the other projects with the option `Copy projects into workspace` is checked, because only one `cnf` project is allowed in a workspace

## Configuration

Go to project `it.ismb.pert.cpswarm.simulation.gazebo`
*  **gazeboManager.bndrun**   
   There is a run descriptor file `gazeboManager.bndrun` with the following `-runproperties:` instruction for configuring the launching environment:
   ``` bash
   -runproperties: \
        org.eclipse.jetty.util.log.class=org.eclipse.jetty.util.log.StdErrLog,\
	    org.eclipse.jetty.LEVEL=WARN,\      # Avoid abose superfluous debug info printed on Stdin.
	    logback.configurationFile=resources/logback.xml,\        # Configuration of ch.qos.logback.core bundle
	    org.apache.felix.log.storeDebug=false,\     # Configuration of org.apache.felix.log bundle to determine whether or not debug messages will be stored in the history
	    org.osgi.service.http.port=8080,\           # The default port used for Felix servlets and resources available via HTTP
	    ros.core.native=true,\        # Indicating if launching the installed ROS system or the rosjava ROScore implementation of the rosjava_core project
	    gazebo.launch=false,\        # You can set it true to just open the Gazebo simulator without running a simulation to use `loadScene` command, but as a dependency bundle for the simulation manager, it's false
	    ros.master.uri=http://localhost:11311,\     # It is used to manually indicate the Ros environment variable in case the user doesn't set it during the Ros installation
	    Manager.config.file.manager.xml=resources/manager.xml,\     # Specify the location of the configuration file of the Gazebo simulation manager
	    javax.net.ssl.trustStorePassword=changeit,\
	    javax.net.ssl.trustStore=/usr/lib/jvm/java-1.8.0-openjdk-amd64/jre/lib/security/cacerts,\      # Replace path of the JDK with the user's value in real use case
	    org.osgi.framework.trust.repositories=/usr/lib/jvm/java-1.8.0-openjdk-amd64/jre/lib/security/cacerts      # Replace path of the JDK with the user's value in real use case
    ```  
*  **resources/manager.xml**

   It is a configuration file can be used to change some system parameters which are used by the Gazebo simulation manager to communicate with other components in the software. 

   Here is the default values, set the values to be used in the real use case
   ``` xml
   <settings>
   <uuid>22e6dbf2-ca2f-437f-8397-49daada26042</uuid> <!-- If present, indicates the UUID to be used in the JID (it is useful to have fixed JIDs) -->
   <serverURI>123.123.123.123</serverURI>  <!-- URI of the XMPP server  -->
   <serverName>pippo.pluto.it</serverName>  <!-- name of the XMPP server  -->
   <serverPassword>server</serverPassword>  <!-- Password to be used to connect to the XMPP server -->
   <dataFolder>/home/cpswarm/Desktop/output/</dataFolder> <!-- Data folder where to store the data -->
   <dimensions>3</dimensions> <!-- dimensions supported by the wrapped simulator -->
   <maxAgents>8</maxAgents> <!-- max agents supported by the wrapped simulator -->
   <optimizationUser>frevo</optimizationUser> <!-- XMPP user of the optimization tool -->
   <orchestratorUser>orchestrator</orchestratorUser> <!-- XMPP user of the orchestrator -->
   <rosFolder>/home/cpswarm/Desktop/test/src/</rosFolder> <!-- folder of the ROS workspace, it must be the <src> folder -->
   <monitoring>true</monitoring> <!--  indication if the monitoring GUI has to be used or not  -->
   <mqttBroker>tcp://123.123.123.123:1883</mqttBroker> <!--  MQTT broker to be used if the monitoring is set to true  -->
   <timeout>90000</timeout> <!-- Timeout in milliseconds for one simulation -->
   <fake>false</fake> <!-- Indicate if real simulations need to be done or not -->
   </settings>
   ```

## Run


*  Way1: Run the `gazeboManager.bndrun` in the project folder from terminal
   ``` bash
   $ bnd package gazeboManger.bndrun
   $ java -jar gazeboManager.jar
   ```
*  Way2: Run the `gazeboManager.bndrun` in Eclipse

   Run as -> Bnd OSGi Run Launcher
   
   or you can click the `Run OSGI` buntton in the right-top corner from `Run` tab of this bndrun file


