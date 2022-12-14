#!/usr/bin/env bash

# Hiermit wird der MapReduce-Job zum Zusammenführen des NCDC Datensatzes ausgeführt.
# Das Skript soll nach Verbinden auf die HDP Sandbox per ssh als maria_dev benutzt werden.
# Siehe https://github.com/Resteklicken/Hauptseminar-Hadoop für mehr Informationen.

hadoop jar /usr/hdp/current/hadoop-mapreduce-client/hadoop-streaming-*.jar \
-archives hdfs://sandbox-hdp.hortonworks.com:8020/user/maria_dev/input/ncdc.jar \
-files hdfs://sandbox-hdp.hortonworks.com:8020/user/maria_dev/input/concatenate_ncdc_data.sh#concatenate_ncdc_data.sh \
-D mapred.reduce.tasks=0 \
-D mapred.map.tasks.speculative.execution=false \
-input /user/maria_dev/input/file_names.txt \
-inputformat org.apache.hadoop.mapred.lib.NLineInputFormat \
-output /user/maria_dev/output \
-mapper concatenate_ncdc_data.sh

# hadoop jar weist Hadoop an, ein JAR auszuführen. Dafür wird das Hadoop Streaming Jar ausgewählt, welches mit dem Framework mitgeliefert wird und Eingaben von STDIN entgegen nimmt.
# -archives hdfs://sandbox-hdp.hortonworks.com:8020/user/maria_dev/input/ncdc.jar sorgt dafür, dass Hadoop das im HDFS gespeicherte JAR ncdc.jar in die Laufzeitumgebung des MapReduce Jobs kopiert und automatisch entpackt. Prozesse können während des Jobs unter ncdc.jar auf die darin enthaltenen Dateien (hier die TARs) zugreifen.
# -files hdfs://sandbox-hdp.hortonworks.com:8020/user/maria_dev/input/concatenate_ncdc_data.sh#concatenate_ncdc_data.sh weist Hadoop eigentlich an, die Mapper-Klasse auf alle am Job beteiligten Nodes zu kopieren, damit sie dort lokal zur Verfügung steht. Durch das Präfix hdfs://host:port/ teilt man Hadoop mit, dass die Datei bereits im HDFS liegt. Mit #concatenate_ncdc_data.sh am Ende des Pfades gibt man der Datei einen Alias, damit man in der -mapper Option nicht wieder den vollen Pfad angeben muss.      
# -D überschreibt priorisiert Werte, die bereits in Konfigurationsdateien gesetzt sind.
# mapred.reduce.tasks=0 macht aus diesem Job einen reinen Map-Job ohne Reduce-Phase, da für die Umwandlung der Dateien keine Reduce-Phase nötig ist.
# mapred.map.tasks.speculative.execution=false verhindert die sogenannte spekulative Ausführung. Ist diese Option aktiviert, startet Hadoop manchmal mehrere Jobs für einen InputSplit und filtert in der Shuffle-Phase doppelte Ergebnisse. Sind manche Nodes deutlich langsamer als andere, kann das normalerweise Performancegewinne bringen. In diesem Fall würde das aber dazu führen, dass Dateien doppelt ins HDFS geschrieben würden.
# -input /user/maria_dev/input/file_names.txt gibt den Pfad zur Datei im HDFS an
# -inputformat org.apache.hadoop.mapred.lib.NLineInputFormat gibt die Java Klasse des InputFormats an.
# -output /user/maria_dev/output gibt den Pfad für die Ausgabe an. Es muss sich hierbei um einen Ordner handeln. HDFS arbeitet nach dem "write once, read many times" Prinzip, daher dürfen Ausgabeordner grundsätzlich nicht vorher existieren.
# -mapper /user/maria_dev/input/concatenate_ncdc_data.sh gibt die Mapper-Klasse an. Würde die Java API statt der Streaming API verwendet, stünde hier eine Java Klasse