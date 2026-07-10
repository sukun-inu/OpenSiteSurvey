package com.opensitesurvey.tool.wifi7;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Wifi7ParserTest {

    @Test
    void noIeMeansNotCapable() {
        Wifi7Parser.Wifi7Info info = Wifi7Parser.parse(null);
        assertFalse(info.ehtCapable());
        assertFalse(info.mloCapable());
    }

    @Test
    void emptyIeMeansNotCapable() {
        Wifi7Parser.Wifi7Info info = Wifi7Parser.parse(new byte[0]);
        assertFalse(info.ehtCapable());
        assertFalse(info.mloCapable());
    }

    @Test
    void ehtCapabilitiesElementMarksEhtCapable() {
        byte[] ie = {(byte) 255, 2, 108, 0}; // EID 255, len 2, ext id 108 (EHT Capabilities)
        Wifi7Parser.Wifi7Info info = Wifi7Parser.parse(ie);
        assertTrue(info.ehtCapable());
        assertFalse(info.mloCapable());
    }

    @Test
    void ehtOperationElementAlsoMarksEhtCapable() {
        byte[] ie = {(byte) 255, 2, 106, 0}; // ext id 106 (EHT Operation)
        Wifi7Parser.Wifi7Info info = Wifi7Parser.parse(ie);
        assertTrue(info.ehtCapable());
    }

    @Test
    void multiLinkElementMarksMloCapableButNotEht() {
        byte[] ie = {(byte) 255, 2, 107, 0}; // ext id 107 (Multi-Link)
        Wifi7Parser.Wifi7Info info = Wifi7Parser.parse(ie);
        assertTrue(info.mloCapable());
        assertFalse(info.ehtCapable());
    }

    @Test
    void unrelatedExtensionElementIsIgnored() {
        byte[] ie = {(byte) 255, 2, 35, 0}; // ext id 35 = HE Capabilities, not EHT/MLO
        Wifi7Parser.Wifi7Info info = Wifi7Parser.parse(ie);
        assertFalse(info.ehtCapable());
        assertFalse(info.mloCapable());
    }

    @Test
    void unrelatedTopLevelTagIsIgnored() {
        byte[] ie = {61, 2, 1, 0}; // HT Operation, not an extension element at all
        Wifi7Parser.Wifi7Info info = Wifi7Parser.parse(ie);
        assertFalse(info.ehtCapable());
        assertFalse(info.mloCapable());
    }

    @Test
    void multipleElementsCombineAcrossTheWholeBlob() {
        byte[] eht = {(byte) 255, 2, 108, 0};
        byte[] mlo = {(byte) 255, 2, 107, 0};
        byte[] ie = new byte[eht.length + mlo.length];
        System.arraycopy(eht, 0, ie, 0, eht.length);
        System.arraycopy(mlo, 0, ie, eht.length, mlo.length);
        Wifi7Parser.Wifi7Info info = Wifi7Parser.parse(ie);
        assertTrue(info.ehtCapable());
        assertTrue(info.mloCapable());
    }

    @Test
    void truncatedElementStopsParsingDefensively() {
        byte[] ie = {(byte) 255, 5, 108}; // claims len=5 but only 1 body byte actually follows
        Wifi7Parser.Wifi7Info info = Wifi7Parser.parse(ie);
        assertFalse(info.ehtCapable());
    }
}
