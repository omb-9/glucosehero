package com.omb9.glucosehero.data.cgm.xdrip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XdripTrendNamesTest {

    @Test
    fun `pascal case slope names are kept`() {
        assertEquals("DoubleUp", XdripTrendNames.normalize("DoubleUp"))
        assertEquals("SingleUp", XdripTrendNames.normalize("SingleUp"))
        assertEquals("FortyFiveUp", XdripTrendNames.normalize("FortyFiveUp"))
        assertEquals("Flat", XdripTrendNames.normalize("Flat"))
        assertEquals("FortyFiveDown", XdripTrendNames.normalize("FortyFiveDown"))
        assertEquals("SingleDown", XdripTrendNames.normalize("SingleDown"))
        assertEquals("DoubleDown", XdripTrendNames.normalize("DoubleDown"))
        assertEquals("TripleUp", XdripTrendNames.normalize("TripleUp"))
        assertEquals("TripleDown", XdripTrendNames.normalize("TripleDown"))
    }

    @Test
    fun `spaced and underscored names normalize`() {
        assertEquals("DoubleUp", XdripTrendNames.normalize("Double Up"))
        assertEquals("SingleDown", XdripTrendNames.normalize("SINGLE_DOWN"))
        assertEquals("Flat", XdripTrendNames.normalize(" flat "))
    }

    @Test
    fun `hidden and invalid slopes become null`() {
        assertNull(XdripTrendNames.normalize("9"))
        assertNull(XdripTrendNames.normalize("NONE"))
        assertNull(XdripTrendNames.normalize("NOT COMPUTABLE"))
        assertNull(XdripTrendNames.normalize("NOT_COMPUTABLE"))
        assertNull(XdripTrendNames.normalize("OUT OF RANGE"))
        assertNull(XdripTrendNames.normalize(""))
        assertNull(XdripTrendNames.normalize(null))
        assertNull(XdripTrendNames.normalize("Sideways"))
    }
}
