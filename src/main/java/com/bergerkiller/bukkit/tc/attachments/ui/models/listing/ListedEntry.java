package com.bergerkiller.bukkit.tc.attachments.ui.models.listing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.inventory.ItemStack;

/**
 * A type of listed entry. Can be an item model, a directory, or
 * a combination of these.
 */
public abstract class ListedEntry implements Comparable<ListedEntry> {
    private ListedEntry parent = null;
    /**
     * Direct children of this entry. Is sorted by the number of nested item models
     * contained within, which is important for calculating the displayed items list.
     */
    private List<ListedEntry> children = Collections.emptyList();
    protected int nestedItemCount = 0;
    protected boolean isPostInitialized = false;

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
     * Lower-cased version of {@link #name()}. Useful for searches.
     *
     * @return name, all lower-case
     */
    public abstract String nameLowerCase();

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
     * Attempts to navigate the children of this entry recursively to find the entry
     * at the path specified. May return more than one element if the last path part
     * matches multiple entries that start with that token.
     *
     * @param pathParts Parts to match
     * @return List of matching entries, can be empty
     */
    public final List<ListedEntry> matchWithPathPrefix(Iterable<String> pathParts) {
        Iterator<String> iter = pathParts.iterator();
        if (!iter.hasNext()) {
            return Collections.singletonList(this); // Empty path
        }

        ListedEntry curr = this;
        while (true) {
            String tokenLower = iter.next().toLowerCase(Locale.ENGLISH);
            boolean isLastToken = !iter.hasNext();
            if (isLastToken) {
                // Match with starts with and return result
                List<ListedEntry> result = new ArrayList<>(3);
                for (ListedEntry e : curr.children()) {
                    if (e.nameLowerCase().startsWith(tokenLower)) {
                        result.add(e);
                    }
                }
                return result;
            } else {
                // Match exactly (ignore case)
                ListedEntry next = null;
                for (ListedEntry e : curr.children()) {
                    if (e.nameLowerCase().equals(tokenLower)) {
                        next = e;
                        break;
                    }
                }
                if (next != null) {
                    curr = next;
                } else {
                    return Collections.emptyList(); // Not found
                }
            }
        }
    }

    /**
     * Matches all child entries of this entry whose name includes the token specified.
     * This is done recursively.
     *
     * @param token
     * @return List of matching listed entries
     */
    public final List<ListedEntry> matchChildrenNameContains(String token) {
        String tokenLower = token.toLowerCase(Locale.ENGLISH);
        ArrayList<ListedEntry> result = new ArrayList<>(10);
        for (ListedEntry child : children()) {
            child.fillMatchingContains(tokenLower, result);
        }
        return result;
    }

    private void fillMatchingContains(String tokenLower, List<ListedEntry> result) {
        if (nameLowerCase().contains(tokenLower)) {
            result.add(this);
        } else {
            for (ListedEntry child : children()) {
                child.fillMatchingContains(tokenLower, result);
            }
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
     * Clones this entry. The cloned entry will be parented to the new parent
     * specified. Does not run the usual setParent code and copies the nested item
     * count as-is.
     *
     * @param newParent The new parent entry
     */
    protected final ListedEntry assignCloneTo(ListedEntry newParent) {
        ListedEntry clone = unsafeClone(newParent);
        clone.parent = null; // do setParent handling
        clone.setParent(newParent);
        return clone;
    }

    private final ListedEntry unsafeClone(ListedEntry newParent) {
        ListedEntry clone = this.cloneSelf(newParent == null ? null : newParent.namespace());
        clone.isPostInitialized = true; // No need to setup the Item again
        clone.parent = newParent;
        clone.nestedItemCount = this.nestedItemCount;
        if (!this.children.isEmpty()) {
            clone.children = new ArrayList<>(this.children.size());
            for (ListedEntry child : this.children) {
                //TODO: Recursion could go bad...
                clone.children.add(child.unsafeClone(clone));
            }
        }
        return clone;
    }

    /**
     * Attempts to find or re-create this entry in a new listing root. Nested item count
     * and children will be updated, with the items initialized after
     * {@link #postInitializeAll()} is called.
     *
     * @param root New root to assign to
     * @return cloned entry, now assigned to root. Is null if this is a root entry.
     */
    protected abstract ListedEntry findOrCreateInRoot(ListedRoot root);

    /**
     * Clones this entry and assigns it to a new listing root. Any required parent
     * directories and namespaces are created in the root first.
     *
     * @param root
     * @return
     */
    protected final ListedEntry assignToRoot(ListedRoot root) {
        ListedEntry parent = this.parent().findOrCreateInRoot(root);
        return this.assignCloneTo(parent);
    }

    /**
     * Clones this entry itself. Parent and children don't have to be updated here, as that's
     * done by {@link #clone(ListedEntry)} itself.
     *
     * @param namespace The namespace in which the new entry should reside. Is null if no namespace
     *                  is known yet (root or namespace entry).
     */
    protected abstract ListedEntry cloneSelf(ListedNamespace namespace);

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
    protected void postInitializeAll() {
        if (!isPostInitialized) {
            isPostInitialized = true;
            postInitialize();
            this.children.sort((a, b) -> Integer.compare(a.nestedItemCount, b.nestedItemCount));
            for (ListedEntry e : this.children) {
                e.postInitializeAll();
            }
        }
    }
}