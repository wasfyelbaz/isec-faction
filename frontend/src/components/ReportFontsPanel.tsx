import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, Trash2, Upload } from 'lucide-react';
import { reportFontsApi } from '../api';
import type { ReportFont } from '../types';
import { Button, IconButton } from './index';
import ConfirmDialog from './ConfirmDialog';
import './ReportFontsPanel.css';

export interface ReportFontsPanelProps {
  /** The selected template's Report Font, so the panel can say whether the server has it. */
  reportFont?: string;
}

/** What the server accepts; see ReportFontService. Browsers report font MIME types inconsistently, so extensions too. */
const ACCEPT = '.ttf,.otf,font/ttf,font/otf,font/sfnt,application/x-font-ttf,application/x-font-opentype,application/font-sfnt';

/** Matches the server's cap; see ReportFontService.MAX_FILE_SIZE. */
const MAX_BYTES = 25 * 1024 * 1024;

/** The id the Report Font input's `list` attribute points at. */
export const REPORT_FONT_FAMILIES_LIST_ID = 'report-font-families';

const prettySize = (bytes?: number) => {
  if (!bytes && bytes !== 0) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

/**
 * The fonts the server can render PDFs in, and the controls to add or remove one.
 *
 * <p>A PDF is drawn by LibreOffice on the server with the fonts installed there, so a Report Font
 * or a DOCX font the server lacks is silently substituted. This panel closes that gap from the
 * interface: upload the .ttf/.otf files, the server installs them and restarts its report engine.
 *
 * <p>Global, not per template — a font installed once serves every template — so it sits beside
 * the Report Font field rather than in the template's own settings. It also owns the `datalist`
 * that field suggests families from, so the designer needs nothing more than a `list` attribute.
 */
export default function ReportFontsPanel({ reportFont }: ReportFontsPanelProps) {
  const [fonts, setFonts] = useState<ReportFont[]>([]);
  const [families, setFamilies] = useState<string[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [files, setFiles] = useState<File[]>([]);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [pendingDelete, setPendingDelete] = useState<ReportFont | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [showFamilies, setShowFamilies] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const [list, installed] = await Promise.all([
        reportFontsApi.list(),
        reportFontsApi.installedFamilies(),
      ]);
      setFonts(list.data ?? []);
      setFamilies(installed.data ?? []);
    } catch {
      setFonts([]);
      setFamilies(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleUpload = async () => {
    if (files.length === 0) return;
    const tooBig = files.find((f) => f.size > MAX_BYTES);
    if (tooBig) {
      setError(`${tooBig.name} is larger than 25 MB`);
      return;
    }
    setUploading(true);
    setError(null);
    try {
      await reportFontsApi.upload(files);
      setFiles([]);
      if (fileInputRef.current) fileInputRef.current.value = '';
      await load();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to upload the font');
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    setError(null);
    try {
      await reportFontsApi.delete(pendingDelete.id);
      setPendingDelete(null);
      await load();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to delete the font');
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  };

  const wanted = (reportFont ?? '').trim();
  const known = families !== null && families.length > 0;
  const missing = known && wanted !== ''
    && !families!.some((f) => f.toLowerCase() === wanted.toLowerCase());

  return (
    <div className="report-fonts-panel">
      {/* Suggestions for the Report Font input: every family the server can actually draw. */}
      <datalist id={REPORT_FONT_FAMILIES_LIST_ID}>
        {(families ?? []).map((family) => <option key={family} value={family} />)}
      </datalist>

      {missing && (
        <div className="report-fonts-warning">
          <AlertTriangle size={14} />
          <span>
            "{wanted}" is not installed on the server, so PDFs will fall back to a substitute font.
            Upload its .ttf files below.
          </span>
        </div>
      )}

      {error && <div className="report-fonts-error">{error}</div>}

      {loading ? (
        <div className="report-fonts-empty">Loading…</div>
      ) : fonts.length === 0 ? (
        <div className="report-fonts-empty">
          No fonts uploaded yet. The server's built-in fonts are still available.
        </div>
      ) : (
        <ul className="report-fonts-list">
          {fonts.map((font) => (
            <li key={font.id} className="report-fonts-item">
              <div className="report-fonts-meta">
                <span className="report-fonts-name">
                  {font.family} <span className="report-fonts-style">{font.style}</span>
                </span>
                <span className="report-fonts-file">
                  {font.fileName}
                  {font.fileSize ? ` · ${prettySize(font.fileSize)}` : ''}
                </span>
              </div>
              <IconButton
                icon={Trash2}
                variant="delete"
                title="Delete font"
                onClick={() => setPendingDelete(font)}
              />
            </li>
          ))}
        </ul>
      )}

      <div className="report-fonts-upload">
        <input
          ref={fileInputRef}
          type="file"
          accept={ACCEPT}
          multiple
          className="report-fonts-file-input"
          aria-label="Font files"
          onChange={(e) => { setFiles(Array.from(e.target.files ?? [])); setError(null); }}
        />
        <Button
          type="button"
          variant="secondary"
          size="sm"
          icon={Upload}
          onClick={handleUpload}
          disabled={files.length === 0 || uploading}
        >
          {uploading ? 'Installing…' : files.length > 1 ? `Upload ${files.length} files` : 'Upload'}
        </Button>
      </div>

      <p className="report-fonts-hint">
        TrueType or OpenType files (.ttf, .otf), up to 25 MB each. Upload every style you use —
        regular, bold, italic, bold italic — as its own file. Installing restarts the report engine,
        which takes a few seconds.
      </p>

      {known && (
        <div className="report-fonts-families">
          <button
            type="button"
            className="report-fonts-families-toggle"
            onClick={() => setShowFamilies((v) => !v)}
          >
            {showFamilies ? 'Hide' : 'Show'} the {families!.length} font families available on the server
          </button>
          {showFamilies && (
            <div className="report-fonts-family-list">
              {families!.map((family) => (
                <span key={family} className="report-fonts-family">{family}</span>
              ))}
            </div>
          )}
        </div>
      )}

      <ConfirmDialog
        isOpen={!!pendingDelete}
        onClose={() => setPendingDelete(null)}
        onConfirm={handleDelete}
        title="Delete Font"
        message={`Delete ${pendingDelete?.family} ${pendingDelete?.style}? PDFs that use it will fall `
          + 'back to a substitute font from the next report on. This cannot be undone.'}
        confirmText="Delete"
        variant="danger"
        isLoading={deleting}
      />
    </div>
  );
}
