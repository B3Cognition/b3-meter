import { useState, useCallback, useRef } from 'react'
import { themes, applyTheme, getSavedTheme } from '../../themes/themes'
import { useLogStore } from '../../store/logStore'
import './MenuBar.css'

export interface MenuBarHandlers {
  onNewPlan: () => void
  onOpen: () => void
  onSave: () => void
  onSaveAs: () => void
  onImport: () => void
  onExport: () => void
  onUndo: () => void
  onRedo: () => void
  onCut: () => void
  onCopy: () => void
  onPaste: () => void
  onDuplicate: () => void
  onDelete: () => void
  onToggle: () => void
  onRun: () => void
  onStop: () => void
  onStopNow: () => void
  onClearResults: () => void
  onShowAbout: () => void
  onShowShortcuts: () => void
  hasPlan: boolean
  isRunning: boolean
  isStopping: boolean
}

export function MenuBar(props: MenuBarHandlers) {
  const {
    onNewPlan, onOpen, onSave, onSaveAs, onImport, onExport,
    onUndo, onRedo, onCut, onCopy, onPaste, onDuplicate, onDelete, onToggle,
    onRun, onStop, onStopNow, onClearResults,
    onShowAbout, onShowShortcuts,
    hasPlan, isRunning, isStopping,
  } = props

  const [openMenu, setOpenMenu] = useState<string | null>(null)
  const [themeSubmenuOpen, setThemeSubmenuOpen] = useState(false)
  const [activeTheme, setActiveTheme] = useState(getSavedTheme())
  const barRef = useRef<HTMLDivElement>(null)
  const logVisible = useLogStore((s) => s.visible)
  const toggleLogVisible = useLogStore((s) => s.toggleVisible)

  const close = useCallback(() => {
    setOpenMenu(null)
    setThemeSubmenuOpen(false)
  }, [])

  const toggleMenu = useCallback((name: string) => {
    setOpenMenu(prev => prev === name ? null : name)
    setThemeSubmenuOpen(false)
  }, [])

  const handleMenuHover = useCallback((name: string) => {
    // Only switch menus on hover if one is already open
    setOpenMenu(prev => prev !== null ? name : prev)
    setThemeSubmenuOpen(false)
  }, [])

  const handleThemeSelect = useCallback((id: string) => {
    applyTheme(id)
    setActiveTheme(id)
    close()
  }, [close])

  const item = (label: string, action: () => void, shortcut?: string, disabled?: boolean) => (
    <button
      className="menu-item"
      disabled={disabled}
      onClick={() => { action(); close() }}
    >
      <span className="menu-item-label">{label}</span>
      {shortcut && <span className="menu-item-shortcut">{shortcut}</span>}
    </button>
  )

  const separator = () => <div className="menu-separator" />

  const isMac = typeof navigator !== 'undefined' && /Mac/.test(navigator.platform)
  const mod = isMac ? '\u2318' : 'Ctrl+'

  return (
    <>
      {openMenu && <div className="menu-backdrop" onClick={close} />}
      <div className="menu-bar" ref={barRef}>

        {/* ── File ── */}
        <div className="menu-bar-item"
          onMouseEnter={() => handleMenuHover('file')}
        >
          <button
            className={`menu-bar-button ${openMenu === 'file' ? 'active' : ''}`}
            onClick={() => toggleMenu('file')}
          >
            File
          </button>
          {openMenu === 'file' && (
            <div className="menu-dropdown">
              {item('New Test Plan', onNewPlan, `${mod}N`)}
              {item('Open...', onOpen, `${mod}O`)}
              {item('Save', onSave, `${mod}S`, !hasPlan)}
              {item('Save As...', onSaveAs, undefined, !hasPlan)}
              {separator()}
              {item('Import JMX...', onImport)}
              {item('Export JMX...', onExport, undefined, !hasPlan)}
            </div>
          )}
        </div>

        {/* ── Edit ── */}
        <div className="menu-bar-item"
          onMouseEnter={() => handleMenuHover('edit')}
        >
          <button
            className={`menu-bar-button ${openMenu === 'edit' ? 'active' : ''}`}
            onClick={() => toggleMenu('edit')}
          >
            Edit
          </button>
          {openMenu === 'edit' && (
            <div className="menu-dropdown">
              {item('Undo', onUndo, `${mod}Z`)}
              {item('Redo', onRedo, `${mod}Y`)}
              {separator()}
              {item('Cut', onCut, `${mod}X`)}
              {item('Copy', onCopy, `${mod}C`)}
              {item('Paste', onPaste, `${mod}V`)}
              {separator()}
              {item('Duplicate', onDuplicate, `${mod}D`)}
              {item('Delete', onDelete, 'Del')}
              {separator()}
              {item('Toggle', onToggle, `${mod}T`)}
            </div>
          )}
        </div>

        {/* ── Run ── */}
        <div className="menu-bar-item"
          onMouseEnter={() => handleMenuHover('run')}
        >
          <button
            className={`menu-bar-button ${openMenu === 'run' ? 'active' : ''}`}
            onClick={() => toggleMenu('run')}
          >
            Run
          </button>
          {openMenu === 'run' && (
            <div className="menu-dropdown">
              {item('Start', onRun, `${mod}R`, !hasPlan || isRunning)}
              {item('Stop', onStop, undefined, !isRunning || isStopping)}
              {item('Stop Now', onStopNow, undefined, !isRunning)}
              {separator()}
              {item('Clear Results', onClearResults)}
            </div>
          )}
        </div>

        {/* ── Options ── */}
        <div className="menu-bar-item"
          onMouseEnter={() => handleMenuHover('options')}
        >
          <button
            className={`menu-bar-button ${openMenu === 'options' ? 'active' : ''}`}
            onClick={() => toggleMenu('options')}
          >
            Options
          </button>
          {openMenu === 'options' && (
            <div className="menu-dropdown">
              {item(logVisible ? 'Hide Log Viewer' : 'Show Log Viewer', toggleLogVisible)}
              {separator()}
              <div
                className="menu-submenu"
                onMouseEnter={() => setThemeSubmenuOpen(true)}
                onMouseLeave={() => setThemeSubmenuOpen(false)}
              >
                <button className="menu-submenu-trigger">
                  <span>Theme</span>
                  <span className="menu-submenu-arrow">&#9654;</span>
                </button>
                {themeSubmenuOpen && (
                  <div className="menu-submenu-panel">
                    {themes.map(theme => (
                      <button
                        key={theme.id}
                        className={`menu-theme-item ${activeTheme === theme.id ? 'current' : ''}`}
                        onClick={() => handleThemeSelect(theme.id)}
                      >
                        <span
                          className="menu-theme-swatch"
                          style={{
                            background: `linear-gradient(135deg, ${theme.vars['--bg-primary']} 0%, ${theme.vars['--accent']} 100%)`,
                          }}
                        />
                        <span>{theme.emoji} {theme.name}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* ── Help ── */}
        <div className="menu-bar-item"
          onMouseEnter={() => handleMenuHover('help')}
        >
          <button
            className={`menu-bar-button ${openMenu === 'help' ? 'active' : ''}`}
            onClick={() => toggleMenu('help')}
          >
            Help
          </button>
          {openMenu === 'help' && (
            <div className="menu-dropdown">
              {item('About jMeter Next', onShowAbout)}
              {item('Keyboard Shortcuts', onShowShortcuts)}
            </div>
          )}
        </div>

      </div>
    </>
  )
}
