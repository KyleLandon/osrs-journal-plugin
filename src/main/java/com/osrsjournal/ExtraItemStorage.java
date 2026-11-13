package com.osrsjournal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Item containers that are only populated when the player opens them — looting
 * bag (Check) and Player-Owned House costume-room storage. Numeric IDs match
 * {@code net.runelite.api.gameval.InventoryID}. Each key is replaced independently
 * so opening the armour case cannot wipe a previously synced cape rack.
 */
final class ExtraItemStorage
{
    static final String LOOTING_BAG = "looting_bag";

    private static final Map<Integer, String> BY_ID;

    static
    {
        Map<Integer, String> ids = new LinkedHashMap<>();
        ids.put(516, LOOTING_BAG);
        ids.put(446, "poh_magic_wardrobe");
        ids.put(447, "poh_armour");
        ids.put(448, "poh_fancy_dress");
        ids.put(449, "poh_treasure_1");
        ids.put(450, "poh_treasure_2");
        ids.put(451, "poh_treasure_3");
        ids.put(452, "poh_capes");
        ids.put(453, "poh_holiday");
        ids.put(485, "poh_treasure_1a");
        ids.put(486, "poh_treasure_2a");
        ids.put(487, "poh_treasure_3a");
        ids.put(488, "poh_capes_2");
        ids.put(627, "poh_treasure_0");
        BY_ID = Collections.unmodifiableMap(ids);
    }

    private ExtraItemStorage()
    {
    }

    static boolean isTracked(int containerId)
    {
        return BY_ID.containsKey(containerId);
    }

    static String keyFor(int containerId)
    {
        return BY_ID.get(containerId);
    }

    static Set<Integer> containerIds()
    {
        return BY_ID.keySet();
    }

    static boolean isAllowedKey(String key)
    {
        return key != null && BY_ID.containsValue(key);
    }
}
