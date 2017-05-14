#!/bin/bash
SOURCE_DIR=$1
DESTINATION_DIR=$2

for ((i=1;i<=10;i++)); do
  echo "Thread Count: ${i}";
  java -jar target/ThumbnailMaker.jar --source $SOURCE_DIR --output $DESTINATION_DIR --thread ${i} |  grep "^Total.*" | awk '{print $11}' >> /var/tmp/times.log 
done
