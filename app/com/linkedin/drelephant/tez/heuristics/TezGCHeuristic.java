/*
 * Copyright 2016 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.linkedin.drelephant.tez.heuristics;

import com.linkedin.drelephant.configurations.heuristic.HeuristicConfigurationData;
import com.linkedin.drelephant.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.linkedin.drelephant.analysis.Heuristic;
import com.linkedin.drelephant.analysis.HeuristicResult;
import com.linkedin.drelephant.analysis.Severity;
import com.linkedin.drelephant.tez.data.TezCounterData;
import com.linkedin.drelephant.tez.data.TezDAGApplicationData;
import com.linkedin.drelephant.tez.data.TezDAGData;
import com.linkedin.drelephant.tez.data.TezVertexTaskData;
import com.linkedin.drelephant.tez.data.TezVertexData;
import com.linkedin.drelephant.math.Statistics;

import java.util.Map;

import org.apache.log4j.Logger;


/**
 * Analyses garbage collection efficiency
 */
public  class TezGCHeuristic implements Heuristic<TezDAGApplicationData> {
  private static final Logger logger = Logger.getLogger(TezGCHeuristic.class);

  // Severity Parameters
  private static final String GC_RATIO_SEVERITY = "gc_ratio_severity";
  private static final String RUNTIME_SEVERITY = "runtime_severity_in_min";

  // Default value of parameters
  private double[] gcRatioLimits = {0.01d, 0.02d, 0.03d, 0.04d};   // Garbage Collection Time / CPU Time
  private double[] runtimeLimits = {5, 10, 12, 15};                // Task Runtime in milli sec

  private HeuristicConfigurationData _heuristicConfData;

  private void loadParameters() {
    Map<String, String> paramMap = _heuristicConfData.getParamMap();
    String heuristicName = _heuristicConfData.getHeuristicName();

    double[] confGcRatioThreshold = Utils.getParam(paramMap.get(GC_RATIO_SEVERITY), gcRatioLimits.length);
    if (confGcRatioThreshold != null) {
      gcRatioLimits = confGcRatioThreshold;
    }
    logger.info(heuristicName + " will use " + GC_RATIO_SEVERITY + " with the following threshold settings: "
        + Arrays.toString(gcRatioLimits));

    double[] confRuntimeThreshold = Utils.getParam(paramMap.get(RUNTIME_SEVERITY), runtimeLimits.length);
    if (confRuntimeThreshold != null) {
      runtimeLimits = confRuntimeThreshold;
    }
    logger.info(heuristicName + " will use " + RUNTIME_SEVERITY + " with the following threshold settings: "
        + Arrays.toString(runtimeLimits));
    for (int i = 0; i < runtimeLimits.length; i++) {
      runtimeLimits[i] = runtimeLimits[i] * Statistics.MINUTE_IN_MS;
    }
  }

  public TezGCHeuristic(HeuristicConfigurationData heuristicConfData) {
    this._heuristicConfData = heuristicConfData;

    loadParameters();
  }


  @Override
  public HeuristicConfigurationData getHeuristicConfData() {
    return _heuristicConfData;
  }

