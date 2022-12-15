//Import aller nötigen Klassen des Hadoop Frameworks
import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

//Der Einfachheit halber sind Mapper und Reducer in der Klasse mit der Main-Funktion gebündelt.
//Dies ist der Klassenname, der später im Programmaufruf angegeben wird, damit Hadoop weiß, wo es die Main-Funtkion findet.
public class MaxTemperature {
  //Eine Klasse, die Mapper erweitert, wird erstellt. Dabei müssen vier Datentypen angegeben werden. 
  //Diese Datentypen müssen auf das gewählte InputFormat und die Struktur der Eingabedaten abgestimmt sein.
  //Als InputFormat wird das TextInputFormat verwendet.
  public static class TemperatureMapper
                      //Eingabe-Key, Eingabewert, Ausgabe-Key, Ausgabewert
        extends Mapper<LongWritable, Text, Text, DoubleWritable>{
    //Diese beiden Werte signalisieren ungültige Messwerte im Datensatz
    private final static String invalidReadingOne = "9999.9";
    private final static String invalidReadingTwo = "999.9";
    //Headerzeilen erkennt man an diesem Wort am Zeilenanfang    
    private final static String headerLineStart = new String("\"STATION\"");
    //Die Indizes der Tageshöchsttemperatur und des Datums in der kommagetrennten Eingabezeile
    private final static int temperaturePosition = 20;
    private final static int datePosition = 1;
    //Die Parametertypen der Map-Funktion müssen mit denen der Mapper-Klasse übereinstimmen.
    //Context wird vom Framework bereitgestellt und dient dazu, Schlüssel-Wert-Paare zwischen den einzelnen Phasen weiterzugeben
    //Der Mapper schreibt sein Ergebnis in den Context, der Reducer liest es, verarbeitet es weiter und schreibt sein Ergebnis wieder in den Context.
    //Zu guter letzt wird es in eine Datei im HDFS geschrieben.
    public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
      //Der Key ist beim TextInputFormat der Offset der Zeile zum Dateianfang. Er ist hier nicht weiter wichtig und wird daher ignoriert.
      //Der Value ist der Textinhalt der Zeile und muss von Hadoops Datentyp 'Text' in einen Java String konvertiert werden.
      String line = value.toString();
      //Headerzeilen werden durch einen Vergleich herausgefiltert.
      if (!line.startsWith(headerLineStart))  {
        //Die Zeile wird an den Kommata in einzelne Felder getrennt.
        String[] csvFields = line.split(",");
        //Das Feld mit der Tageshöchsttemperatur wird aus der Zeile extrahiert.
        //Die Eingabewerte sind von "" und teilweise von Whitespace umgeben und werden bereinigt. 
        String temperatureString = csvFields[temperaturePosition].replaceAll("\"","").trim();
        //Ungültige Messwerte werden gefiltert.
        if (!temperatureString.equals(invalidReadingOne) && !temperatureString.equals(invalidReadingTwo))  {
          //Das Datumsfeld (YYYY-MM-DD) wird extrahiert, bereinigt und auf Jahr und Monat gekürzt.
          String monthYear = csvFields[datePosition].replaceAll("\"","").trim().substring(0, 7);
          //Der Temperatur String wird zu einer Kommazahl umgewandelt.
          double tempFahrenheit = Double.parseDouble(temperatureString);
          //Die Temperatur wird von Grad Fahrenheit in Grad Celsius umgerechnet.
          double tempCelsius = (tempFahrenheit - 32.0) / 1.8;
          //Die Temperatur wird auf zwei Nachkommastellen gerundet.
          tempCelsius = (double) Math.round(tempCelsius * 100) / 100;
          //Die Java Datentypen werden wieder Hadoop's serialisierbare Datentypen verpackt.
          Text outKey = new Text(monthYear);
          DoubleWritable outValue = new DoubleWritable(tempCelsius);
          //Das Key Value Pair aus (Jahr-Monat:Temperatur) wird zum Sortieren geschickt.
          context.write(outKey, outValue);}}}}

  //Eine Klasse, die Reducer erweitert, wird erstellt. Dabei müssen vier Datentypen angegeben werden. 
  //Diese Datentypen müssen auf die Ausgaben der Mapper-Klasse abgestimmt sein.
  public static class TemperatureReducer
                        //Eingabe-Key, Eingabewert, Ausgabe-Key, Ausgabewert
        extends Reducer<Text,DoubleWritable,Text,DoubleWritable> {
    //Ein Container für das Endergebnis wird erstellt.
    private DoubleWritable result = new DoubleWritable();
    //Die Parametertypen der Reduce-Funktion müssen mit denen der Reducer-Klasse übereinstimmen.
    //Anders als die Map-Funktion erhält reduce() keinen einzelnen Wert, sondern eine Liste an Werten.
    //Zwischen Map- und Reduce-Phase sortiert das Framework die Outputs aller Mapper nach Keys und übergibt Reducern jeweils die Werte zu einem Key.
    public void reduce(Text key, Iterable<DoubleWritable> values, Context context
                        ) throws IOException, InterruptedException {
      //Eine initiale Höchsttemperatur wird gesetzt.
      double max = -100000.0;
      //Iteriere über alle Werte.
      for (DoubleWritable val : values) {
        //Entpacke Hadoop's Datentyp in eine Kommazahl.
        double nextTemp = val.get();
        //Prüfe, ob ein neuer Maximalwert gefunden wurde.
        if (nextTemp > max) max = nextTemp;
      }
      //Wurden alle Werte verglichen, wird das Key Value Pair aus (Jahr-Monat:Höchsttemperatur) wie im OutputFormat spezifiziert in die Ausgabedatei geschrieben.
      result.set(max);
      context.write(key, result);
  }}
  
  //Die Main-Funktion dient zum setzen der Job-Parameter und zum Starten des Jobs.
  public static void main(String[] args) throws Exception {
    //Erstelle eine neue Job Configuration.
    Configuration conf = new Configuration();
    //Bereite einen Job zur Ausführung vor.
    Job job = Job.getInstance(conf, "max temp");
    //Teile dem Framework mit, welche Klassen als Mapper, Reducer und Vor-Reducer (Combiner) dienen sollen.
    job.setJarByClass(MaxTemperature.class);
    job.setMapperClass(TemperatureMapper.class);
    job.setCombinerClass(TemperatureReducer.class);
    job.setReducerClass(TemperatureReducer.class);
    //Bestimme die Datentypen des Job Outputs für den RecordWriter. Anhand dessen werden die Werte in die Ausgabedatei geschrieben.
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(DoubleWritable.class);
    //Lese Eingabe- und Ausgabepfad von den Parametern des Funktionsaufrufs.
    FileInputFormat.addInputPath(job, new Path(args[0]));
    FileOutputFormat.setOutputPath(job, new Path(args[1]));
    //Übergib den Job an YARN und warte auf dessen Abschluss.
    System.exit(job.waitForCompletion(true) ? 0 : 1);
  }
}