# Usage of CPSWarm gazebo-simulation-manager Image
The [`cpswarm/gazebo-simulation-manager`](https://cloud.docker.com/u/cpswarm/repository/docker/cpswarm/gazebo-simulation-manager) image provides a gazebo Simulation Manager that includes a XMPP client and interfaces with a Gazebo simulator. It was a Cpswarm pre-build image using Ubuntu16.04 and Openjdk-8 and it allows a further dockerization together with your ros simulations starting from it.

Similar steps are available for other OS and JDK versions.

### Structure of example folder
``` java
    example/
        resources/
            manager.xml                -- Configuration file for connecting to XMPP server and simulation tool capability
        Dockerfile-Gazebo-Simulation   -- Dockerfile for creating the gazebo-simulation image
        XMPP-Certifivcation.pem         -- Certificate extracted from the XMPP server
        launch_SM.sh                   -- Script for launching the simulation manager
        Gazebo-simulation-package/       -- This is the Ros packages folder
```


The `example` folder provides an example about how to create a docker image based on above pre-build gazebo-simulation-manager:latest image that will include the Ros packages which can be used to simulate and optimize it using the CPSwarm Simulation and Optimization Environment.

Before containerizing the ros simulation package starting from the gazebo-simulation-manager image, follow the steps:

1.  Change the configuration file `manager.xml` in `resources` folder according to the real use case. This file can be used to change some system parameters used by the Gazebo simulation manager to communicate with other components in the CPSWarm simulation environment.
3.  place the Gazebo simulation packages here in the `example` folder, it will be moved to an existing ros workspace `/home/catkin_ws/src/` folder coming from the `cpswarm/Gazebo-simulation-manager:latest` image.
4.  Replace the file `XMPP-Certifivcation.pem` used in real case.
5.  (If using default setting, skip this step) The `gazeboManager.jar` has some internal system properies already set inside for configuring its launching environment, so user can set individual System properties with the -D option for passing the command line parameters to override the properties listed below:

      ``` bash
      org.eclipse.jetty.util.log.class=org.eclipse.jetty.util.log.StdErrLog,\
      org.eclipse.jetty.LEVEL=WARN,\                           # Avoid verbose superfluous debug info printed on Stdin.
      logback.configurationFile=resources/logback.xml,\        # Configuration of ch.qos.logback.core bundle
      org.apache.felix.log.storeDebug=false,\          # Configuration of org.apache.felix.log bundle to determine whether or not debug messages will be stored in the history
      org.osgi.service.http.port=8080,\                # The default port used for Felix servlets and resources available via HTTP
      ros.core.native=true,\                      # Indicating if launching the installed ROS system or the rosjava ROScore implementation of the rosjava_core project
      verbosity=2,\                               # Selected verbosity level: 0 NO_OUTPUT, 1 ONLY_FITNESS_SCORE, 2 ALL
      ros.master.uri=http://localhost:11311,\     # It is used to manually indicate the Ros environment variable in case the user doesn't set it during the Ros installation
      Manager.config.file.manager.xml=resources/manager.xml,\     # Specify the location of the configuration file of the Gazebo simulation manager
      javax.net.ssl.trustStorePassword=changeit,\
      javax.net.ssl.trustStore=/usr/lib/jvm/java-1.8.0-openjdk-amd64/jre/lib/security/cacerts,\                 # Replace path of the JDK with the user's value in real use case
      org.osgi.framework.trust.repositories=/usr/lib/jvm/java-1.8.0-openjdk-amd64/jre/lib/security/cacerts      # Replace path of the JDK with the user's value in real use case
      ```
5.  Create `gazebo-simulation` image

*  Build gazebo-simulation image in the example folder
   ``` bash
   sudo docker build . --tag=gazebo-simulation:latest -f Dockerfile-Gazebo-Simulation
   ```
*  Run gazebo-simulation image and start Gazebo simulation manager

   The "launch_SM.sh" script which launches the Gazebo simulation manager will be executed by default once the image is run, the properties set by `-D` option will be delivered to the `gazeboManager.jar` coming from the cpswarm/gazebo-simulation-manager image. Access to the Gazebo GUI from [http://localhost:6901/vnc.html](http://localhost:6901/vnc.html) if visual:=true
   ```bash
   sudo docker run -it -p 5901:5901 -p 6901:6901 gazebo-simulation:latest /home/launch_SM.sh -Dverbosity=2
   ```

*  Run gazebo-simulation image and enter the `bash` shell, then launch Gazebo simulation manager
   ```bash
   $ sudo docker run -it -p 5901:5901 -p 6901:6901 gazebo-simulation:latest bash
   ~# ./launch_SM.sh -Dverbosity=2
   ```

*  Update the image in dockerhub and then deploy it using kubernetes

   This can be done using the features provided by the Simulation and Optimization Orchestrator. To deploy the image that you have created you can use the file deployment_gazebo_example.json, changing the name of the container to be deployed in /template/spec/containers/image and the number of containers to be deployed in /spec/replicas. This snippet of code deploys beside the gazebo simulation image, also one service, it is needed to allow to access to the simulator GUI, through VNC. 
   Note 1: the format used is a simplified version of the one used to describe a Kubernetes deployment. If you don't want to use the SOO to deploy the images, but directly work on the Kubernetes API you have to use a different file.
   Note 2: in the example, the containers have a node selector set in /template/spec/containers/nodeSelector, this allow to select the nodes in which it can be installed, for example in this case, it will be installed in all the nodes with the label component:gazebo (the label can be  add with the command: kubectl label node <node_to_be_labeled> component=gazebo). If nodeSelector is not used the image will be installed in one of the nodes of the cluster with enough resources to run it.
