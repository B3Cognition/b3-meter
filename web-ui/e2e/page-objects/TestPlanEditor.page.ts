/**
 * T047 — TestPlanEditor Page Object
 *
 * Encapsulates all interactions with the test plan editor UI:
 *   - Tree node operations (add, select, delete)
 *   - Node count queries
 *   - Undo / redo keyboard shortcuts
 *   - JMX import / export
 *
 * Usage:
 *   import { TestPlanEditorPage } from './TestPlanEditor.page.js';
 *
 *   test('add a node', async ({ page }) => {
 *     const editor = new TestPlanEditorPage(page);
 *     await editor.goto();
 *     await editor.addNode('ThreadGroup', 'My Group');
 *     expect(await editor.getTreeNodeCount()).toBe(2);
 *   });
 */

import type { Page, Locator } from '@playwright/test';

export class TestPlanEditorPage {
  readonly page: Page;

  // ---------------------------------------------------------------------------
  // Locators
  // ---------------------------------------------------------------------------

  /** The tree root container rendered by react-arborist. */
  readonly tree: Locator;

  /** All rendered tree row nodes. */
  readonly treeNodes: Locator;

  /** The "No test plan loaded" empty-state message. */
  readonly emptyState: Locator;

  /** The file input for importing a .jmx file. */
  readonly importInput: Locator;

  /** The "Import" button that opens the file picker. */
  readonly importButton: Locator;

  /** The "Export" button. */
  readonly exportButton: Locator;

  constructor(page: Page) {
    this.page = page;

    this.tree       = page.locator('[role="tree"]');
    this.treeNodes  = page.locator('[role="treeitem"]');
    this.emptyState = page.getByText('No test plan loaded');
    this.importInput  = page.locator('input[type="file"]');
    this.importButton = page.getByRole('button', { name: /import/i });
    this.exportButton = page.getByRole('button', { name: /export/i });
  }

  // ---------------------------------------------------------------------------
  // Navigation
  // ---------------------------------------------------------------------------

  /**
   * Navigate to the test plan editor route.
   * Defaults to the root path — adjust if the app uses a sub-route.
   */
  async goto(path = '/'): Promise<void> {
    await this.page.goto(path);
    await this.page.waitForLoadState('networkidle');
  }

  // ---------------------------------------------------------------------------
  // Tree operations
  // ---------------------------------------------------------------------------

  /**
   * Return the number of tree nodes currently visible.
   *
   * This counts `[role="treeitem"]` elements rendered by react-arborist.
   */
  async getTreeNodeCount(): Promise<number> {
    return this.treeNodes.count();
  }

  /**
   * Select a tree node by its visible label text.
   *
   * @param name - Exact or partial display name of the node.
   */
  async selectNode(name: string): Promise<void> {
    await this.treeNodes.filter({ hasText: name }).first().click();
  }

  /**
   * Open the context menu for a node and click "Add Child".
   *
   * @param parentName - The visible name of the parent node to right-click.
   */
  async addNode(parentName: string): Promise<void> {
    await this.treeNodes.filter({ hasText: parentName }).first().click({ button: 'right' });
    await this.page.getByRole('button', { name: 'Add Child' }).click();
  }

  /**
   * Open the context menu for a node and click "Delete".
   *
   * @param nodeName - The visible name of the node to delete.
   */
  async deleteNode(nodeName: string): Promise<void> {
    await this.treeNodes.filter({ hasText: nodeName }).first().click({ button: 'right' });
    await this.page.getByRole('button', { name: 'Delete' }).click();
  }

  /**
   * Open the context menu for a node and click "Duplicate".
   *
   * @param nodeName - The visible name of the node to duplicate.
   */
  async duplicateNode(nodeName: string): Promise<void> {
    await this.treeNodes.filter({ hasText: nodeName }).first().click({ button: 'right' });
    await this.page.getByRole('button', { name: 'Duplicate' }).click();
  }

  /**
   * Toggle the enabled/disabled state of a node via the context menu.
   *
   * @param nodeName - The visible name of the node.
   */
  async toggleNodeEnabled(nodeName: string): Promise<void> {
    await this.treeNodes.filter({ hasText: nodeName }).first().click({ button: 'right' });
    // The button reads "Disable" when node is enabled, "Enable" when disabled.
    const btn = this.page.getByRole('button', { name: /^(enable|disable)$/i });
    await btn.click();
  }

  // ---------------------------------------------------------------------------
  // Undo / Redo
  // ---------------------------------------------------------------------------

  /**
   * Trigger Undo via the standard keyboard shortcut (Ctrl+Z / Cmd+Z).
   */
  async undo(): Promise<void> {
    const modifier = process.platform === 'darwin' ? 'Meta' : 'Control';
    await this.page.keyboard.press(`${modifier}+z`);
  }

  /**
   * Trigger Redo via the standard keyboard shortcut (Ctrl+Shift+Z / Cmd+Shift+Z).
   */
  async redo(): Promise<void> {
    const modifier = process.platform === 'darwin' ? 'Meta' : 'Control';
    await this.page.keyboard.press(`${modifier}+Shift+z`);
  }

  // ---------------------------------------------------------------------------
  // Import / Export
  // ---------------------------------------------------------------------------

  /**
   * Import a .jmx file by setting it on the hidden file input.
   *
   * @param filePath - Absolute or relative path to the .jmx file on disk.
   */
  async importJmx(filePath: string): Promise<void> {
    // The file input may be hidden; use setInputFiles regardless.
    await this.importInput.setInputFiles(filePath);
    // Wait for the tree to appear after import.
    await this.tree.waitFor({ state: 'visible', timeout: 10_000 });
  }

  /**
   * Click the Export button and wait for the download to start.
   *
   * @returns The Playwright Download object for further assertions.
   */
  async exportJmx() {
    const [download] = await Promise.all([
      this.page.waitForEvent('download'),
      this.exportButton.click(),
    ]);
    return download;
  }

  // ---------------------------------------------------------------------------
  // Assertions (convenience wrappers)
  // ---------------------------------------------------------------------------

  /**
   * Wait until the tree contains at least `count` nodes.
   * Useful after async operations (import, add) that trigger a re-render.
   */
  async waitForNodeCount(count: number, timeout = 5_000): Promise<void> {
    await this.page.waitForFunction(
      (expected) => document.querySelectorAll('[role="treeitem"]').length >= expected,
      count,
      { timeout },
    );
  }

  /**
   * Return true if the empty-state placeholder is visible.
   */
  async isEmptyStateVisible(): Promise<boolean> {
    return this.emptyState.isVisible();
  }
}
