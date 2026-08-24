package com.osrsjournal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ExtraItemStorageTest
{
	@Test
	public void lootingBagAndCostumeRoomAreTracked()
	{
		assertEquals(ExtraItemStorage.LOOTING_BAG, ExtraItemStorage.keyFor(516));
		assertEquals("poh_armour", ExtraItemStorage.keyFor(447));
		assertEquals("poh_capes", ExtraItemStorage.keyFor(452));
		assertTrue(ExtraItemStorage.isTracked(516));
		assertTrue(ExtraItemStorage.isAllowedKey("poh_holiday"));
	}

	@Test
	public void menuAndCheckInventoriesAreIgnored()
	{
		assertFalse(ExtraItemStorage.isTracked(445));
		assertFalse(ExtraItemStorage.isTracked(496));
		assertFalse(ExtraItemStorage.isAllowedKey("inventory"));
		assertFalse(ExtraItemStorage.isAllowedKey(null));
	}
}
