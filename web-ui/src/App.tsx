import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { Upload, Download, FilePlus, Undo2, Redo2, Play, Square, StopCircle, Code2, TreePine, ChevronDown, ChevronRight, Activity, GitCompareArrows, Zap, Shield } from 'lucide-react'
import { TestPlanTree } from './components/TreeEditor'
import { TreeSearch } from './components/TreeEditor/TreeSearch'
import { PropertyPanel } from './components/PropertyPanel/PropertyPanel'
import { ResultsTabs } from './components/Dashboard/ResultsTabs'
import { ThemePicker } from './components/ThemePicker/ThemePicker'
import { MenuBar } from './components/MenuBar/MenuBar'
import { XmlEditor } from './components/XmlEditor/XmlEditor'
import { LogViewer } from './components/LogViewer/LogViewer'
import { SLADiscovery } from './components/Innovation/SLADiscovery'
import { ABPerformance } from './components/Innovation/ABPerformance'
import { ChaosLoad } from './components/Innovation/ChaosLoad'
import { SelfSmoke } from './components/SelfSmoke/SelfSmoke'
import { useTestPlanStore } from './store/testPlanStore'
import { useRunStore } from './store/runStore'
import { useLogStore } from './store/logStore'
import { useUiStore } from './store/uiStore'
import { useRunTimer } from './hooks/useRunTimer'
import { applyTheme, getSavedTheme } from './themes/themes'
import { importJmx, listPlans, updatePlan, getPlan } from './api/plans'
import { startRun, stopRun, stopRunNow } from './api/runs'
import { setClipboardNode, getClipboardNode } from './components/TreeEditor/NodeContextMenu'
import type { TestPlanNode } from './types/test-plan'
import './App.css'

