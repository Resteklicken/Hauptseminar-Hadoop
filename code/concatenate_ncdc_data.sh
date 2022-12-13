#!/usr/bin/env bash

# Dies ist ein Helper-Skript zum Zusammenfügen aller CSV-Dateien in den
# komprimierten Archiven (.tar.gz) der einzelnen Jahre im NCDC Wetter-Datensatz.
# Es basiert auf der Anleitung zum Präparieren des NCDC Datensatzes
# aus White, T. E. (2015). Hadoop: The Definitive Guide (4th edition). O'Reilly Media. 
# Tom White's Code 

# Als Eingabedatei wird eine Textdatei mit den Dateinamen der Archive erwartet:

# cat file_names.txt
# 2016.tar.gz
# 2017.tar.gz
# ...
# 2022.tar.gz

# Für jede Zeile in der Eingabedatei wird von Hadoop ein Map-Prozess gestaret.
# NLineInputFormat gibt jedem Mapper eine einzige Zeile aus der Eingabedatei als Key-Value-Paar als Input.
# Der Key ist der Offset der Zeile zum Dateianfang, an dieser Stelle nicht weiter interessant.
# Der Value ist der Inhalt der Zeile, der hier in die Variable inputfile gelesen wird.
# Nachrichten auf STDERR mit dem Präfix "reporter:status:" werden von Hadoop als MapReduce Statusupdates interpretiert.
# Dadurch denkt Hadoop nicht, der Job hätte sich aufgehängt. 
read offset inputfile
echo "reporter:status:Verarbeite $inputfile" >&2

# basename gibt den Dateinamen ohne den Rest des Pfades und ohne die Dateiendung zurück
target_dir=`basename $inputfile .tar.gz`

# Erstelle für das Jahr ein neues Verzeichnis und entpacke das Archiv dort hin
# Mit der Option -C am ENDE wird NACH dem Entpacken in das Verzeichnis gewechselt 
mkdir -p $target_dir
echo "reporter:status:Entpacke $inputfile nach $target_dir" >&2
tar zxf `basename $inputfile` -C $target_dir

# Füge alle CSV-Dateien im Ordner in einer Datei mit Endung ".complete" zusammen.
echo "report:status:Füge alle Dateien des Jahres $target_dir zusammen" >&2
for file in $target/*
do
    cat $file >> $target.complete
    echo "report:status:Bearbeite $file" >&2
done

# Komprimiere die Datei wieder mit gzip und speichere das Ergebnis im HDFS.
# Durch das Argument "-" nach "-put" wird dabei STDIN als Quelle verwendet.
echo "report:status:Komprimiere Datei und schreibe ins HDFS" >&2
gzip -c $target.complete | $HADOOP_HOME/bin/hdfs dfs -put - processed/$target.gz
echo "report:status:Fertig" >&2

# Die Ausführung des Skripts erfolgt mittels dem folgenden Befehl:

# hadoop jar $HADOOP_HOME/share/hadoop/tools/lib/hadoop-streaming-*.jar \
# -D mapred.reduce.tasks=0 \
# -D mapred.map.tasks.speculative.execution=false \
# -input /user/maria_dev/input/file_names.txt \
# -inputformat org.apache.hadoop.mapred.lib.NLineInputFormat \
# -output /user/maria_dev/output \
# -mapper /user/maria_dev/input/concatenate_ncdc_data.sh \
# -file /user/maria_dev/input/concatenate_ncdc_data.sh

# Hadoop wird angewiesen, ein JAR auszuführen. Dafür wird das Hadoop Streaming Jar ausgewählt,
#   welches mit dem Framework mitgeliefert wird und Eingaben von STDIN eingegen nimmt.
# Die Option -D überschreibt priorisiert Werte, die bereits in Konfigurationsdateien gesetzt sind.
# mapred.reduce.tasks=0 macht aus diesem Job einen reinen Map-Job ohne Reduce-Phase, 
#   da für die Umwandlung der Dateien keine Reduce-Phase nötig ist.
# mapred.map.tasks.speculative.execution=false verhindert die sogenannte spekulative Ausführung.
#   Ist diese Option aktiviert, startet Hadoop manchmal mehrere Jobs für einen InputSplit und filtert
#   in der Shuffle-Phase doppelte Ergebnisse. Sind manche Nodes deutlich langsamer als andere, kann das
#   normalerweise Performancegewinne bringen. In diesem Fall würde das aber dazu führen, dass Dateien doppelt 
#   ins HDFS geschrieben würden.
# -input /user/maria_dev/input/file_names.txt gibt den Pfad zur Datei im HDFS an
# -inputformat org.apache.hadoop.mapred.lib.NLineInputFormat gibt die Java Klasse des InputFormats an.
# -output /user/maria_dev/output gibt den Pfad für die Ausgabe an. Es muss sich hierbei um einen Ordner
#   handeln. HDFS arbeitet nach dem "write once, read many times" Prinzip, daher dürfen Ausgabeordner
#   grundsätzlich nicht vorher existieren.
# -mapper /user/maria_dev/input/concatenate_ncdc_data.sh gibt die Mapper-Klasse an. Würde die Java API statt der
#   Streaming API verwendet, stünde hier eine Java Klasse
# -file /user/maria_dev/input/concatenate_ncdc_data.sh weist Hadoop an, die Mapper-Klasse auf alle am Job beteiligten
#   Nodes zu kopieren, damit sie dort lokal zur Verfügung steht. Man könnte die nötigen Dateien auch händisch auf 
#   allen Nodes im Cluster ablegen und diesen Schritt weglassen. Die HDP Sandbox besteht eh nur aus einem Node, daher
#   ist diese Option nur der Vollständigkeit halber angegeben.