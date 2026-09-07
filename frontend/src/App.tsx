import { ChangeEvent, useRef, useState } from 'react';
import './styles.css';

export function App() {
  const [file, setFile] = useState<File>();
  const input = useRef<HTMLInputElement>(null);
  const select = (files: FileList | null) => setFile(files?.[0]);
  const onDrop = (event: React.DragEvent<HTMLButtonElement>) => {
    event.preventDefault();
    select(event.dataTransfer.files);
  };

  return <main className="page">
    <header><p className="mark">GHOSTDROP</p><h1>Files that vanish.</h1></header>
    <section className="panel" aria-labelledby="upload-title">
      <h2 id="upload-title">Drop a file.</h2>
      <button className="dropzone" type="button" onClick={() => input.current?.click()} onDrop={onDrop} onDragOver={event => event.preventDefault()}>
        <span aria-hidden="true">◌</span><strong>Drop your file here</strong><small>or choose a file</small>
      </button>
      <input ref={input} id="file" aria-label="Choose file" type="file" hidden onChange={(event: ChangeEvent<HTMLInputElement>) => select(event.target.files)} />
      {file && <p className="file-name" role="status">{file.name}</p>}
      <label>Expires after<select defaultValue="86400"><option value="300">5 minutes</option><option value="3600">1 hour</option><option value="21600">6 hours</option><option value="86400">24 hours</option><option value="259200">3 days</option><option value="604800">7 days</option><option value="2592000">30 days</option></select></label>
      <label>Password<input type="password" placeholder="Optional" autoComplete="new-password" /></label>
      <label>Download limit<select defaultValue=""><option value="">Unlimited</option><option value="1">1</option><option value="3">3</option><option value="10">10</option></select></label>
      <button className="primary" type="button" disabled={!file}>Create GhostDrop</button>
    </section>
  </main>;
}
