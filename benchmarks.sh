#!/bin/bash

for ((i=1;i<=10;i++)); do
  echo "Thread Count: ${i}";
  java -jar target/ThumbnailMaker.jar --source /Users/saurav/Desktop/ --output /Users/saurav/Desktop/Personal_Projects/thumbnails/ --thread ${i} |  grep "^Total.*" | awk '{print $11}' >> /var/tmp/times.log 
done
