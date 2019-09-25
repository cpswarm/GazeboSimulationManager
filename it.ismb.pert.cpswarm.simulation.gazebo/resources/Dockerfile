FROM cpswarm/gazebo-em-ex-deps:1.0.17

#ENV TURTLEBOT_3D_SENSOR no3d
#ENV TURTLEBOT_TOP_PLATE_DEVICE rplidar

COPY . $HOME

RUN chmod +x $HOME/startup.sh
RUN chmod +x $HOME/ws/costmap_clear.sh

RUN mkdir cpswarm

WORKDIR $HOME/ws/

RUN catkin init --workspace .

RUN chmod +x ros.sh \
&& /bin/bash ros.sh

RUN echo "source $HOME/ws/devel/setup.bash" >> ~/.bashrc \
		  && echo "source $HOME/ws/global_variables.env" >> ~/.bashrc \
		  && /bin/bash -c "source ~/.bashrc"

WORKDIR $HOME

ENV JAVA_HOME /usr/lib/jvm/java-1.8.0-openjdk-amd64/

RUN keytool -noprompt -importcert -trustcacerts \
    -file pert-demoenergy-virtus.ismb.polito.it.pem -alias pert-demoenergy-virtus.ismb.polito.it \
    -storepass changeit -keystore -J-Duser.language=en $JAVA_HOME/jre/lib/security/cacerts

ENTRYPOINT ["/root/dockerstartup/vnc_startup.sh"]
CMD ["--wait"]


