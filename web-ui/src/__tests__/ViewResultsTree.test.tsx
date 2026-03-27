/**
 * Tests for ViewResultsTree — filter pass/fail, select sample shows details.
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { ViewResultsTree } from '../components/Dashboard/ViewResultsTree.js';
import { useRunStore } from '../store/runStore.js';
import type { MetricsBucket } from '../types/sse-events.js';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeBucket(
  label: string,
  overrides: Partial<MetricsBucket> = {},
): MetricsBucket {
  return {
    timestamp: new Date().toISOString(),
    samplerLabel: label,
    sampleCount: 10,
    errorCount: 0,
    avgResponseTime: 100,
    percentile90: 150,
    percentile95: 200,
    percentile99: 300,
    samplesPerSecond: 5,
    ...overrides,
  };
}

const passSample = makeBucket('login',   { errorCount: 0 });
const failSample = makeBucket('checkout', { errorCount: 3 });

beforeEach(() => {
  useRunStore.getState().reset();
});

afterEach(() => {
  useRunStore.getState().reset();
});

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

describe('ViewResultsTree — empty state', () => {
  it('renders the component wrapper', () => {
    render(<ViewResultsTree samples={[]} />);
    expect(screen.getByTestId('view-results-tree')).toBeInTheDocument();
  });

  it('shows empty placeholder when no samples', () => {
    render(<ViewResultsTree samples={[]} />);
    expect(screen.getByTestId('vrt-empty')).toBeInTheDocument();
  });

  it('shows placeholder text inviting user to start a run', () => {
    render(<ViewResultsTree samples={[]} />);
    expect(screen.getByText(/No data yet/i)).toBeInTheDocument();
  });

  it('shows detail placeholder when nothing is selected', () => {
    render(<ViewResultsTree samples={[passSample]} />);
    expect(screen.getByTestId('vrt-placeholder')).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Filter toolbar
// ---------------------------------------------------------------------------

describe('ViewResultsTree — filter toolbar', () => {
  it('renders All, Pass, and Fail filter buttons', () => {
    render(<ViewResultsTree samples={[passSample, failSample]} />);
    expect(screen.getByTestId('filter-all')).toBeInTheDocument();
    expect(screen.getByTestId('filter-pass')).toBeInTheDocument();
    expect(screen.getByTestId('filter-fail')).toBeInTheDocument();
  });

  it('defaults to "all" filter with All button active', () => {
    render(<ViewResultsTree samples={[passSample, failSample]} />);
    const allBtn = screen.getByTestId('filter-all');
    expect(allBtn.getAttribute('aria-pressed')).toBe('true');
  });

  it('shows result count in the toolbar', () => {
    render(<ViewResultsTree samples={[passSample, failSample]} />);
    expect(screen.getByText(/2 result/i)).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Result list rendering
// ---------------------------------------------------------------------------

describe('ViewResultsTree — result list', () => {
  it('renders a list item for each sample', () => {
    render(<ViewResultsTree samples={[passSample, failSample]} />);
    const listbox = screen.getByRole('listbox');
    const items = within(listbox).getAllByRole('option');
    expect(items).toHaveLength(2);
  });

  it('shows pass icon for successful samples', () => {
    render(<ViewResultsTree samples={[passSample]} />);
    expect(screen.getByLabelText('pass')).toBeInTheDocument();
  });

  it('shows fail icon for failed samples', () => {
    render(<ViewResultsTree samples={[failSample]} />);
    expect(screen.getByLabelText('fail')).toBeInTheDocument();
  });

  it('shows the sampler label text in each list item', () => {
    render(<ViewResultsTree samples={[passSample]} />);
    expect(screen.getByText('login')).toBeInTheDocument();
  });

  it('shows response time in each list item', () => {
    render(<ViewResultsTree samples={[passSample]} />);
    expect(screen.getByText('100 ms')).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Filter: pass only
// ---------------------------------------------------------------------------

describe('ViewResultsTree — pass filter', () => {
  it('shows only passing items when Pass filter is active', () => {
    render(<ViewResultsTree samples={[passSample, failSample]} />);
    fireEvent.click(screen.getByTestId('filter-pass'));

    const listbox = screen.getByRole('listbox');
    const items = within(listbox).getAllByRole('option');
    expect(items).toHaveLength(1);
    expect(within(items[0]!).getByText('login')).toBeInTheDocument();
  });

  it('updates result count after switching to Pass filter', () => {
    render(<ViewResultsTree samples={[passSample, failSample]} />);
    fireEvent.click(screen.getByTestId('filter-pass'));
    expect(screen.getByText(/1 result/i)).toBeInTheDocument();
  });

  it('marks Pass button as active after clicking', () => {
    render(<ViewResultsTree samples={[passSample, failSample]} />);
    fireEvent.click(screen.getByTestId('filter-pass'));
    expect(screen.getByTestId('filter-pass').getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByTestId('filter-all').getAttribute('aria-pressed')).toBe('false');
  });
});

// ---------------------------------------------------------------------------
// Filter: fail only
// ---------------------------------------------------------------------------

describe('ViewResultsTree — fail filter', () => {
  it('shows only failing items when Fail filter is active', () => {
    render(<ViewResultsTree samples={[passSample, failSample]} />);
    fireEvent.click(screen.getByTestId('filter-fail'));

    const listbox = screen.getByRole('listbox');
    const items = within(listbox).getAllByRole('option');
    expect(items).toHaveLength(1);
    expect(within(items[0]!).getByText('checkout')).toBeInTheDocument();
  });

  it('shows "no results" message when filter matches nothing', () => {
    render(<ViewResultsTree samples={[passSample]} />);
    fireEvent.click(screen.getByTestId('filter-fail'));
    expect(screen.getByTestId('vrt-empty')).toBeInTheDocument();
    expect(screen.getByText(/No results match/i)).toBeInTheDocument();
  });

  it('marks Fail button as active after clicking', () => {
    render(<ViewResultsTree samples={[passSample, failSample]} />);
    fireEvent.click(screen.getByTestId('filter-fail'));
    expect(screen.getByTestId('filter-fail').getAttribute('aria-pressed')).toBe('true');
  });
});

// ---------------------------------------------------------------------------
// Selecting a sample shows detail panel
// ---------------------------------------------------------------------------

describe('ViewResultsTree — detail panel', () => {
  it('shows detail panel when a result is clicked', () => {
    render(<ViewResultsTree samples={[passSample]} />);
    const listbox = screen.getByRole('listbox');
    const item = within(listbox).getAllByRole('option')[0]!;
    fireEvent.click(item);
    expect(screen.getByTestId('vrt-detail')).toBeInTheDocument();
  });

  it('hides placeholder when detail panel is shown', () => {
    render(<ViewResultsTree samples={[passSample]} />);
    fireEvent.click(within(screen.getByRole('listbox')).getAllByRole('option')[0]!);
    expect(screen.queryByTestId('vrt-placeholder')).not.toBeInTheDocument();
  });

  it('shows the label in the detail panel', () => {
    render(<ViewResultsTree samples={[passSample]} />);
    fireEvent.click(within(screen.getByRole('listbox')).getAllByRole('option')[0]!);
    const detail = screen.getByTestId('vrt-detail');
    // Label appears in detail table
    expect(within(detail).getAllByText('login').length).toBeGreaterThan(0);
  });

  it('shows response code 200 for a passing sample', () => {
    render(<ViewResultsTree samples={[passSample]} />);
    fireEvent.click(within(screen.getByRole('listbox')).getAllByRole('option')[0]!);
    const detail = screen.getByTestId('vrt-detail');
    expect(within(detail).getByText('200')).toBeInTheDocument();
  });

  it('shows response code 500 for a failing sample', () => {
    render(<ViewResultsTree samples={[failSample]} />);
    fireEvent.click(within(screen.getByRole('listbox')).getAllByRole('option')[0]!);
    const detail = screen.getByTestId('vrt-detail');
    expect(within(detail).getByText('500')).toBeInTheDocument();
  });

  it('shows "false" as success value for failing sample', () => {
    render(<ViewResultsTree samples={[failSample]} />);
    fireEvent.click(within(screen.getByRole('listbox')).getAllByRole('option')[0]!);
    const detail = screen.getByTestId('vrt-detail');
    expect(within(detail).getByText('false')).toBeInTheDocument();
  });

  it('shows "true" as success value for passing sample', () => {
    render(<ViewResultsTree samples={[passSample]} />);
    fireEvent.click(within(screen.getByRole('listbox')).getAllByRole('option')[0]!);
    const detail = screen.getByTestId('vrt-detail');
    expect(within(detail).getByText('true')).toBeInTheDocument();
  });

  it('deselects item when clicked again, showing placeholder', () => {
    render(<ViewResultsTree samples={[passSample]} />);
    const item = within(screen.getByRole('listbox')).getAllByRole('option')[0]!;
    fireEvent.click(item);
    expect(screen.getByTestId('vrt-detail')).toBeInTheDocument();
    fireEvent.click(item);
    expect(screen.getByTestId('vrt-placeholder')).toBeInTheDocument();
  });

  it('clears selection when switching filter', () => {
    render(<ViewResultsTree samples={[passSample, failSample]} />);
    const item = within(screen.getByRole('listbox')).getAllByRole('option')[0]!;
    fireEvent.click(item);
    expect(screen.getByTestId('vrt-detail')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('filter-fail'));
    expect(screen.queryByTestId('vrt-detail')).not.toBeInTheDocument();
    expect(screen.getByTestId('vrt-placeholder')).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Store integration
// ---------------------------------------------------------------------------

describe('ViewResultsTree — store integration', () => {
  it('reads samples from runStore when no prop is provided', () => {
    useRunStore.getState().addSample(makeBucket('store-test'));
    render(<ViewResultsTree />);
    const listbox = screen.getByRole('listbox');
    expect(within(listbox).getAllByRole('option')).toHaveLength(1);
    expect(screen.getByText('store-test')).toBeInTheDocument();
  });
});
