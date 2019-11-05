#!/bin/bash
#export example_variable_1=v1  # if needed, please define variables here to ensure all instruction executed by one process
#export example_variable_2=v2
source /opt/ros/kinetic/setup.bash
cd $HOME/ws/
catkin build
