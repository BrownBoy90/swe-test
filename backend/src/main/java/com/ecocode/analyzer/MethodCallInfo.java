package com.ecocode.analyzer;

import java.util.ArrayList;
import java.util.List;

public class MethodCallInfo {
    private String calleeName;
    private String scope;
    private List<String> arguments = new ArrayList<>();
    private int lineNumber;
    private boolean inLoop;
    private String callerClassName;
    private String callerMethodName;
    private String resolvedTarget; // e.g., com.example.ClassName#methodName
    private String filePath;
    private String callerId;
    private String declaringType;
    private boolean databaseOperation;
    private boolean repositoryOperation;
    private boolean listReceiver;
    private boolean repeatablePureCall;
    private String executionRegion;

    public void setCallerId(String id) { callerId = id; }
    public String getDeclaringType() { return declaringType; }
    public void setDeclaringType(String value) { declaringType = value; }
    public boolean isDatabaseOperation() { return databaseOperation; }
    public void setDatabaseOperation(boolean value) { databaseOperation = value; }
    public boolean isRepositoryOperation() { return repositoryOperation; }
    public void setRepositoryOperation(boolean value) { repositoryOperation = value; }
    public boolean isListReceiver() { return listReceiver; }
    public void setListReceiver(boolean value) { listReceiver = value; }
    public boolean isRepeatablePureCall() { return repeatablePureCall; }
    public void setRepeatablePureCall(boolean value) { repeatablePureCall = value; }
    public String getExecutionRegion() { return executionRegion; }
    public void setExecutionRegion(String value) { executionRegion = value; }

    public MethodCallInfo() {}

    public MethodCallInfo(String calleeName, String scope, List<String> arguments, int lineNumber,
                          boolean inLoop, String callerClassName, String callerMethodName, String filePath) {
        this.calleeName = calleeName;
        this.scope = scope;
        this.arguments = arguments != null ? arguments : new ArrayList<>();
        this.lineNumber = lineNumber;
        this.inLoop = inLoop;
        this.callerClassName = callerClassName;
        this.callerMethodName = callerMethodName;
        this.filePath = filePath;
    }

    public String getCalleeName() { return calleeName; }
    public void setCalleeName(String calleeName) { this.calleeName = calleeName; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public List<String> getArguments() { return arguments; }
    public void setArguments(List<String> arguments) { this.arguments = arguments; }

    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

    public boolean isInLoop() { return inLoop; }
    public void setInLoop(boolean inLoop) { this.inLoop = inLoop; }

    public String getCallerClassName() { return callerClassName; }
    public void setCallerClassName(String callerClassName) { this.callerClassName = callerClassName; }

    public String getCallerMethodName() { return callerMethodName; }
    public void setCallerMethodName(String callerMethodName) { this.callerMethodName = callerMethodName; }

    public String getResolvedTarget() { return resolvedTarget; }
    public void setResolvedTarget(String resolvedTarget) { this.resolvedTarget = resolvedTarget; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getCallerFullMethodId() {
        if (callerId != null) return callerId;
        return (callerClassName != null ? callerClassName : "") + "#" + (callerMethodName != null ? callerMethodName : "");
    }
}
