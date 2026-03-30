// Copyright 2024-2026 b3meter Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
import type { NodeRendererProps } from 'react-arborist';
import type { TestPlanNode } from '../../types/test-plan.js';
import type { LucideIcon } from 'lucide-react';
import {
  FlaskConical,
  FolderOpen,
  Users,
  Globe,
  Cable,
  Database,
  HardDrive,
  Mail,
  CheckCircle,
  Timer,
  Ruler,
  Clock,
  Table,
  FileText,
  Cookie,
  Settings,
  Repeat,
  GitBranch,
  RefreshCw,
  ArrowRightLeft,
  ChevronRight,
  Search,
  Braces,
  BarChart3,
  FileBarChart,
  LayoutGrid,
  FileQuestion,
} from 'lucide-react';

// ---------------------------------------------------------------------------
// Icon configuration
// ---------------------------------------------------------------------------

interface IconConfig {
  icon: LucideIcon;
  color?: string;
  /** Optional second icon rendered overlapping (for composite icons). */
  secondIcon?: LucideIcon;
  secondColor?: string;
}

/** Maps JMeter element types to Lucide icon configurations. */
const TYPE_ICON_CONFIG: Record<string, IconConfig> = {
  // Plan / workspace
  TestPlan:               { icon: FlaskConical,   color: '#0d9488' },
  Workspace:              { icon: FolderOpen },

  // Thread groups
  ThreadGroup:            { icon: Users,          color: '#3b82f6' },

  // Samplers
  HTTPSampler:            { icon: Globe,          color: '#3b82f6' },
  HTTPSamplerProxy:       { icon: Globe,          color: '#3b82f6' },
  TCPSampler:             { icon: Cable },
  JDBCSampler:            { icon: Database },
  FTPSampler:             { icon: HardDrive },
  SMTPSampler:            { icon: Mail },

  // Assertions
  ResponseAssertion:      { icon: CheckCircle,    color: '#16a34a' },
  DurationAssertion:      { icon: Timer,          secondIcon: CheckCircle, secondColor: '#16a34a' },
  SizeAssertion:          { icon: Ruler },

  // Timers
  ConstantTimer:          { icon: Clock,          color: '#ea580c' },
  GaussianRandomTimer:    { icon: Clock,          color: '#ea580c' },
  UniformRandomTimer:     { icon: Clock,          color: '#ea580c' },

  // Config elements
  CSVDataSet:             { icon: Table },
  HeaderManager:          { icon: FileText },
  HTTPHeaderManager:      { icon: FileText },
  CookieManager:          { icon: Cookie },
  HTTPCookieManager:      { icon: Cookie },
  UserDefinedVariables:   { icon: Settings },
  HTTPRequestDefaults:    { icon: Globe,          secondIcon: Settings },

  // Controllers
  LoopController:         { icon: Repeat },
  IfController:           { icon: GitBranch },
  WhileController:        { icon: RefreshCw },
  TransactionController:  { icon: ArrowRightLeft },
  SimpleController:       { icon: ChevronRight },

  // Post-processors / extractors
  RegexExtractor:         { icon: Search },
  JSONPathExtractor:      { icon: Braces },
  JSONPostProcessor:      { icon: Braces },

  // Listeners
  ResultCollector:        { icon: BarChart3,      color: '#db2777' },
  ViewResultsTree:        { icon: BarChart3,      color: '#db2777' },
  Summariser:             { icon: FileBarChart },
  SummaryReport:          { icon: FileBarChart },
  AggregateReport:        { icon: LayoutGrid },
};

const DEFAULT_ICON_CONFIG: IconConfig = { icon: FileQuestion };

// ---------------------------------------------------------------------------
// Icon rendering helper
// ---------------------------------------------------------------------------

interface NodeIconProps {
  type: string;
  disabled: boolean;
}

/** Renders the Lucide icon(s) for a given JMeter element type. */
function NodeIcon({ type, disabled }: NodeIconProps) {
  const config = TYPE_ICON_CONFIG[type] ?? DEFAULT_ICON_CONFIG;
  const disabledStyle = disabled ? { opacity: 0.4, color: '#888' } : undefined;

  const Icon = config.icon;
  const primaryColor = disabled ? '#888' : config.color;

  if (config.secondIcon) {
    const SecondIcon = config.secondIcon;
    const secondaryColor = disabled ? '#888' : (config.secondColor ?? config.color);
    return (
      <span className="node-icon" style={{ position: 'relative', display: 'inline-flex', ...disabledStyle }}>
        <Icon size={16} color={primaryColor} />
        <SecondIcon
          size={10}
          color={secondaryColor}
          style={{ position: 'absolute', right: -3, bottom: -2 }}
        />
      </span>
    );
  }

  return (
    <span className="node-icon" style={disabledStyle}>
      <Icon size={16} color={primaryColor} />
    </span>
  );
}

// ---------------------------------------------------------------------------
// NodeRenderer component
// ---------------------------------------------------------------------------

/**
 * NodeRenderer component used as the children render prop of react-arborist's Tree.
 *
 * @param props - NodeRendererProps from react-arborist, typed to TestPlanNode.
 */
export function NodeRenderer({
  node,
  style,
  dragHandle,
}: NodeRendererProps<TestPlanNode>) {
  const isDisabled = !node.data.enabled;

  return (
    <div
      ref={dragHandle}
      style={style}
      className={[
        'tree-node',
        node.isSelected ? 'selected' : '',
        isDisabled ? 'disabled' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      onClick={() => node.select()}
    >
      <NodeIcon type={node.data.type} disabled={isDisabled} />
      <span className="node-name">{node.data.name}</span>
      {isDisabled && <span className="node-badge">disabled</span>}
    </div>
  );
}
