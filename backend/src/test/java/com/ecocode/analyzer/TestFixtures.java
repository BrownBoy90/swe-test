package com.ecocode.analyzer;

public class TestFixtures {

    public static final String INEFFICIENT = """
        package com.example;
        import java.util.*;
        import org.springframework.data.repository.CrudRepository;
        public class InefficientService {
            private CrudRepository<Object, Long> repo;
            public List<String> getNames(List<Long> ids) {
                List<String> result = new ArrayList<>();
                for (Long id : ids) {
                    Object s = repo.findById(id);
                    result.add(s.toString());
                }
                return result;
            }
            public String buildReport(List<String> items) {
                String report = "";
                for (String item : items) {
                    report = report + item + ",";
                }
                return report;
            }
        }
        """;

    public static final String EFFICIENT = """
        package com.example;
        import java.util.*;
        import org.springframework.data.repository.CrudRepository;
        import java.util.stream.*;
        public class EfficientService {
            private CrudRepository<Object, Long> repo;
            public List<String> getNames(List<Long> ids) {
                return repo.findAllById(ids).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
            }
            public String buildReport(List<String> items) {
                StringBuilder sb = new StringBuilder();
                for (String item : items) sb.append(item).append(',');
                return sb.toString();
            }
        }
        """;
}
