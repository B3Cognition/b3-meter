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
