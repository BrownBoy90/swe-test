package com.ecocode.service;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ScoreRegressionTest {
    @Test void missingRuntimeIsNotPretendedOptimal() {
        var score = new EcoScoreService().compute(null, List.of(), List.of());
        assertNull(score.getCpuScore());
        assertNull(score.getMemoryScore());
        assertNull(score.getDatabaseScore());
        assertNull(score.getNetworkScore());
        assertEquals(score.getStaticScore(), score.getTotalScore());
        assertTrue(new RuntimeProfilerService().profile("ignored", null).isEmpty());
    }
}
