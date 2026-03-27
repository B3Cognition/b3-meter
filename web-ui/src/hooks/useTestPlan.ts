/**
 * useTestPlan — React hook for loading, saving, creating, and importing test plans.
 *
 * Bridges the plans API with testPlanStore and uiStore so callers only
 * need to call simple verbs (load, save, create, importFile).
 */

import { useTestPlanStore } from '../store/testPlanStore.js';
import { useUiStore } from '../store/uiStore.js';
import { getPlan, updatePlan, createPlan, importJmx } from '../api/plans.js';

export function useTestPlan() {
  const { setTree, tree } = useTestPlanStore();
  const { setSaveStatus } = useUiStore();

  /**
   * Load a test plan from the backend and replace the current tree.
   * Sets saveStatus to 'error' if the request fails.
   */
  const load = async (id: string): Promise<void> => {
    try {
      const plan = await getPlan(id);
      if (plan.tree) setTree(plan.tree);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load plan';
      setSaveStatus('error', message);
      throw err;
    }
  };

  /**
   * Save the current tree back to the backend.
   * Updates saveStatus throughout the lifecycle.
   */
  const save = async (id: string): Promise<void> => {
    if (tree === null) return;

    setSaveStatus('saving');
    try {
      await updatePlan(id, { tree });
      setSaveStatus('saved');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to save plan';
      setSaveStatus('error', message);
      throw err;
    }
  };

  /**
   * Create a new test plan with the given name.
   * Loads the newly created plan's tree into the store.
   */
  const create = async (name: string): Promise<void> => {
    try {
      const plan = await createPlan({ name });
      if (plan.tree) setTree(plan.tree);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to create plan';
      setSaveStatus('error', message);
      throw err;
    }
  };

  /**
   * Import a .jmx file as a new test plan.
   * Loads the imported plan's tree into the store on success.
   */
  const importFile = async (file: File): Promise<void> => {
    try {
      const plan = await importJmx(file);
      if (plan.tree) setTree(plan.tree);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to import plan';
      setSaveStatus('error', message);
      throw err;
    }
  };

  return { load, save, create, importFile };
}
