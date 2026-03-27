import { useState, useCallback, useRef } from 'react'
import { importJmx } from '../../api/plans'
import { useTestPlanStore } from '../../store/testPlanStore'
import './JmxUpload.css'

export function JmxUpload() {
  const [dragging, setDragging] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const { setTree } = useTestPlanStore()

  const handleFile = useCallback(async (file: File) => {
    if (!file.name.endsWith('.jmx')) {
      setError('Please select a .jmx file')
      return
    }
    if (file.size > 50 * 1024 * 1024) {
      setError('File exceeds 50 MB limit')
      return
    }

    setUploading(true)
    setError(null)
    setSuccess(null)

    try {
      const plan = await importJmx(file)
      // Load the imported plan into the tree editor
      if (plan.treeData) {
        setTree({
          root: {
            id: plan.id,
            type: 'TestPlan',
            name: plan.name,
            enabled: true,
            properties: {},
            children: []
          }
        })
      }
      setSuccess(`Imported "${plan.name}" successfully!`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Import failed')
    } finally {
      setUploading(false)
    }
  }, [setTree])

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setDragging(false)
    const file = e.dataTransfer.files[0]
    if (file) handleFile(file)
  }, [handleFile])

  const onDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setDragging(true)
  }, [])

  const onDragLeave = useCallback(() => setDragging(false), [])

  const onFileSelect = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) handleFile(file)
    e.target.value = '' // reset for re-upload
  }, [handleFile])

  return (
    <div className="jmx-upload">
      <div
        className={`drop-zone ${dragging ? 'dragging' : ''} ${uploading ? 'uploading' : ''}`}
        onDrop={onDrop}
        onDragOver={onDragOver}
        onDragLeave={onDragLeave}
        onClick={() => fileInputRef.current?.click()}
        role="button"
        tabIndex={0}
        aria-label="Upload JMX file"
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".jmx"
          onChange={onFileSelect}
          className="file-input-hidden"
          aria-hidden="true"
        />
        {uploading ? (
          <div className="upload-spinner">Importing...</div>
        ) : (
          <>
            <div className="drop-icon">📂</div>
            <div className="drop-text">
              Drop .jmx file here or <span className="drop-link">browse</span>
            </div>
            <div className="drop-hint">Supports JMeter 3.x — 6.x</div>
          </>
        )}
      </div>
      {error && <div className="upload-error" role="alert">{error}</div>}
      {success && <div className="upload-success" role="status">{success}</div>}
    </div>
  )
}
