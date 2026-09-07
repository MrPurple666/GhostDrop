import { ChangeEvent, useRef, useState } from 'react';
import { apiJson } from './api';
import { Copy } from './locale';
import { formatSize } from './messages';

interface CreateResponse {
  uploadUrl: string;
  shareUrl: string;
}

interface UploadPageProps {
  copy: Copy;
  onCreated: (shareUrl: string) => void;
}

export function UploadPage({ copy, onCreated }: UploadPageProps) {
  const [file, setFile] = useState<File>();
  const [expiresInSeconds, setExpiresInSeconds] = useState('86400');
  const [password, setPassword] = useState('');
  const [maxDownloads, setMaxDownloads] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const select = (files: FileList | null) => setFile(files?.[0]);
  const onDrop = (event: React.DragEvent<HTMLButtonElement>) => {
    event.preventDefault();
    select(event.dataTransfer.files);
  };

  const create = async () => {
    if (!file) return;
    setBusy(true);
    setError(false);
    try {
      const created = await apiJson<CreateResponse>('/api/v1/uploads', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          fileName: file.name,
          contentType: file.type || 'application/octet-stream',
          fileSize: file.size,
          expiresInSeconds: Number(expiresInSeconds),
          password: password || undefined,
          maxDownloads: maxDownloads ? Number(maxDownloads) : undefined
        })
      });
      const uploaded = await fetch(created.uploadUrl, { method: 'PUT', headers: { 'content-type': file.type || 'application/octet-stream' }, body: file });
      if (!uploaded.ok) throw new Error('upload');
      onCreated(`${location.origin}${created.shareUrl}`);
    } catch {
      setError(true);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="panel" aria-labelledby="upload-title">
      <h2 id="upload-title">{copy.dropFile}</h2>
      <button className="dropzone" type="button" onClick={() => input.current?.click()} onDrop={onDrop} onDragOver={event => event.preventDefault()}>
        <span aria-hidden="true">◌</span><strong>{copy.dropFile}</strong><small>{file ? `${file.name} · ${formatSize(file.size)}` : copy.chooseFile}</small>
      </button>
      <input ref={input} id="file" aria-label={copy.chooseFile} type="file" hidden onChange={(event: ChangeEvent<HTMLInputElement>) => select(event.target.files)} />
      <label>{copy.expiration}
        <select value={expiresInSeconds} onChange={event => setExpiresInSeconds(event.target.value)}>
          <option value="300">5 minutes</option><option value="3600">1 hour</option><option value="21600">6 hours</option><option value="86400">24 hours</option><option value="259200">3 days</option><option value="604800">7 days</option><option value="2592000">30 days</option>
        </select>
      </label>
      <label>{copy.password}<input type="password" value={password} onChange={event => setPassword(event.target.value)} placeholder={copy.optional} autoComplete="new-password" /></label>
      <label>{copy.downloadLimit}
        <select value={maxDownloads} onChange={event => setMaxDownloads(event.target.value)}>
          <option value="">{copy.unlimited}</option><option value="1">1</option><option value="3">3</option><option value="10">10</option>
        </select>
      </label>
      <button className="primary" type="button" disabled={!file || busy} onClick={create}>{busy ? copy.uploading : copy.create}</button>
      {error && <p className="error" role="alert">{copy.failed}</p>}
    </section>
  );
}
