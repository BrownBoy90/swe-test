package com.ecocode.service;

import com.ecocode.analyzer.ProjectModel;
import com.ecocode.model.AnalysisRun;
import com.ecocode.model.RuntimeMetric;
import com.ecocode.model.StaticIssue;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RuntimeProfilerService {
    public List<RuntimeMetric> profile(String sourcePath, AnalysisRun run) {
        return List.of();
    }
    public List<RuntimeMetric> profile(String sourcePath, AnalysisRun run, ProjectModel model, List<StaticIssue> issues) {
        // A generic source archive provides no representative runtime workload.
        // Never execute uploaded build scripts automatically or fabricate measurements.
        return List.of();
    }
}
