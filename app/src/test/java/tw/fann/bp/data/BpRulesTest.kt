package tw.fann.bp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BpRulesTest {

    @Test
    fun categorize_boundaries() {
        assertEquals(BpCategory.NORMAL, BpRules.categorize(119, 79))
        assertEquals(BpCategory.ELEVATED, BpRules.categorize(120, 79))
        assertEquals(BpCategory.ELEVATED, BpRules.categorize(129, 79))
        assertEquals(BpCategory.STAGE1, BpRules.categorize(130, 70))
        assertEquals(BpCategory.STAGE1, BpRules.categorize(118, 80))
        assertEquals(BpCategory.STAGE1, BpRules.categorize(139, 89))
        assertEquals(BpCategory.STAGE2, BpRules.categorize(140, 70))
        assertEquals(BpCategory.STAGE2, BpRules.categorize(125, 90))
    }

    @Test
    fun validate_acceptsNormalReading_withOrWithoutPulse() {
        assertNull(BpRules.validate(128, 82, 71))
        assertNull(BpRules.validate(128, 82, null))
    }

    @Test
    fun validate_rejectsMissingOrOutOfRange() {
        assertNotNull(BpRules.validate(null, 82, 71))
        assertNotNull(BpRules.validate(59, 40, 71))
        assertNotNull(BpRules.validate(261, 82, 71))
        assertNotNull(BpRules.validate(128, 29, 71))
        assertNotNull(BpRules.validate(128, 82, 29))
        assertNotNull(BpRules.validate(128, 82, 201))
    }

    @Test
    fun validate_rejectsDiastolicNotBelowSystolic() {
        assertNotNull(BpRules.validate(90, 90, 70))
        assertNotNull(BpRules.validate(80, 95, 70))
    }
}
