import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { App } from '../App'

describe('App', () => {
  it('renders without crashing', () => {
    render(<App />)
    // App root element must be present
    expect(document.querySelector('.app')).toBeInTheDocument()
  })

  it('renders the toolbar with the application name', () => {
    render(<App />)
    expect(screen.getByRole('banner')).toHaveTextContent('jMeter Next')
  })

  it('renders the tree-panel navigation', () => {
    render(<App />)
    const treePanels = screen.getAllByRole('navigation')
    expect(treePanels.length).toBeGreaterThanOrEqual(1)
  })

  it('renders the property-panel main region', () => {
    render(<App />)
    expect(screen.getByRole('main')).toBeInTheDocument()
  })

  it('renders the status bar with Ready text', () => {
    render(<App />)
    expect(screen.getByRole('contentinfo')).toHaveTextContent('Ready')
  })
})
