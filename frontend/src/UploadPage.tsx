import { ChangeEvent, useRef, useState } from 'react';
import { apiJson } from './api';
import { Folder } from './components/Folder';
import { Copy } from './locale';
import { formatSize } from './messages';

interface CreateResponse { uploadUrl: string; shareUrl: string; }
interface UploadPageProps { copy: Copy; onCreated: (shareUrl: string) => void; theme?: 'dark' | 'light'; }

const LIFETIMES = [300, 3600, 21600, 86400, 259200, 604800, 2592000];

export function UploadPage({ copy, onCreated, theme = 'dark' }: UploadPageProps) {
  const [file, setFile] = useState<File>();
  const [expiresInSeconds, setExpiresInSeconds] = useState('86400');
  const [password, setPassword] = useState('');
  const [maxDownloads, setMaxDownloads] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(false);
  const [dragging, setDragging] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const select = (files: FileList | null) => setFile(files?.[0]);

  const onDrop = (event: React.DragEvent) => { event.preventDefault(); setDragging(false); select(event.dataTransfer.files); };
  const onDragOver = (event: React.DragEvent) => { event.preventDefault(); setDragging(true); };
  const onDragLeave = () => setDragging(false);

  const create = async () => {
    if (!file) return;
    setBusy(true); setError(false);
    try {
      const created = await apiJson<CreateResponse>('/api/v1/uploads', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ fileName: file.name, contentType: file.type || 'application/octet-stream', fileSize: file.size, expiresInSeconds: Number(expiresInSeconds), password: password || undefined, maxDownloads: maxDownloads ? Number(maxDownloads) : undefined })
      });
      const uploaded = await fetch(created.uploadUrl, { method: 'PUT', headers: { 'content-type': file.type || 'application/octet-stream' }, body: file });
      if (!uploaded.ok) throw new Error('upload');
      onCreated(`${location.origin}${created.shareUrl}`);
    } catch {
      setError(true);
    } finally { setBusy(false); }
  };

  return (
    <section className="upload-wrap">
      <div className="upload-side">
        <button
          type="button"
          className="dropzone"
          onClick={() => input.current?.click()}
          onDrop={onDrop}
          onDragOver={onDragOver}
          onDragLeave={onDragLeave}
          disabled={busy}
        >
          <Folder variant={theme} open={file ? true : dragging} />
          <span className={`dropzone-label ${file ? 'has-file' : ''}`} role="status">
            <strong>{file ? file.name : copy.dropFile}</strong>
            <span className="meta"> · {file ? formatSize(file.size) : copy.chooseFile}</span>
          </span>
        </button>
        <input ref={input} id="file" aria-label={copy.chooseFile} type="file" hidden onChange={(event: ChangeEvent<HTMLInputElement>) => select(event.target.files)} />
      </div>

      <div className="controls">
        <div className="ctrl">
          <label>
            <span className="cap"><span>{copy.expiration}</span></span>
            <select value={expiresInSeconds} onChange={event => setExpiresInSeconds(event.target.value)} disabled={!file}>
              {LIFETIMES.map(seconds => (
                <option key={seconds} value={seconds}>{lifetimeLabel(copy, seconds)}</option>
              ))}
            </select>
          </label>
        </div>

        <div className="ctrl">
          <label>
            <span className="cap"><span>{copy.password}</span><span className="req">{copy.optional}</span></span>
            <input type="password" value={password} onChange={event => setPassword(event.target.value)} placeholder="••••••••" disabled={!file} autoComplete="new-password" />
          </label>
        </div>

        <div className="ctrl">
          <label>
            <span className="cap"><span>{copy.downloadLimit}</span></span>
            <div className="segmented" role="group" aria-label={copy.downloadLimit}>
              {['', '1', '3', '10'].map(value => (
                <button key={value || 'unlimited'} type="button" aria-pressed={maxDownloads === value} disabled={!file}
                  onClick={() => setMaxDownloads(value)}>{value === '' ? copy.unlimited : value}</button>
              ))}
            </div>
          </label>
        </div>

        <button className="btn btn-primary" type="button" disabled={!file || busy} onClick={create}>
          {busy ? '…' : copy.create}
        </button>

        {busy && <p className="status busy" role="status"><span className="led" />{copy.uploading}</p>}
        {error && <p className="status err" role="alert"><span className="led" />{copy.failed}</p>}
      </div>
    </section>
  );
}

function lifetimeLabel(_copy: Copy, seconds: number): string {
  if (seconds === 300) return '5 MIN';
  if (seconds === 3600) return '1 HR';
  if (seconds === 21600) return '6 HR';
  if (seconds === 86400) return '24 HR';
  if (seconds === 259200) return '3 D';
  if (seconds === 604800) return '7 D';
  return '30 D';
}