export function App() {
  const { tree, setTree, selectedNodeId } = useTestPlanStore()
  const temporalStore = useTestPlanStore.temporal
  const { status, runId, setRunId, setStatus } = useRunStore()
  const [activeTab, setActiveTab] = useState<'visual' | 'xml' | 'results' | 'sla' | 'ab' | 'chaos' | 'smoke'>('visual')
  const [activeRunId, setActiveRunId] = useState<string | null>(null)
  const [treeCollapsed, setTreeCollapsed] = useState(false)
  const planXmlMap = useUiStore(s => s.planXmlMap)
  const setPlanXmlMap = useUiStore(s => s.setPlanXmlMap)
  const setPlanXml = useUiStore(s => s.setPlanXml)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => { applyTheme(getSavedTheme()) }, [])

  // Load saved plans from backend on startup
  useEffect(() => {
    listPlans().then(plans => {
      if (plans.length > 0 && !tree) {
        const children = plans.map(p => ({
          id: p.id,
          type: 'TestPlan' as const,
          name: p.name,
          enabled: true,
          properties: {} as Record<string, unknown>,
          children: [] as any[],
        }))
        setTree({
          root: {
            id: 'workspace',
            type: 'Workspace',
            name: 'Test Plans',
            enabled: true,
            properties: {},
            children,
          },
        })

        // Store raw JMX XML (treeData) for each plan so the XML editor can display it
        const xmlMap: Record<string, string> = {}
        plans.forEach(p => { if (p.treeData) xmlMap[p.id] = p.treeData })
        setPlanXmlMap(xmlMap)

        // For plans without treeData in the list response, fetch individually
        const missing = plans.filter(p => !p.treeData)
        if (missing.length > 0) {
          Promise.all(missing.map(p =>
            getPlan(p.id).then(full => {
              if (full.treeData) xmlMap[full.id] = full.treeData
            }).catch(() => { /* ignore */ })
          )).then(() => setPlanXmlMap({ ...xmlMap }))
        }
      }
    }).catch(() => { /* backend may not be running */ })
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Reconnect to active runs after page refresh
  useEffect(() => {
    fetch('/api/v1/runs').then(r => r.json()).then((runs: any[]) => {
      const active = runs.find((r: any) => r.status === 'RUNNING')
      if (active) {
        setRunId(active.id)
        setActiveRunId(active.id)
        setStatus('running')
        setActiveTab('results')
      }
    }).catch(() => {})
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Poll for run completion — checks both 'starting' and 'running' states
  useEffect(() => {
    if ((status !== 'running' && status !== 'starting') || !runId) return
    const checkStatus = async () => {
      try {
        const res = await fetch(`/api/v1/runs/${runId}`)
        if (res.ok) {
          const data = await res.json()
          const s = (data.status || '').toUpperCase()
          if (s === 'STOPPED' || s === 'ERROR' || s === 'COMPLETED') {
            setStatus(s === 'ERROR' ? 'error' : 'stopped')
          }
        }
      } catch { /* ignore */ }
    }
    // Check immediately, then every 1s (fast runs finish before first 2s interval)
    checkStatus()
    const interval = setInterval(checkStatus, 1000)
    return () => clearInterval(interval)
  }, [status, runId, setStatus])

  // ─── Keyboard Shortcuts ───
  const handleSave = useCallback(async () => {
    if (!tree) return
    const planId = tree.root.id
    try {
      await updatePlan(planId, { tree })
    } catch {
      // Backend may not be running — silently ignore
    }
  }, [tree])

  useEffect(() => {
    function findNodeById(root: TestPlanNode, id: string): TestPlanNode | undefined {
      if (root.id === id) return root
      for (const child of root.children) {
        const found = findNodeById(child, id)
        if (found) return found
      }
      return undefined
    }

    function findParent(root: TestPlanNode, targetId: string): TestPlanNode | undefined {
      for (const child of root.children) {
        if (child.id === targetId) return root
        const found = findParent(child, targetId)
        if (found) return found
      }
      return undefined
    }

    function deepClone(node: TestPlanNode, suffix: string): TestPlanNode {
      return {
        ...node,
        id: `${node.id}-${suffix}`,
        name: `${node.name} (copy)`,
        properties: { ...node.properties },
        children: node.children.map((c) => deepClone(c, suffix)),
      }
    }

    function handleKeyDown(e: KeyboardEvent) {
      const target = e.target as HTMLElement
      // Don't intercept shortcuts when typing in input fields
      if (
        target.tagName === 'INPUT' ||
        target.tagName === 'TEXTAREA' ||
        target.tagName === 'SELECT' ||
        target.isContentEditable
      ) {
        return
      }

      const ctrl = e.ctrlKey || e.metaKey
      const shift = e.shiftKey
      const key = e.key.toLowerCase()

      // Ctrl+Z → Undo
      if (ctrl && !shift && key === 'z') {
        e.preventDefault()
        temporalStore?.getState().undo()
        return
      }

      // Ctrl+Y or Ctrl+Shift+Z → Redo
      if ((ctrl && key === 'y') || (ctrl && shift && key === 'z')) {
        e.preventDefault()
        temporalStore?.getState().redo()
        return
      }

      // Ctrl+R → Start run
      if (ctrl && key === 'r') {
        e.preventDefault()
        handleRun()
        return
      }

      // Ctrl+S → Save
      if (ctrl && key === 's') {
        e.preventDefault()
        handleSave()
        return
      }

      // Delete → Delete selected node
      if (e.key === 'Delete' && !ctrl && !shift) {
        const state = useTestPlanStore.getState()
        if (state.selectedNodeId && state.tree) {
          // Don't delete root/workspace
          if (state.selectedNodeId !== state.tree.root.id) {
            state.deleteNode(state.selectedNodeId)
          }
        }
        return
      }

      // Ctrl+T → Toggle enable/disable selected node
      if (ctrl && key === 't') {
        e.preventDefault()
        const state = useTestPlanStore.getState()
        if (state.selectedNodeId && state.tree) {
          const node = findNodeById(state.tree.root, state.selectedNodeId)
          if (node) {
            state.updateProperty(node.id, 'enabled', !node.enabled)
          }
        }
        return
      }

      // Ctrl+D → Duplicate selected node
      if (ctrl && key === 'd') {
        e.preventDefault()
        const state = useTestPlanStore.getState()
        if (state.selectedNodeId && state.tree) {
          const node = findNodeById(state.tree.root, state.selectedNodeId)
          if (!node) return
          const parent = findParent(state.tree.root, state.selectedNodeId)
          if (!parent) return // can't duplicate root
          const clone = deepClone(node, `dup-${Date.now()}`)
          const idx = parent.children.findIndex((c) => c.id === state.selectedNodeId)
          state.addNode(parent.id, clone)
          state.moveNode(clone.id, parent.id, idx + 1)
        }
        return
      }

      // Ctrl+C → Copy selected node
      if (ctrl && key === 'c') {
        const state = useTestPlanStore.getState()
        if (state.selectedNodeId && state.tree) {
          const node = findNodeById(state.tree.root, state.selectedNodeId)
          if (node) {
            setClipboardNode(deepClone(node, `clip-${Date.now()}`))
          }
        }
        return
      }

      // Ctrl+V → Paste under selected node
      if (ctrl && key === 'v') {
        const state = useTestPlanStore.getState()
        const clip = getClipboardNode()
        if (state.selectedNodeId && state.tree && clip) {
          const pasted = deepClone(clip, `paste-${Date.now()}`)
          state.addNode(state.selectedNodeId, pasted)
        }
        return
      }

      // Alt+ArrowUp → Move Up
      if (e.altKey && e.key === 'ArrowUp') {
        e.preventDefault()
        const state = useTestPlanStore.getState()
        if (state.selectedNodeId && state.tree) {
          const parent = findParent(state.tree.root, state.selectedNodeId)
          if (!parent) return
          const idx = parent.children.findIndex((c) => c.id === state.selectedNodeId)
          if (idx > 0) {
            state.moveNode(state.selectedNodeId, parent.id, idx - 1)
          }
        }
        return
      }

      // Alt+ArrowDown → Move Down
      if (e.altKey && e.key === 'ArrowDown') {
        e.preventDefault()
        const state = useTestPlanStore.getState()
        if (state.selectedNodeId && state.tree) {
          const parent = findParent(state.tree.root, state.selectedNodeId)
          if (!parent) return
          const idx = parent.children.findIndex((c) => c.id === state.selectedNodeId)
          if (idx >= 0 && idx < parent.children.length - 1) {
            state.moveNode(state.selectedNodeId, parent.id, idx + 2)
          }
        }
        return
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [temporalStore, handleSave]) // eslint-disable-line react-hooks/exhaustive-deps

  // Auto-log run status changes
  const addLog = useLogStore((s) => s.addLog)
  useEffect(() => {
    if (status === 'running') addLog('INFO', `Test run started (runId=${runId})`)
    else if (status === 'stopped') addLog('INFO', `Test run stopped (runId=${runId})`)
    else if (status === 'error') addLog('ERROR', `Test run error (runId=${runId})`)
  }, [status]) // eslint-disable-line react-hooks/exhaustive-deps

  const handleImport = async (file: File) => {
    try {
      const plan = await importJmx(file)
      const importedPlan = { id: plan.id, type: 'TestPlan', name: plan.name, enabled: true, properties: {}, children: [] }
      if (tree) {
        // Add imported plan to existing workspace
        const { addNode } = useTestPlanStore.getState()
        addNode(tree.root.id, importedPlan)
      } else {
        setTree({ root: { id: 'workspace', type: 'Workspace', name: 'Test Plans', enabled: true, properties: {}, children: [importedPlan] } })
      }
      // Store raw JMX XML for the imported plan
      if (plan.treeData) {
        setPlanXml(plan.id, plan.treeData)
      }
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Import failed')
    }
  }

  const handleExport = () => {
    if (!tree) return
    // Export current plan as JMX download
    const planId = tree.root.id
    window.open(`/api/v1/plans/${planId}/export`, '_blank')
  }

  const handleRun = async () => {
    if (!tree) return
    // Use selected node's nearest plan, or first child plan — never 'workspace'
    const firstPlan = tree.root.children?.[0]
    const planId = selectedNodeId && selectedNodeId !== 'workspace'
      ? selectedNodeId
      : firstPlan?.id
    if (!planId) return
    setStatus('starting')
    try {
      const response = await startRun({ planId, ...(runDuration > 0 ? { durationSeconds: runDuration } : {}) })
      const newRunId = response.id || response.runId!
      setRunId(newRunId)
      setActiveRunId(newRunId)
      setStatus('running')
      setActiveTab('results')
      // Start polling for completion and metrics directly — don't rely on useEffect timing
      const pollInterval = setInterval(async () => {
        try {
          const res = await fetch(`/api/v1/runs/${newRunId}`)
          if (res.ok) {
            const data = await res.json()
            const s = (data.status || '').toUpperCase()
            if (s === 'STOPPED' || s === 'ERROR' || s === 'COMPLETED') {
              setStatus(s === 'ERROR' ? 'error' : 'stopped')
              clearInterval(pollInterval)
              // Fetch final metrics and add to store
              try {
                const mRes = await fetch(`/api/v1/runs/${newRunId}/metrics`)
                if (mRes.ok) {
                  const m = await mRes.json()
                  if (m.sampleCount > 0) {
                    useRunStore.getState().addSample(m)
                  }
                }
              } catch { /* ignore */ }
            }
          }
        } catch { /* ignore */ }
      }, 1000)
    } catch (err) {
      setStatus('error')
      alert(err instanceof Error ? err.message : 'Failed to start')
    }
  }

  const handleStop = async () => {
    if (!runId) return
    setStatus('stopping')
    try { await stopRun(runId); setStatus('stopped') }
    catch { setStatus('error') }
  }

  const handleStopNow = async () => {
    if (!runId) return
    try { await stopRunNow(runId); setStatus('stopped') }
    catch { setStatus('error') }
  }

  const isActive = status === 'starting' || status === 'running' || status === 'stopping'
  const timerDisplay = useRunTimer(status)
  const samples = useRunStore(s => s.samples)

  // Thread counter: N = active threads proxy (latest sampleCount), M = total configured threads
  const totalConfiguredThreads = useMemo(() => {
    if (!tree) return 0
    return countThreads(tree.root)
  }, [tree])

  const activeThreads = useMemo(() => {
    if (!isActive || samples.length === 0) return 0
    const last = samples[samples.length - 1]
    return last ? last.sampleCount : 0
  }, [isActive, samples])

  const threadDisplay = `${activeThreads}/${totalConfiguredThreads}`

  // ─── Menu Bar Handlers ───
  const handleNewPlan = useCallback(() => {
    const newPlan = { id: `plan-${Date.now()}`, type: 'TestPlan', name: 'New Test Plan', enabled: true, properties: {}, children: [] as TestPlanNode[] }
    if (tree) {
      const { addNode } = useTestPlanStore.getState()
      addNode(tree.root.id, newPlan)
    } else {
      setTree({ root: { id: 'workspace', type: 'Workspace', name: 'Test Plans', enabled: true, properties: {}, children: [newPlan] } })
    }
  }, [tree, setTree])

  const handleOpen = useCallback(() => {
    fileInputRef.current?.click()
  }, [])

  const handleSaveAs = useCallback(async () => {
    // Same as save for now — backend manages persistence
    await handleSave()
  }, [handleSave])

  const handleCut = useCallback(() => {
    const state = useTestPlanStore.getState()
    if (state.selectedNodeId && state.tree) {
      const node = findNodeByIdGlobal(state.tree.root, state.selectedNodeId)
      if (node) {
        setClipboardNode(deepCloneGlobal(node, `clip-${Date.now()}`))
        if (state.selectedNodeId !== state.tree.root.id) {
          state.deleteNode(state.selectedNodeId)
        }
      }
    }
  }, [])

  const handleCopy = useCallback(() => {
    const state = useTestPlanStore.getState()
    if (state.selectedNodeId && state.tree) {
      const node = findNodeByIdGlobal(state.tree.root, state.selectedNodeId)
      if (node) {
        setClipboardNode(deepCloneGlobal(node, `clip-${Date.now()}`))
      }
    }
  }, [])

  const handlePaste = useCallback(() => {
    const state = useTestPlanStore.getState()
    const clip = getClipboardNode()
    if (state.selectedNodeId && state.tree && clip) {
      const pasted = deepCloneGlobal(clip, `paste-${Date.now()}`)
      state.addNode(state.selectedNodeId, pasted)
    }
  }, [])

  const handleDuplicate = useCallback(() => {
    const state = useTestPlanStore.getState()
    if (state.selectedNodeId && state.tree) {
      const node = findNodeByIdGlobal(state.tree.root, state.selectedNodeId)
      if (!node) return
      const parent = findParentGlobal(state.tree.root, state.selectedNodeId)
      if (!parent) return
      const clone = deepCloneGlobal(node, `dup-${Date.now()}`)
      const idx = parent.children.findIndex((c) => c.id === state.selectedNodeId)
      state.addNode(parent.id, clone)
      state.moveNode(clone.id, parent.id, idx + 1)
    }
  }, [])

  const handleDelete = useCallback(() => {
    const state = useTestPlanStore.getState()
    if (state.selectedNodeId && state.tree) {
      if (state.selectedNodeId !== state.tree.root.id) {
        state.deleteNode(state.selectedNodeId)
      }
    }
  }, [])

  const handleToggle = useCallback(() => {
    const state = useTestPlanStore.getState()
    if (state.selectedNodeId && state.tree) {
      const node = findNodeByIdGlobal(state.tree.root, state.selectedNodeId)
      if (node) {
        state.updateProperty(node.id, 'enabled', !node.enabled)
      }
    }
  }, [])

  const handleClearResults = useCallback(() => {
    useRunStore.getState().clearSamples()
  }, [])

  const [runDuration, setRunDuration] = useState(0)
  const [showAbout, setShowAbout] = useState(false)
  const [showShortcuts, setShowShortcuts] = useState(false)

  return (
    <div className="app">
      {/* ─── MENU BAR ─── */}
      <MenuBar
        onNewPlan={handleNewPlan}
        onOpen={handleOpen}
        onSave={handleSave}
        onSaveAs={handleSaveAs}
        onImport={handleOpen}
        onExport={handleExport}
        onUndo={() => temporalStore?.getState().undo()}
        onRedo={() => temporalStore?.getState().redo()}
        onCut={handleCut}
        onCopy={handleCopy}
        onPaste={handlePaste}
        onDuplicate={handleDuplicate}
        onDelete={handleDelete}
        onToggle={handleToggle}
        onRun={handleRun}
        onStop={handleStop}
        onStopNow={handleStopNow}
        onClearResults={handleClearResults}
        onShowAbout={() => setShowAbout(true)}
        onShowShortcuts={() => setShowShortcuts(true)}
        hasPlan={!!tree}
        isRunning={isActive}
        isStopping={status === 'stopping'}
      />

      {/* ─── TOOLBAR ─── */}
      <header className="toolbar">
        <span className="toolbar-title">jMeter Next</span>

        <div className="toolbar-group">
          <button className="icon-btn" title="Import JMX" onClick={() => fileInputRef.current?.click()}>
            <Upload size={16} /> <span className="icon-label">Import</span>
          </button>
          <input ref={fileInputRef} type="file" accept=".jmx" className="hidden"
            onChange={e => { const f = e.target.files?.[0]; if (f) handleImport(f); e.target.value = '' }} />

          <button className="icon-btn" title="Export JMX" onClick={handleExport} disabled={!tree}>
            <Download size={16} /> <span className="icon-label">Export</span>
          </button>

          <button className="icon-btn" title="New Plan" onClick={() => {
            const newPlan = { id: `plan-${Date.now()}`, type: 'TestPlan', name: 'New Test Plan', enabled: true, properties: {}, children: [] }
            if (tree) {
              // Add as sibling — make root a workspace containing multiple plans
              const { addNode } = useTestPlanStore.getState()
              addNode(tree.root.id, newPlan)
            } else {
              setTree({ root: { id: 'workspace', type: 'Workspace', name: 'Test Plans', enabled: true, properties: {}, children: [newPlan] } })
            }
          }}>
            <FilePlus size={16} /> <span className="icon-label">New</span>
          </button>

          <div className="toolbar-divider" />

          <button className="icon-btn" title="Undo (Ctrl+Z)" onClick={() => temporalStore?.getState().undo()}>
            <Undo2 size={16} />
          </button>
          <button className="icon-btn" title="Redo (Ctrl+Y)" onClick={() => temporalStore?.getState().redo()}>
            <Redo2 size={16} />
          </button>
        </div>

        <div className="toolbar-tabs">
          <button className={`tab ${activeTab === 'visual' ? 'active' : ''}`} onClick={() => setActiveTab('visual')}>
            <TreePine size={14} /> Visual
          </button>
          <button className={`tab ${activeTab === 'xml' ? 'active' : ''}`} onClick={() => setActiveTab('xml')}>
            <Code2 size={14} /> XML
          </button>
          <button className={`tab ${activeTab === 'results' ? 'active' : ''}`} onClick={() => setActiveTab('results')}>
            Results
          </button>
          <button className={`tab ${activeTab === 'sla' ? 'active' : ''}`} onClick={() => setActiveTab('sla')}>
            <Activity size={14} /> SLA
          </button>
          <button className={`tab ${activeTab === 'ab' ? 'active' : ''}`} onClick={() => setActiveTab('ab')}>
            <GitCompareArrows size={14} /> A/B
          </button>
          <button className={`tab ${activeTab === 'chaos' ? 'active' : ''}`} onClick={() => setActiveTab('chaos')}>
            <Zap size={14} /> Chaos
          </button>
          <button className={`tab ${activeTab === 'smoke' ? 'active' : ''}`} onClick={() => setActiveTab('smoke')}>
            <Shield size={14} /> Self Smoke
          </button>
        </div>

        <div className="toolbar-actions">
          <ThemePicker />
          <span className="toolbar-timer" title="Elapsed time">{timerDisplay}</span>
          <span className="toolbar-timer" title="Active / Total threads">{threadDisplay}</span>
          {!isActive ? (
            <>
              <select
                className="icon-btn"
                value={runDuration}
                onChange={e => setRunDuration(Number(e.target.value))}
                title="Run duration (0 = as defined in JMX)"
                style={{ fontSize: 11, padding: '2px 4px', minWidth: 60 }}
              >
                <option value={0}>JMX default</option>
                <option value={30}>30s</option>
                <option value={60}>1min</option>
                <option value={180}>3min</option>
                <option value={300}>5min</option>
                <option value={600}>10min</option>
              </select>
              <button className="icon-btn run-btn" onClick={handleRun} disabled={!tree}>
                <Play size={16} /> Run
              </button>
            </>
          ) : (
            <>
              <button className="icon-btn stop-btn" onClick={handleStop} disabled={status === 'stopping'}>
                <Square size={14} /> Stop
              </button>
              <button className="icon-btn stopnow-btn" onClick={handleStopNow}>
                <StopCircle size={14} /> Now
              </button>
            </>
          )}
          <span className={`status-pill status-${status}`}>{status}</span>
        </div>
      </header>

      {/* ─── WORKSPACE: Tree always visible on left, content on right ─── */}
      {(activeTab === 'visual' || activeTab === 'xml' || activeTab === 'results') && (
      <div className="workspace">
        {/* Tree panel — always visible across all tabs */}
        {(activeTab === 'visual' || activeTab === 'xml' || activeTab === 'results') && (
          <nav className="tree-panel">
            <div className="panel-header" onClick={() => setTreeCollapsed(!treeCollapsed)} style={{ cursor: 'pointer' }}>
              {treeCollapsed ? <ChevronRight size={14} /> : <ChevronDown size={14} />}
              <span>Test Plan</span>
            </div>
            {!treeCollapsed && (
              <>
                <TreeSearch />
                {!tree && (
                  <div className="tree-empty-state">
                    <Upload size={32} style={{ opacity: 0.3, marginBottom: 8 }} />
                    <p>Drop a .jmx file here</p>
                    <p style={{ fontSize: 11 }}>or use Import / New in toolbar</p>
                  </div>
                )}
                <TestPlanTree />
              </>
            )}
          </nav>
        )}

        {/* Right content panel — changes based on active tab */}
        <main className="property-panel" style={{ flex: 1, minWidth: 0 }}>
          {activeTab === 'visual' && (
            <>
              <div className="panel-header">Properties</div>
              {selectedNodeId ? <PropertyPanel /> : (
                <div className="empty-state">{tree ? 'Select a node' : 'Import or create a test plan'}</div>
              )}
            </>
          )}

          {activeTab === 'xml' && (
            <>
              <div className="panel-header">JMX Source</div>
              <XmlEditor
                value={
                  selectedNodeId && planXmlMap[selectedNodeId]
                    ? planXmlMap[selectedNodeId]
                    : tree
                      ? '<!-- Select a test plan node to view its JMX XML. -->'
                      : '<!-- No test plan loaded. Import or create one. -->'
                }
                readOnly={!selectedNodeId || !planXmlMap[selectedNodeId]}
                onChange={selectedNodeId ? (xml) => setPlanXml(selectedNodeId, xml) : undefined}
              />
            </>
          )}

          {activeTab === 'results' && (
            <>
              <div className="panel-header">Results</div>
              <ResultsTabs runId={activeRunId ?? runId ?? undefined} />
            </>
          )}
        </main>
      </div>
      )}

      {/* Full-width panels (no tree sidebar) */}
      {activeTab === 'sla' && (
        <div className="results-workspace">
          <SLADiscovery />
        </div>
      )}

      {activeTab === 'ab' && (
        <div className="results-workspace">
          <ABPerformance />
        </div>
      )}

      {activeTab === 'chaos' && (
        <div className="results-workspace">
          <ChaosLoad />
        </div>
      )}

      {activeTab === 'smoke' && (
        <div className="results-workspace">
          <SelfSmoke />
        </div>
      )}

      {/* ─── LOG VIEWER ─── */}
      <LogViewer />

      {/* ─── ABOUT DIALOG ─── */}
      {showAbout && (
        <div className="modal-backdrop" onClick={() => setShowAbout(false)}>
          <div className="modal-dialog" onClick={e => e.stopPropagation()}>
            <h3>About jMeter Next</h3>
            <p style={{ margin: '12px 0' }}>A modern web-based load testing tool inspired by Apache JMeter.</p>
            <p style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Built with React + TypeScript + Zustand</p>
            <button className="icon-btn" style={{ marginTop: 16 }} onClick={() => setShowAbout(false)}>Close</button>
          </div>
        </div>
      )}

      {/* ─── SHORTCUTS DIALOG ─── */}
      {showShortcuts && (
        <div className="modal-backdrop" onClick={() => setShowShortcuts(false)}>
          <div className="modal-dialog modal-wide" onClick={e => e.stopPropagation()}>
            <h3>Keyboard Shortcuts</h3>
            <table className="shortcuts-table">
              <tbody>
                <tr><td>Ctrl+N</td><td>New Test Plan</td></tr>
                <tr><td>Ctrl+O</td><td>Open / Import JMX</td></tr>
                <tr><td>Ctrl+S</td><td>Save</td></tr>
                <tr><td>Ctrl+Z</td><td>Undo</td></tr>
                <tr><td>Ctrl+Y</td><td>Redo</td></tr>
                <tr><td>Ctrl+X</td><td>Cut</td></tr>
                <tr><td>Ctrl+C</td><td>Copy</td></tr>
                <tr><td>Ctrl+V</td><td>Paste</td></tr>
                <tr><td>Ctrl+D</td><td>Duplicate</td></tr>
                <tr><td>Del</td><td>Delete</td></tr>
                <tr><td>Ctrl+T</td><td>Toggle Enable/Disable</td></tr>
                <tr><td>Ctrl+R</td><td>Start Run</td></tr>
                <tr><td>Ctrl+F</td><td>Search in Tree</td></tr>
                <tr><td>Alt+Up/Down</td><td>Move Node Up/Down</td></tr>
              </tbody>
            </table>
            <button className="icon-btn" style={{ marginTop: 16 }} onClick={() => setShowShortcuts(false)}>Close</button>
          </div>
        </div>
      )}

      {/* ─── STATUS BAR ─── */}
      <footer className="status-bar">
        <span>{status === 'idle' ? 'Ready' : status}</span>
        {tree && <span>Plans: {tree.root.children?.length ?? 0} | Nodes: {countNodes(tree.root)}</span>}
      </footer>
    </div>
  )
}

function countNodes(node: { children?: unknown[] }): number {
  if (!node.children) return 1
  return 1 + (node.children as { children?: unknown[] }[]).reduce((sum, c) => sum + countNodes(c), 0)
}

function countThreads(node: TestPlanNode): number {
  let count = 0
  if (node.type === 'ThreadGroup' && node.enabled) {
    const numThreads = Number(node.properties?.num_threads ?? node.properties?.numThreads ?? 1)
    count += isNaN(numThreads) ? 1 : numThreads
  }
  for (const child of node.children) {
    count += countThreads(child)
  }
  return count
}

function findNodeByIdGlobal(root: TestPlanNode, id: string): TestPlanNode | undefined {
  if (root.id === id) return root
  for (const child of root.children) {
    const found = findNodeByIdGlobal(child, id)
    if (found) return found
  }
  return undefined
}

function findParentGlobal(root: TestPlanNode, targetId: string): TestPlanNode | undefined {
  for (const child of root.children) {
    if (child.id === targetId) return root
    const found = findParentGlobal(child, targetId)
    if (found) return found
  }
  return undefined
}

function deepCloneGlobal(node: TestPlanNode, suffix: string): TestPlanNode {
  return {
    ...node,
    id: `${node.id}-${suffix}`,
    name: `${node.name} (copy)`,
    properties: { ...node.properties },
    children: node.children.map((c) => deepCloneGlobal(c, suffix)),
  }
}
