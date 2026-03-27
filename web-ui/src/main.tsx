import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './App.css'
import './themes/effects.css'
import { App } from './App'

const rootElement = document.getElementById('root')
if (rootElement === null) {
  throw new Error('Root element not found. Ensure index.html has <div id="root"></div>.')
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
