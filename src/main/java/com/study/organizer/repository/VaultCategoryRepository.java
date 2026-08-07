package com.study.organizer.repository;

import com.study.organizer.vault.ObsidianVault;

import java.util.List;

/**
 * Categories, stored as the folders of an Obsidian vault.
 *
 * <p>There is nothing to keep in step here: creating a category creates a
 * folder, and listing the categories lists the folders. A category you make in
 * Obsidian by adding a folder appears in the application, and one you make in
 * the application appears in Obsidian. They cannot drift apart because there is
 * only one of them.
 */
public class VaultCategoryRepository implements CategoryRepository {

    private final ObsidianVault vault;

    /**
     * @param vault the vault whose folders are the categories
     */
    public VaultCategoryRepository(ObsidianVault vault) {
        this.vault = vault;
    }

    @Override
    public void ensureExists(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        vault.ensureCategory(name.trim());
    }

    @Override
    public List<String> findAll() {
        return vault.listCategories();
    }
}
