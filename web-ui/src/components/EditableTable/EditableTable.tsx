import { useState, useRef, useCallback, useEffect } from 'react'
import './EditableTable.css'

/* -----------------------------------------------------------------------
   Types
   ----------------------------------------------------------------------- */

export interface Column {
  key: string
  label: string
  type: 'text' | 'checkbox' | 'select'
  options?: string[]   // required when type === 'select'
  width?: string       // e.g. '120px', '30%'
}

export type Row = Record<string, string | boolean>

export interface EditableTableProps {
  columns: Column[]
  rows: Row[]
  onChange: (rows: Row[]) => void
}

/* -----------------------------------------------------------------------
   Component
   ----------------------------------------------------------------------- */

export function EditableTable({ columns, rows, onChange }: EditableTableProps) {
  const [selectedRowIndex, setSelectedRowIndex] = useState<number | null>(null)
  const [editingCell, setEditingCell] = useState<{ row: number; col: string } | null>(null)
  const inputRef = useRef<HTMLInputElement | null>(null)
  const selectRef = useRef<HTMLSelectElement | null>(null)

  // Focus the input/select whenever the editing cell changes
  useEffect(() => {
    inputRef.current?.focus()
    selectRef.current?.focus()
  }, [editingCell])

  /* ---- helpers ---- */

  const cloneRows = useCallback((): Row[] => rows.map(r => ({ ...r })), [rows])

  const updateCell = useCallback(
    (rowIdx: number, colKey: string, value: string | boolean) => {
      const next = cloneRows()
      next[rowIdx] = { ...next[rowIdx], [colKey]: value }
      onChange(next)
    },
    [cloneRows, onChange],
  )

  /* ---- action handlers ---- */

  const handleAdd = useCallback(() => {
    const empty: Row = {}
    for (const col of columns) {
      if (col.type === 'checkbox') {
        empty[col.key] = false
      } else if (col.type === 'select') {
        empty[col.key] = col.options?.[0] ?? ''
      } else {
        empty[col.key] = ''
      }
    }
    const next = [...rows, empty]
    onChange(next)
    setSelectedRowIndex(next.length - 1)
    setEditingCell(null)
  }, [columns, rows, onChange])

  const handleDelete = useCallback(() => {
    if (selectedRowIndex === null || selectedRowIndex >= rows.length) return
    const next = cloneRows()
    next.splice(selectedRowIndex, 1)
    onChange(next)
    if (next.length === 0) {
      setSelectedRowIndex(null)
    } else if (selectedRowIndex >= next.length) {
      setSelectedRowIndex(next.length - 1)
    }
    setEditingCell(null)
  }, [selectedRowIndex, rows.length, cloneRows, onChange])

  const handleMoveUp = useCallback(() => {
    if (selectedRowIndex === null || selectedRowIndex <= 0) return
    const next = cloneRows()
    const a = selectedRowIndex - 1
    const b = selectedRowIndex
    const tmp = next[a]!
    next[a] = next[b]!
    next[b] = tmp
    onChange(next)
    setSelectedRowIndex(a)
    setEditingCell(null)
  }, [selectedRowIndex, cloneRows, onChange])

  const handleMoveDown = useCallback(() => {
    if (selectedRowIndex === null || selectedRowIndex >= rows.length - 1) return
    const next = cloneRows()
    const a = selectedRowIndex
    const b = selectedRowIndex + 1
    const tmp = next[a]!
    next[a] = next[b]!
    next[b] = tmp
    onChange(next)
    setSelectedRowIndex(selectedRowIndex + 1)
    setEditingCell(null)
  }, [selectedRowIndex, rows.length, cloneRows, onChange])

  /* ---- commit helpers ---- */

  const commitText = useCallback(
    (rowIdx: number, colKey: string, value: string) => {
      updateCell(rowIdx, colKey, value)
      setEditingCell(null)
    },
    [updateCell],
  )

  /* ---- render cell ---- */

  const renderCell = (col: Column, rowIdx: number) => {
    const row = rows[rowIdx]
    if (!row) return null
    const value = row[col.key]
    const isEditing =
      editingCell !== null && editingCell.row === rowIdx && editingCell.col === col.key

    // Checkbox — always rendered inline, no edit mode toggle
    if (col.type === 'checkbox') {
      return (
        <div className="editable-table-cell-checkbox">
          <input
            type="checkbox"
            checked={!!value}
            onChange={(e) => {
              e.stopPropagation()
              updateCell(rowIdx, col.key, e.target.checked)
            }}
          />
        </div>
      )
    }

    // Select
    if (col.type === 'select') {
      if (isEditing) {
        return (
          <select
            ref={selectRef}
            className="editable-table-cell-select"
            value={String(value ?? '')}
            onChange={(e) => {
              updateCell(rowIdx, col.key, e.target.value)
              setEditingCell(null)
            }}
            onBlur={() => setEditingCell(null)}
            onKeyDown={(e) => {
              if (e.key === 'Escape') setEditingCell(null)
            }}
          >
            {(col.options ?? []).map((opt) => (
              <option key={opt} value={opt}>
                {opt}
              </option>
            ))}
          </select>
        )
      }
      return (
        <span
          className="editable-table-cell-display"
          onDoubleClick={(e) => {
            e.stopPropagation()
            setEditingCell({ row: rowIdx, col: col.key })
          }}
        >
          {String(value ?? '')}
        </span>
      )
    }

    // Text
    if (isEditing) {
      return (
        <input
          ref={inputRef}
          className="editable-table-cell-input"
          type="text"
          defaultValue={String(value ?? '')}
          onBlur={(e) => commitText(rowIdx, col.key, e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              commitText(rowIdx, col.key, (e.target as HTMLInputElement).value)
            } else if (e.key === 'Escape') {
              setEditingCell(null)
            }
          }}
        />
      )
    }

    return (
      <span
        className="editable-table-cell-display"
        onDoubleClick={(e) => {
          e.stopPropagation()
          setEditingCell({ row: rowIdx, col: col.key })
        }}
      >
        {String(value ?? '')}
      </span>
    )
  }

  /* ---- main render ---- */

  const hasSelection = selectedRowIndex !== null && selectedRowIndex < rows.length

  return (
    <div className="editable-table-container">
      <table className="editable-table">
        <colgroup>
          {columns.map((col) => (
            <col key={col.key} style={col.width ? { width: col.width } : undefined} />
          ))}
        </colgroup>
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={col.key}>{col.label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((_, rowIdx) => (
            <tr
              key={rowIdx}
              className={rowIdx === selectedRowIndex ? 'editable-table-row-selected' : ''}
              onClick={() => {
                setSelectedRowIndex(rowIdx)
              }}
            >
              {columns.map((col) => (
                <td key={col.key}>{renderCell(col, rowIdx)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>

      {rows.length === 0 && (
        <div className="editable-table-empty">No entries</div>
      )}

      <div className="editable-table-actions">
        <button type="button" className="editable-table-btn" onClick={handleAdd}>
          Add
        </button>
        <button
          type="button"
          className="editable-table-btn"
          disabled={!hasSelection}
          onClick={handleDelete}
        >
          Delete
        </button>
        <button
          type="button"
          className="editable-table-btn"
          disabled={!hasSelection || selectedRowIndex === 0}
          onClick={handleMoveUp}
        >
          Up
        </button>
        <button
          type="button"
          className="editable-table-btn"
          disabled={!hasSelection || selectedRowIndex === rows.length - 1}
          onClick={handleMoveDown}
        >
          Down
        </button>
      </div>
    </div>
  )
}
