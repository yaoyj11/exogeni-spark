from debian:jessie

RUN apt-get update && apt-get install -y build-essential apt-utils
RUN apt-get install -y gdb
RUN apt-get install -y openjdk-7-jdk
RUN apt-get install -y git curl make htop vim

RUN apt-get install -y libssl1.0.0
RUN apt-get install -y libpam0g-dev
RUN apt-get install -y libssl-dev
RUN apt-get install -y ant

COPY spark-2.1.1  /root/spark-2.1.1
COPY master.sh /root/master.sh
COPY worker.sh /root/worker.sh
EXPOSE 7001 7002 7003 7004 7005 7006 7077 6066 7012 7013 7014 7015 7016 8881
#ADD a.out /opt/

ENTRYPOINT ["/bin/sh", "-c"]
CMD    /usr/sbin/sshd -D

