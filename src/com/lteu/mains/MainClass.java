package com.lteu.mains;

import java.util.List;

import com.lteu.deliverable.*;
import com.lteu.services.ServicesLTE;

import com.iitm.wcn.wifi.entities.AccessPoint;
import com.iitm.wcn.wifi.entities.UserEquipment;
import com.iitm.wcn.wifi.params.Params;
import com.iitm.wcn.wifi.services.Services;
import com.lteu.deliverable.LteuQlearning;

public class MainClass {

	/**
	 * @param args
	 */
	static List<BaseStation> bts;
	static List<UserEquipmentLTE> ue;
	private static List<AccessPoint> apList;
	private static List<UserEquipment> ueList;
	
	public static void main(String[] args) {
		
		LteuQlearning lteu = new LteuQlearning();
		Services services = new Services();
		ServicesLTE servicesLTE  = new ServicesLTE();
		/* Initialization of WiFi simulation environment */
		apList = Services.createAPsNew();
		ueList = Services.createUsers(apList);
		/* Association of users to APs */
		services.associateUsersToAPs(ueList, apList);
		/* Initialization of LTE simulation environment */
		bts = servicesLTE.CreateBS(apList);
		ue = servicesLTE.CreateUE(bts, apList);
		
		double scalingFactor = Params.NO_OF_AP;
		
		if(scalingFactor == 0) {
			scalingFactor = 1;
		}
		
		double avgSINR[] = new double[ParamsLTE.NUM_BASE_STATIONS];
		//double avgUserAssoc[] = new double[ParamsLTE.NUM_BASE_STATIONS];
		for(int j=0; j<ParamsLTE.TRIALS; j++){
			for(int i=0; i<ParamsLTE.NUM_BASE_STATIONS; i++){
				avgSINR[i] = avgSINR[i] + bts.get(i).averageSINR(); // taking avg value over all bts
			}
		}
		for(int j=0; j<ParamsLTE.NUM_BASE_STATIONS; j++){
			avgSINR[j] = avgSINR[j]/ParamsLTE.TRIALS;
		}
		/* end of initialization */
		/* simulation */
		/* simulation runs in steps of SIFS, because SIFS is the smallest unit */
 		
		for(BaseStation bs:bts) {
			List<AccessPoint> ap = bs.getAccessPoint();
			bs.initLTEU();
			int timeLTEU = bs.LTEUTimeSlot();
			double cost;   
			boolean initFlag = true;
			for(long time = 0; time < Params.SIM_DURATION;) {
				for(int slotPercent=0; slotPercent<=ParamsLTE.DUTY_CYCLE_SPLIT; slotPercent+=Params.SIFS, time +=Params.SIFS)
				{
					//0.2*ParamsLTE.DUTY_CYCLE 
					
					if(slotPercent <= timeLTEU*ParamsLTE.DUTY_CYCLE) {
						int userDataRateReq[] = new int [] {0, 0, 0};
						int totalData = 0;
						
						if(initFlag) {
							bs.initCLAA();
							initFlag = false;
						}
						for(AccessPoint accpoint : ap) {
							accpoint.setChannelAsBusy();
						}
						int timeLTE = Params.SIFS * ParamsLTE.SUBFRAME_DUR;
						int totalRB = ParamsLTE.RB;
						for(AccessPoint accpoint : ap) {
							if(accpoint.getTxStartTime() == time) {
								accpoint.setTxStartTime(time + accpoint.getBackoffTime());
								accpoint.putInBackoffMode();
				                accpoint.updateBackoffTime();
							} else if(accpoint.isInBackoffMode()) {
								/* if the channel is busy then pause(increment) the backoff timer */
								if(accpoint.isChannelBusy()) {
									accpoint.setTxStartTime(accpoint.getTxStartTime() + Params.SIFS);
									/* reset the channel idletimer */
									accpoint.getChannel().resetIdleTimer();
								}
								else {
									/* if the channel is idle for DIFS then resume(stop incrementing) the backoff timer */
									accpoint.getChannel().updateIdleTimer(Params.SIFS);
									if( accpoint.getChannel().getIdleTimer() < Params.DIFS ) {
										accpoint.setTxStartTime( accpoint.getTxStartTime() + Params.SIFS );
									}
								}
							}
						}
						if(slotPercent % ParamsLTE.DUTY_CYCLE == 0 && slotPercent !=0) {
							
							for(UserEquipmentLTE ue: bs.getUsersAssociated()) {
								double sinr = 0.0;
								for(int s=0;s<ParamsLTE.SINR_CQI.size()-1;s++) {
									if(ParamsLTE.SINR_CQI.get(s) == ue.getSINR()) {
										sinr = ParamsLTE.SINR_CQI.get(s);
										break;
									} else if(ParamsLTE.SINR_CQI.get(0) > ue.getSINR()) {
										sinr = ParamsLTE.SINR_CQI.get(0);
										break;
									} else if (ParamsLTE.SINR_CQI.get(s) < ue.getSINR() && ParamsLTE.SINR_CQI.get(s+1) > ue.getSINR()) {
										sinr = ParamsLTE.SINR_CQI.get(s);
										break;
									} else if(ParamsLTE.SINR_CQI.get(ParamsLTE.SINR_CQI.size()-1) > ue.getSINR()) {
										sinr = ParamsLTE.SINR_CQI.get(ParamsLTE.SINR_CQI.size()-1);
										break;
									}
								}
								totalData = ParamsLTE.CQI_MCS.get(ParamsLTE.SINR_CQI.indexOf(sinr)) * ParamsLTE.RE; 
								
								if(ue.getDataRequest() == ParamsLTE.DATARATE[2]) {
									userDataRateReq[2]++;
								} else if(ue.getDataRequest() == ParamsLTE.DATARATE[1]) {
									userDataRateReq[1]++;
								} else {
									userDataRateReq[0]++;
								}
							}
							
							int totalReq = ParamsLTE.DATARATE[0] * userDataRateReq[0] + 
										ParamsLTE.DATARATE[1] * userDataRateReq[1] + 
										ParamsLTE.DATARATE[2] * userDataRateReq[2];
							int totalDataAvail = totalData * totalRB / 1024;
							
							for(UserEquipmentLTE ue: bs.getUsersAssociated()) {
								if(ue.getDataRequest() == ParamsLTE.DATARATE[2]) {
									double data = ParamsLTE.DATARATE[2]*totalDataAvail / totalReq;
									double ratio = ParamsLTE.DATARATE[2]/totalReq;
									ue.setDataRec(data);
								} else if(ue.getDataRequest() == ParamsLTE.DATARATE[1]) { 
									double data = ParamsLTE.DATARATE[1]*totalDataAvail / totalReq;
									double ratio = ParamsLTE.DATARATE[1]/totalReq;
									ue.setDataRec(data);
								} else {
									double data = ParamsLTE.DATARATE[0]*totalDataAvail / totalReq;
									double ratio = ParamsLTE.DATARATE[0]/totalReq;
									ue.setDataRec(data);
								}
								bs.updateCLAA(totalData*totalRB/slotPercent); //(8 * 1024 * 1024)); ParamsLTE.DUTY_CYCLE
								ue.setSatisfaction();							
							}
							
							if(slotPercent == timeLTEU*ParamsLTE.DUTY_CYCLE) {
								cost = bs.calculateCost();
								bs.setNextState(bs.nextState());
								bs.minAction();
								bs.update(bs.getCurrState(), timeLTEU, cost);
								bs.updateCurrState();
							}	
						}
					} else {
						initFlag = true;
						for(AccessPoint accpoint : ap) {
							accpoint.setChannelAsFree();
							/* if this accpoint is scheduled to start at this time */
							if(accpoint.getTxStartTime() == time) {
								/* check whether the channel is busy */
								if(accpoint.waitedDIFS() == false) {
									/* if channel not busy wait for DIFS time */
									accpoint.setTxStartTime( time + Params.DIFS);
									accpoint.waitForDIFS();						
								} else {
									/* lock the channel */
									accpoint.setChannelAsBusy();
									/* send data */
					            }
					        }
							/* otherwise check whether the station is in backoff mode */
							else if(accpoint.isInBackoffMode()) {
								/* if the channel is busy then pause(increment) the backoff timer */
								if(accpoint.isChannelBusy()) {
									accpoint.setTxStartTime(accpoint.getTxStartTime() + Params.SIFS);
									/* reset the channel idletimer */
									accpoint.getChannel().resetIdleTimer();
								} else {
									/* if the channel is idle for DIFS then resume(stop incrementing) the backoff timer */
							   		accpoint.getChannel().updateIdleTimer(Params.SIFS);
									if( accpoint.getChannel().getIdleTimer() < Params.DIFS ) {
										accpoint.setTxStartTime( accpoint.getTxStartTime() + Params.SIFS );
									}
								}
							}
							
							
							/* set the channel free after the data transmission is completed */
					        if( accpoint.getTxStartTime() + accpoint.getTxDuration() + Params.SIFS == time) {
					        	for(UserEquipment ue :accpoint.getAssociatedUEList()) {
					        		ue.updateThroughput(accpoint.getTxDuration());
					        	}
					        	accpoint.setAsCompleted(time);
					        	accpoint.setChannelAsFree();
					        	// new schedule
					    		// services.printAPSchedule(apList);
					        }
					        if(slotPercent==ParamsLTE.DUTY_CYCLE_SPLIT && accpoint.getTxStartTime() + accpoint.getTxDuration() + Params.SIFS < time) {
				        		for(UserEquipment ue :accpoint.getAssociatedUEList()) {
					        		ue.updateThroughput(time - accpoint.getTxStartTime());
					        	}
					        	
					        	accpoint.setRemaining(time, accpoint.getTxDuration() - time + accpoint.getTxStartTime());
					        	accpoint.setChannelAsFree();
					        	// new schedule
					    		// services.printAPSchedule(apList);
					        }
						}
					}
				}
				initFlag = true;
			}
		}
		double a = btsThroughput()/1024;
		double b = wifiThroughput();
		
		System.out.println("Avg thruput: " + a + " wifi throughput: " + b + " LTE user satisfaction: " + btsSatisfaction() + 
				" jain fairness: " + jainFairness(a/(ParamsLTE.TARGET_DATA_REQ/1024), b/Params.TARGET_DATA_REQ));
	}
	
	static double jainFairness(double a, double b) {
		return Math.pow(a+b,2)/(2*(a*a + b*b));
	}
	
	static double wifiThroughput() {
		double thrput = 0;
		int count=0;
		for(AccessPoint ap:apList) {
			thrput = thrput + ap.getAvgThroughput();
			count++;
		}
		return thrput/count;
	}
	
	static double btsThroughput() {
		double thrput = 0;
		int count=0;
		for(BaseStation bs:bts) {
			thrput = thrput + bs.averageThroughput();
			count++;
		}
		return thrput/count;
	}
	
	static double btsSatisfaction() {
		double satis = 0;
		int count=0;
		for(BaseStation bs:bts) {
			satis = satis + bs.averageSatis();
			count++;
		}
		return satis/count;
	}
	
	static double wifiSatisfaction() {
		double satis = 0;
		int count=0;
		for(AccessPoint ap:apList) {
			satis = satis + ap.averageSatis();
			count++;
		}
		return satis/count;
	}
}