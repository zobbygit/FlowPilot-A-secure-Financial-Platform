import { useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { Alert } from './Alert';
import { Button } from './Button';
import { api, toApiError } from '../lib/api';
import { AVATAR_MAX, AVATAR_TYPES, checkFile } from '../lib/files';

export function AvatarUploader({ url, name }: { url: string | null; name: string }) {
  const qc = useQueryClient();
  const ref = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const done = () => { 
    qc.invalidateQueries({ queryKey: ['profile'] }); 
    qc.invalidateQueries({ queryKey: ['me'] }); 
  };

  const upload = async (file: File) => {
    const bad = checkFile(file, AVATAR_TYPES, AVATAR_MAX);
    if (bad) return setError(bad);
    setBusy(true); 
    setError(null);
    
    try { 
      const fd = new FormData(); 
      fd.append('file', file); 
      
      // Correctly sends FormData without manual headers
      await api.post('/users/me/avatar', fd); 
      done(); 
    }
    catch (e) { 
      setError(toApiError(e).message); 
    } finally { 
      setBusy(false); 
      if (ref.current) ref.current.value = ''; 
    }
  };

  const remove = async () => { 
    setBusy(true); 
    try { 
      await api.delete('/users/me/avatar'); 
      done(); 
    } catch (e) { 
      setError(toApiError(e).message); 
    } finally { 
      setBusy(false); 
    } 
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-4">
        {url ? <img src={url} alt={`${name} profile`} className="h-16 w-16 rounded-full object-cover" referrerPolicy="no-referrer" />
          : <span aria-hidden="true" className="flex h-16 w-16 items-center justify-center rounded-full bg-accent/20 text-xl font-bold text-accent">{name.slice(0, 1).toUpperCase()}</span>}
        <div className="flex flex-wrap gap-2">
          <input ref={ref} id="avatar-file" type="file" accept={AVATAR_TYPES.join(',')} className="sr-only" onChange={(e) => e.target.files?.[0] && upload(e.target.files[0])} />
          <Button variant="secondary" loading={busy} onClick={() => ref.current?.click()}>Change photo</Button>
          {url && <Button variant="ghost" disabled={busy} onClick={remove}>Remove</Button>}
        </div>
      </div>
      <p className="text-xs text-muted">JPEG, PNG or WebP, up to 2 MB.</p>
      {error && <Alert>{error}</Alert>}
    </div>
  );
}