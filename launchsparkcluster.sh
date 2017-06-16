#!/bin/bash
#run sliceName spark WorkerNum
SCRIPT=$(readlink -f $0)
SCRIPTPATH=`dirname $SCRIPT`
./target/appassembler/bin/SafeSdxExample ~/.ssl/geni-yuanjuny.pem ~/.ssl/geni-yuanjuny.pem "https://geni.renci.org:11443/orca/xmlrpc" $1 $2 $3 "~/.ssh/id_rsa" $SCRIPTPATH/spark.tar.gz  $4
