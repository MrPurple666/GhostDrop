import { useEffect, useState } from 'react';
import { DropPage } from './DropPage';
import { Copy, LocaleKey, detectLocale } from './locale';
import { messages } from './messages';
import { UploadPage } from './UploadPage';

type Theme = 'dark' | 'light';

function useTheme(): [Theme, (t: Theme) => void] {
  const [theme, setTheme] = useState<Theme>(() => {
    try {
      const stored = localStorage.getItem('ghostdrop-theme');
      if (stored === 'dark' || stored === 'light') return stored;
      if (typeof window.matchMedia === 'function' && window.matchMedia('(prefers-color-scheme: light)').matches) return 'light';
    } catch { /* storage unavailable */ }
    return 'dark';
  });
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try { localStorage.setItem('ghostdrop-theme', theme); } catch { /* storage unavailable */ }
  }, [theme]);
  return [theme, setTheme];
}

function ShareView({ copy, link, onReset }: { copy: Copy; link: string; onReset: () => void }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = async () => {
    try { await navigator.clipboard.writeText(link); setCopied(true); } catch { /* clipboard unavailable */ }
  };
  return (
    <section className="result" aria-live="polite">
      <p className="status ok"><span className="led" />{copy.complete}</p>
      <div className="field">
        <span className="cap">{copy.shareLink}</span>
        <div className="urlbox">
          <input readOnly value={link} onFocus={event => event.currentTarget.select()} aria-label={copy.shareLink} />
          <button className="btn btn-ghost" type="button" onClick={handleCopy}>{copied ? copy.copied : copy.copy}</button>
        </div>
      </div>
      <button className="btn btn-ghost" type="button" style={{ width: 'fit-content', marginTop: 8 }} onClick={onReset}>← {copy.makeAnother}</button>
    </section>
  );
}

export function App() {
  const [locale, setLocale] = useState<LocaleKey>(() => detectLocale(navigator.language));
  const [shareUrl, setShareUrl] = useState('');
  const [theme, setTheme] = useTheme();
  const copy: Copy = messages[locale];
  const match = location.pathname.match(/^\/d\/([^/]+)$/);
  const dropId = match ? decodeURIComponent(match[1]) : null;

  return (
    <>
      <div className="topbar">
        <span className="brand">GHOSTDROP<span className="dot">_</span></span>
        <div className="seg" role="group" aria-label="Theme">
          {(['dark', 'light'] as Theme[]).map(t => (
            <button key={t} type="button" aria-pressed={theme === t} onClick={() => setTheme(t)}>{t === 'dark' ? 'OLED' : 'PAPER'}</button>
          ))}
        </div>
        <div className="seg" role="group" aria-label="Language">
          {(['en-US', 'pt-BR'] as LocaleKey[]).map(key => (
            <button key={key} type="button" className={key === locale ? 'active' : ''} aria-pressed={key === locale} onClick={() => setLocale(key)}>{key === 'en-US' ? 'EN' : 'PT'}</button>
          ))}
        </div>
      </div>

      <main className="page">
        {dropId ? <DropPage key={dropId} id={dropId} copy={copy} /> : (
          <>
            <header className="hero">
              <p className="eyebrow">TEMP · AUTO-ERASE · NO TRACE</p>
              <h1 className="hero-title">{copy.title}</h1>
            </header>
            {shareUrl ? <ShareView copy={copy} link={shareUrl} onReset={() => setShareUrl('')} /> : <UploadPage copy={copy} onCreated={setShareUrl} theme={theme} />}
          </>
        )}
      </main>

      <footer className="foot">
        <span>Files never touch the server</span>
        <span>END-TO-END · PRESIGNED TRANSFERS</span>
      </footer>
    </>
  );
}
