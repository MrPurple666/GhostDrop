import { ChangeEvent, useRef, useState } from 'react';
import './styles.css';
import { Locale, messages } from './messages';

export function App() {
  const [file, setFile] = useState<File>();
  const input = useRef<HTMLInputElement>(null);
  const locale: Locale = navigator.language === 'pt-BR' ? 'pt-BR' : 'en-US';
  const copy = messages[locale];
  const select = (files: FileList | null) => setFile(files?.[0]);
  const onDrop = (event: React.DragEvent<HTMLButtonElement>) => {
    event.preventDefault();
    select(event.dataTransfer.files);
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
      <label>{copy.expiration}<select defaultValue="86400"><option value="300">5 minutes</option><option value="3600">1 hour</option><option value="21600">6 hours</option><option value="86400">24 hours</option><option value="259200">3 days</option><option value="604800">7 days</option><option value="2592000">30 days</option></select></label>
      <label>{copy.password}<input type="password" placeholder={copy.optional} autoComplete="new-password" /></label>
      <label>{copy.downloadLimit}<select defaultValue=""><option value="">{copy.unlimited}</option><option value="1">1</option><option value="3">3</option><option value="10">10</option></select></label>
      <button className="primary" type="button" disabled={!file}>{copy.create}</button>
    </section>
  </main>;
}
