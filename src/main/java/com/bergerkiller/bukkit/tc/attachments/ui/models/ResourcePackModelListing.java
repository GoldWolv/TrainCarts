package com.bergerkiller.bukkit.tc.attachments.ui.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.map.MapResourcePack;
import com.bergerkiller.bukkit.common.map.MapResourcePack.ResourceType;
import com.bergerkiller.bukkit.common.map.util.Model.ModelOverride;
import com.bergerkiller.bukkit.common.map.util.ModelInfoLookup;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

/**
 * Provides a listing of all the models available in a resource pack. The information can be
 * used to show a dialog to a Player which can be browsed.
 */
public class ResourcePackModelListing {
    private final Plugin plugin;
    private final List<ListedItemModel> allListedItems = new ArrayList<>();
    private final Map<String, ListedNamespace> namespacesByName = new HashMap<>();
    private ListedRoot root = new ListedRoot();
    private MapResourcePack resourcePack;

    /**
     * Initializes a new model listing. {@link #showDialog(Player)} cannot be used.
     */
    public ResourcePackModelListing() {
        this(null);
    }

    /**
     * Initializes a new model listing. The input plugin is only used for showing a dialog with
     * items to players.
     *
     * @param plugin
     */
    public ResourcePackModelListing(Plugin plugin) {
        this.plugin = plugin;
        this.resourcePack = null;
    }

    /**
     * Builds a new dialog to display this model listing inside of. The returned builder object can
     * be further configured, where {@link DialogBuilder#show()} will display the dialog to the Player.<br>
     * <br>
     * <b>A valid plugin must have been specified in the constructor of this listing</b>
     *
     * @param player Player to which to show the dialog
     * @return builder
     */
    public DialogBuilder buildDialog(Player player) {
        if (this.plugin == null) {
            throw new IllegalStateException("No plugin was specified on constructor, cannot show dialog");
        }
        return new DialogBuilder(this.plugin, player, this);
    }

