package com.ecocode.analyzer.rules;

import com.ecocode.analyzer.CallGraph;
import com.ecocode.analyzer.MethodCallInfo;
import com.ecocode.analyzer.ProjectModel;
import com.ecocode.model.StaticIssue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EC101_RepeatedCallInLoop implements EcoRule {

    @Override
    public String getRuleId() { return "EC101"; }

    @Override
    public String getName() { return "Repeated Database Call in Loop"; }

    @Override
    public String getDescription() {
        return "Potential repeated database operations in a batch loop; actual query counts require measurement.";
    }

    @Override
    public String getSeverity() { return "HIGH"; }

    @Override
    public List<StaticIssue> analyze(ProjectModel model, CallGraph callGraph) {
        List<StaticIssue> issues = new ArrayList<>();

        for (MethodCallInfo call : model.getAllMethodCalls()) {
            if (!call.isInLoop()) continue;

            boolean isDbCall = isDatabaseMethod(call, callGraph, model);
            if (isDbCall) {
                StaticIssue issue = new StaticIssue();
                issue.setRuleId(getRuleId());
                issue.setSeverity(getSeverity());
                issue.setConfidence("MEDIUM");
                issue.setFileName(call.getFilePath());
                issue.setLineNumber(call.getLineNumber());
                issue.setClassName(call.getCallerClassName());
                issue.setMethodName(call.getCallerMethodName());
                issue.setCallPath(call.getCallerFullMethodId() + " -> " + call.getCalleeName());
                issue.setExplanation("Potential repeated database operation via '" + call.getCalleeName() + "' inside a loop at line " + call.getLineNumber() + ".");
                issue.setRecommendation("Inspect query count; use batching or prefetching when transaction and ordering semantics permit.");
                issue.setTradeOffs("Batching can reduce round trips at the cost of memory; confirm with a representative workload.");
                issues.add(issue);
            }
        }

        return issues;
    }

    private boolean isDatabaseMethod(MethodCallInfo call, CallGraph graph, ProjectModel model) {
        if (call.isDatabaseOperation()) return true;
        if (call.getResolvedTarget() == null) return false;
        var reachable = graph.reachableFrom(call.getResolvedTarget());
        return model.getAllMethodCalls().stream().anyMatch(c -> c.isDatabaseOperation() &&
                (c.getCallerFullMethodId().equals(call.getResolvedTarget()) || reachable.contains(c.getCallerFullMethodId())));
    }
}
