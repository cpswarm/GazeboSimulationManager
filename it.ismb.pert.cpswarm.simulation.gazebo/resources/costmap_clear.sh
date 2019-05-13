#!/bin/bash
while true 
do
sleep 1s
echo "Clearing costmaps..."
rosservice call /robot_0/move_base/clear_costmaps
rosservice call /robot_1/move_base/clear_costmaps
rosservice call /robot_2/move_base/clear_costmaps
done


