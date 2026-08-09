package com.osrsjournal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Captures obtained Collection Log items when the player opens a log page.
 *
 * <p>Listens for {@link ScriptID#COLLECTION_DRAW_LIST} (same hook as Chat
 * Commands' pets tracker), reads the page title + item widgets, and
 * accumulates pages until {@link #drainPages()} is called by the sync path.
 */
@Slf4j
@Singleton
class CollectionLogCapture
{
	/** Title child on {@link InterfaceID.Collection#HEADER_TEXT} (ChatCommands uses 0). */
	private static final int HEADER_TITLE_CHILD = 0;

	private final Client client;

	/** Pages changed since the last drain — keyed by page title. */
	private final Map<String, List<CapturedItem>> pending = new LinkedHashMap<>();

	private Runnable onPagesChanged;

	@Inject
	CollectionLogCapture(Client client)
	{
		this.client = client;
	}

	void setOnPagesChanged(Runnable onPagesChanged)
	{
		this.onPagesChanged = onPagesChanged;
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.COLLECTION_DRAW_LIST)
		{
			return;
		}

		Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		if (header == null || header.getChildren() == null)
		{
			return;
		}

		Widget titleWidget = header.getChild(HEADER_TITLE_CHILD);
		if (titleWidget == null)
		{
			return;
		}
		String page = titleWidget.getText();
		if (page == null || page.isEmpty())
		{
			return;
		}
		page = Text.removeTags(page).replace('\u00A0', ' ').trim();
		if (page.isEmpty())
		{
			return;
		}

		Widget itemsRoot = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
		List<CapturedItem> obtained = new ArrayList<>();
		if (itemsRoot != null && itemsRoot.getChildren() != null)
		{
			for (Widget child : itemsRoot.getChildren())
			{
				if (child == null || child.getOpacity() != 0)
				{
					continue;
				}
				int itemId = child.getItemId();
				if (itemId <= 0)
				{
					continue;
				}
				String name = cleanItemName(child.getName());
				int qty = child.getItemQuantity();
				if (qty <= 0)
				{
					qty = 1;
				}
				obtained.add(new CapturedItem(itemId, name, qty));
			}
		}

		synchronized (pending)
		{
			pending.put(page, obtained);
		}
		log.debug("Collection log page '{}' — {} obtained item(s)", page, obtained.size());

		Runnable cb = onPagesChanged;
		if (cb != null)
		{
			cb.run();
		}
	}

	/**
	 * Returns and clears pages captured since the last drain.
	 * Safe to call from any thread; capture writes happen on the client thread.
	 */
	Map<String, List<CapturedItem>> drainPages()
	{
		synchronized (pending)
		{
			if (pending.isEmpty())
			{
				return Collections.emptyMap();
			}
			Map<String, List<CapturedItem>> out = new LinkedHashMap<>(pending);
			pending.clear();
			return out;
		}
	}

	boolean hasPending()
	{
		synchronized (pending)
		{
			return !pending.isEmpty();
		}
	}

	private static String cleanItemName(String raw)
	{
		if (raw == null || raw.isEmpty())
		{
			return "";
		}
		return Text.removeTags(raw).replace('\u00A0', ' ').trim();
	}

	/** Obtained item on a collection-log page. */
	static final class CapturedItem
	{
		private final int itemId;
		private final String itemName;
		private final int quantity;

		CapturedItem(int itemId, String itemName, int quantity)
		{
			this.itemId = itemId;
			this.itemName = itemName != null ? itemName : "";
			this.quantity = quantity;
		}

		int getItemId()
		{
			return itemId;
		}

		String getItemName()
		{
			return itemName;
		}

		int getQuantity()
		{
			return quantity;
		}
	}
}
