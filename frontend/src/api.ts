export function apiUrl(path: string): string {
  return `${import.meta.env.VITE_API_URL ?? ''}${path}`;
}

export async function apiJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(apiUrl(path), init);
  if (!response.ok) throw new Error(String(response.status));
  return response.json() as Promise<T>;
}
