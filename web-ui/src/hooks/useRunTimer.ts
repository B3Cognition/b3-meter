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
