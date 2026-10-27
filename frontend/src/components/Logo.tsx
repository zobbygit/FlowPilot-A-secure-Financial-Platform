export function Logo({ className = '' }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2 font-semibold tracking-tight ${className}`}>
      <svg width="28" height="28" viewBox="0 0 64 64" aria-hidden="true">
        <defs>
          <linearGradient id="fp-logo" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#8b5cf6" />
            <stop offset="1" stopColor="#4c1d95" />
          </linearGradient>
        </defs>
        <rect width="64" height="64" rx="16" fill="rgb(var(--fp-surface-2))" />
        <path d="M20 14h26l-8 12H28v8h14l-8 12H20z" fill="url(#fp-logo)" />
      </svg>
      <span className="text-lg">FlowPilot</span>
    </span>
  );
}
