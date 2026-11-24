package com.ecocode.analyzer.rules;

import com.ecocode.analyzer.CallGraph;
import com.ecocode.analyzer.ProjectModel;
import com.ecocode.model.StaticIssue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EC105_RepeatedAllocationHotPath implements EcoRule {

    @Override
    public String getRuleId() { return "EC105"; }

    @Override
    public String getName() { return "Repeated Object Allocation on Hot Path"; }

    @Override
    public String getDescription() {
        return "Instantiates object inside loop body where constructor arguments are static or loop-invariant.";
    }

    @Override
    public String getSeverity() { return "LOW"; }

    @Override
    public List<StaticIssue> analyze(ProjectModel model, CallGraph callGraph) {
        List<StaticIssue> issues = new ArrayList<>();

        for (ProjectModel.ObjectCreationEvent event : model.getObjectCreationsInLoop()) {
            boolean isConstantOrEmpty = event.arguments.isEmpty() || event.arguments.stream()
                    .allMatch(arg -> arg.matches("\".*\"") || arg.matches("-?\\d+(\\.\\d+)?") ||
                                     arg.equals("true") || arg.equals("false") || arg.equals("null"));

            if (isConstantOrEmpty && java.util.Set.of("BigInteger", "java.math.BigInteger", "BigDecimal", "java.math.BigDecimal").contains(event.typeName)) {
                StaticIssue issue = new StaticIssue();
                issue.setRuleId(getRuleId());
                issue.setSeverity(getSeverity());
                issue.setConfidence("MEDIUM");
                issue.setFileName(event.filePath);
                issue.setLineNumber(event.lineNumber);
                issue.setClassName(event.className);
                issue.setMethodName(event.methodName);
                issue.setCallPath(event.className + "#" + event.methodName + " -> new " + event.typeName + "()");
                issue.setExplanation("Object allocation 'new " + event.typeName + "()' inside loop at line " + event.lineNumber + " does not depend on iteration variables.");
                issue.setRecommendation("Profile this allocation; reuse an immutable value only if identity, exceptions and observable behaviour are preserved.");
                issue.setTradeOffs("Potential allocation savings; not proof that a loop is hot.");
                issues.add(issue);
            }
        }

        return issues;
    }
}
