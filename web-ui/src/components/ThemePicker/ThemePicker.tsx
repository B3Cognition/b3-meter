import { useState } from 'react'
import { themes, applyTheme, getSavedTheme } from '../../themes/themes'
import './ThemePicker.css'

export function ThemePicker() {
  const [activeTheme, setActiveTheme] = useState(getSavedTheme())
  const [open, setOpen] = useState(false)

  const handleSelect = (id: string) => {
    applyTheme(id)
    setActiveTheme(id)
    setOpen(false)
  }

  const current = themes.find(t => t.id === activeTheme)

  return (
    <div className="theme-picker">
      <button
        className="theme-trigger"
        onClick={() => setOpen(!open)}
        aria-label="Change theme"
        title="Change theme"
      >
        {current?.emoji ?? '🎨'} Theme
      </button>
      {open && (
        <div className="theme-dropdown" role="menu">
          <div className="theme-dropdown-header">Choose Theme</div>
          <div className="theme-grid">
            {themes.map(theme => (
              <button
                key={theme.id}
                className={`theme-option ${activeTheme === theme.id ? 'active' : ''}`}
                onClick={() => handleSelect(theme.id)}
                role="menuitem"
                title={theme.name}
              >
                <span
                  className="theme-swatch"
                  style={{
                    background: `linear-gradient(135deg, ${theme.vars['--bg-primary']} 0%, ${theme.vars['--accent']} 100%)`,
                  }}
                />
                <span className="theme-emoji">{theme.emoji}</span>
                <span className="theme-name">{theme.name}</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
