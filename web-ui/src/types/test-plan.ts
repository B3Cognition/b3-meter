/**
 * Core types for the JMeter test plan tree structure.
 */

export interface TestPlanNode {
  id: string;
  /** Element type, e.g. 'ThreadGroup' | 'HTTPSampler' | 'ResponseAssertion' */
  type: string;
  name: string;
  enabled: boolean;
  properties: Record<string, unknown>;
  children: TestPlanNode[];
}

export interface TestPlanTree {
  root: TestPlanNode;
}
