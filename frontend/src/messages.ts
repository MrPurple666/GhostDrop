export type Locale = 'en-US' | 'pt-BR';

export const messages: Record<Locale, Record<string, string>> = {
  'en-US': {
    title: 'Files that vanish.',
    dropFile: 'Drop a file here',
    chooseFile: 'Choose file',
    expiration: 'Expires after',
    password: 'Password',
    optional: 'Optional',
    downloadLimit: 'Download limit',
    unlimited: 'Unlimited',
    create: 'Create GhostDrop',
    creating: 'Creating upload…',
    uploading: 'Uploading…',
    complete: 'Upload complete. The link is ready.',
    failed: 'Upload failed. Check the file and try again.',
    shareLink: 'Share link',
    copy: 'Copy',
    copied: 'Copied',
    makeAnother: 'Create another GhostDrop',
    download: 'Download file',
    passwordProtected: 'This GhostDrop is password protected.',
    unlock: 'Unlock and download',
    downloading: 'Preparing download…',
    wrongPassword: 'Incorrect password.',
    unavailable: 'This GhostDrop is no longer available.',
    remainingDownloads: 'Remaining downloads',
    size: 'Size'
  },
  'pt-BR': {
    title: 'Arquivos que desaparecem.',
    dropFile: 'Solte seu arquivo aqui',
    chooseFile: 'Escolha um arquivo',
    expiration: 'Expira em',
    password: 'Senha',
    optional: 'Opcional',
    downloadLimit: 'Limite de downloads',
    unlimited: 'Ilimitado',
    create: 'Criar GhostDrop',
    creating: 'Criando upload…',
    uploading: 'Enviando…',
    complete: 'Upload concluído. O link está pronto.',
    failed: 'Falha no upload. Verifique o arquivo e tente de novo.',
    shareLink: 'Link para compartilhar',
    copy: 'Copiar',
    copied: 'Copiado',
    makeAnother: 'Criar outro GhostDrop',
    download: 'Baixar arquivo',
    passwordProtected: 'Este GhostDrop é protegido por senha.',
    unlock: 'Desbloquear e baixar',
    downloading: 'Preparando download…',
    wrongPassword: 'Senha incorreta.',
    unavailable: 'Este GhostDrop não está mais disponível.',
    remainingDownloads: 'Downloads restantes',
    size: 'Tamanho'
  }
};

export function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
