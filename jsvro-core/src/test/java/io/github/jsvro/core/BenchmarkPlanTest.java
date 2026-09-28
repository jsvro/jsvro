package io.github.jsvro.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BenchmarkPlanTest {

    @Test
    void explicitPlanUsesTheGivenIterationsInRowOrder() {
        BenchmarkPlan plan = BenchmarkPlan.explicit("100:5000, 10:10000,1000:1000,50:7000");

        assertEquals(List.of(10, 50, 100, 1000), plan.sizes());
        assertEquals(10_000, plan.iterations(10));
        assertEquals(7_000, plan.iterations(50));
        assertEquals(5_000, plan.iterations(100));
        assertEquals(1_000, plan.iterations(1000));
        assertEquals(BenchmarkPlan.Mode.CUSTOM, plan.mode());
    }

    @Test
    void theShippedFullPlanLoadsAsAFullRun() {
        BenchmarkPlan plan = BenchmarkPlan.full();

        assertEquals(BenchmarkPlan.Mode.FULL, plan.mode());
        assertFalse(plan.sizes().isEmpty());
    }

    @Test
    void fullPlanPropertiesMapRowsToIterationsInRowOrder() {
        BenchmarkPlan plan = BenchmarkPlan.fromProperties("""
                1000=3000
                10=10000
                100000=30
                """);

        assertEquals(BenchmarkPlan.Mode.FULL, plan.mode());
        assertEquals(List.of(10, 1000, 100_000), plan.sizes());
        assertEquals(10_000, plan.iterations(10));
        assertEquals(3_000, plan.iterations(1000));
        assertEquals(30, plan.iterations(100_000));
    }

    @Test
    void fullPlanPropertiesAreCheckedLikeCustomPlans() {
        assertEquals("At most 10000 iterations are allowed, but '10=10001' asks for 10001",
                assertThrows(IllegalArgumentException.class, () -> BenchmarkPlan.fromProperties("10=10001")).getMessage());
        assertEquals("At most 100000 rows are allowed, but '200000=10' asks for 200000",
                assertThrows(IllegalArgumentException.class, () -> BenchmarkPlan.fromProperties("200000=10")).getMessage());
        assertEquals("Expected a positive number of iterations in '10=many'",
                assertThrows(IllegalArgumentException.class, () -> BenchmarkPlan.fromProperties("10=many")).getMessage());
        assertEquals("A benchmark plan needs at least one rows:iterations entry",
                assertThrows(IllegalArgumentException.class, () -> BenchmarkPlan.fromProperties("")).getMessage());
    }

    @Test
    void explicitPlanAllowsOneToTenThousandIterations() {
        BenchmarkPlan plan = BenchmarkPlan.explicit("10:10000,100000:1");

        assertEquals(10_000, plan.iterations(10));
        assertEquals(1, plan.iterations(100_000));
        assertEquals("At most 10000 iterations are allowed, but '10:10001' asks for 10001",
                assertThrows(IllegalArgumentException.class, () -> BenchmarkPlan.explicit("10:10001")).getMessage());
    }

    @Test
    void rejectsMoreThanOneHundredThousandRows() {
        assertEquals("At most 100000 rows are allowed, but '100001:10' asks for 100001",
                assertThrows(IllegalArgumentException.class, () -> BenchmarkPlan.explicit("100001:10")).getMessage());
    }

    @Test
    void rejectsMalformedPlans() {
        assertEquals("Expected rows:iterations but got '100'",
                assertThrows(IllegalArgumentException.class, () -> BenchmarkPlan.explicit("100")).getMessage());
        assertEquals("Expected a positive number of iterations in '100:0'",
                assertThrows(IllegalArgumentException.class, () -> BenchmarkPlan.explicit("100:0")).getMessage());
        assertEquals("Expected a positive number of rows in 'x:10'",
                assertThrows(IllegalArgumentException.class, () -> BenchmarkPlan.explicit("x:10")).getMessage());
        assertEquals("Rows 10 appear more than once in '10:1,10:2'",
                assertThrows(IllegalArgumentException.class, () -> BenchmarkPlan.explicit("10:1,10:2")).getMessage());
    }
}
