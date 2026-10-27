const KEY = 'fp-cookie-consent';
export const CONSENT_VERSION = 1;
export type ConsentChoice = 'accepted' | 'rejected';

export function readConsent(): ConsentChoice | null {
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { choice?: ConsentChoice; version?: number };
    // A policy change (version bump) re-prompts; a corrupt value is treated as "no choice".
    if (parsed.version !== CONSENT_VERSION || (parsed.choice !== 'accepted' && parsed.choice !== 'rejected')) return null;
    return parsed.choice;
  } catch {
    return null;
  }
}

export function saveConsent(choice: ConsentChoice): void {
  try {
    window.localStorage.setItem(KEY, JSON.stringify({ choice, version: CONSENT_VERSION, at: new Date().toISOString() }));
  } catch {
    /* storage unavailable: the banner simply shows again next visit */
  }
}

/** Optional (non-essential) scripts must check this before loading. FlowPilot ships none yet. */
export const analyticsAllowed = (): boolean => readConsent() === 'accepted';
