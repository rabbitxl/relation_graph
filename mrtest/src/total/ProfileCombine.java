package total;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.math.NumberUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import util.SimpleStringTokenizer;


public class ProfileCombine extends Configured implements Tool {
	// 默认分隔符
	private final static String FIELD_SEPERATOR = "\001";
	// from
	private static int FROM_INTERACTIVE = 1;
	private static int FROM_PROFILE = 2;
	private static int fromtype = 0;
	
	public static class MapClass extends Mapper<Text, Text, Text, Text> {
		private final static int FROM_USER_ID = 0;
		private final static int TYPE_INDEX = 1;
		private final static int TO_USER_ID = 2;
		private final static int COUNT_INDEX = 3;
		private final static int SCORE_INDEX = 4;
		private final static int VALUE_INDEX = 5;
		private static Map<String, Double> weightMap = null;
		// 总数,目前写死
		private static double total = 6.0;
		
		@Override
		public void map(Text key, Text value, Context context)
				throws IOException ,InterruptedException {
			if(fromtype == FROM_INTERACTIVE){
				List<String> fields = new SimpleStringTokenizer(value.toString(), FIELD_SEPERATOR).getAllElements();
				if(NumberUtils.toLong(fields.get(0), 0) == 0)
					return;
				if(NumberUtils.toLong(fields.get(1), 0) == 0)
					return;
				
				double score = NumberUtils.toDouble(fields.get(3),0)/2;
				context.write(new Text(fields.get(0)+"_"+fields.get(1)),
						new Text(fields.get(2)+FIELD_SEPERATOR+score+FIELD_SEPERATOR+FROM_INTERACTIVE));
			}
			else if(fromtype == FROM_PROFILE){
				List<String> fields = new SimpleStringTokenizer(value.toString(), FIELD_SEPERATOR).getAllElements();
				if(NumberUtils.toLong(fields.get(FROM_USER_ID), 0) == 0)
					return;
				if(NumberUtils.toLong(fields.get(TO_USER_ID), 0) == 0)
					return;
				
				double score = NumberUtils.toDouble(fields.get(SCORE_INDEX),0);
				score *= 1/total;
				
				context.write(new Text(fields.get(FROM_USER_ID)+"_"+fields.get(TO_USER_ID)),
						new Text(fields.get(TYPE_INDEX)+FIELD_SEPERATOR+score+FIELD_SEPERATOR+FROM_PROFILE));
			}
		}
		
		protected void setup(Mapper<Text,Text,Text,Text>.Context context)
				throws IOException ,InterruptedException {
			FileSplit split = (FileSplit) context.getInputSplit();
			String path = split.getPath().toUri().getPath();
			if(path.indexOf("interactive") != -1){
				fromtype = FROM_INTERACTIVE;
			}
			else
				fromtype = FROM_PROFILE;

		};
	}
	
	public static class Reduce extends Reducer<Text, Text, Text, Text> {
		private final static int TYPE_INDEX = 0;
		private final static int SCORE = 1;
		private final static int LOCALTYPE_INDEX = 2;
		
		@Override
		public void reduce(Text key, Iterable<Text> values, Context context) 
				throws IOException ,InterruptedException {
			Iterator<Text> itor = values.iterator();
			int combineType = 0;
			double score = 0.0;
			while (itor.hasNext()) {
				List<String> fields = new SimpleStringTokenizer(itor.next().toString(), FIELD_SEPERATOR).getAllElements();
				int type = NumberUtils.toInt(fields.get(TYPE_INDEX), 0);
				if(type == 0)
					continue;
				int localtype = NumberUtils.toInt(fields.get(LOCALTYPE_INDEX), 0);
				if(localtype == FROM_INTERACTIVE){
					combineType |= type;
				}else if(localtype == FROM_PROFILE){
					combineType |= (1<<(type-1));
				}
				else
					continue;

				score += NumberUtils.toDouble(fields.get(SCORE), 0);
			}
			
			String fromUid = "";
			String toUid = "";
			String uids[] = key.toString().split("_");
			fromUid = uids[0];
			toUid = uids[1];
			if(combineType == 0)
				return;
			
			context.write(new Text(),new Text(fromUid+FIELD_SEPERATOR
					+toUid +FIELD_SEPERATOR
					+combineType +FIELD_SEPERATOR
					+score));
			
			context.progress();
		}
	}
	
	@Override
	public int run(String[] args) throws Exception {
		Job job = new Job(getConf());
		job.setJarByClass(ProfileCombine.class);
		job.setJobName("combine all profile data");
		job.setNumReduceTasks(400);
		job.getConfiguration().set("mapred.child.java.opts","-Xmx1024m");
		job.getConfiguration().set("mapred.job.queue.name", "cug-taobao-sns");

		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);

		job.setMapperClass(MapClass.class);
		job.setReducerClass(Reduce.class);

		job.setInputFormatClass(SequenceFileInputFormat.class);
		SequenceFileOutputFormat.setOutputCompressionType(job, SequenceFile.CompressionType.BLOCK);
		job.setOutputFormatClass(SequenceFileOutputFormat.class);

		// 由于zeus的时间参数不能正常运作,所以这里我们自己指定时间.
		// 替换 参数中的yyyyMMdd
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH)-1);
		SimpleDateFormat s = new SimpleDateFormat("yyyyMMdd");
		String datepath = s.format(cal.getTime());

		// 合并所有的profile的所有数据类型
		for(int i=0; i<args.length-1; i++){
			String path = args[i].replaceAll("yyyyMMdd", datepath);
			FileInputFormat.addInputPath(job, new Path(path));
			System.out.println(path);
		}
		
		// 最后一个是输出路径
		String outpath = args[args.length-1].replaceAll("yyyyMMdd", datepath);
		FileOutputFormat.setOutputPath(job, new Path(outpath));

		boolean success = job.waitForCompletion(true);
		return success ? 0 : 1;
	}
	
	public static void main(String[] args) throws Exception {
		int status = ToolRunner.run(new Configuration(), new ProfileCombine(), args);
		System.exit(status);
	}

}
