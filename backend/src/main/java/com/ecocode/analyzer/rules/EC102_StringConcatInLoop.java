package com.ecocode.analyzer.rules;

import com.ecocode.analyzer.CallGraph;
import com.ecocode.analyzer.ProjectModel;
import com.ecocode.model.StaticIssue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EC102_StringConcatInLoop implements EcoRule {

    @Override
    public String getRuleId() { return "EC102"; }

    @Override
    public String getName() { return "String Concatenation in Loop"; }

    @Override
    public String getDescription() {
        return "Repeatedly grows a loop-carried String, potentially requiring quadratic character copying.";
    }

    @Override
    public String getSeverity() { return "MEDIUM"; }

    @Override
    public List<StaticIssue> analyze(ProjectModel model, CallGraph callGraph) {
        List<StaticIssue> issues = new ArrayList<>();

        for (ProjectModel.StringConcatEvent event : model.getStringConcatsInLoop()) {
            StaticIssue issue = new StaticIssue();
            issue.setRuleId(getRuleId());
            issue.setSeverity(getSeverity());
            issue.setConfidence("MEDIUM");
            issue.setFileName(event.filePath);
            issue.setLineNumber(event.lineNumber);
            issue.setClassName(event.className);
            issue.setMethodName(event.methodName);
            issue.setCallPath(event.className + "#" + event.methodName);
            issue.setExplanation("Loop-carried string accumulation at line " + event.lineNumber + ": " + event.expression);
            issue.setRecommendation("Initialize a StringBuilder before the loop, append within it, and convert after the loop if semantics permit.");
            issue.setTradeOffs("May reduce repeated copying and allocations; preserve null handling and any intermediate reads.");
            issues.add(issue);
        }

        return issues;
    }
}
