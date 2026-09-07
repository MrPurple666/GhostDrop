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
    create: 'Create GhostDrop'
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
    create: 'Criar GhostDrop'
  }
};
