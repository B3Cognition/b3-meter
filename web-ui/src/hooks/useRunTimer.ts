import { useState, useEffect, useRef } from 'react'
import type { RunStatus } from '../store/runStore'

/**
 * Returns a formatted HH:MM:SS string that ticks every second while status is 'running'.
 * Resets to "00:00:00" when not running.
 */
export function useRunTimer(status: RunStatus): string {
  const [elapsed, setElapsed] = useState(0)
  const startTimeRef = useRef<number | null>(null)
  const prevStatusRef = useRef<RunStatus>(status)

  useEffect(() => {
    // Detect transition into running
    if (
      (status === 'running' || status === 'starting') &&
      prevStatusRef.current !== 'running' &&
      prevStatusRef.current !== 'starting'
    ) {
      startTimeRef.current = Date.now()
      setElapsed(0)
    }

    // Detect transition out of running
    if (
      status !== 'running' &&
      status !== 'starting' &&
      status !== 'stopping'
    ) {
      startTimeRef.current = null
      setElapsed(0)
    }

    prevStatusRef.current = status
  }, [status])

  useEffect(() => {
    if (status !== 'running' && status !== 'starting' && status !== 'stopping') {
      return
    }

    const interval = setInterval(() => {
      if (startTimeRef.current !== null) {
        setElapsed(Math.floor((Date.now() - startTimeRef.current) / 1000))
      }
    }, 1000)

    return () => clearInterval(interval)
  }, [status])

  return formatTime(elapsed)
}

function formatTime(totalSeconds: number): string {
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return (
    String(hours).padStart(2, '0') + ':' +
    String(minutes).padStart(2, '0') + ':' +
    String(seconds).padStart(2, '0')
  )
}
