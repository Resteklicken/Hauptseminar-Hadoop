import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class MaxTemperature {

    public static class WeatherMapper
         extends Mapper<LongWritable, Text, Text, FloatWritable>{

      private final static String invalidReading = "9999.9";
      private final static String headerLineStart = new String("\"STATION\"");
      private final static int temperaturePosition = 20;
      private final static int datePosition = 20;
  
      public void map(LongWritable key, Text value, Context context
                      ) throws IOException, InterruptedException {
        String line = value.toString();
        if (!line.startsWith(headerLineStart))  {
          String[] csvFields = line.split(",");
          if (!(csvFields[temperaturePosition].equals(invalidReading)))  {
            String date = csvFields[datePosition];
            double temperature = Double.parseDouble(csvFields[temperaturePosition]);

            Text outKey = new Text(date);
            FloatWritable outValue = new FloatWritable(temperature);
            context.write(outKey, outValue);
          }
        }
        
      }
    }
  
    public static class MonthReducer
         extends Reducer<Text,FloatWritable,Text,FloatWritable> {
      private FloatWritable result = new FloatWritable();
  
      public void reduce(Text key, Iterable<IntWritable> values,
                         Context context
                         ) throws IOException, InterruptedException {
        int sum = 0;
        for (IntWritable val : values) {
          sum += val.get();
        }
        result.set(sum);
        context.write(key, result);
      }
    }
  
    public static void main(String[] args) throws Exception {
      Configuration conf = new Configuration();
      Job job = Job.getInstance(conf, "max temp");
      job.setJarByClass(WordCount.class);
      job.setMapperClass(TokenizerMapper.class);
      job.setCombinerClass(IntSumReducer.class);
      job.setReducerClass(IntSumReducer.class);
      job.setOutputKeyClass(Text.class);
      job.setOutputValueClass(IntWritable.class);
      FileInputFormat.addInputPath(job, new Path(args[0]));
      FileOutputFormat.setOutputPath(job, new Path(args[1]));
      System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
  }