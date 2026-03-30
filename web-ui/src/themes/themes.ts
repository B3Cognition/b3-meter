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
export interface Theme {
  id: string
  name: string
  emoji: string
  vars: Record<string, string>
  bodyClass?: string // extra class on <body> for CSS effects
}

export const themes: Theme[] = [
  // ═══════════════════════════════════════════════
  // JMETER OFFICIAL THEMES
  // ═══════════════════════════════════════════════
  {
    id: 'jmeter-classic', name: 'JMeter Classic', emoji: '☕',
    vars: {
      '--bg-primary': '#F0F0F0', '--bg-secondary': '#D4D0C8', '--bg-tertiary': '#FFFFFF',
      '--text-primary': '#000000', '--text-secondary': '#444444',
      '--accent': '#316AC5', '--accent-hover': '#4A80E0',
      '--border': '#808080', '--success': '#008000', '--error': '#FF0000',
    }
  },
  {
    id: 'jmeter-darcula', name: 'JMeter Darcula', emoji: '🌑',
    vars: {
      '--bg-primary': '#3C3F41', '--bg-secondary': '#2B2B2B', '--bg-tertiary': '#45494A',
      '--text-primary': '#A9B7C6', '--text-secondary': '#6A8759',
      '--accent': '#4B6EAF', '--accent-hover': '#5F83CC',
      '--border': '#555555', '--success': '#499C54', '--error': '#FF6B68',
    }
  },

  // ═══════════════════════════════════════════════
  // FOR THE 50-YEAR-OLD NERDS
  // ═══════════════════════════════════════════════
  {
    id: 'norton', name: 'Norton Commander', emoji: '💾',
    bodyClass: 'theme-norton',
    vars: {
      '--bg-primary': '#0000aa', '--bg-secondary': '#0000aa', '--bg-tertiary': '#000080',
      '--text-primary': '#ffff55', '--text-secondary': '#55ffff',
      '--accent': '#55ff55', '--accent-hover': '#ffffff',
      '--border': '#55ffff', '--success': '#55ff55', '--error': '#ff5555',
      '--font-family': '"Fixedsys", "Perfect DOS VGA 437", "Courier New", monospace',
      '--border-style': 'double',
    }
  },
  {
    id: 'msdos', name: 'MS-DOS', emoji: '🖥️',
    bodyClass: 'theme-msdos',
    vars: {
      '--bg-primary': '#000000', '--bg-secondary': '#000000', '--bg-tertiary': '#1a1a1a',
      '--text-primary': '#aaaaaa', '--text-secondary': '#555555',
      '--accent': '#ffffff', '--accent-hover': '#55ff55',
      '--border': '#555555', '--success': '#55ff55', '--error': '#ff5555',
      '--font-family': '"Courier New", monospace',
    }
  },
  {
    id: 'amiga', name: 'Amiga Workbench', emoji: '🦆',
    bodyClass: 'theme-amiga',
    vars: {
      '--bg-primary': '#0055aa', '--bg-secondary': '#ffffff', '--bg-tertiary': '#ff8800',
      '--text-primary': '#000000', '--text-secondary': '#0055aa',
      '--accent': '#ff8800', '--accent-hover': '#ffaa00',
      '--border': '#000000', '--success': '#00aa00', '--error': '#aa0000',
    }
  },

  // ═══════════════════════════════════════════════
  // INSANE ARTISTIC THEMES WITH EFFECTS
  // ═══════════════════════════════════════════════
  {
    id: 'aurora', name: 'Aurora Borealis', emoji: '🌌',
    bodyClass: 'theme-aurora',
    vars: {
      '--bg-primary': '#0a0a1a', '--bg-secondary': '#0d1025', '--bg-tertiary': '#121530',
      '--text-primary': '#e0f0ff', '--text-secondary': '#7faacc',
      '--accent': '#00ff88', '--accent-hover': '#44ffaa',
      '--border': '#1a3050', '--success': '#00ff88', '--error': '#ff4466',
      '--glow-color': '#00ff8840',
    }
  },
  {
    id: 'neonokyo', name: 'Neo Tokyo', emoji: '🏙️',
    bodyClass: 'theme-neotokyo',
    vars: {
      '--bg-primary': '#0a0010', '--bg-secondary': '#140020', '--bg-tertiary': '#1e0030',
      '--text-primary': '#ff0066', '--text-secondary': '#cc0055',
      '--accent': '#00ffff', '--accent-hover': '#33ffff',
      '--border': '#ff006640', '--success': '#00ff66', '--error': '#ff0033',
      '--glow-color': '#ff006640',
    }
  },
  {
    id: 'hologram', name: 'Hologram', emoji: '🔮',
    bodyClass: 'theme-hologram',
    vars: {
      '--bg-primary': '#0a0a0f', '--bg-secondary': '#12121a', '--bg-tertiary': '#1a1a25',
      '--text-primary': '#88ccff', '--text-secondary': '#5588aa',
      '--accent': '#00aaff', '--accent-hover': '#33ccff',
      '--border': '#00aaff30', '--success': '#00ffaa', '--error': '#ff4488',
      '--glow-color': '#00aaff20',
    }
  },
  {
    id: 'matrix', name: 'Matrix Rain', emoji: '💊',
    bodyClass: 'theme-matrix',
    vars: {
      '--bg-primary': '#000000', '--bg-secondary': '#001100', '--bg-tertiary': '#002200',
      '--text-primary': '#00ff41', '--text-secondary': '#008f11',
      '--accent': '#00ff41', '--accent-hover': '#33ff66',
      '--border': '#003300', '--success': '#00ff41', '--error': '#ff0000',
      '--font-family': '"Courier New", monospace',
    }
  },
  {
    id: 'lcars', name: 'LCARS', emoji: '🖖',
    bodyClass: 'theme-lcars',
    vars: {
      '--bg-primary': '#000000', '--bg-secondary': '#111122', '--bg-tertiary': '#1a1a33',
      '--text-primary': '#ff9900', '--text-secondary': '#cc99cc',
      '--accent': '#ff9900', '--accent-hover': '#ffcc00',
      '--border': '#cc6699', '--success': '#99cc99', '--error': '#cc3333',
      '--border-radius': '0 20px 20px 0',
    }
  },
  {
    id: 'starwars', name: 'Imperial HQ', emoji: '⚔️',
    bodyClass: 'theme-imperial',
    vars: {
      '--bg-primary': '#0a0e17', '--bg-secondary': '#121a2b', '--bg-tertiary': '#1a2540',
      '--text-primary': '#4fc3f7', '--text-secondary': '#2196f3',
      '--accent': '#2196f3', '--accent-hover': '#42a5f5',
      '--border': '#1a3a5c', '--success': '#4caf50', '--error': '#f44336',
      '--glow-color': '#2196f320',
    }
  },
  {
    id: 'kpop', name: 'K-pop Sparkle', emoji: '💜',
    bodyClass: 'theme-kpop',
    vars: {
      '--bg-primary': '#1a0a2e', '--bg-secondary': '#2d1b4e', '--bg-tertiary': '#3d2b5e',
      '--text-primary': '#f8e0ff', '--text-secondary': '#d0a0e0',
      '--accent': '#ff69b4', '--accent-hover': '#ff85c8',
      '--border': '#6030a0', '--success': '#69f0ae', '--error': '#ff5252',
      '--glow-color': '#ff69b430',
    }
  },
  {
    id: 'cyberpunk', name: 'Cyberpunk 2077', emoji: '🤖',
    bodyClass: 'theme-cyberpunk',
    vars: {
      '--bg-primary': '#0a0a0a', '--bg-secondary': '#1a1a2e', '--bg-tertiary': '#2a2a3e',
      '--text-primary': '#fcee09', '--text-secondary': '#bdb600',
      '--accent': '#fcee09', '--accent-hover': '#fff44f',
      '--border': '#333300', '--success': '#00e5ff', '--error': '#ff1744',
      '--glow-color': '#fcee0930',
    }
  },
  {
    id: 'vaporwave', name: 'Vaporwave', emoji: '🌴',
    bodyClass: 'theme-vaporwave',
    vars: {
      '--bg-primary': '#1a0030', '--bg-secondary': '#2d004d', '--bg-tertiary': '#400070',
      '--text-primary': '#ff71ce', '--text-secondary': '#01cdfe',
      '--accent': '#05ffa1', '--accent-hover': '#33ffb7',
      '--border': '#b967ff', '--success': '#05ffa1', '--error': '#ff6b6b',
      '--glow-color': '#b967ff30',
    }
  },
  {
    id: 'synthwave', name: 'Synthwave Sunset', emoji: '🎹',
    bodyClass: 'theme-synthwave',
    vars: {
      '--bg-primary': '#0a0020', '--bg-secondary': '#150035', '--bg-tertiary': '#200050',
      '--text-primary': '#f0e0ff', '--text-secondary': '#c0a0e0',
      '--accent': '#ff2975', '--accent-hover': '#ff5599',
      '--border': '#6020a0', '--success': '#00f0ff', '--error': '#ff2975',
      '--glow-color': '#ff297530',
    }
  },
  {
    id: 'dracula', name: 'Dracula', emoji: '🧛',
    vars: {
      '--bg-primary': '#282a36', '--bg-secondary': '#343746', '--bg-tertiary': '#44475a',
      '--text-primary': '#f8f8f2', '--text-secondary': '#6272a4',
      '--accent': '#bd93f9', '--accent-hover': '#caa4fa',
      '--border': '#44475a', '--success': '#50fa7b', '--error': '#ff5555',
    }
  },
  {
    id: 'catppuccin', name: 'Catppuccin Mocha', emoji: '🐱',
    vars: {
      '--bg-primary': '#1e1e2e', '--bg-secondary': '#313244', '--bg-tertiary': '#45475a',
      '--text-primary': '#cdd6f4', '--text-secondary': '#a6adc8',
      '--accent': '#cba6f7', '--accent-hover': '#d4b6ff',
      '--border': '#45475a', '--success': '#a6e3a1', '--error': '#f38ba8',
    }
  },
  {
    id: 'gruvbox', name: 'Gruvbox', emoji: '🔥',
    vars: {
      '--bg-primary': '#1d2021', '--bg-secondary': '#282828', '--bg-tertiary': '#3c3836',
      '--text-primary': '#ebdbb2', '--text-secondary': '#a89984',
      '--accent': '#fe8019', '--accent-hover': '#fabd2f',
      '--border': '#504945', '--success': '#b8bb26', '--error': '#fb4934',
    }
  },
  {
    id: 'ghibli', name: 'Studio Ghibli', emoji: '🌿',
    vars: {
      '--bg-primary': '#f5f0e8', '--bg-secondary': '#ede5d8', '--bg-tertiary': '#e0d5c4',
      '--text-primary': '#3d3228', '--text-secondary': '#6b5e4f',
      '--accent': '#5b8c5a', '--accent-hover': '#4a7a49',
      '--border': '#c9bda8', '--success': '#5b8c5a', '--error': '#c0392b',
    }
  },
  {
    id: 'cottagecore', name: 'Cottagecore', emoji: '🌼',
    vars: {
      '--bg-primary': '#f5f2ed', '--bg-secondary': '#ebe5db', '--bg-tertiary': '#ddd5c8',
      '--text-primary': '#3a3530', '--text-secondary': '#6b635a',
      '--accent': '#7a9b6d', '--accent-hover': '#6a8b5d',
      '--border': '#c5baa8', '--success': '#7a9b6d', '--error': '#c47a6c',
    }
  },
  {
    id: 'darkacademia', name: 'Dark Academia', emoji: '📚',
    vars: {
      '--bg-primary': '#1c1410', '--bg-secondary': '#2a2018', '--bg-tertiary': '#3a2e22',
      '--text-primary': '#e8dcc8', '--text-secondary': '#b8a890',
      '--accent': '#c8a96e', '--accent-hover': '#dabb82',
      '--border': '#4a3c2e', '--success': '#7a9e7e', '--error': '#b85450',
    }
  },
  {
    id: 'glitch', name: 'Glitch Art', emoji: '📺',
    bodyClass: 'theme-glitch',
    vars: {
      '--bg-primary': '#0a0a0a', '--bg-secondary': '#151515', '--bg-tertiary': '#202020',
      '--text-primary': '#ffffff', '--text-secondary': '#888888',
      '--accent': '#ff0000', '--accent-hover': '#00ff00',
      '--border': '#333333', '--success': '#00ff00', '--error': '#ff0000',
    }
  },
  {
    id: 'retrowave', name: 'Retrowave Grid', emoji: '🕹️',
    bodyClass: 'theme-retrowave',
    vars: {
      '--bg-primary': '#0c0028', '--bg-secondary': '#14003d', '--bg-tertiary': '#1c0052',
      '--text-primary': '#ff6ec7', '--text-secondary': '#c85aa0',
      '--accent': '#00d4ff', '--accent-hover': '#33ddff',
      '--border': '#ff6ec740', '--success': '#00ff80', '--error': '#ff3366',
      '--glow-color': '#00d4ff20',
    }
  },
]

export function applyTheme(themeId: string) {
  const theme = themes.find(t => t.id === themeId) ?? themes[0]!
  if (!theme) return
  const root = document.documentElement
  // Remove all theme body classes
  document.body.className = document.body.className.replace(/theme-\S+/g, '').trim()
  // Apply CSS variables
  for (const [prop, value] of Object.entries(theme.vars)) {
    root.style.setProperty(prop, value)
  }
  // Apply body class for CSS effects
  if (theme.bodyClass) {
    document.body.classList.add(theme.bodyClass)
  }
  // Apply custom font if specified
  if (theme.vars['--font-family']) {
    root.style.setProperty('font-family', theme.vars['--font-family'])
  }
  localStorage.setItem('b3meter-theme', themeId)
}

export function getSavedTheme(): string {
  return localStorage.getItem('b3meter-theme') ?? 'jmeter-classic'
}
