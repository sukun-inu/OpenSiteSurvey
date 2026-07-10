package com.opensitesurvey.tool.wifi7;

/**
 * Detects 802.11be (Wi-Fi 7) EHT capability and Multi-Link Operation (MLO) advertisement from a
 * beacon/probe response's raw IE blob.
 *
 * <p>EHT Capabilities, EHT Operation, and the (Basic) Multi-Link element are all "Element ID
 * Extension" elements - unlike HT (tag 61) or VHT (tag 192), which each get their own top-level
 * tag, these share the reserved top-level tag 255 and are disambiguated by a second, inner
 * "extension ID" byte at the start of the element body (same family as the HE Capabilities/
 * Operation extension elements {@link com.opensitesurvey.tool.channel.ChannelWidthParser}
 * deliberately doesn't parse either).
 *
 * <p>Deliberately limited to presence detection only, not the elements' own detailed capability
 * bitmaps - EHT Operation's and the Multi-Link element's internal layout both have several
 * optional, presence-flag-gated sub-fields that vary across 802.11be draft revisions, and getting
 * a bit offset wrong there would silently produce a wrong value rather than an absent one (worse
 * than not parsing it at all, since downstream code - e.g. channel planning scoring - would trust
 * whatever is returned). "Does this AP advertise the element at all" is unambiguous from the
 * element ID alone and doesn't carry that risk.
 */
public final class Wifi7Parser {

    private static final int EID_EXTENSION = 255;
    private static final int EXT_ID_EHT_OPERATION = 106;
    private static final int EXT_ID_MULTI_LINK = 107;
    private static final int EXT_ID_EHT_CAPABILITIES = 108;

    public record Wifi7Info(boolean ehtCapable, boolean mloCapable) {
        public static final Wifi7Info NONE = new Wifi7Info(false, false);
    }

    private Wifi7Parser() {
    }

    public static Wifi7Info parse(byte[] ie) {
        if (ie == null) {
            return Wifi7Info.NONE;
        }
        boolean ehtCapable = false;
        boolean mloCapable = false;
        int pos = 0;
        while (pos + 2 <= ie.length) {
            int eid = ie[pos] & 0xFF;
            int len = ie[pos + 1] & 0xFF;
            int contentStart = pos + 2;
            if (contentStart + len > ie.length) {
                break; // truncated/corrupt tail - stop parsing defensively
            }
            if (eid == EID_EXTENSION && len >= 1) {
                int extId = ie[contentStart] & 0xFF;
                if (extId == EXT_ID_EHT_CAPABILITIES || extId == EXT_ID_EHT_OPERATION) {
                    ehtCapable = true;
                } else if (extId == EXT_ID_MULTI_LINK) {
                    mloCapable = true;
                }
            }
            pos = contentStart + len;
        }
        return new Wifi7Info(ehtCapable, mloCapable);
    }
}
