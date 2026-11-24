package com.ecocode.analyzer;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.JavaParser;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.ast.body.Parameter;
import java.util.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavaProjectParser {

    private static final Set<String> RESOURCE_TYPES = Set.of(
            "InputStream", "FileInputStream", "BufferedInputStream",
            "OutputStream", "FileOutputStream", "BufferedOutputStream",
            "Reader", "BufferedReader", "FileReader", "Writer", "BufferedWriter", "FileWriter",
            "Connection", "Statement", "PreparedStatement", "ResultSet"
    );

    private static final Set<String> IGNORED_DIR_NAMES = Set.of(
            ".git", ".gradle", "build", "target", "node_modules", ".idea", "bin", "out",
            ".vscode", "dist", ".settings", ".svn"
    );

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JavaProjectParser.class);

    public ProjectModel parseProject(String projectPathStr) {
        Path root = Paths.get(projectPathStr);
        if (!Files.exists(root)) {
            Path parentRelative = Paths.get("..", projectPathStr);
            if (Files.exists(parentRelative)) {
                root = parentRelative;
            } else {
                throw new IllegalArgumentException("Project root not found: " + projectPathStr);
            }
        }
        return parse(root);
    }

    private JavaParser parser;

    public ProjectModel parse(Path projectRoot) {
        log.info("JavaProjectParser scanning: {}", projectRoot.toAbsolutePath());

        if (!Files.exists(projectRoot)) {
            throw new IllegalArgumentException("Project root not found: " + projectRoot);
        }

        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            javaFiles = paths
                    .filter(this::isAnalyzableJavaFile)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalArgumentException("Error scanning project root: " + projectRoot, e);
        }

        log.info("Found {} analyzable Java files in {}", javaFiles.size(), projectRoot);

        if (javaFiles.isEmpty()) {
            throw new IllegalArgumentException("No .java files found in: " + projectRoot);
        }

        ProjectModel model = new ProjectModel(projectRoot.toString());
        // Per-scan solver, with source roots inferred from package declarations.
        CombinedTypeSolver types = new CombinedTypeSolver(new ReflectionTypeSolver());
        Set<Path> roots = new LinkedHashSet<>();
        JavaParser discovery = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE));
        for (Path file : javaFiles) {
            try {
                var unit = discovery.parse(file).getResult();
                if (unit.isEmpty()) continue;
                Path root = file.toAbsolutePath().getParent();
                String pkg = unit.get().getPackageDeclaration().map(x -> x.getNameAsString()).orElse("");
                if (!pkg.isEmpty()) {
                    String[] parts = pkg.split("\\.");
                    for (int i = parts.length - 1; i >= 0 && root != null; i--) {
                        if (!root.getFileName().toString().equals(parts[i])) { root = null; break; }
                        root = root.getParent();
                    }
                }
                if (root != null) roots.add(root);
            } catch (Exception ignored) { }
        }
        for (Path root : roots) types.add(new JavaParserTypeSolver(root));
        parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)
                .setSymbolResolver(new JavaSymbolSolver(types)));
        javaFiles.stream().sorted().forEach(file -> parseFile(file.toFile(), model));
        if (model.getClasses().isEmpty()) throw new IllegalArgumentException("No supported Java classes could be parsed");
        return model;
    }

    private boolean isAnalyzableJavaFile(Path path) {
        if (!path.toString().endsWith(".java")) {
            return false;
        }
        // Exclude unwanted directories (.git, build, target, .gradle, etc.)
        for (Path part : path) {
            if (IGNORED_DIR_NAMES.contains(part.toString())) {
                return false;
            }
        }
        return true;
    }

    public void parseFile(File file, ProjectModel model) {
        try {
            if (parser == null) {
                parser = new JavaParser(new ParserConfiguration()
                    .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)
                    .setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver())));
            }
            CompilationUnit cu = parser.parse(file).getResult().orElseThrow();
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            for (ClassOrInterfaceDeclaration classDecl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                String simpleName = classDecl.getNameAsString();
                String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;

                ClassInfo classInfo = new ClassInfo(qualifiedName, simpleName, packageName, file.getAbsolutePath());

                // Database operations are classified per call, not by class naming.

                // Collect fields
                for (FieldDeclaration field : classDecl.getFields()) {
                    for (VariableDeclarator var : field.getVariables()) {
                        classInfo.getFieldTypes().put(var.getNameAsString(), var.getTypeAsString());
                    }
                }

                // Process Methods
                for (MethodDeclaration methodDecl : classDecl.getMethods()) {
                    String methodName = methodDecl.getNameAsString();
                    String returnType = methodDecl.getTypeAsString();
                    List<String> paramTypes = methodDecl.getParameters().stream()
                            .map(p -> p.getTypeAsString())
                            .collect(Collectors.toList());
                    List<String> paramNames = methodDecl.getParameters().stream()
                            .map(p -> p.getNameAsString())
                            .collect(Collectors.toList());

                    int startLine = methodDecl.getBegin().map(p -> p.line).orElse(0);
                    int endLine = methodDecl.getEnd().map(p -> p.line).orElse(0);
                    boolean isPublic = methodDecl.isPublic();

                    MethodInfo methodInfo = new MethodInfo(methodName, returnType, paramTypes, paramNames, startLine, endLine, isPublic);

                    try { methodInfo.setResolvedId(methodDecl.resolve().getQualifiedSignature()); }
                    catch (RuntimeException ignored) { }

                    // Parse Method Calls
                    for (MethodCallExpr callExpr : methodDecl.findAll(MethodCallExpr.class)) {
                        if (callExpr.findAncestor(MethodDeclaration.class).orElse(null) != methodDecl) continue;
                        String calleeName = callExpr.getNameAsString();
                        String scope = callExpr.getScope().map(Expression::toString).orElse(null);
                        List<String> args = callExpr.getArguments().stream()
                                .map(Expression::toString)
                                .collect(Collectors.toList());
                        int line = callExpr.getBegin().map(p -> p.line).orElse(0);

                        boolean inLoop = isRepeatedLoopCandidate(callExpr);

                        MethodCallInfo callInfo = new MethodCallInfo(
                                calleeName, scope, args, line, inLoop,
                                qualifiedName, methodName, file.getAbsolutePath()
                        );

                        callInfo.setCallerId(methodInfo.getResolvedId());
                        enrichCall(callExpr, callInfo);
                        methodInfo.getCalls().add(callInfo);
                        model.addMethodCall(callInfo);
                    }

                    // Only loop-carried local string accumulation, not independent formatting.
                    methodDecl.findAll(AssignExpr.class).forEach(assignment -> {
                        if (isGrowingString(assignment)) {
                            int line = assignment.getBegin().map(p -> p.line).orElse(0);
                            model.addStringConcatEvent(new ProjectModel.StringConcatEvent(file.getAbsolutePath(), qualifiedName, methodName, line, assignment.toString()));
                        }
                    });

                    // Parse Object Creation in Loop (EC105)
                    methodDecl.findAll(ObjectCreationExpr.class).forEach(objExpr -> {
                        if (isInsideLoop(objExpr)) {
                            int line = objExpr.getBegin().map(p -> p.line).orElse(0);
                            String typeName;
                            try { typeName = objExpr.calculateResolvedType().describe(); }
                            catch (RuntimeException e) { return; }
                            List<String> args = objExpr.getArguments().stream().map(Expression::toString).collect(Collectors.toList());
                            model.addObjectCreationEvent(
                                    new ProjectModel.ObjectCreationEvent(file.getAbsolutePath(), qualifiedName, methodName, line, typeName, args)
                            );
                        }
                    });

                    // Parse Unclosed Resources (EC107)
                    methodDecl.findAll(VariableDeclarator.class).forEach(varDecl -> {
                        String typeStr = varDecl.getTypeAsString();
                        if (isResourceType(typeStr) && isKnownOwnedResource(varDecl)) {
                            if (varDecl.getInitializer().isPresent() && !isInsideTryWithResources(varDecl) && !mayBeClosedOrTransferred(varDecl, methodDecl)) {
                                int line = varDecl.getBegin().map(p -> p.line).orElse(0);
                                model.addUnclosedResource(
                                        new ProjectModel.ResourceHandlingEvent(
                                                file.getAbsolutePath(), qualifiedName, methodName, line, typeStr, varDecl.getNameAsString()
                                        )
                                );
                            }
                        }
                    });

                    classInfo.getMethods().add(methodInfo);
                }

                model.addClass(classInfo);
            }
        } catch (Exception e) {
            System.err.println("Skipping file " + file.getAbsolutePath() + " due to parse warning/error: " + e.getMessage());
        }
    }

    private boolean isInsideLoop(Node node) {
        return node.findAncestor(ForStmt.class).isPresent() ||
                node.findAncestor(ForEachStmt.class).isPresent() ||
                node.findAncestor(WhileStmt.class).isPresent() ||
                node.findAncestor(DoStmt.class).isPresent();
    }

    private static final Set<String> PURE_MATH = Set.of("sqrt", "pow", "sin", "cos", "tan", "log", "log10", "exp", "hypot", "cbrt", "atan2");

    private void enrichCall(MethodCallExpr expr, MethodCallInfo call) {
        Set<String> types = new HashSet<>();
        try {
            var resolved = expr.resolve();
            call.setResolvedTarget(resolved.getQualifiedSignature());
            call.setDeclaringType(resolved.declaringType().getQualifiedName());
            types.add(call.getDeclaringType());
        } catch (RuntimeException ignored) { }
        try {
            if (expr.getScope().isPresent()) {
                var type = expr.getScope().get().calculateResolvedType();
                if (type.isReferenceType()) {
                    var ref = type.asReferenceType();
                    types.add(ref.getQualifiedName());
                    ref.getAllAncestors().forEach(t -> types.add(t.getQualifiedName()));
                }
            }
        } catch (RuntimeException ignored) { }
        // If a dependency is missing, accept only an explicit qualified/imported API type
        // on a field/parameter, never a repository-looking variable or method name.
        expr.getScope().filter(Expression::isNameExpr).ifPresent(scope -> {
            String name = scope.asNameExpr().getNameAsString();
            expr.findAncestor(MethodDeclaration.class).ifPresent(method -> {
                boolean local = method.findAll(VariableDeclarator.class).stream().anyMatch(v -> v.getNameAsString().equals(name));
                if (local) return;
                Optional<Parameter> param = method.getParameters().stream().filter(v -> v.getNameAsString().equals(name)).findFirst();
                if (param.isPresent()) addExplicitType(expr, param.get().getTypeAsString(), types);
                else method.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(cls -> cls.getFields().stream()
                    .flatMap(field -> field.getVariables().stream()).filter(v -> v.getNameAsString().equals(name))
                    .forEach(v -> addExplicitType(expr, v.getTypeAsString(), types)));
            });
        });
        call.setListReceiver(types.stream().anyMatch(t -> Set.of("java.util.List", "java.util.ArrayList", "java.util.LinkedList", "java.util.Vector").contains(t)));
        String name = call.getCalleeName();
        boolean repo = types.stream().anyMatch(t -> t.startsWith("org.springframework.data.") && (t.endsWith("Repository") || t.endsWith("RepositoryImplementation")));
        call.setRepositoryOperation(repo);
        boolean repositoryCall = repo && (name.startsWith("find") || name.startsWith("read") || name.startsWith("getBy") || name.startsWith("query") || name.startsWith("save") || name.startsWith("delete") || name.startsWith("count") || name.startsWith("exists"));
        boolean jdbc = types.stream().anyMatch(t -> Set.of("java.sql.Statement", "java.sql.PreparedStatement", "java.sql.CallableStatement").contains(t)) && name.startsWith("execute");
        boolean jpa = types.stream().anyMatch(t -> Set.of("jakarta.persistence.EntityManager", "javax.persistence.EntityManager", "jakarta.persistence.Query", "javax.persistence.Query", "jakarta.persistence.TypedQuery", "javax.persistence.TypedQuery").contains(t)) && Set.of("find", "persist", "merge", "remove", "flush", "getResultList", "getSingleResult", "executeUpdate").contains(name);
        boolean template = types.contains("org.springframework.jdbc.core.JdbcTemplate") && (name.startsWith("query") || Set.of("update", "batchUpdate", "execute").contains(name));
        call.setDatabaseOperation(repositoryCall || jdbc || jpa || template);
        // Conservative EC108: known pure numerical methods, literal inputs, same straight-line block.
        // Never infer purity from method spelling alone.
        if (Set.of("java.lang.Math", "java.lang.StrictMath").contains(Objects.toString(call.getDeclaringType(), ""))
                && PURE_MATH.contains(name) && !expr.getArguments().isEmpty()
                && expr.getArguments().stream().allMatch(Expression::isLiteralExpr)) {
            Node statement = expr.findAncestor(Statement.class).orElse(null);
            if (statement != null && statement.getParentNode().orElse(null) instanceof BlockStmt block
                    && statement instanceof ExpressionStmt
                    && expr.getParentNode().map(parent -> parent instanceof VariableDeclarator).orElse(false)) {
                call.setRepeatablePureCall(true);
                call.setExecutionRegion(block.getRange().map(Object::toString).orElse(null));
            }
        }
    }

    private void addExplicitType(Node node, String raw, Set<String> types) {
        String name = raw.split("<", 2)[0].trim();
        if (name.contains(".")) types.add(name);
        else node.findCompilationUnit().ifPresent(cu -> cu.getImports().stream()
            .filter(i -> !i.isStatic() && !i.isAsterisk() && i.getName().getIdentifier().equals(name))
            .forEach(i -> types.add(i.getNameAsString())));
    }

    private boolean isAncestor(Node ancestor, Node node) {
        for (Node parent = node.getParentNode().orElse(null); parent != null; parent = parent.getParentNode().orElse(null)) {
            if (parent == ancestor) return true;
        }
        return false;
    }

    private Node nearestLoop(Node node) {
        Node p = node.getParentNode().orElse(null);
        while (p != null) {
            if (p instanceof ForStmt || p instanceof ForEachStmt || p instanceof WhileStmt || p instanceof DoStmt) return p;
            if (p instanceof LambdaExpr || p instanceof MethodDeclaration) return null;
            p = p.getParentNode().orElse(null);
        }
        return null;
    }

    private boolean isRepeatedLoopCandidate(Node node) {
        Node loop = nearestLoop(node);
        if (loop == null) return false;
        // Interactive or indefinite loops are not classified as batch loops by this rule.
        if (loop instanceof WhileStmt w && w.getCondition().isBooleanLiteralExpr() && w.getCondition().asBooleanLiteralExpr().getValue()) return false;
        if (loop instanceof ForStmt f && f.getInitialization().stream().anyMatch(n -> n == node || isAncestor(n, node))) return false;
        Node cursor = node;
        while (cursor != loop && cursor != null) {
            Node parent = cursor.getParentNode().orElse(null);
            if (parent instanceof BlockStmt block && cursor instanceof Statement stmt) {
                int index = block.getStatements().indexOf(stmt);
                if (index >= 0 && index + 1 < block.getStatements().size()) {
                    Statement next = block.getStatement(index + 1);
                    if (next.isReturnStmt() || next.isThrowStmt() || (next.isBreakStmt() && next.asBreakStmt().getLabel().isEmpty() && !node.findAncestor(SwitchStmt.class).isPresent())) return false;
                }
            }
            if (cursor instanceof ReturnStmt || cursor instanceof ThrowStmt) return false;
            cursor = parent;
        }
        return true;
    }

    private boolean isGrowingString(AssignExpr assignment) {
        Node loop = nearestLoop(assignment);
        if (loop == null || !isRepeatedLoopCandidate(assignment) || !assignment.getTarget().isNameExpr()) return false;
        String name = assignment.getTarget().asNameExpr().getNameAsString();
        try {
            if (!assignment.getTarget().calculateResolvedType().describe().equals("java.lang.String")) return false;
            var decl = assignment.getTarget().asNameExpr().resolve().toAst();
            if (decl.isEmpty() || !(decl.get() instanceof VariableDeclarator) || isAncestor(loop, decl.get())) return false;
        } catch (RuntimeException ignored) { return false; }
        if (assignment.getOperator() == AssignExpr.Operator.PLUS) return true;
        if (assignment.getOperator() != AssignExpr.Operator.ASSIGN) return false;
        Expression value = assignment.getValue();
        while (value.isBinaryExpr() && value.asBinaryExpr().getOperator() == BinaryExpr.Operator.PLUS) value = value.asBinaryExpr().getLeft();
        return value.isNameExpr() && value.asNameExpr().getNameAsString().equals(name) && value != assignment.getValue();
    }

    private boolean mayBeClosedOrTransferred(VariableDeclarator var, MethodDeclaration method) {
        String name = var.getNameAsString();
        // Conservative: any explicit close or possible ownership escape suppresses the leak candidate.
        if (method.findAll(MethodCallExpr.class).stream().anyMatch(c ->
            (c.getNameAsString().equals("close") && c.getScope().map(Object::toString).orElse("").equals(name)) ||
            c.getArguments().stream().anyMatch(arg -> arg.isNameExpr() && arg.toString().equals(name)))) return true;
        if (method.findAll(ReturnStmt.class).stream().anyMatch(ret -> ret.getExpression().map(Object::toString).orElse("").equals(name))) return true;
        if (method.findAll(TryStmt.class).stream().anyMatch(t -> t.getResources().stream().anyMatch(res -> res.toString().equals(name)))) return true;
        return method.findAll(AssignExpr.class).stream().anyMatch(x -> x.getValue().toString().equals(name));
    }

    private boolean isKnownOwnedResource(VariableDeclarator var) {
        if (var.getInitializer().isEmpty()) return false;
        try {
            String type = var.getType().resolve().describe();
            return (type.startsWith("java.io.") || type.startsWith("java.sql.")) &&
                (var.getInitializer().get().isObjectCreationExpr() || var.getInitializer().get().isMethodCallExpr());
        } catch (RuntimeException e) { return false; }
    }

    private boolean isResourceType(String typeStr) {
        return RESOURCE_TYPES.contains(typeStr.substring(typeStr.lastIndexOf('.') + 1));
    }

    private boolean isInsideTryWithResources(Node node) {
        return node.findAncestor(TryStmt.class)
                .map(tryStmt -> !tryStmt.getResources().isEmpty() && tryStmt.getResources().stream().anyMatch(r -> r.toString().contains(node.toString())))
                .orElse(false);
    }
}