    /**
     * Builds a new dialog to display this model listing inside of. The returned builder object can
     * be further configured, where {@link DialogBuilder#show()} will display the dialog to the Player.
     *
     * @param player Player to which to show the dialog
     * @param plugin Plugin that will be managing this dialog
     * @return builder
     */
    public DialogBuilder buildDialog(Player player, Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin is null");
        }
        return new DialogBuilder(plugin, player, this);
    }

    /**
     * Shows a navigatable dialog of item models to the player. This is a creative menu dialog,
     * so the player can take items from the menu and drag them in their own inventory.
     *
     * @param player Player to show a creative model listing dialog to
     */
    public void showCreativeDialog(Player player) {
        buildDialog(player).asCreativeMenu().show();
    }

    /**
     * Closes any open model listing dialog for the Player specified. Any consumers of the dialog
     * will be notified of the closure.
     *
     * @param player Player to close the dialog for
     */
    public static void closeDialog(Player player) {
        ResourcePackModelListingDialog.close(player);
    }

    /**
     * Closes all model listing dialogs for all Players on the server. Useful to be run on plugin
     * shutdown.
     */
    public static void closeAllDialogs() {
        ResourcePackModelListingDialog.closeAll();
    }

    /**
     * Closes all model listing dialogs for all Players on the server that were managed by the
     * plugin specified.
     *
     * @param plugin Plugin for which to close all dialogs
     */
    public static void closeAllDialogs(Plugin plugin) {
        ResourcePackModelListingDialog.closeAllByPlugin(plugin);
    }

    /**
     * Gets whether this model listing is empty. It will be empty if there is no resource pack set,
     * or the resource pack doesn't override any item models.
     *
     * @return True if empty
     */
    public boolean isEmpty() {
        return allListedItems.isEmpty();
    }

    /**
     * Clears all listed items
     */
    public void clear() {
        allListedItems.clear();
        namespacesByName.clear();
        root = new ListedRoot();
    }

    /**
     * Gets the root entry, which contains the namespace for which custom models exist
     *
     * @return root
     */
    public ListedRoot root() {
        return root;
    }

    /**
     * Gets the {@link MapResourcePack} that was last loaded in using
     * {@link #load(MapResourcePack)}
     *
     * @return loaded resource pack
     */
    public MapResourcePack loadedResourcePack() {
        return resourcePack;
    }

    /**
     * Initializes this model listing by parsing the files contained inside a loaded resource
     * pack
     *
     * @param resourcePack
     */
    public void load(MapResourcePack resourcePack) {
        clear();

        this.resourcePack = resourcePack;

        // Figure out all minecraft items that are overrided by the resource pack
        boolean logged = false;
        Set<String> allOverridedModels = new HashSet<String>();
        for (MapResourcePack p = resourcePack; p != null && p != MapResourcePack.VANILLA; p = p.getBase()) {
            if (!logged) {
                logged = true;
                logLoading("Loading resource pack item model lists");
            }
            allOverridedModels.addAll(p.listResources(ResourceType.MODELS, "item", false));
        }
        if (allOverridedModels.isEmpty()) {
            return;
        }

        // Figure out what ItemStack corresponds with each item model and register them all
        for (Material material : ItemUtil.getItemTypes()) {
            for (ItemStack item : ItemUtil.getItemVariants(material)) {
                String path = "item/" + ModelInfoLookup.lookupItemRenderOptions(item).lookupModelName();
                if (allOverridedModels.contains(path)) {
                    for (ModelOverride override : resourcePack.getModel(path).getOverrides()) {
                        addListedItem(override.model, override.applyToItem(item));
                    }
                }
            }
        }

        // Required for displayedItems() to work, and to set up display names/lores of items
        root.postInitializeAll();

        logLoading("Resource pack item model lists loaded");
    }

    private void logLoading(String message) {
        if (plugin != null) {
            plugin.getLogger().log(Level.INFO, "[Resource Pack Models] " + message);
        } else {
            System.out.println("[Resource Pack Models] " + message);
        }
    }

    private void addListedItem(String path, ItemStack item) {
        // Decode the path namespace and directory structure
        ListedNamespace namespace;
        ListedEntry containingEntry;
        String name;
        String pathWithoutNamespace;
        String fullPath;
        {
            int namespaceStart = path.indexOf(':');
            String namespaceName;
            if (namespaceStart == -1) {
                namespaceName = "minecraft";
                pathWithoutNamespace = path;
                fullPath = "minecraft:" + path;
            } else {
                namespaceName = path.substring(0, namespaceStart);
                pathWithoutNamespace = path.substring(namespaceStart + 1);
                fullPath = path;
            }
            namespace = findOrCreateNamespace(namespaceName);

            int directoryPathEnd = pathWithoutNamespace.lastIndexOf('/');
            if (directoryPathEnd == -1) {
                containingEntry = namespace;
                name = pathWithoutNamespace;
            } else {
                String directoryPath = pathWithoutNamespace.substring(0, directoryPathEnd);
                containingEntry = namespace.findOrCreateDirectory(directoryPath);
                name = pathWithoutNamespace.substring(directoryPathEnd + 1);
            }
        }

        // Create listed item model, then register it in here
        ListedItemModel entry = new ListedItemModel(fullPath, pathWithoutNamespace, name, item);
        entry.setParent(containingEntry);
        allListedItems.add(entry);
    }

    private ListedNamespace findOrCreateNamespace(String namespace) {
        ListedNamespace entry = namespacesByName.computeIfAbsent(namespace, ListedNamespace::new);
        if (entry.parent() == null) {
            entry.setParent(root);
        }
        return entry;
    }

    @SuppressWarnings("deprecation")
    private static ItemStack createDefaultBGItem() {
        try {
            ItemStack item;
            if (CommonCapabilities.MATERIAL_ENUM_CHANGES) {
                item = ItemUtil.createItem(
                        MaterialUtil.getMaterial("GRAY_STAINED_GLASS_PANE"),
                        1);
            } else {
                item = ItemUtil.createItem(
                        MaterialUtil.getMaterial("LEGACY_STAINED_GLASS_PANE"),
                        DyeColor.GRAY.getWoolData(), 1);
            }
            ItemUtil.setDisplayName(item, ChatColor.RESET.toString());
            return item;
        } catch (Throwable t) {
            return null; // Meh. Not important enough to fail everything!
        }
    }

    /**
     * Configures the behavior of a model listing dialog
     */
    public static final class DialogBuilder implements Cloneable {
        private static final ItemStack DEFAULT_BG_ITEM = createDefaultBGItem();

        private final Plugin plugin;
        private final Player player;
        private final ResourcePackModelListing listing;
        boolean creativeMenu = false;
        String title = "Resource Pack Models";
        String query = "";
        ItemStack bgItem = DEFAULT_BG_ITEM;
        boolean cancelOnRootRightClick = false;

        protected DialogBuilder(Plugin plugin, Player player, ResourcePackModelListing listing) {
            this.plugin = plugin;
            this.player = player;
            this.listing = listing;
        }

        /**
         * Gets the plugin that manages the dialog
         *
         * @return plugin owner
         */
        public Plugin plugin() {
            return plugin;
        }

        /**
         * Gets the Player to which the dialog will be displayed
         *
         * @return player
         */
        public Player player() {
            return player;
        }

        /**
         * Gets the resource pack model listing instance from which model information will be
         * read to display in the dialog.
         *
         * @return listing
         */
        public ResourcePackModelListing listing() {
            return listing;
        }

        /**
         * Makes the dialog into a creative menu. No item can be selected, but items can be taken
         * from the inventory as if it is a creative menu. If used, {@link #show()} will always only return
         * a cancelled result.
         *
         * @return this
         */
        public DialogBuilder asCreativeMenu() {
            creativeMenu = true;
            return this;
        }

        /**
         * Sets an alternative title to use for the dialog window
         *
         * @param title New title to use
         * @return this
         */
        public DialogBuilder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * Gets the title of the dialog window
         *
         * @return dialog window title
         */
        public String getTitle() {
            return title;
        }

        /**
         * Sets the item displayed for item slots in the window which are not filled
         * with an item or UI component.
         *
         * @param item Item to display as background. Null to leave the slots empty.
         * @return this
         */
        public DialogBuilder backgroundItem(ItemStack item) {
            this.bgItem = item;
            return this;
        }

        /**
         * Sets it to cancel the dialog when it is right-clicked and the dialog
         * can't move back any further.
         *
         * @return this
         */
        public DialogBuilder cancelOnRootRightClick() {
            return cancelOnRootRightClick(true);
        }

        /**
         * Sets whether to cancel the dialog when it is right-clicked and the dialog
         * can't move back any further.
         *
         * @param cancel True to cancel the dialog when right-clicking at root
         * @return this
         */
        public DialogBuilder cancelOnRootRightClick(boolean cancel) {
            this.cancelOnRootRightClick = cancel;
            return this;
        }

        /**
         * Gets whether right-clicking the menu while at the root directory layer closes
         * the dialog.
         *
         * @return True if cancel on right clicking at root is enabled
         */
        public boolean isCancelOnRootRightClick() {
            return cancelOnRootRightClick;
        }

        /**
         * Sets an initial search query for when the dialog window is opened.
         * If set to non-empty, will only show item models that match the contents
         * of the query.
         *
         * @param query Search query
         * @return this
         */
        public DialogBuilder query(String query) {
            this.query = query;
            return this;
        }

        /**
         * Gets the search query that filters the item models displayed in the dialog
         *
         * @return search query
         */
        public String getQuery() {
            return query;
        }

        /**
         * Shows the item model listing dialog the Player. Any previous open dialogs are closed.
         *
         * @return a future completed when an item is selected or the dialog is closed. The result
         *         can contain a selected item (model) if successful.
         */
        public CompletableFuture<DialogResult> show() {
            return ResourcePackModelListingDialog.show(this);
        }

        /**
         * Clones the configured options of this dialog so that it can be modified and shown again.
         */
        @Override
        public DialogBuilder clone() {
            DialogBuilder clone = new DialogBuilder(this.plugin, this.player, this.listing);
            clone.creativeMenu = this.creativeMenu;
            clone.title = this.title;
            clone.query = this.query;
            clone.bgItem = this.bgItem;
            return clone;
        }
    }

    /**
     * The result of displaying a dialog to a Player
     */
    public static final class DialogResult {
        private final ListedItemModel result;
        private final DialogBuilder dialog;
        private final boolean closedWithRootRightClick;

        public DialogResult(DialogBuilder dialog, boolean closedWithRootRightClick) {
            this.result = null;
            this.dialog = dialog;
            this.closedWithRootRightClick = closedWithRootRightClick;
        }

        public DialogResult(DialogBuilder dialog, ListedItemModel result) {
            this.result = result;
            this.dialog = dialog;
            this.closedWithRootRightClick = false;
        }

        /**
         * Gets the state of the dialog options at the time the item model selection
         * dialog was closed. If the user had inputed a new query, the new query is
         * made available here.
         *
         * @return Dialog at the time of closing
         */
        public DialogBuilder dialog() {
            return dialog;
        }

        /**
         * Gets whether an item was selected and the dialog selection was successful.
         * Returns false if the dialog was closed, or if root-right-click cancelling
         * was enabled, closed that way.
         *
         * @return True if an item was selected
         */
        public boolean success() {
            return result != null;
        }

        /**
         * Gets whether the dialog selection was cancelled. Opposite of {@link #success()}
         *
         * @return True if cancelled
         */
        public boolean cancelled() {
            return result == null;
        }

        /**
         * Gets whether item model selection was cancelled by right-clicking the dialog
         * at the root level, rather than closing the dialog.
         *
         * @return True if the dialog was closed by clicking at the root directory level
         *         on the dialog, and {@link DialogBuilder#cancelOnRootRightClick()}
         *         was set.
         */
        public boolean cancelledWithRootRightClick() {
            return closedWithRootRightClick;
        }

        /**
         * Gets the item model that was selected. Returns null if {@link #success()}
         * returns false.
         *
         * @return Selected item model, null if none was selected
         */
        public ListedItemModel selected() {
            return result;
        }

        /**
         * Gets the ItemStack of the item model that was selected. returns null if
         * {@link #success()} returns false.
         *
         * @return Selected item, null if none was selected
         */
        public ItemStack selectedItem() {
            return result == null ? null : result.item();
        }
    }

    /**
     * An item model from the resource pack that should be listed
     */
    public static final class ListedItemModel extends ListedEntry {
        private final String fullPath;
        private final String path;
        private final String name;
        private final ItemStack item;

        // Figure out the default HideFlags to put in the NBT
        private static final int DEFAULT_HIDE_FLAGS;
        static {
            ItemStack item = ItemUtil.createItem(MaterialUtil.getFirst("GLASS", "LEGACY_GLASS"), 1);
            ItemMeta meta = item.getItemMeta();
            for (ItemFlag flag : ItemFlag.values()) {
                if (flag.name().startsWith("HIDE_")) { // Probably is all of them
                    meta.addItemFlags(flag);
                }
            }
            item.setItemMeta(meta);
            CommonTagCompound nbt = ItemUtil.getMetaTag(item, true);
            DEFAULT_HIDE_FLAGS = (nbt == null) ? 0 : nbt.getValue("HideFlags", 0);
        }

        public ListedItemModel(String fullPath, String path, String name, ItemStack item) {
            this.nestedItemCount = 1;
            this.fullPath = fullPath;
            this.path = path;
            this.name = name;
            this.item = ItemUtil.createItem(item);
        }

        @Override
        @SuppressWarnings("deprecation")
        protected void postInitialize() {
            CommonTagCompound nbt = ItemUtil.getMetaTag(this.item);

            // Hides everything except the lores we add
            nbt.putValue("HideFlags", DEFAULT_HIDE_FLAGS);

            String itemName = ItemUtil.getDisplayName(this.item);
            ItemUtil.setDisplayName(this.item, ChatColor.AQUA + name);
            ItemUtil.addLoreName(this.item, ChatColor.WHITE.toString() + ChatColor.ITALIC + fullPath);

            addLoreSpacer(this.item);

            {
                addLoreProperty(this.item, "Item", itemName);
            }

            {
                int cmd = nbt.containsKey("CustomModelData") ? nbt.getValue("CustomModelData", 0) : 0;
                if (cmd != 0) {
                    addLoreProperty(this.item, "Custom model data", cmd);
                }
            }

            {
                short damage = this.item.getDurability();
                if (damage != 0) {
                    addLoreProperty(this.item, "Damage", damage);
                }
            }

            if (nbt.containsKey("Unbreakable") && nbt.getValue("Unbreakable", false)) {
                addLoreProperty(this.item, "Unbreakable", true);
            }
        }

        private static void addLoreSpacer(ItemStack item) {
            ItemUtil.addLoreName(item, "");
        }

        private static void addLoreProperty(ItemStack item, String name, Object value) {
            ItemUtil.addLoreName(item, ChatColor.DARK_GRAY + name + ": " + ChatColor.GRAY + value);
        }

        @Override
        public String name() {
            return name;
        }

        /**
         * Gets the path of this item model, excluding the namespace prefix
         *
         * @return path
         */
        public String path() {
            return path;
        }

        @Override
        public String fullPath() {
            return fullPath;
        }

        @Override
        public int sortPriority() {
            return 3;
        }

        @Override
        public ListedNamespace namespace() {
            return parent().namespace();
        }

        @Override
        public ItemStack item() {
            return item;
        }

        @Override
        public List<ListedItemModel> explode() {
            return Collections.singletonList(this);
        }

        @Override
        protected void fillItems(List<ListedItemModel> items) {
            items.add(this);
        }

        @Override
        public String toString() {
            return "Item name=" + name + " path=" + fullPath + " item=" + item;
        }
    }

    public static final class ListedDirectory extends ListedEntry {
        private static final Material ITEM_TAG_TYPE = MaterialUtil.getFirst("LEGACY_NAME_TAG", "NAME_TAG");
        private final ListedNamespace namespace;
        private final String path;
        private final ItemStack item;

        public ListedDirectory(ListedNamespace namespace, String path) {
            this.namespace = namespace;
            this.path = path;
            this.item = ItemUtil.createItem(ITEM_TAG_TYPE, 1);
        }

        @Override
        protected void postInitialize() {
            ItemUtil.setDisplayName(this.item, ChatColor.YELLOW + this.path);
            ItemUtil.addLoreName(this.item, ChatColor.WHITE.toString() + ChatColor.ITALIC + this.fullPath());
            ItemUtil.addLoreName(this.item, "");
            ItemUtil.addLoreName(this.item, ChatColor.DARK_GRAY +
                    "< " + ChatColor.GRAY + this.nestedItemCount + ChatColor.DARK_GRAY + " Item models >");
        }

        @Override
        public String name() {
            return path;
        }

        /**
         * Gets the path of this directory, excluding the namespace prefix
         *
         * @return path
         */
        public String path() {
            return path;
        }

        @Override
        public String fullPath() {
            return namespace.fullPath() + path;
        }

        @Override
        public int sortPriority() {
            return 2;
        }

        @Override
        public ListedNamespace namespace() {
            return namespace;
        }

        @Override
        public ItemStack item() {
            return item;
        }

        @Override
        public String toString() {
            return "Directory: " + path;
        }
    }

    /**
     * A root namespace, such as 'minecraft'
     */
    public static final class ListedNamespace extends ListedEntry {
        private static final Material ITEM_TYPE = MaterialUtil.getFirst("LEGACY_NAME_TAG", "NAME_TAG");
        private final String name;
        private final ItemStack item;
        private final Map<String, ListedDirectory> directories = new HashMap<>();

        public ListedNamespace(String namespace) {
            this.name = namespace;
            this.item = ItemUtil.createItem(ITEM_TYPE, 1);
        }

        @Override
        protected void postInitialize() {
            ItemUtil.setDisplayName(this.item, ChatColor.YELLOW + this.name + ":");
            ItemUtil.addLoreName(this.item, "");
            ItemUtil.addLoreName(this.item, ChatColor.DARK_GRAY +
                    "< " + ChatColor.GRAY + this.nestedItemCount + ChatColor.DARK_GRAY + " Item models >");
        }

        protected ListedDirectory findOrCreateDirectory(String path) {
            ListedDirectory entry = initDirectory(path);

            // Find parent directories as well
            if (entry.parent() == null) {
                ListedDirectory d = entry;
                while (true) {
                    int d_path_end = d.path().lastIndexOf('/');
                    if (d_path_end == -1) {
                        // namespace is the parent
                        d.setParent(this);
                        break;
                    }

                    // find or create this directory as well, then continue
                    String parent_dir_path = d.path().substring(0, d_path_end);
                    ListedDirectory dp = initDirectory(parent_dir_path);
                    d.setParent(dp);
                    if (dp.parent() != null) {
                        break;
                    }

                    // Recurse down to root
                    d = dp;
                }
            }

            return entry;
        }

        private ListedDirectory initDirectory(String path) {
            return directories.computeIfAbsent(path, p -> new ListedDirectory(this, p));
        }

        /**
         * Gets the namespace name represented by this listed namespace entry
         *
         * @return namespace
         */
        @Override
        public String name() {
            return name;
        }

        @Override
        public String fullPath() {
            return name + ":";
        }

        @Override
        public int sortPriority() {
            return 1;
        }

        @Override
        public ListedNamespace namespace() {
            return this;
        }

        @Override
        public ItemStack item() {
            return item;
        }

        @Override
        public String toString() {
            return "Namespace: " + name;
        }
    }

    /**
     * Root entry. Should not be displayed.
     */
    public static final class ListedRoot extends ListedEntry {

        @Override
        protected void postInitialize() {
        }

        @Override
        public String name() {
            return "";
        }

        @Override
        public String fullPath() {
            return "";
        }

        @Override
        public int sortPriority() {
            return 0;
        }

        @Override
        public ItemStack item() {
            return null; // Unused
        }

        @Override
        public ListedNamespace namespace() {
            return null;
        }

        /**
         * Gets a list of root namespaces in which custom models exist
         *
         * @return namespaces
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        public List<ListedNamespace> namespaces() {
            return (List) this.children();
        }

        @Override
        public String toString() {
            return "<ROOT>";
        }
    }

    /**
     * A type of listed entry. Can be an item model, a directory, or
     * a combination of these.
     */
    public static abstract class ListedEntry implements Comparable<ListedEntry> {
        private ListedEntry parent = null;
        /**
         * Direct children of this entry. Is sorted by the number of nested item models
         * contained within, which is important for calculating the displayed items list.
         */
        private List<ListedEntry> children = Collections.emptyList();
        protected int nestedItemCount = 0;

        /**
         * Returns the item that should be displayed for this entry
         *
         * @return item
         */
        public abstract ItemStack item();

        /**
         * Gets the name of this entry. The entry is sorted alphabetically based
         * on this name.
         *
         * @return name
         */
        public abstract String name();

        /**
         * Gets the full path of this entry. This includes the namespace and any
         * parent directories.
         *
         * @return full path
         */
        public abstract String fullPath();

        /**
         * Overrides sort priority. Lower priority entries are displayed before
         * higher priority number entries.
         *
         * @return sort priority
         */
        public abstract int sortPriority();

        /**
         * Gets the namespace this entry is for
         *
         * @return namespace
         */
        public abstract ListedNamespace namespace();

        /**
         * Gets the entry that contains this entry. Returns null if this is the
         * root entry.
         *
         * @return parent
         */
        public final ListedEntry parent() {
            return parent;
        }

        /**
         * Gets the total number of item models contained by this entry and it's
         * nested children.
         *
         * @return Total item count
         */
        public final int nestedItemCount() {
            return nestedItemCount;
        }

        /**
         * Gets a list of children that are contained by this entry
         *
         * @return list of children
         */
        public final List<ListedEntry> children() {
            return children;
        }

        /**
         * Explodes all nested children, only returning the nested item model entries contained
         * within. The returned list is sorted by item model name alphabetically.
         *
         * @return list of all item models
         */
        public List<ListedItemModel> explode() {
            List<ListedItemModel> items = new ArrayList<>(this.nestedItemCount);
            this.fillItems(items);
            Collections.sort(items); // Sort by name alphabetically
            return items;
        }

        protected void fillItems(List<ListedItemModel> items) {
            for (ListedEntry child : children()) {
                child.fillItems(items);
            }
        }

        /**
         * Tries to compact this item, if this item contains only a single item model
         *
         * @return the one item model entry, or this
         */
        public final ListedEntry compact() {
            // If this entry only contains a single child, pick the child instead
            // This will render as the full path anyway, so it's fine
            // If only a single item model is contained, it will return that one item model
            ListedEntry e = this;
            while (e.children().size() == 1) {
                e = e.children().get(0);
            }
            return e;
        }

        /**
         * Generates a List of entries that should be displayed when this entry is being
         * displayed. By default will list all direct children, but if there's less items
         * than the limit specified, will unpack one or more children to fill the space.<br>
         * <br>
         * Might return more than the number to be displayed, in which case pagination should
         * be used to properly render it.
         *
         * @param numDisplayed Maximum number of entries that are displayed at one time
         * @return displayed items
         */
        public final List<? extends ListedEntry> displayedItems(int numDisplayed) {
            int numChildren = children().size();

            // Probably doesn't happen but just in case
            if (numChildren == 0) {
                return Collections.emptyList();
            }

            // If there are more children than can be displayed, add every child to the list
            // If a child directory only contains a single model, display the model in its place
            // Directories with only one single sub-directory are entered automatically
            if (numChildren >= numDisplayed) {
                return children().stream()
                        .map(ListedEntry::compact)
                        .sorted()
                        .collect(Collectors.toList());
            }

            // If the number of item models is less than to be displayed, explode everything
            // This also avoids too much memory being allocated when numDisplayed is ridiculously high
            if (this.nestedItemCount <= numDisplayed) {
                return this.explode();
            }

            // Try to explode the entries with the least number of item models
            // Other than that, same as compact() logic and then sorting it
            int spaceRemaining = numDisplayed - numChildren;
            List<ListedEntry> entries = new ArrayList<>(numDisplayed);
            for (ListedEntry child : this.children()) {
                ListedEntry e = child.compact();
                if (e.nestedItemCount > 1 && (e.nestedItemCount - 1) <= spaceRemaining) {
                    spaceRemaining -= (e.nestedItemCount - 1);
                    entries.addAll(e.explode());
                } else {
                    entries.add(e);
                }
            }
            Collections.sort(entries);
            return entries;
        }

        @Override
        public int compareTo(ListedEntry o) {
            int sortOrder = Integer.compare(this.sortPriority(), o.sortPriority());
            if (sortOrder != 0) {
                return sortOrder;
            } else {
                return this.name().compareTo(o.name());
            }
        }

        protected void setParent(ListedEntry parent) {
            if (this.parent != parent) {
                if (this.parent != null) {
                    this.parent.children.remove(this);
                    this.parent.updateNestedItemCount(-this.nestedItemCount);
                }
                this.parent = parent;
                if (parent.children.isEmpty()) {
                    parent.children = new ArrayList<>();
                }
                parent.children.add(this);
                parent.updateNestedItemCount(this.nestedItemCount);
            }
        }

        protected void updateNestedItemCount(int increase) {
            for (ListedEntry e = this; e != null; e = e.parent) {
                e.nestedItemCount += increase;
            }
        }

        /**
         * Can be overrides by listed entries to perform post-initialization that requires
         * knowledge of the parent and child entries.
         */
        protected abstract void postInitialize();

        /**
         * Initializes this entry and all child entries, recursively.
         * <ul>
         * <li>Sets up the item displayed in the menu
         * <li>Sorts the children so that the children with the most nested item models are
         *     sorted to the end of the list. Does not use compareTo!
         * </ul>
         */
        protected final void postInitializeAll() {
            this.postInitialize();
            this.children.sort((a, b) -> Integer.compare(a.nestedItemCount, b.nestedItemCount));
            for (ListedEntry e : this.children) {
                e.postInitializeAll();
            }
        }
    }
}
