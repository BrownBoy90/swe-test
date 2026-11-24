package com.ecocode.service;

import com.ecocode.analyzer.ProjectModel;
import com.ecocode.model.AnalysisRun;
import com.ecocode.model.EcoScore;
import com.ecocode.model.RuntimeMetric;
import com.ecocode.model.StaticIssue;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EcoScoreService {

    public EcoScore compute(AnalysisRun run, List<StaticIssue> issues, List<RuntimeMetric> metrics) {
        return compute(run, issues, metrics, null);
    }

    public EcoScore compute(AnalysisRun run, List<StaticIssue> issues, List<RuntimeMetric> metrics, ProjectModel model) {
        // 1. Static AST Score calculation (normalized against project size so it never flatlines to 0)
        long highCount = issues.stream().filter(i -> "HIGH".equalsIgnoreCase(i.getSeverity())).count();
        long medCount = issues.stream().filter(i -> "MEDIUM".equalsIgnoreCase(i.getSeverity())).count();
        long lowCount = issues.stream().filter(i -> "LOW".equalsIgnoreCase(i.getSeverity()) && !"EC106".equals(i.getRuleId())).count();

        double penaltyPoints = (highCount * 3.8) + (medCount * 1.6) + (lowCount * 0.4);

        int totalUnits;
        if (model != null && !model.getClasses().isEmpty()) {
            int totalMethods = model.getClasses().stream().mapToInt(c -> c.getMethods().size()).sum();
            totalUnits = Math.max(12, totalMethods);
        } else {
            totalUnits = Math.max(15, (int)(issues.size() * 1.5));
        }

        double defectRatio = penaltyPoints / totalUnits;
        int staticScore;
        if (penaltyPoints == 0) {
            staticScore = 100;
        } else {
            staticScore = (int) Math.round(100.0 / (1.0 + 1.8 * defectRatio));
            staticScore = Math.max(5, Math.min(100, staticScore));
        }

        // 2. Metrics lookup
        Map<String, Double> metricMap = metrics.stream()
                .collect(Collectors.toMap(RuntimeMetric::getMetricName, RuntimeMetric::getMetricValue, (v1, v2) -> v1));

        Integer cpuScore = component(metricMap.get("avg_cpu_pct"), 20, 95);
        Integer memoryScore = component(metricMap.get("peak_memory_mb"), 80, 600);
        Integer dbScore = component(metricMap.get("db_queries_per_req"), 2.5, 40);
        Integer networkScore = component(metricMap.get("payload_kb"), 50, 1000);
        double weighted = .15 * staticScore;
        double weight = .15;
        Integer[] scores = {cpuScore, memoryScore, dbScore, networkScore};
        double[] weights = {.25, .20, .20, .20};
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] != null) { weighted += scores[i] * weights[i]; weight += weights[i]; }
        }
        return new EcoScore(run, (int)Math.round(weighted / weight), cpuScore, memoryScore, dbScore, networkScore, staticScore);
    }

    private Integer component(Double value, double good, double bad) {
        if (value == null || !Double.isFinite(value) || value < 0) return null;
        return (int)Math.round(Math.max(0, Math.min(100, 100 * (bad - value) / (bad - good))));
    }
}
