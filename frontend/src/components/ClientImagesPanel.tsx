import { useEffect, useRef, useState } from 'react';
import { Trash2, Upload } from 'lucide-react';
import { clientImagesApi } from '../api';
import type { ClientImage } from '../types';
import { Button, FormHint, IconButton, Input } from './index';
import ConfirmDialog from './ConfirmDialog';
import './ClientImagesPanel.css';
import { useTerminology } from '../context/TerminologyContext';

export interface ClientImagesPanelProps {
  organizationId: string;
  /** False renders the list read-only — no upload or delete. */
  canWrite: boolean;
}

/** Matches the server's allowlist; see ClientImageService.ALLOWED_CONTENT_TYPES. */
const ACCEPT = 'image/png,image/jpeg,image/gif,image/webp,image/svg+xml';

/** Matches the server's cap; see ClientImageService.MAX_FILE_SIZE. */
const MAX_BYTES = 5 * 1024 * 1024;

const prettySize = (bytes?: number) => {
  if (!bytes && bytes !== 0) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

/**
 * A client's images, in named slots.
 *
 * <p>Managed independently of the organization form — each upload and delete is its own request —
 * so these are not part of that form's save, the same arrangement the divisions panel uses.
 *
 * <p>Thumbnails are fetched through the authenticated api client and handed to an object URL
 * rather than pointing an `<img src>` at the endpoint: unlike branding, these images require a
 * token, and a bare src would arrive without the Authorization header and 401.
 */
export default function ClientImagesPanel({ organizationId, canWrite }: ClientImagesPanelProps) {
  const { organizationLower } = useTerminology();
  const [images, setImages] = useState<ClientImage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  /** Slot name -> object URL. Revoked whenever it is replaced, and on unmount. */
  const [previews, setPreviews] = useState<Record<string, string>>({});
  const previewsRef = useRef<Record<string, string>>({});

  const [slotName, setSlotName] = useState('logo');
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [pendingDelete, setPendingDelete] = useState<ClientImage | null>(null);
  const [deleting, setDeleting] = useState(false);

  const releasePreviews = () => {
    Object.values(previewsRef.current).forEach((url) => URL.revokeObjectURL(url));
    previewsRef.current = {};
  };

  const load = async () => {
    setLoading(true);
    try {
      const res = await clientImagesApi.list(organizationId);
      const list = res.data ?? [];
      setImages(list);

      // Fetched one at a time rather than eagerly in parallel: a client has a handful of slots,
      // and a failure on one must not cost the others their thumbnail.
      releasePreviews();
      setPreviews({});
      for (const image of list) {
        try {
          const blob = await clientImagesApi.content(organizationId, image.name);
          const url = URL.createObjectURL(blob);
          previewsRef.current[image.name] = url;
          setPreviews((current) => ({ ...current, [image.name]: url }));
        } catch {
          // No thumbnail for this one; the row still lists it so it can be replaced or removed.
        }
      }
    } catch {
      setImages([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    return releasePreviews;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [organizationId]);

  const handleUpload = async () => {
    if (!file) return;
    const name = slotName.trim().toLowerCase();
    if (!/^[a-z0-9_-]{1,40}$/.test(name)) {
      setError("An image name must be 1-40 characters of a-z, 0-9, '_' or '-'");
      return;
    }
    if (file.size > MAX_BYTES) {
      setError('An image cannot be larger than 5 MB');
      return;
    }
    setUploading(true);
    setError(null);
    try {
      await clientImagesApi.upload(organizationId, name, file);
      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
      await load();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to upload the image');
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    setError(null);
    try {
      await clientImagesApi.delete(organizationId, pendingDelete.name);
      setPendingDelete(null);
      await load();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to delete the image');
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  };

  const replacing = images.some((i) => i.name === slotName.trim().toLowerCase());

  return (
    <div className="form-panel client-images-panel">
      <h3 className="form-section-title">Images</h3>
      <FormHint>
        Logos and other artwork belonging to this {organizationLower}. Each one fills a named slot —
        <code>logo</code>, <code>cover</code>, <code>signature</code> — so a report can ask for the
        slot rather than for a particular file. Uploading to a name that is already taken replaces
        what is there. PNG, JPEG, GIF, WebP or SVG, up to 5 MB.
      </FormHint>

      {error && <div className="client-images-error">{error}</div>}

      {loading ? (
        <div className="client-images-empty">Loading…</div>
      ) : images.length === 0 ? (
        <div className="client-images-empty">No images yet.</div>
      ) : (
        <ul className="client-images-list">
          {images.map((image) => (
            <li key={image.id} className="client-images-item">
              <div className="client-images-thumb">
                {previews[image.name] ? (
                  <img src={previews[image.name]} alt={image.name} />
                ) : (
                  <span className="client-images-thumb-missing">?</span>
                )}
              </div>
              <div className="client-images-meta">
                <span className="client-images-name">{image.name}</span>
                <span className="client-images-file">
                  {image.originalFileName}
                  {image.fileSize ? ` · ${prettySize(image.fileSize)}` : ''}
                </span>
              </div>
              {canWrite && (
                <IconButton
                  icon={Trash2}
                  variant="delete"
                  title="Delete image"
                  onClick={() => setPendingDelete(image)}
                />
              )}
            </li>
          ))}
        </ul>
      )}

      {canWrite && (
        <div className="client-images-upload">
          <label className="client-images-field">
            <span>Name</span>
            <Input
              value={slotName}
              onChange={(e) => setSlotName(e.target.value)}
              placeholder="logo"
              aria-label="Image slot name"
            />
          </label>
          <label className="client-images-field client-images-field--file">
            <span>File</span>
            <input
              ref={fileInputRef}
              type="file"
              accept={ACCEPT}
              className="client-images-file-input"
              onChange={(e) => { setFile(e.target.files?.[0] ?? null); setError(null); }}
            />
          </label>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            icon={Upload}
            onClick={handleUpload}
            disabled={!file || uploading}
          >
            {uploading ? 'Uploading…' : replacing ? 'Replace' : 'Upload'}
          </Button>
        </div>
      )}

      <ConfirmDialog
        isOpen={!!pendingDelete}
        onClose={() => setPendingDelete(null)}
        onConfirm={handleDelete}
        title="Delete Image"
        message={`Delete the "${pendingDelete?.name}" image? The stored file is removed too, and `
          + 'anything using this slot will have nothing to show. This cannot be undone.'}
        confirmText="Delete"
        variant="danger"
        isLoading={deleting}
      />
    </div>
  );
}
