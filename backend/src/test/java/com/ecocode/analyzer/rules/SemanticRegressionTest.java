package com.ecocode.analyzer.rules;

import com.ecocode.analyzer.*;
import com.ecocode.model.StaticIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SemanticRegressionTest {
    @TempDir Path root;
    private List<StaticIssue> scan(String code, EcoRule rule) throws Exception {
        Files.writeString(root.resolve("Sample.java"), code);
        ProjectModel model = new JavaProjectParser().parse(root);
        new SymbolSolver().resolveSymbols(model);
        return rule.analyze(model, CallGraph.buildFrom(model));
    }
    @Test void fileSaveAndGetterAreNotDatabaseCalls() throws Exception {
        assertTrue(scan("""
            import java.util.*;
            class Sample {
                int getId() { return 1; }
                void save() { System.out.println("file"); }
                void work(List<Sample> items) { for (Sample s : items) { s.getId(); s.save(); } }
            }
            """, new EC101_RepeatedCallInLoop()).isEmpty());
    }
    @Test void jdbcQueryInsideBatchIsFlaggedButNotSiblingGetter() throws Exception {
        var issues = scan("""
            import java.sql.*;
            class Sample {
                int getId() { return 1; }
                void work(PreparedStatement statement) throws SQLException {
                    for (int i=0; i<10; i++) { getId(); statement.executeQuery(); }
                }
            }
            """, new EC101_RepeatedCallInLoop());
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).getExplanation().contains("executeQuery"));
    }
    @Test void transitiveDatabaseCallDoesNotContaminateSiblings() throws Exception {
        var issues = scan("""
            import java.sql.*;
            class Sample {
                int getId() { return 1; }
                void query(PreparedStatement s) throws SQLException { s.executeQuery(); }
                void work(PreparedStatement s) throws SQLException {
                    for (int i=0;i<10;i++) { getId(); query(s); }
                }
            }
            """, new EC101_RepeatedCallInLoop());
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).getExplanation().contains("query"));
    }
    @Test void queryThenReturnIsNotRepeated() throws Exception {
        assertTrue(scan("""
            import java.sql.*;
            class Sample {
                void work(PreparedStatement s) throws SQLException {
                    for (int i=0;i<10;i++) { if (i==3) { s.executeQuery(); return; } }
                }
            }
            """, new EC101_RepeatedCallInLoop()).isEmpty());
    }
    @Test void independentStringFormattingIsNotGrowingAccumulator() throws Exception {
        assertTrue(scan("""
            class Sample { void work(String[] items) {
                for (String item : items) System.out.println("Item " + item);
            } }
            """, new EC102_StringConcatInLoop()).isEmpty());
    }
    @Test void growingStringIsDetectedExactlyOnce() throws Exception {
        assertEquals(1, scan("""
            class Sample { String work(String[] items) {
                String out = "";
                for (String item : items) { out = out + item + ","; }
                return out;
            } }
            """, new EC102_StringConcatInLoop()).size());
    }
    @Test void collectionTypeNotVariableNameDeterminesLookup() throws Exception {
        var issues = scan("""
            import java.util.*;
            class Sample { void work(List<Integer> x, Set<Integer> ids, int[] items) {
                for (int item : items) { x.contains(item); ids.contains(item); }
            } }
            """, new EC103_CollectionLookupInLoop());
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).getExplanation().contains("x.contains"));
    }
    @Test void arbitraryFindAllIsNotDatabaseFetch() throws Exception {
        assertTrue(scan("""
            class Sample { void findAll() {} void work() { findAll(); } }
            """, new EC104_UnboundedCollection()).isEmpty());
    }
    @Test void repositoryFetchIsPotentiallyUnbounded() throws Exception {
        assertEquals(1, scan("""
            import org.springframework.data.repository.CrudRepository;
            class Sample { void work(CrudRepository<Object, Long> repository) { repository.findAll(); } }
            """, new EC104_UnboundedCollection()).size());
    }
    @Test void mutableObjectAllocationMustNotBeHoisted() throws Exception {
        assertTrue(scan("""
            import java.util.*;
            class Sample { void work(List<Sample> items) {
                for (int i=0; i<10; i++) items.add(new Sample());
            } }
            """, new EC105_RepeatedAllocationHotPath()).isEmpty());
    }
    @Test void explicitFinallyCloseIsNotLeak() throws Exception {
        assertTrue(scan("""
            import java.io.*;
            class Sample { void work() throws IOException {
                InputStream in = new FileInputStream("x");
                try { in.read(); } finally { in.close(); }
            } }
            """, new EC107_ResourceHandling()).isEmpty());
    }
    @Test void unclosedOwnedFileStreamIsCandidate() throws Exception {
        assertEquals(1, scan("""
            import java.io.*;
            class Sample { void work() throws IOException {
                InputStream in = new FileInputStream("x"); in.read();
            } }
            """, new EC107_ResourceHandling()).size());
    }
    @Test void scannerInputIsNotDuplicateComputation() throws Exception {
        assertTrue(scan("""
            import java.util.*;
            class Sample { void work(Scanner sc) {
                int id = sc.nextInt(); int age = sc.nextInt();
            } }
            """, new EC108_DuplicateComputation()).isEmpty());
    }
    @Test void identicalPureLiteralCallsInSameBlockAreCandidates() throws Exception {
        assertEquals(1, scan("""
            class Sample { void work() {
                double a = Math.sqrt(100); double b = Math.sqrt(100);
            } }
            """, new EC108_DuplicateComputation()).size());
    }
    @Test void mutuallyExclusiveMathCallsAreNotDuplicates() throws Exception {
        assertTrue(scan("""
            class Sample { void work(boolean flag) {
                if (flag) { double a = Math.sqrt(100); }
                else { double b = Math.sqrt(100); }
            } }
            """, new EC108_DuplicateComputation()).isEmpty());
    }
    @Test void uploadedStudentProjectDoesNotReproduceOriginalFalsePositives() throws Exception {
        Path fixture = Path.of("src/test/resources/student-regression");
        ProjectModel model = new JavaProjectParser().parse(fixture);
        CallGraph graph = CallGraph.buildFrom(model);
        for (EcoRule rule : List.of(new EC101_RepeatedCallInLoop(), new EC102_StringConcatInLoop(), new EC108_DuplicateComputation())) {
            assertTrue(rule.analyze(model, graph).isEmpty(), rule.getRuleId());
        }
    }
}
