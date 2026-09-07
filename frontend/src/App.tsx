import { useState } from 'react';
import { DropPage } from './DropPage';
import { Copy, LocaleKey, detectLocale } from './locale';
import { messages } from './messages';
import { UploadPage } from './UploadPage';

function ShareView({ copy, link, onReset }: { copy: Copy; link: string; onReset: () => void }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(link);
      setCopied(true);
    } catch {
      /* clipboard unavailable */
    }
  };
  return (
    <section className="panel">
      <h2>{copy.complete}</h2>
      <div className="share">
        <input readOnly value={link} onFocus={event => event.currentTarget.select()} aria-label={copy.shareLink} />
        <button className="secondary" type="button" onClick={handleCopy}>{copied ? copy.copied : copy.copy}</button>
      </div>
      <a className="back" href="/" onClick={onReset}>{copy.makeAnother}</a>
    </section>
  );
}

export function App() {
  const [locale, setLocale] = useState<LocaleKey>(() => detectLocale(navigator.language));
  const copy: Copy = messages[locale];
  const [shareUrl, setShareUrl] = useState('');
  const match = location.pathname.match(/^\/d\/([^/]+)$/);
  const dropId = match ? decodeURIComponent(match[1]) : null;

  const toggle = (target: LocaleKey) => {
    setLocale(target);
    try { localStorage.setItem('ghostdrop-locale', target); } catch { /* storage unavailable */ }
  };

  return (
    <main className="page">
      <header>
        <div className="topline">
          <p className="mark">GHOSTDROP</p>
          <div className="locales" role="group" aria-label="Language">
            {(['en-US', 'pt-BR'] as LocaleKey[]).map(key => (
              <button key={key} type="button" className={key === locale ? 'active' : ''} aria-pressed={key === locale} onClick={() => toggle(key)}>{key === 'en-US' ? 'EN' : 'PT'}</button>
            ))}
          </div>
        </div>
        <h1>{copy.title}</h1>
      </header>
      {dropId ? <DropPage key={dropId} id={dropId} copy={copy} /> : shareUrl ? <ShareView copy={copy} link={shareUrl} onReset={() => setShareUrl('')} /> : <UploadPage copy={copy} onCreated={setShareUrl} />}
    </main>
  );
}
