import { useEffect, useState } from 'react';
import { apiJson } from './api';
import { Copy } from './locale';
import { formatSize } from './messages';

interface FileInfo { fileName: string; fileSize: number; passwordProtected: boolean; remainingDownloads: number | null; }
interface DownloadResponse { downloadUrl: string; }
interface DropPageProps { id: string; copy: Copy; }

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
    setBusy(true); setWrong(false);
    try {
      const created = await apiJson<DownloadResponse>(`/api/v1/files/${id}/downloads`, {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: info?.passwordProtected ? JSON.stringify({ password }) : '{}'
      });
      location.href = created.downloadUrl;
    } catch { setWrong(true); setBusy(false); }
  };

  return (
    <section className="drop-card" aria-busy={!info && !unavailable}>
      {unavailable ? (
        <>
          <p className="eyebrow">404 — DROP</p>
          <h1 className="hero-title">{copy.unavailable}</h1>
          <a className="backlink" href="/">← {copy.makeAnother}</a>
        </>
      ) : !info ? (
        <p className="status busy" role="status"><span className="led" />{copy.downloading}</p>
      ) : (
        <>
          <p className="eyebrow">DROP · {id.slice(0, 8).toUpperCase()}</p>
          <h1 className="file-name">{info.fileName}</h1>

          <div className="meta-row">
            <div className="meta-cell"><div className="cap">{copy.size}</div><div className="val">{formatSize(info.fileSize)}</div></div>
            {info.remainingDownloads != null && (
              <div className="meta-cell"><div className="cap">{copy.remainingDownloads}</div><div className="val">{info.remainingDownloads}</div></div>
            )}
          </div>

          {info.passwordProtected && (
            <div className="ctrl" style={{ marginBottom: 16 }}>
              <label>
                <span className="cap"><span>{copy.passwordProtected}</span></span>
                <input type="password" value={password} onChange={event => { setPassword(event.target.value); setWrong(false); }} placeholder="••••••••" autoComplete="current-password" aria-invalid={wrong} />
              </label>
            </div>
          )}

          <button className="btn btn-primary" type="button" disabled={busy} onClick={download}>
            {info.passwordProtected ? copy.unlock : copy.download}
          </button>

          {wrong && <p className="status err" role="alert"><span className="led" />{copy.wrongPassword}</p>}
          <a className="backlink" href="/">← {copy.makeAnother}</a>
        </>
      )}
    </section>
  );
}
