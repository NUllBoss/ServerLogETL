package com.z.transformer.mr.etl;

import java.io.IOException;
import java.util.Map;
import java.util.zip.CRC32;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.log4j.Logger;
import com.z.transformer.common.EventLogConstants;
import com.z.transformer.common.EventLogConstants.EventEnum;
import com.z.transformer.util.LoggerUtil;


public class AnalysisDataMapper1  extends Mapper<Object, Text, NullWritable, Put>{
	  private static final Logger logger=Logger.getLogger(AnalysisDataMapper1.class);
	 private CRC32 crc32=null;
	   private byte[] Family=null;
	   private long currentDayInMIlls=-1;
	@Override
	protected void setup(Mapper<Object, Text, NullWritable, Put>.Context context)
			throws IOException, InterruptedException {
	      crc32=new CRC32();
	      this.Family=EventLogConstants.BYTES_EVENT_LOGS_FAMILY_NAME;
	}
	@Override
	protected void map(Object key, Text value,  Context context)
			throws IOException, InterruptedException {
		Map<String, String> clientInfo = LoggerUtil.handleLogText(value.toString());
		
		if(clientInfo.isEmpty()) {
			logger.debug("get log faile "+ value.toString());
			return;
			
		}
		
		EventEnum event=EventEnum.valueOfAlias(clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_EVENT_NAME));
		if(event==null) {
			   logger.debug("can not parse this event "+clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_EVENT_NAME));
		}else {
			handleEventData(clientInfo,event,context,value);
		}
		
		
	}
    /**
     * 
     * @param clientInfo
     * @param event
     * @param context
     * @param value
     */
	public void handleEventData(Map<String, String> clientInfo, EventEnum event, Context context, Text value) {
		if(filterEventData(clientInfo, event)) {
			
		}else {
			logger.debug("faile"+value.toString());
		}
				
	}
	public boolean filterEventData(Map<String, String> clientInfo, EventEnum event) {
		boolean result=StringUtils.isNotBlank(clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_SERVER_TIME))
				&& StringUtils.isNotBlank(clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_PLATFORM));
		switch (clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_PLATFORM)) {
		case EventLogConstants.PlatformNameConstants.JAVA_SERVER_SDK:
			  result=result&&StringUtils.isNotBlank(clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_MEMBER_ID));
		      switch (event) {
			case CHARGEREFUND:
				//tui kuan event
				break;
			case CHARGESUCCESS:
				result=result&&StringUtils.isNotBlank(clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_ORDER_ID));
				
				break;

			default:
				logger.debug("can not deal this event "+clientInfo);
				result=false;
				break;
			}
			  break;
		case EventLogConstants.PlatformNameConstants.PC_WEBSITE_SDK:
			switch (event) {
			case CHARGEREQUEST:
				result=result&&StringUtils.isNotBlank(clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_ORDER_ID))
				&& StringUtils.isNotBlank(clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_CURRENT_URL))&&
				StringUtils.isNotBlank(clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_ORDER_CURRENCY_TYPE))&&
				StringUtils.isNotBlank(clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_ORDER_PAYMENT_TYPE))&&
				StringUtils.isNotBlank(clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_ORDER_CURRENCY_AMOUNT));
				break;
			case EVENT:
				result=result&&StringUtils.isNotBlank(clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_EVENT_CATEGORY))&&
						StringUtils.isNotBlank(clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_EVENT_ACTION));
				break;
			case LAUNCH:
			
				break;
			case PAGEVIEW:
				result=result&&StringUtils.isNotBlank(clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_CURRENT_URL));
				
				break;
				

			default:
				logger.debug("can not deal this event"+clientInfo);
				result=false;
				break;
			}
			break;

		default:
			   result=false;
			   logger.debug("can not confirm message source"+clientInfo);
			break;
		}
		
		return result;
	}
	public void outPutData(Map<String, String> clientInfo, Context context) throws IOException, InterruptedException {
		String uuid=clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_UUID);
		long servertime = Long.valueOf(clientInfo.get(EventLogConstants.LOG_COLUMN_NAME_SERVER_TIME))  ;
		clientInfo.remove(EventLogConstants.LOG_COLUMN_NAME_USER_AGENT);
		byte[] rowKey=generateRowKey(uuid, servertime, clientInfo);
		Put put =new Put(rowKey);
		for(Map.Entry<String, String> entry: clientInfo.entrySet()) {
			if(StringUtils.isNotBlank(entry.getKey())||StringUtils.isNotBlank(entry.getValue())){
				put.add(Family, Bytes.toBytes(entry.getKey()),Bytes.toBytes(entry.getValue()) );
				
				
			}
		}
		context.write(NullWritable.get(), put);
	}
	public byte[] generateRowKey(String uuid,long servertime,Map<String, String> clientInfo) {
		crc32.reset();
		byte[] timeBytes=Bytes.toBytes(servertime-this.currentDayInMIlls);
		if(StringUtils.isNotBlank(uuid)) {
			this.crc32.update(Bytes.toBytes(uuid));
			
		}
		this.crc32.update(Bytes.toBytes(clientInfo.hashCode()));
		byte[] uuidMapDataBytes =Bytes.toBytes(this.crc32.getValue());
		byte [] buffer =new byte[timeBytes.length+uuidMapDataBytes.length];
		
		System.arraycopy(timeBytes, 0, buffer, 0, timeBytes.length);
		System.arraycopy(uuidMapDataBytes, 0, buffer, timeBytes.length, uuidMapDataBytes.length);
		return buffer;
		
	
	}

}