  @Override
  public HeuristicResult apply(TezDAGApplicationData data) {

    if(!data.getSucceeded()) {
      return null;
    }
    TezDAGData[] tezDAGsData = data.getTezDAGData();
    TezVertexTaskData[] tasks = null;
    int i=0;
    int taskLength=0;
    List<Long> gcMs = new ArrayList<Long>();
    List<Long> cpuMs = new ArrayList<Long>();
    List<Long> runtimesMs = new ArrayList<Long>();
    
   /* List<Long> vGcMs[] = new List[tezVertexes.length];;
    List<Long> vCpuMs[] = new List[tezVertexes.length];;
    List<Long> vRuntimesMs[] = new List[tezVertexes.length];;
    String vertexNames[] = new String[tezVertexes.length];
    */
for(TezDAGData tezDAGData:tezDAGsData){   	
		
    	TezVertexData tezVertexes[] = tezDAGData.getVertexData();
    for (TezVertexData tezVertexData:tezVertexes){
    	
    	 tasks = tezVertexData.getTasksData();
    	 taskLength+=tasks.length;
    /*	 vertexNames[i] = tezVertexData.getVertexName();
    	 vGcMs[i] = new ArrayList<Long>();
    	 vCpuMs[i] = new ArrayList<Long>();
     	vRuntimesMs[i] = new ArrayList<Long>();*/

    for (TezVertexTaskData task : tasks) {
      if (task.isSampled()) {
        runtimesMs.add(task.getTotalRunTimeMs());
        gcMs.add(task.getCounters().get(TezCounterData.CounterName.GC_MILLISECONDS));
        cpuMs.add(task.getCounters().get(TezCounterData.CounterName.CPU_MILLISECONDS));
        
     /*   vRuntimesMs[i].add(task.getTotalRunTimeMs());
        vGcMs[i].add(task.getCounters().get(TezCounterData.CounterName.GC_MILLISECONDS));
        vCpuMs[i].add(task.getCounters().get(TezCounterData.CounterName.CPU_MILLISECONDS));  */    
        
      }
    }
    i++;
    }
}

    long avgRuntimeMs = Statistics.average(runtimesMs);
    long avgCpuMs = Statistics.average(cpuMs);
    long avgGcMs = Statistics.average(gcMs);
    double ratio = avgCpuMs != 0 ? avgGcMs*(1.0)/avgCpuMs: 0;

    Severity severity;
    if (tasks.length == 0) {
      severity = Severity.NONE;
    } else {
      severity = getGcRatioSeverity(avgRuntimeMs, avgCpuMs, avgGcMs);
    }

    HeuristicResult result = new HeuristicResult(_heuristicConfData.getClassName(),
        _heuristicConfData.getHeuristicName(), severity, Utils.getHeuristicScore(severity, tasks.length));
    
    

  /*  long vAvgRuntimeMs[] = new long[tezVertexes.length];
    long vAvgCpuMs[] = new long[tezVertexes.length];
    long vAvgGcMs[] = new long[tezVertexes.length];
    double vRatio[] = new double[tezVertexes.length];
    Severity vSeverity[] = new Severity[tezVertexes.length];*/

   /* for(int vertexNumber=0;vertexNumber<tezVertexes.length;vertexNumber++){
    	vAvgRuntimeMs[vertexNumber]= Statistics.average(vRuntimesMs[vertexNumber]);
    	vAvgGcMs[vertexNumber]= Statistics.average(vGcMs[vertexNumber]);
    	vAvgCpuMs[vertexNumber]= Statistics.average(vCpuMs[vertexNumber]);
    	vRatio[vertexNumber]=vAvgCpuMs[vertexNumber] != 0 ? vAvgGcMs[vertexNumber]*(1.0)/vAvgCpuMs[vertexNumber]: 0;
        vSeverity[vertexNumber] = getGcRatioSeverity(avgRuntimeMs, avgCpuMs, avgGcMs);

    	if (vRuntimesMs[vertexNumber].size() == 0) {
  	      vSeverity[vertexNumber] = Severity.NONE;
  	    } else {
  	    	 vSeverity[vertexNumber] = getGcRatioSeverity(vAvgRuntimeMs[vertexNumber], vAvgCpuMs[vertexNumber], vAvgGcMs[vertexNumber]);;
  	    }
    	if(vSeverity[vertexNumber].getValue()!= 0){
    		 result.addResultDetail("Number of tasks in vertex "+vertexNames[vertexNumber], Integer.toString(vRuntimesMs[vertexNumber].size()));
    		    result.addResultDetail("Avg vertex task runtime (ms)"+vertexNames[vertexNumber], Long.toString(vAvgRuntimeMs[vertexNumber]));
    		    result.addResultDetail("Avg vertex task CPU time (ms)"+vertexNames[vertexNumber], Long.toString(vAvgCpuMs[vertexNumber]));
    		    result.addResultDetail("Avg vertex task GC time (ms)"+vertexNames[vertexNumber], Long.toString(vAvgGcMs[vertexNumber]));
    		    result.addResultDetail("Vertex Task GC/CPU ratio"+vertexNames[vertexNumber], Double.toString(vRatio[vertexNumber]));
    	}
    }*/
    
    result.addResultDetail("Number of vertexes", Integer.toString(i));
    result.addResultDetail("Number of tasks", Integer.toString(taskLength));
    result.addResultDetail("Avg task runtime (ms)", Long.toString(avgRuntimeMs));
    result.addResultDetail("Avg task CPU time (ms)", Long.toString(avgCpuMs));
    result.addResultDetail("Avg task GC time (ms)", Long.toString(avgGcMs));
    result.addResultDetail("Task GC/CPU ratio", Double.toString(ratio));
    return result;
  }

  private Severity getGcRatioSeverity(long runtimeMs, long cpuMs, long gcMs) {
    double gcRatio = ((double)gcMs)/cpuMs;
    Severity ratioSeverity = Severity.getSeverityAscending(
        gcRatio, gcRatioLimits[0], gcRatioLimits[1], gcRatioLimits[2], gcRatioLimits[3]);

    // Severity is reduced if task runtime is insignificant
    Severity runtimeSeverity = getRuntimeSeverity(runtimeMs);

    return Severity.min(ratioSeverity, runtimeSeverity);
  }

  private Severity getRuntimeSeverity(long runtimeMs) {
    return Severity.getSeverityAscending(
        runtimeMs, runtimeLimits[0], runtimeLimits[1], runtimeLimits[2], runtimeLimits[3]);
  }

}
