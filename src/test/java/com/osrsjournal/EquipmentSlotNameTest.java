package com.osrsjournal;

import static org.junit.Assert.assertEquals;
import net.runelite.api.EquipmentInventorySlot;
import org.junit.Test;

public class EquipmentSlotNameTest
{
	@Test
	public void amuletMapsToWebsiteNeckSlot()
	{
		assertEquals("neck", OsrsJournalPlugin.canonicalSlotName(EquipmentInventorySlot.AMULET));
	}

	@Test
	public void otherSlotsKeepRuneLiteNames()
	{
		assertEquals("head", OsrsJournalPlugin.canonicalSlotName(EquipmentInventorySlot.HEAD));
		assertEquals("cape", OsrsJournalPlugin.canonicalSlotName(EquipmentInventorySlot.CAPE));
		assertEquals("weapon", OsrsJournalPlugin.canonicalSlotName(EquipmentInventorySlot.WEAPON));
		assertEquals("body", OsrsJournalPlugin.canonicalSlotName(EquipmentInventorySlot.BODY));
		assertEquals("shield", OsrsJournalPlugin.canonicalSlotName(EquipmentInventorySlot.SHIELD));
		assertEquals("legs", OsrsJournalPlugin.canonicalSlotName(EquipmentInventorySlot.LEGS));
		assertEquals("gloves", OsrsJournalPlugin.canonicalSlotName(EquipmentInventorySlot.GLOVES));
		assertEquals("boots", OsrsJournalPlugin.canonicalSlotName(EquipmentInventorySlot.BOOTS));
		assertEquals("ring", OsrsJournalPlugin.canonicalSlotName(EquipmentInventorySlot.RING));
		assertEquals("ammo", OsrsJournalPlugin.canonicalSlotName(EquipmentInventorySlot.AMMO));
	}
}
