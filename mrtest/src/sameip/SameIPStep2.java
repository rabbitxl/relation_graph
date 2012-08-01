package sameip;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.math.NumberUtils;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Reducer.Context;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import samecompany.Employee;
import util.SimpleStringTokenizer;

public class SameIPStep2 extends Configured implements Tool {
	private final static double Wt = 2.0;
	private final static double Wk = 1-(double)23808245888.0/(double)32792266871.0;
	private final static int TO_USER_ID = 0;
	private final static String TYPE = "14";
	// Ĭ�Ϸָ���
	private final static String FIELD_SEPERATOR = "\001";
	
	public static class MapClass extends Mapper<Text, Text, Text, Text> {
		
		/**
		 * ��һ����������
		 * fromUserId	toUserId	sameip
		 *
		 */
		protected void map(Text key, Text value, Context context) 
				throws IOException ,InterruptedException {
			SimpleStringTokenizer simpleStringTokenizer = new SimpleStringTokenizer(value.toString(), "\t", 2);
			List<String> fields = simpleStringTokenizer.getAllElements();
			context.write(key, value);
		};
	}

	public static final class CombinerReduce extends
			Reducer<Text, Text, Text, Text> {
//		private final int ONEMAP_SAMEIP_MAX = 1000;

		protected void reduce(Text key, Iterable<Text> values,
				Context context) throws IOException, InterruptedException {
			Iterator<Text> it = values.iterator();
			Map<String, Integer> map = new HashMap<String, Integer>(500);
			int count = 0;
			while (it.hasNext()) {
//				if (count++ > ONEMAP_SAMEIP_MAX)
//					return;
				SimpleStringTokenizer simpleStringTokenizer = new SimpleStringTokenizer(it.next().toString(), "\t", 2);
				List<String> fields = simpleStringTokenizer.getAllElements();
				String hashkey = key+"_"+fields.get(0);
				if(map.containsKey(hashkey)){
					map.put(hashkey, map.get(hashkey)+1);
				}
				else
					map.put(key+"_"+fields.get(0), 1);
			}
			for(String uidkey : map.keySet()){
				String keys[] = uidkey.split("_");
				
				context.write(key, new Text(keys[1]+"\t"+map.get(uidkey)));
			}
		}
	}
	
	public static class Reduce extends Reducer<Text, Text, Text, Text> {
		private static final int ONE_REDUCE_MAX = 1000;
		
		protected void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException ,InterruptedException {
			Map<String, Integer> map = new HashMap<String, Integer>(500);
			Iterator<Text> itor = values.iterator();
			while (itor.hasNext()) {
				SimpleStringTokenizer simpleStringTokenizer = new SimpleStringTokenizer(itor.next().toString(), "\t", 2);
				List<String> fields = simpleStringTokenizer.getAllElements();
				Long toUid = NumberUtils.toLong(fields.get(TO_USER_ID), 0);
				if(toUid == 0)
					continue;
				
				if(map.size() > ONE_REDUCE_MAX)
					break;
				
				String hashkey = key+"_"+fields.get(TO_USER_ID);
				if(map.containsKey(hashkey)){
					map.put(hashkey, map.get(hashkey)+1);
				}
				else
					map.put(key+"_"+fields.get(0), 1);
			}
			for(String uidkey : map.keySet()){
				String keys[] = uidkey.split("_");
				double score = 0.0;
				int sonWeight = map.get(uidkey);
				double degreeWeight = NumberUtils.toDouble(context.getConfiguration().get("degreeWeight"), Wt);
				double distributeParam = (1-NumberUtils.toDouble(context.getConfiguration().get("distributeParam"), Wk));
				score = Math.sqrt(sonWeight)*degreeWeight*distributeParam/20;

				// ����Ŀǰob����汾֮ǰ����double�ڲ�ͬ�İ汾�ϲ�һ�µ�����.
				// ����socre�ȳ���һ������Ȼ����int����
				int mscore = (int)(score * 100000);
				context.write(new Text(), new Text(key +FIELD_SEPERATOR
									+ TYPE +FIELD_SEPERATOR
									+ keys[1] +FIELD_SEPERATOR
									+ sonWeight +FIELD_SEPERATOR
									+ mscore +FIELD_SEPERATOR
									));
			}

		}
	}

	public int run(String[] args) throws Exception {
		Job job = new Job(getConf());
		job.setJarByClass(SameIPStep2.class);
		job.setJobName("same company step 2 ...");
		job.setNumReduceTasks(50);

		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);

		job.setMapperClass(SameIPStep2.MapClass.class);
		job.setCombinerClass(SameIPStep2.CombinerReduce.class);
		job.setReducerClass(SameIPStep2.Reduce.class);

		job.setInputFormatClass(SequenceFileInputFormat.class);
		SequenceFileOutputFormat.setOutputCompressionType(job, SequenceFile.CompressionType.BLOCK);
		job.setOutputFormatClass(SequenceFileOutputFormat.class);

		// ����zeus��ʱ�����������������,�������������Լ�ָ��ʱ��.
		// �滻 �����е�yyyyMMdd
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH)-1);
		SimpleDateFormat s = new SimpleDateFormat("yyyyMMdd");
		String datepath = s.format(cal.getTime());
		String path = args[0].replaceAll("yyyyMMdd", datepath);
		FileInputFormat.setInputPaths(job, new Path(path));
		String outpath = args[1].replaceAll("yyyyMMdd", datepath);
		FileOutputFormat.setOutputPath(job, new Path(outpath));

		job.getConfiguration().set("degreeWeight", args[2]);
		job.getConfiguration().set("distributeParam", args[3]);
		boolean success = job.waitForCompletion(true);
		return success ? 0 : 1;
	}

	public static void main(String[] args) throws Exception {
		int ret = ToolRunner.run(new SameIPStep2(), args);
		System.exit(ret);
	}
}