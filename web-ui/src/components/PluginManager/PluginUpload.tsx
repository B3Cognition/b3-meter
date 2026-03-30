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
import { useState, useCallback, useRef } from 'react';
import './PluginManager.css';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const MAX_JAR_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

export interface PluginUploadProps {
  onUpload: (file: File) => void | Promise<void>;
  isUploading?: boolean;
  /** External error string, e.g. from a failed API call. */
  error?: string | null;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function PluginUpload({ onUpload, isUploading = false, error = null }: PluginUploadProps) {
  const [dragOver, setDragOver]       = useState(false);
  const [validationError, setValidation] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const validate = useCallback((file: File): string | null => {
    if (!file.name.toLowerCase().endsWith('.jar')) {
      return 'Only .jar files are accepted.';
    }
    if (file.size === 0) {
      return 'The selected file is empty.';
    }
    if (file.size > MAX_JAR_SIZE_BYTES) {
      return `File is too large (${(file.size / 1024 / 1024).toFixed(1)} MB). Maximum size is 50 MB.`;
    }
    return null;
  }, []);

  const handleFile = useCallback(async (file: File) => {
    setValidation(null);
    const err = validate(file);
    if (err) {
      setValidation(err);
      return;
    }
    await onUpload(file);
  }, [onUpload, validate]);

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file) {
        void handleFile(file);
      }
      // Reset input so the same file can be re-uploaded after removal
      if (inputRef.current) {
        inputRef.current.value = '';
      }
    },
    [handleFile],
  );

  const handleDrop = useCallback(
    (e: React.DragEvent<HTMLDivElement>) => {
      e.preventDefault();
      setDragOver(false);
      const file = e.dataTransfer.files?.[0];
      if (file) {
        void handleFile(file);
      }
    },
    [handleFile],
  );

  const handleDragOver = useCallback((e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragOver(true);
  }, []);

  const handleDragLeave = useCallback(() => {
    setDragOver(false);
  }, []);

  const displayError = validationError ?? error;

  return (
    <div className="plugin-upload">
      <div
        className={`plugin-upload__dropzone${dragOver ? ' plugin-upload__dropzone--active' : ''}${isUploading ? ' plugin-upload__dropzone--uploading' : ''}`}
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        data-testid="plugin-upload-dropzone"
        aria-label="Drop a JAR file here or click to select"
      >
        <p className="plugin-upload__hint">
          {isUploading
            ? 'Uploading…'
            : 'Drag and drop a .jar plugin file here, or click to select.'}
        </p>
        <label className="plugin-upload__label">
          <input
            ref={inputRef}
            type="file"
            accept=".jar,application/java-archive"
            className="plugin-upload__input"
            onChange={handleInputChange}
            disabled={isUploading}
            data-testid="plugin-upload-input"
            aria-label="Select plugin JAR file"
          />
          <span className="plugin-upload__btn" aria-hidden="true">
            Browse…
          </span>
        </label>
        <p className="plugin-upload__limit">Maximum file size: 50 MB</p>
      </div>

      {displayError && (
        <div className="plugin-upload__error" role="alert" data-testid="plugin-upload-error">
          {displayError}
        </div>
      )}
    </div>
  );
}
