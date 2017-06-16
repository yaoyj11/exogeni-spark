#!/bin/sh
sudo docker run -i -t  -d -p 4040:4040 -p 6066:6066 -p 7077:7077 -p 8080:8080 -p 8081:8081   -h sparkserver --name sparkserver sparkimg
