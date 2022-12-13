#!/usr/bin/env bash

hadoop jar /usr/hdp/current/hadoop-mapreduce-client/hadoop-streaming-*.jar \
-archives hdfs://sandbox-hdp.hortonworks.com:8020/user/maria_dev/input/ncdc.jar \
-files hdfs://sandbox-hdp.hortonworks.com:8020/user/maria_dev/input/concatenate_ncdc_data.sh#concatenate_ncdc_data.sh \
-D mapred.reduce.tasks=0 \
-D mapred.map.tasks.speculative.execution=false \
-input /user/maria_dev/input/file_names.txt \
-inputformat org.apache.hadoop.mapred.lib.NLineInputFormat \
-output /user/maria_dev/output \
-mapper concatenate_ncdc_data.sh