package com.z.transformer.mr.etl;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.util.Tool;

import com.z.transformer.common.EventLogConstants;
import com.z.transformer.common.GlobalConstants;
import com.z.transformer.util.TimeUtil;

public class AnalysisDataRunner1 implements Tool {
     private Configuration conf=null;
	@Override
	public Configuration getConf() {
		// TODO Auto-generated method stub
		return this.conf;
		
	}

	@Override
	public void setConf(Configuration conf) {
		// TODO Auto-generated method stub
		this.conf=HBaseConfiguration.create(conf);
	}

	@Override
	public int run(String[] args) throws Exception {
		Configuration conf=this.getConf();
		this.processArgs(conf, args);
		Job job =Job.getInstance(conf,"Event-ETL");
		job.setJarByClass(AnalysisDataRunner1.class);
		job.setMapperClass(AnalysisDataMapper1.class);
		job.setMapOutputKeyClass(NullWritable.class);
		job.setMapOutputValueClass(Put.class);
		job.setNumReduceTasks(0);
		this.initJobInputPath(job);
		   initHBaseOutPutConfig(job);
		
		
		
		
		
		return job.waitForCompletion(true)?0:1;
	}
	private void initHBaseOutPutConfig(Job job) throws IOException {
		Configuration conf=this.getConf();
		String date=conf.get(GlobalConstants.RUNNING_DATE_PARAMES);
		String tableNameSuffix=TimeUtil.parseLong2String(TimeUtil.parseNginxServerTime2Long(date));
		String tablename=EventLogConstants.HBASE_NAME_EVENT_LOGS+tableNameSuffix;
		TableMapReduceUtil.initTableReducerJob(tablename, null, job);
	    HBaseAdmin admin=null;
	    admin=new HBaseAdmin(conf);
	    TableName tn=TableName.valueOf(tablename);
	    HTableDescriptor htd=new HTableDescriptor(tn);
	    
	    htd.addFamily(new HColumnDescriptor(EventLogConstants.EVENT_LOGS_FAMILY_NAME));
	    if (admin.tableExists(tn)) {
			if(admin.isTableEnabled(tn)) {
				admin.disableTable(tn);
			}
			admin.deleteTable(tn);
		}
	/*   byte[][] keySplits=new byte[3][];
	   keySplits[0]=Bytes.toBytes("1"); 
	   keySplits[0]=Bytes.toBytes("2");
	   keySplits[0]=Bytes.toBytes("3");
	   admin.createTableAsync(htd,keySplits);*/
	   
	   admin.createTable(htd);
	   admin.close();
	   
	}
	private void initJobInputPath(Job job) throws IOException {
		Configuration conf=this.getConf();
		String date =conf.get(GlobalConstants.RUNNING_DATE_PARAMES);
		String hdfspath=TimeUtil.parseLong2String(TimeUtil.parseString2Long(date,"yyyy/MM/dd"));
		if(GlobalConstants.HDFS_LOGS_PATH_PREFIX.endsWith("/")) {
			hdfspath=GlobalConstants.HDFS_LOGS_PATH_PREFIX+hdfspath;
			
		}else {
			hdfspath=GlobalConstants.HDFS_LOGS_PATH_PREFIX+File.pathSeparator+hdfspath;
		}
		 FileSystem fs=FileSystem.get(conf);
		 Path inPath=new Path(hdfspath);
			if(fs.exists(inPath)) {
				FileInputFormat.addInputPath(job, inPath);
				
			}else {
				throw new RuntimeException("HDFS can not found this path "+hdfspath);
			}	 
	}
	/**
	 * bin/yarn jar ETL.jar
	 * @param args
	 */
	private void processArgs(Configuration conf, String[] args) {
		String date=null;
		for(int i=0;i<args.length;i++) {
			if(args[i].equals("-date")) {
				date=args[i+1];
			}
			
		}
		if(StringUtils.isNotBlank(date)) {
			date=TimeUtil.getYesterday();
		}
		 conf.set(GlobalConstants.RUNNING_DATE_PARAMES, date);
		
	}
 
}
