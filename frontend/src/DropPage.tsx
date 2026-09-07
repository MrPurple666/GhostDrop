import { useEffect, useState } from 'react';
import { apiJson } from './api';
import { Copy } from './locale';
import { formatSize } from './messages';

interface FileInfo {
  fileName: string;
  contentType: string;
  fileSize: number;
  passwordProtected: boolean;
  remainingDownloads: number | null;
}

interface DownloadResponse {
  downloadUrl: string;
}

interface DropPageProps {
  id: string;
  copy: Copy;
}

export function DropPage({ id, copy }: DropPageProps) {
  const [info, setInfo] = useState<FileInfo>();
  const [unavailable, setUnavailable] = useState(false);
  const [password, setPassword] = useState('');
  const [wrong, setWrong] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    apiJson<FileInfo>(`/api/v1/files/${id}`).then(
      value => { if (active) setInfo(value); },
      () => { if (active) setUnavailable(true); }
    );
    return () => { active = false; };
  }, [id]);

  const download = async () => {
    setBusy(true);
    setWrong(false);
    try {
      const created = await apiJson<DownloadResponse>(`/api/v1/files/${id}/downloads`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: info?.passwordProtected ? JSON.stringify({ password }) : '{}'
      });
      location.href = created.downloadUrl;
    } catch {
      setWrong(true);
      setBusy(false);
    }
  };

  return (
    <section className="panel drop" aria-busy={!info && !unavailable}>
      {unavailable && <>
        <h2>{copy.unavailable}</h2>
        <a href="/">{copy.makeAnother}</a>
      </>}
      {!unavailable && !info && <p className="status" role="status">{copy.downloading}</p>}
      {!unavailable && info && <>
        <h2 className="file-name">{info.fileName}</h2>
        <dl className="meta">
          <div><dt>{copy.size}</dt><dd>{formatSize(info.fileSize)}</dd></div>
          {info.remainingDownloads != null && <div><dt>{copy.remainingDownloads}</dt><dd>{info.remainingDownloads}</dd></div>}
        </dl>
        {info.passwordProtected && <>
          <label>{copy.passwordProtected}
            <input type="password" value={password} onChange={event => { setPassword(event.target.value); setWrong(false); }} aria-invalid={wrong} autoComplete="current-password" />
          </label>
          {wrong && <p className="error" role="alert">{copy.wrongPassword}</p>}
        </>}
        <button className="primary" type="button" disabled={busy} onClick={download}>{copy.download}</button>
      </>}
    </section>
  );
}
