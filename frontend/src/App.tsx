import { ChangeEvent, useRef, useState } from 'react';
import './styles.css';
import { Locale, messages } from './messages';

export function App() {
  const [file, setFile] = useState<File>();
  const [expiresInSeconds, setExpiresInSeconds] = useState('86400');
  const [password, setPassword] = useState('');
  const [maxDownloads, setMaxDownloads] = useState('');
  const [shareUrl, setShareUrl] = useState('');
  const [status, setStatus] = useState('');
  const input = useRef<HTMLInputElement>(null);
  const locale: Locale = navigator.language === 'pt-BR' ? 'pt-BR' : 'en-US';
  const copy = messages[locale];
  const select = (files: FileList | null) => setFile(files?.[0]);
  const onDrop = (event: React.DragEvent<HTMLButtonElement>) => {
    event.preventDefault();
    select(event.dataTransfer.files);
  };
  const create = async () => {
    if (!file) return;
    setStatus('Creating upload…');
    try {
      const response = await fetch(`${import.meta.env.VITE_API_URL ?? ''}/api/v1/uploads`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ fileName: file.name, contentType: file.type || 'application/octet-stream', fileSize: file.size, expiresInSeconds: Number(expiresInSeconds), password: password || undefined, maxDownloads: maxDownloads ? Number(maxDownloads) : undefined })
      });
      if (!response.ok) throw new Error('create');
      const upload = await response.json() as { uploadUrl: string; shareUrl: string };
      setStatus('Uploading…');
      const uploaded = await fetch(upload.uploadUrl, { method: 'PUT', headers: { 'content-type': file.type || 'application/octet-stream' }, body: file });
      if (!uploaded.ok) throw new Error('upload');
      setShareUrl(`${location.origin}${upload.shareUrl}`);
      setStatus('Upload complete. The link is ready.');
    } catch {
      setStatus('Upload failed. Check the file and try again.');
    }
  };

  return <main className="page">
    <header><p className="mark">GHOSTDROP</p><h1>{copy.title}</h1></header>
    <section className="panel" aria-labelledby="upload-title">
      <h2 id="upload-title">{copy.dropFile}</h2>
      <button className="dropzone" type="button" onClick={() => input.current?.click()} onDrop={onDrop} onDragOver={event => event.preventDefault()}>
        <span aria-hidden="true">◌</span><strong>{copy.dropFile}</strong><small>{copy.chooseFile}</small>
      </button>
      <input ref={input} id="file" aria-label={copy.chooseFile} type="file" hidden onChange={(event: ChangeEvent<HTMLInputElement>) => select(event.target.files)} />
      {file && <p className="file-name" role="status">{file.name}</p>}
      <label>{copy.expiration}<select value={expiresInSeconds} onChange={event => setExpiresInSeconds(event.target.value)}><option value="300">5 minutes</option><option value="3600">1 hour</option><option value="21600">6 hours</option><option value="86400">24 hours</option><option value="259200">3 days</option><option value="604800">7 days</option><option value="2592000">30 days</option></select></label>
      <label>{copy.password}<input type="password" value={password} onChange={event => setPassword(event.target.value)} placeholder={copy.optional} autoComplete="new-password" /></label>
      <label>{copy.downloadLimit}<select value={maxDownloads} onChange={event => setMaxDownloads(event.target.value)}><option value="">{copy.unlimited}</option><option value="1">1</option><option value="3">3</option><option value="10">10</option></select></label>
      <button className="primary" type="button" disabled={!file || status === 'Creating upload…' || status === 'Uploading…'} onClick={create}>{copy.create}</button>
      {status && <p role="status">{status}</p>}
      {shareUrl && <label>Share link<input readOnly value={shareUrl} onFocus={event => event.currentTarget.select()} /></label>}
    </section>
  </main>;
}
